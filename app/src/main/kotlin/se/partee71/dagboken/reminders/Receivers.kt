package se.partee71.dagboken.reminders

import android.app.AlarmManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import javax.inject.Provider
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.datetime.TimeZone
import se.partee71.dagboken.core.engine.Reminder
import se.partee71.dagboken.core.engine.medReminderDate
import se.partee71.dagboken.di.ApplicationScope
import se.partee71.dagboken.reminders.ReminderIntents.date
import se.partee71.dagboken.reminders.ReminderIntents.reminder
import se.partee71.dagboken.reminders.ReminderIntents.slot
import se.partee71.dagboken.reminders.ReminderIntents.time

// Påminnelsernas mottagare (TP-8, NOT-3…NOT-14) – alla icke-exporterade (manifestet) och tunna: vad de gör står i
// ReminderActions (testbart utan Hilt), som använder AlarmScheduler (larm), ReminderContent (vad som visas, via
// repositories) och NotificationHelper (notiser). Ingen Firestore-kod och ingen loggning av innehåll (NFR-13).

/**
 * Mottagarnas arbete efter att `onReceive` returnerat (`goAsync`), i appens scope: först [first] – nästa larm, så att
 * kedjan aldrig bryts (NOT-14) – inom [firstBudget], sedan [then] (notisen) på den tid som återstår, som alltid är
 * minst [TOTAL_BUDGET] − [firstBudget]. Allt ryms under systemets gräns för en mottagare, så att en läsning som
 * hänger aldrig ger en ANR. Ett fel i ett steg fäller aldrig appen i bakgrunden och hindrar inte nästa steg.
 */
class ReceiverWork @Inject constructor(@ApplicationScope private val scope: CoroutineScope) {
    fun run(receiver: BroadcastReceiver, first: suspend () -> Unit, firstBudget: Duration = FIRST_BUDGET, then: suspend () -> Unit = {}) {
        val pending = receiver.goAsync()
        scope.launch {
            try {
                steps(first, firstBudget, then)
            } finally {
                pending.finish()
            }
        }
    }

    internal companion object {
        /** Stegen i [run]: varje steg inom sin tid, och ett undantag i det första hindrar inte det andra. */
        internal suspend fun steps(first: suspend () -> Unit, firstBudget: Duration, then: suspend () -> Unit) {
            step("first", firstBudget, first)
            step("then", thenBudget(firstBudget), then)
        }

        private suspend fun step(name: String, budget: Duration, block: suspend () -> Unit) {
            try {
                withTimeoutOrNull(budget) { block() }
            } catch (e: CancellationException) {
                // Bara när mottagarens coroutine själv är avbruten kastas det vidare; ett CancellationException inifrån
                // (t.ex. en avbruten Firestore-Task) är ett fel i steget, så att nästa steg ändå körs.
                currentCoroutineContext().ensureActive()
                Log.w(TAG, "Påminnelsens steg $name avbröts: ${e.javaClass.simpleName}")
            } catch (e: Exception) {
                // NFR-13: bara stegets och felets typ – aldrig meddelandet, som kan innehålla data. Strippas i release.
                Log.w(TAG, "Påminnelsens steg $name misslyckades: ${e.javaClass.simpleName}")
            }
        }

        private const val TAG = "Reminders"

        /** Nästa larm: `AlarmScheduler.rescheduleNext` tar högst ca 5 s (inloggningens väntan och en läsning). */
        val FIRST_BUDGET = 5.seconds

        /** Under systemets gräns (10 s för en mottagare i förgrunden). */
        val TOTAL_BUDGET = 9500.milliseconds

        /**
         * Tiden som alltid finns kvar för [run]s andra steg – med [FIRST_BUDGET] 4,5 s, så att notisens läsningar ryms
         * (`ReminderContent.SETTINGS_WAIT` + `READ_WAIT` = 4 s).
         */
        fun thenBudget(firstBudget: Duration): Duration = TOTAL_BUDGET - firstBudget
    }
}

/**
 * Vad mottagarna gör med ett Intent – en gång, här. Okända actions och extras ignoreras (mottagarna är inte
 * exporterade, men ett Intent tolkas ändå aldrig mer än nödvändigt).
 */
class ReminderActions @Inject constructor(
    private val scheduler: AlarmScheduler,
    private val content: ReminderContent,
    private val notifications: NotificationHelper,
    private val clock: Clock,
    private val zone: Provider<TimeZone>,
) {
    /** NOT-14: ett larm har levererats – registreras direkt i `onReceive` ([AlarmScheduler.markFired]). */
    fun markFired(intent: Intent) {
        intent.reminder()?.let(scheduler::markFired)
    }

    /** NOT-14: första steget för ett utlöst larm – nästa larm, före notisen, så att kedjan aldrig bryts. */
    suspend fun scheduleNext(intent: Intent) {
        val reminder = intent.reminder() ?: return
        scheduler.rescheduleNext(reminder, intent.time())
    }

    /**
     * Andra steget: notisen för larmet som utlöstes [firedAt] – medicin (NOT-3, NOT-17: tidpunktens otagna doser,
     * ingen notis när inget är kvar), mående (NOT-19: uteblir för ett loggat tillfälle) eller periodslut (NOT-12).
     */
    suspend fun notify(intent: Intent, firedAt: Instant) {
        when (val reminder = intent.reminder() ?: return) {
            is Reminder.Med -> {
                val date = medReminderDate(firedAt, zone.get())
                notifications.postMedReminder(reminder.slot, date, content.medDoses(reminder.slot, date).all)
            }
            is Reminder.Mood -> if (content.moodDue(reminder.occasion)) notifications.postMoodReminder(reminder.occasion)
            Reminder.PeriodEnd -> notifications.postPeriodReminder(content.periodEndings())
        }
    }

    /**
     * NOT-10: "Markera tagen" – notisen stängs bara när doserna verkligen markerats (eller inget fanns kvar att
     * markera); gick läsningen eller skrivningen inte står den kvar med en rad om att bocka av i appen.
     */
    suspend fun markTaken(intent: Intent) {
        if (intent.action != ReminderIntents.ACTION_MARK_TAKEN) return
        val slot = intent.slot() ?: return
        val date = intent.date() ?: return
        val marked = withTimeoutOrNull(MARK_BUDGET) { content.markTaken(slot, date) }
        // Timeout (läsning eller skrivning som inte hann) räknas som misslyckad – då är "Kunde inte markera" sant.
        if (marked?.isSuccess == true) notifications.cancelMed(slot) else notifications.markTakenFailed(slot)
    }

    /** NOT-6, NOT-14: en av [BootReceiver.RESCHEDULE_ACTIONS] lägger om alla larm; annat ger `null`. */
    suspend fun onSystemEvent(intent: Intent): Rescheduled? =
        if (intent.action in BootReceiver.RESCHEDULE_ACTIONS) scheduler.rescheduleAll(alarmsCleared = intent.action in BootReceiver.CLEARS_ALARMS) else null

    /** Klockan nu – när larmet utlöstes. */
    fun now(): Instant = clock.now()

    internal companion object {
        /**
         * Läsningarna (`ReminderContent.READ_WAIT`, 2,5 s) och skrivningen (i cachen direkt) – inom mottagarens första
         * steg ([MARK_RECEIVER_BUDGET]), så att utfallet alltid hinner visas.
         */
        val MARK_BUDGET = 4.seconds

        /** [MedActionReceiver]s första steg: [MARK_BUDGET] plus marginal för att stänga eller uppdatera notisen. */
        val MARK_RECEIVER_BUDGET = 5.seconds
    }
}

/** Ett utlöst larm: nästa larm först, sedan notisen (NOT-14). */
private fun BroadcastReceiver.onAlarm(intent: Intent, action: String, work: ReceiverWork, actions: ReminderActions) {
    if (intent.action != action) return
    // Synkront, innan något suspenderar: en omschemaläggning som hinner före mottagarens coroutine ska se larmet som utlöst.
    actions.markFired(intent)
    val firedAt = actions.now()
    work.run(this, first = { actions.scheduleNext(intent) }) { actions.notify(intent, firedAt) }
}

/** NOT-2, NOT-3, NOT-14, NOT-17: medicinpåminnelsen för en tidpunkt. */
@AndroidEntryPoint
class MedAlarmReceiver : BroadcastReceiver() {
    @Inject lateinit var actions: ReminderActions
    @Inject lateinit var work: ReceiverWork

    override fun onReceive(context: Context, intent: Intent) = onAlarm(intent, ReminderIntents.ACTION_MED_ALARM, work, actions)
}

/** NOT-4, NOT-5, NOT-14, NOT-19: måendepåminnelsen för ett tillfälle. */
@AndroidEntryPoint
class ScreeningReminderReceiver : BroadcastReceiver() {
    @Inject lateinit var actions: ReminderActions
    @Inject lateinit var work: ReceiverWork

    override fun onReceive(context: Context, intent: Intent) = onAlarm(intent, ReminderIntents.ACTION_MOOD_ALARM, work, actions)
}

/** NOT-12, NOT-13, NOT-14: periodslutspåminnelsen. */
@AndroidEntryPoint
class PeriodReminderReceiver : BroadcastReceiver() {
    @Inject lateinit var actions: ReminderActions
    @Inject lateinit var work: ReceiverWork

    override fun onReceive(context: Context, intent: Intent) = onAlarm(intent, ReminderIntents.ACTION_PERIOD_ALARM, work, actions)
}

/** NOT-10: "Markera tagen" i medicinpåminnelsen, utan att appen öppnas ([ReminderActions.markTaken]). */
@AndroidEntryPoint
class MedActionReceiver : BroadcastReceiver() {
    @Inject lateinit var actions: ReminderActions
    @Inject lateinit var work: ReceiverWork

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ReminderIntents.ACTION_MARK_TAKEN) return
        work.run(this, first = { actions.markTaken(intent) }, firstBudget = ReminderActions.MARK_RECEIVER_BUDGET)
    }
}

/**
 * NOT-6, NOT-14: systemet rensar appens larm vid omstart och vid appuppdatering – då läggs alla om. Också när
 * rätten till exakta larm ges (de läggs om som exakta) och när klockan eller tidszonen ändras (larmen ligger på
 * absoluta ögonblick). Går inställningarna inte att läsa läggs efter omstart och uppdatering de senast schemalagda
 * tiderna om ur `AlarmLedger`; annars står befintliga larm kvar ([AlarmScheduler.rescheduleAll]).
 */
@AndroidEntryPoint
class BootReceiver : BroadcastReceiver() {
    @Inject lateinit var actions: ReminderActions
    @Inject lateinit var work: ReceiverWork

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action !in RESCHEDULE_ACTIONS) return
        work.run(this, first = { actions.onSystemEvent(intent) })
    }

    companion object {
        /** Broadcasts som lägger om alla larm – samma lista som mottagarens intent-filter i manifestet (testat). */
        internal val RESCHEDULE_ACTIONS = setOf(
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
        )

        /**
         * NOT-6: de av [RESCHEDULE_ACTIONS] efter vilka systemet har tömt appens larm – då läggs de senast schemalagda
         * om ur `AlarmLedger` om inställningarna inte går att läsa.
         */
        internal val CLEARS_ALARMS = setOf(Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED)
    }
}
