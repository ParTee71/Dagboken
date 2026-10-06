package se.partee71.dagboken.reminders

import android.content.Intent
import android.os.Bundle
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import se.partee71.dagboken.core.engine.Reminder
import se.partee71.dagboken.core.model.Occasion
import se.partee71.dagboken.core.model.Slot

// Påminnelsernas Intents – enda stället för actions och extras (skill android-intent-security). Larm och
// notisåtgärder går bara till appens egna, icke-exporterade mottagare via explicita, oföränderliga
// PendingIntents; extras innehåller aldrig hälsodata (bara tidpunkt, tillfälle, klockslag och datum).

internal object ReminderIntents {
    const val ACTION_MED_ALARM = "se.partee71.dagboken.reminders.MED_ALARM"
    const val ACTION_MOOD_ALARM = "se.partee71.dagboken.reminders.MOOD_ALARM"
    const val ACTION_PERIOD_ALARM = "se.partee71.dagboken.reminders.PERIOD_ALARM"
    const val ACTION_MARK_TAKEN = "se.partee71.dagboken.reminders.MARK_TAKEN"

    /** Tidpunktens `wire` ([Slot]). */
    const val EXTRA_SLOT = "se.partee71.dagboken.reminders.SLOT"

    /** Tillfällets `wire` ([Occasion]). */
    const val EXTRA_OCCASION = "se.partee71.dagboken.reminders.OCCASION"

    /** Inställningens klockslag (`HH:mm`) – larmet kan schemalägga om sig med det när inställningarna inte går att läsa. */
    const val EXTRA_TIME = "se.partee71.dagboken.reminders.TIME"

    /** Dagen doserna gäller (`yyyy-MM-dd`). */
    const val EXTRA_DATE = "se.partee71.dagboken.reminders.DATE"

    /** Vad appen ska öppna ([ReminderLaunch]). */
    const val EXTRA_OPEN = "se.partee71.dagboken.reminders.OPEN"

    fun Intent.slot(): Slot? = getStringExtra(EXTRA_SLOT)?.let { wire -> Slot.SCHEDULED.firstOrNull { it.wire == wire } }

    fun Intent.occasion(): Occasion? = getStringExtra(EXTRA_OCCASION)?.let { wire -> Occasion.entries.firstOrNull { it.wire == wire } }

    fun Intent.time(): LocalTime? = getStringExtra(EXTRA_TIME)?.let { runCatching { LocalTime.parse(it) }.getOrNull() }

    /** Påminnelsen ett larms Intent gäller (action och extras), eller `null` för något annat. */
    fun Intent.reminder(): Reminder? = when (action) {
        ACTION_MED_ALARM -> slot()?.let(Reminder::Med)
        ACTION_MOOD_ALARM -> occasion()?.let(Reminder::Mood)
        ACTION_PERIOD_ALARM -> Reminder.PeriodEnd
        else -> null
    }

    fun Intent.date(): LocalDate? = getStringExtra(EXTRA_DATE)?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
}

/**
 * Vad en tryckt notis öppnar i appen (NOT-9, NOT-11, NOT-12). MainActivity är exporterad (startikonen), så
 * vem som helst kan skicka de här extras – därför tolkas bara kända värden, och det värsta en främmande app kan
 * göra är att öppna Idag, måendearket eller Mediciner.
 */
sealed interface ReminderLaunch {
    /** Idag (NOT-9). */
    data object Today : ReminderLaunch

    /** Idag med måendearket för [occasion] öppet ("Logga nu", NOT-11). */
    data class LogMood(val occasion: Occasion) : ReminderLaunch

    /** Fliken Mediciner (periodslutet, NOT-12). */
    data object Medicines : ReminderLaunch

    companion object {
        internal const val TODAY = "today"
        internal const val MOOD = "mood"
        internal const val MEDICINES = "medicines"

        /**
         * Det [intent] ber om – **en gång**: extras tas bort ur [intent] efteråt, och en start från Senaste
         * (`FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY`, där systemet återanvänder notisens ursprungliga Intent) ger
         * `null`, så att en gammal påminnelse aldrig spelas upp igen.
         */
        fun consume(intent: Intent?): ReminderLaunch? {
            if (intent == null) return null
            val fromHistory = intent.flags and Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY != 0
            val launch = if (fromHistory) null else from(intent)
            intent.removeExtra(ReminderIntents.EXTRA_OPEN)
            intent.removeExtra(ReminderIntents.EXTRA_OCCASION)
            return launch
        }

        /** Det [intent] ber om, eller `null` för en vanlig start (eller okända värden). */
        fun from(intent: Intent?): ReminderLaunch? =
            intent?.let { parse(it.getStringExtra(ReminderIntents.EXTRA_OPEN), it.getStringExtra(ReminderIntents.EXTRA_OCCASION)) }

        /**
         * Sparar [launch] – ännu inte hanterad – i aktivitetens tillstånd, så att den överlever en rotation: bara
         * värdenas namn, inget innehåll. [restore] läser tillbaka den.
         */
        fun save(launch: ReminderLaunch?, state: Bundle) {
            if (launch == null) return
            state.putString(ReminderIntents.EXTRA_OPEN, launch.open)
            if (launch is LogMood) state.putString(ReminderIntents.EXTRA_OCCASION, launch.occasion.wire)
        }

        /** Det [save] sparade, eller `null`. */
        fun restore(state: Bundle?): ReminderLaunch? =
            state?.let { parse(it.getString(ReminderIntents.EXTRA_OPEN), it.getString(ReminderIntents.EXTRA_OCCASION)) }

        /** Bara kända värden; ett okänt tillfälle för "Logga nu" öppnar Idag. */
        private fun parse(open: String?, occasion: String?): ReminderLaunch? = when (open) {
            TODAY -> Today
            MOOD -> Occasion.entries.firstOrNull { it.wire == occasion }?.let(::LogMood) ?: Today
            MEDICINES -> Medicines
            else -> null
        }

        private val ReminderLaunch.open: String
            get() = when (this) {
                Today -> TODAY
                is LogMood -> MOOD
                Medicines -> MEDICINES
            }
    }
}
