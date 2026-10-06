package se.partee71.dagboken.reminders

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import se.partee71.dagboken.core.engine.Reminder
import se.partee71.dagboken.core.engine.ReminderAlarm
import se.partee71.dagboken.core.engine.nextAlarm
import se.partee71.dagboken.core.engine.nextAlarmAt
import se.partee71.dagboken.core.engine.reminderAlarms
import se.partee71.dagboken.core.engine.restoredAlarms
import se.partee71.dagboken.core.model.ReminderSettings
import se.partee71.dagboken.core.model.Occasion
import se.partee71.dagboken.core.model.Slot
import se.partee71.dagboken.data.auth.AuthRepository
import se.partee71.dagboken.data.common.UserScope
import se.partee71.dagboken.data.repository.SettingsRepository

/** Vad [AlarmScheduler.rescheduleAll] gjorde. */
enum class Rescheduled {
    /** Alla larm avbokades och de påslagna lades på nytt. */
    SCHEDULED,

    /** Inställningarna gick inte att läsa (tom cache utan nät) – larmen som ligger står kvar orörda. */
    KEPT,

    /** Ingen inloggad – alla larm avbokades (AUTH-6). */
    SIGNED_OUT,

    /**
     * Efter en omstart, när inställningarna inte gick att läsa: systemet hade tömt larmen, så de senast schemalagda
     * tiderna lades om (`restoredAlarms`) tills inställningarna går att läsa.
     */
    RESTORED,
}

/**
 * Enda stället med larmlogik (NOT-2…NOT-8, NOT-12…NOT-15, NOT-18; skill notifications-alarms): lägger och
 * avbokar påminnelsernas larm i AlarmManager. **Vilka** larm och **när** räknas i `:core` (`reminderAlarms`,
 * `nextAlarm`); inställningarna läses ur `settings/app` i cachen via [SettingsRepository], samma väg som UI:t.
 * Varje påminnelse har en unik `requestCode`, alla PendingIntents är `FLAG_IMMUTABLE` och explicita mot appens
 * egna, icke-exporterade mottagare. Exakta larm när appen får (`canScheduleExactAlarms`), annars inexakta – alltid
 * `…AndAllowWhileIdle`, så att de går igenom i Doze (NOT-8).
 */
@Singleton
class AlarmScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settings: SettingsRepository,
    private val user: UserScope,
    /** Inloggningens eget läge – skiljer "utloggad" från "inloggningen inte inläst än" vid kallstart. */
    private val auth: AuthRepository,
    private val clock: Clock,
    /** Senast schemalagt och senast utlöst per påminnelse, enhetslokalt (NOT-14). */
    private val ledger: AlarmLedger,
    /** Enhetens tidszon, läst vid varje schemaläggning – en ny zon slår igenom vid nästa. */
    private val zone: Provider<TimeZone>,
) {
    private val alarmManager: AlarmManager = checkNotNull(context.getSystemService(AlarmManager::class.java))

    /**
     * Skyddar bara själva ändringen i AlarmManager och [ledger] – kort, aldrig över en läsning – så att två samtidiga
     * omschemaläggningar (omstart och synk) inte blandar sina larm och en utlöst påminnelses nästa larm
     * ([rescheduleNext]) aldrig får vänta ut en annans läsningar. Ett vanligt JVM-lås – inget inuti suspenderar – så att
     * [markFired] kan ta det synkront i en mottagares `onReceive`.
     */
    private val lock = Any()

    /**
     * Ökas av [rescheduleAll] och [cancelAll] under [lock]. [rescheduleNext] läser utanför låset och lägger bara sitt larm
     * om ingen av dem hunnit emellan – annars vinner deras nyare beslut (utloggning, avslagen påminnelse).
     */
    private var generation = 0L

    /**
     * NOT-14: påminnelsen har just levererats – registreras synkront (i `onReceive`, innan något suspenderar) och under
     * samma lås som [rescheduleAll] räknar sina larm, så att en omschemaläggning som hinner före mottagarens coroutine
     * aldrig lägger det passerade larmet igen.
     */
    fun markFired(reminder: Reminder) = synchronized(lock) { ledger.setFired(reminder, clock.now()) }

    /** Om appen får ställa exakta larm; annars kommer påminnelserna ungefärligt (NOT-8, NOT-16). */
    fun canScheduleExactAlarms(): Boolean = context.reminderAccess().exactAlarms

    /**
     * NOT-7, NOT-14, NOT-15: alla larm efter inställningarna – först avbokas alla, sedan läggs de påslagna. Bara i
     * bekräftat utloggat läge avbokas allt (AUTH-6). Hinner inloggningen inte läsas in vid kallstart, eller går
     * inställningarna inte att läsa – tom cache utan nät – rörs ingenting: larmen som ligger står kvar tills data
     * finns. Har systemet tömt larmen ([alarmsCleared]: omstart, appuppdatering) läggs i stället de senast schemalagda
     * tiderna om ur [AlarmLedger] (NOT-6), så att kedjan inte är borta tills appen öppnas.
     *
     * [known] är inställningarna när anroparen redan har dem och vet att någon är inloggad (`ReminderSync`) – då
     * varken väntas på sessionen eller läses de om. Standardvärdena räknas inte som kända: så ser ett dokument ut som
     * saknas i cachen, och de bekräftas därför med en läsning.
     */
    suspend fun rescheduleAll(alarmsCleared: Boolean = false, known: ReminderSettings? = null): Rescheduled {
        val settings = known?.takeIf { it != ReminderSettings() } ?: when (session()) {
            Session.SIGNED_OUT -> {
                cancelAll()
                return Rescheduled.SIGNED_OUT
            }
            Session.UNKNOWN -> null
            Session.SIGNED_IN -> readSettings()?.getOrNull()?.reminders
        }
        val exact = canScheduleExactAlarms()
        if (settings == null) {
            if (!alarmsCleared) return Rescheduled.KEPT
            synchronized(lock) { restoredAlarms(ledger.scheduled(), ledger.fired(), clock.now(), zone.get(), LATE_GRACE).forEach { schedule(it, exact) } }
            return Rescheduled.RESTORED
        }
        synchronized(lock) {
            // Läst under låset: en påminnelse som utlösts under läsningen ovan räknas.
            val alarms = reminderAlarms(settings, clock.now(), zone.get(), LATE_GRACE, ledger.scheduled(), ledger.fired())
            generation++
            cancelAllAlarms()
            alarms.forEach { schedule(it, exact) }
        }
        return Rescheduled.SCHEDULED
    }

    /**
     * NOT-14: en påminnelse som just utlösts lägger sitt nästa larm – efter inställningarna när de går att läsa
     * (avslagen påminnelse: inget nytt larm), annars på [lastKnownTime], så att kedjan inte bryts för att cachen är
     * tom eller inloggningen inte hunnit läsas in vid kallstart. Bara i bekräftat utloggat läge läggs inget nytt.
     * Tar högst några sekunder – mottagaren lägger nästa larm före notisen.
     */
    suspend fun rescheduleNext(reminder: Reminder, lastKnownTime: LocalTime?) {
        val now = clock.now()
        val started = synchronized(lock) {
            ledger.setFired(reminder, now)
            generation
        }
        val fallback = lastKnownTime?.let { nextAlarmAt(reminder, it, now, zone.get()) }
        val next = when (session()) {
            Session.SIGNED_OUT -> null
            Session.UNKNOWN -> fallback
            // Lästa inställningar gäller – också när påminnelsen slagits av (inget nytt larm); bara utan svar används fallback.
            Session.SIGNED_IN -> when (val read = readSettings()) {
                null -> fallback
                else -> read.fold({ nextAlarm(reminder, it.reminders, now, zone.get()) }, { fallback })
            }
        }
        val exact = canScheduleExactAlarms()
        synchronized(lock) {
            if (generation != started) return
            cancel(reminder)
            next?.let { schedule(it, exact) }
        }
    }

    /**
     * AUTH-6: utloggad – avbokar alla påminnelsers larm, också de som inte är påslagna, och tömmer [AlarmLedger]: nästa
     * användare på enheten ska inte ärva den förras utlösningstider.
     */
    fun cancelAll() = synchronized(lock) {
        generation++
        cancelAllAlarms()
        ledger.clear()
    }

    private fun cancelAllAlarms() = ALL.forEach(::cancel)

    /** Lägger [alarm] – exakt när [exact] (läst en gång per omschemaläggning), annars inexakt (NOT-8). */
    private fun schedule(alarm: ReminderAlarm, exact: Boolean) {
        val pending = PendingIntent.getBroadcast(
            context,
            requestCode(alarm.reminder),
            intent(alarm.reminder).putExtra(ReminderIntents.EXTRA_TIME, alarm.time.toString()),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val at = alarm.at.toEpochMilliseconds()
        val exactSet = exact && try {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pending)
            true
        } catch (_: SecurityException) {
            // Rätten drogs in mellan kontrollen och anropet – inexakt i stället för en krasch (NOT-8).
            false
        }
        if (!exactSet) alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pending)
        // Först när larmet ligger: annars kunde en omstart tro att ett larm som aldrig sattes var schemalagt.
        ledger.setScheduled(alarm.reminder, alarm.at)
    }

    private fun cancel(reminder: Reminder) {
        ledger.clearScheduled(reminder)
        PendingIntent.getBroadcast(context, requestCode(reminder), intent(reminder), PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE)
            ?.let { pending ->
                alarmManager.cancel(pending)
                pending.cancel()
            }
    }

    /** Larmets Intent utan klockslaget – det PendingIntent jämför (action, mottagare); extras skiljer inte larm åt. */
    private fun intent(reminder: Reminder): Intent = when (reminder) {
        is Reminder.Med -> Intent(context, MedAlarmReceiver::class.java)
            .setAction(ReminderIntents.ACTION_MED_ALARM)
            .putExtra(ReminderIntents.EXTRA_SLOT, reminder.slot.wire)
        is Reminder.Mood -> Intent(context, ScreeningReminderReceiver::class.java)
            .setAction(ReminderIntents.ACTION_MOOD_ALARM)
            .putExtra(ReminderIntents.EXTRA_OCCASION, reminder.occasion.wire)
        Reminder.PeriodEnd -> Intent(context, PeriodReminderReceiver::class.java).setAction(ReminderIntents.ACTION_PERIOD_ALARM)
    }

    private enum class Session { SIGNED_IN, SIGNED_OUT, UNKNOWN }

    /**
     * Inloggad när sessionen har en användare inom en kort väntan (kallstart: omstart, larm). Annars avgör
     * inloggningens eget läge: ingen användare = bekräftat utloggad; inget svar eller en användare som sessionen
     * ännu inte sett = okänt.
     */
    private suspend fun session(): Session {
        if (withTimeoutOrNull(AUTH_WAIT) { user.uid.first { it != null } } != null) return Session.SIGNED_IN
        val state = withTimeoutOrNull(AUTH_CHECK) {
            runCatching { auth.authState.first() }.fold({ if (it == null) Session.SIGNED_OUT else Session.UNKNOWN }, { Session.UNKNOWN })
        }
        return state ?: Session.UNKNOWN
    }

    /** Inställningarna ur cachen, eller `null` när läsningen inte svarar i tid. */
    private suspend fun readSettings() = withTimeoutOrNull(READ_WAIT) { settings.get() }

    companion object {
        /** Varje påminnelse sin `requestCode` – en kollision skulle skriva över ett annat larm. */
        internal fun requestCode(reminder: Reminder): Int = when (reminder) {
            is Reminder.Med -> REQUEST_MED_BASE + reminder.slot.ordinal
            is Reminder.Mood -> REQUEST_MOOD_BASE + reminder.occasion.ordinal
            Reminder.PeriodEnd -> REQUEST_PERIOD
        }

        /** Alla påminnelser som kan ha ett larm – det som avbokas. */
        internal val ALL: List<Reminder> = Slot.SCHEDULED.map(Reminder::Med) + Occasion.entries.map(Reminder::Mood) + Reminder.PeriodEnd

        private const val REQUEST_MED_BASE = 0x7FE0
        private const val REQUEST_MOOD_BASE = 0x7FF0
        private const val REQUEST_PERIOD = 0x7FD0

        /**
         * NOT-14: så länge efter sin tid ett larm som inte utlösts (ett sent, inexakt larm i Doze) ligger kvar på dagens
         * tid vid en omschemaläggning, i stället för att flyttas till i morgon.
         */
        internal val LATE_GRACE = 30.minutes

        /** Hur länge en omschemaläggning väntar på sessionen, på inloggningens läge och på inställningarna. */
        internal val AUTH_WAIT = 2.seconds
        internal val AUTH_CHECK = 1.seconds
        internal val READ_WAIT = 2.seconds
    }
}
