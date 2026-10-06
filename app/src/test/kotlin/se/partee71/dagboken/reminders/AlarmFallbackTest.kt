package se.partee71.dagboken.reminders

import android.app.AlarmManager
import android.app.PendingIntent
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements
import org.robolectric.shadows.ShadowAlarmManager
import se.partee71.dagboken.core.engine.Reminder
import se.partee71.dagboken.core.model.Slot

/** Rätten till exakta larm dras in mellan kontrollen och anropet (NOT-8): larmet blir inexakt, ingen krasch. */
@RunWith(RobolectricTestRunner::class)
@Config(shadows = [AlarmFallbackTest.RevokedExactAlarms::class])
class AlarmFallbackTest {

    private val f = ReminderFixture(RuntimeEnvironment.getApplication())

    @Test
    fun `SecurityException vid exakt larm ger inexakt larm och larmet hamnar i ledgern`() = runTest {
        ShadowAlarmManager.setCanScheduleExactAlarms(true)
        f.enable(slots = setOf(Slot.MORNING))

        f.scheduler().rescheduleAll()

        val alarm = f.alarms().getValue(Reminder.Med(Slot.MORNING))
        assertTrue(alarm.isAllowWhileIdle)
        assertTrue(alarm.windowLengthMs != ShadowAlarmManager.WINDOW_EXACT, "inexakt")
        assertEquals(alarm.triggerAtTime, AlarmLedger(f.context!!).scheduled()[Reminder.Med(Slot.MORNING)]?.toEpochMilliseconds())
    }

    /** Som när användaren drar in "Alarm och påminnelser" precis innan larmet sätts. */
    @Implements(AlarmManager::class)
    class RevokedExactAlarms : ShadowAlarmManager() {
        @Implementation
        override fun setExactAndAllowWhileIdle(type: Int, triggerAtMillis: Long, operation: PendingIntent) {
            throw SecurityException("SCHEDULE_EXACT_ALARM")
        }
    }
}
