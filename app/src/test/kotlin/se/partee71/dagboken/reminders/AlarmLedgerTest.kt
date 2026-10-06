package se.partee71.dagboken.reminders

import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import se.partee71.dagboken.core.engine.Reminder
import se.partee71.dagboken.core.model.Occasion
import se.partee71.dagboken.core.model.Slot

/** Senast schemalagd och senast utlöst per påminnelse (NOT-14), enhetslokalt och utan innehåll. */
@RunWith(RobolectricTestRunner::class)
class AlarmLedgerTest {

    private val context = RuntimeEnvironment.getApplication()
    private val t1 = Instant.fromEpochMilliseconds(1_778_042_700_000)
    private val t2 = Instant.fromEpochMilliseconds(1_778_129_100_000)

    @Test
    fun `rundtur för varje påminnelse, var för sig, också i en ny instans`() {
        val ledger = AlarmLedger(context)
        AlarmScheduler.ALL.forEachIndexed { i, reminder ->
            ledger.setScheduled(reminder, t1 + i.minutes)
            ledger.setFired(reminder, t2)
        }

        val fresh = AlarmLedger(context)
        assertEquals(AlarmScheduler.ALL.size, fresh.scheduled().size)
        AlarmScheduler.ALL.forEachIndexed { i, reminder ->
            assertEquals(t1 + i.minutes, fresh.scheduled()[reminder], "$reminder")
            assertEquals(t2, fresh.fired()[reminder], "$reminder")
        }
    }

    @Test
    fun `clearScheduled tar bara bort den påminnelsens schemalagda tid`() {
        val ledger = AlarmLedger(context)
        ledger.setScheduled(Reminder.Med(Slot.MORNING), t1)
        ledger.setScheduled(Reminder.Mood(Occasion.BREAKFAST), t1)
        ledger.setFired(Reminder.Med(Slot.MORNING), t2)

        ledger.clearScheduled(Reminder.Med(Slot.MORNING))

        assertEquals(mapOf<Reminder, Instant>(Reminder.Mood(Occasion.BREAKFAST) to t1), ledger.scheduled())
        assertEquals(mapOf<Reminder, Instant>(Reminder.Med(Slot.MORNING) to t2), ledger.fired())
    }

    @Test
    fun `clear tömmer allt (AUTH-6)`() {
        val ledger = AlarmLedger(context)
        ledger.setScheduled(Reminder.PeriodEnd, t1)
        ledger.setFired(Reminder.PeriodEnd, t2)

        ledger.clear()

        assertEquals(emptyMap(), ledger.scheduled())
        assertEquals(emptyMap(), ledger.fired())
    }
}
