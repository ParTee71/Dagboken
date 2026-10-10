package se.partee71.dagboken.reminders

import android.content.Context
import androidx.core.content.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Instant
import se.partee71.dagboken.core.engine.Reminder

/**
 * NOT-14: per påminnelse den senast schemalagda utlösningstiden och när den senast utlöstes – så att en sen,
 * ännu inte utlöst påminnelse ligger kvar på dagens tid också efter att processen dött eller telefonen startats om,
 * men aldrig utlöses två gånger (`reminderAlarms`). **Enhetslokalt** (SharedPreferences, undantagen från Androids
 * backup – NFR-23), aldrig i Firestore: bara påminnelsens nyckel och epoch-millisekunder, inget innehåll.
 */
@Singleton
class AlarmLedger @Inject constructor(@ApplicationContext context: Context) {
    private val prefs = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun scheduled(): Map<Reminder, Instant> = read(SCHEDULED)

    fun fired(): Map<Reminder, Instant> = read(FIRED)

    fun setScheduled(reminder: Reminder, at: Instant) = prefs.edit { putLong(SCHEDULED + key(reminder), at.toEpochMilliseconds()) }

    fun clearScheduled(reminder: Reminder) = prefs.edit { remove(SCHEDULED + key(reminder)) }

    fun setFired(reminder: Reminder, at: Instant) = prefs.edit { putLong(FIRED + key(reminder), at.toEpochMilliseconds()) }

    /** AUTH-6: allt – vid utloggning. */
    fun clear() = prefs.edit { this.clear() }

    private fun read(prefix: String): Map<Reminder, Instant> = AlarmScheduler.ALL.mapNotNull { reminder ->
        val millis = prefs.getLong(prefix + key(reminder), MISSING)
        if (millis == MISSING) null else reminder to Instant.fromEpochMilliseconds(millis)
    }.toMap()

    private fun key(reminder: Reminder): String = when (reminder) {
        is Reminder.Med -> "med." + reminder.slot.wire
        is Reminder.Mood -> "mood." + reminder.occasion.wire
        Reminder.PeriodEnd -> "period"
    }

    private companion object {
        const val FILE = "reminder_alarms"
        const val SCHEDULED = "scheduled."
        const val FIRED = "fired."
        const val MISSING = Long.MIN_VALUE
    }
}
