package se.partee71.dagboken.reminders

import android.content.Intent
import android.content.pm.PackageManager
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import se.partee71.dagboken.MainActivity
import se.partee71.dagboken.core.model.Occasion
import se.partee71.dagboken.core.model.Slot
import se.partee71.dagboken.navigation.AppBackStack
import se.partee71.dagboken.navigation.MedicinesKey
import se.partee71.dagboken.navigation.TodayKey
import se.partee71.dagboken.navigation.TrendsKey
import se.partee71.dagboken.navigation.open
import se.partee71.dagboken.reminders.ReminderIntents.slot
import se.partee71.dagboken.ui.log.LogEvent

/**
 * Omstartsmottagaren (NOT-6, NOT-14; port av 3.x `BootReceiverActionsTest`), mottagarna i manifestet och vad en tryckt
 * påminnelse öppnar (NOT-9, NOT-11, NOT-12) – skill android-intent-security.
 */
@RunWith(RobolectricTestRunner::class)
class ReminderIntentsTest {

    private val context = RuntimeEnvironment.getApplication()

    @Test
    fun `omstart och appuppdatering lägger om larmen (NOT-14)`() {
        assertTrue(Intent.ACTION_BOOT_COMPLETED in BootReceiver.RESCHEDULE_ACTIONS)
        assertTrue(Intent.ACTION_MY_PACKAGE_REPLACED in BootReceiver.RESCHEDULE_ACTIONS)
    }

    @Test
    fun `andra broadcasts lägger inte om larmen`() {
        listOf(Intent.ACTION_SCREEN_ON, Intent.ACTION_TIME_TICK, Intent.ACTION_PACKAGE_REMOVED, ReminderIntents.ACTION_MARK_TAKEN, ReminderIntents.ACTION_MED_ALARM)
            .forEach { assertFalse(it in BootReceiver.RESCHEDULE_ACTIONS, "$it ska inte lägga om larmen") }
    }

    @Test
    fun `omstartsmottagaren lyssnar i manifestet på precis de åtgärder den hanterar`() {
        BootReceiver.RESCHEDULE_ACTIONS.forEach { action ->
            val receivers = context.packageManager.queryBroadcastReceivers(Intent(action).setPackage(context.packageName), 0)
            assertEquals(listOf(BootReceiver::class.java.name), receivers.map { it.activityInfo.name }, action)
        }
    }

    @Test
    fun `påminnelsernas mottagare finns och är inte exporterade`() {
        val info = context.packageManager.getPackageInfo(context.packageName, PackageManager.GET_RECEIVERS)
        val receivers = info.receivers.orEmpty().filter { it.name.startsWith("se.partee71.dagboken.reminders.") }
        assertEquals(
            setOf(MedAlarmReceiver::class, ScreeningReminderReceiver::class, PeriodReminderReceiver::class, MedActionReceiver::class, BootReceiver::class).map { it.java.name }.toSet(),
            receivers.map { it.name }.toSet(),
        )
        assertTrue(receivers.none { it.exported })
    }

    @Test
    fun `bara kända värden tolkas - en främmande app kan inte öppna något annat`() {
        fun launch(open: String?, occasion: String? = null) = ReminderLaunch.from(
            Intent(context, MainActivity::class.java).putExtra(ReminderIntents.EXTRA_OPEN, open).putExtra(ReminderIntents.EXTRA_OCCASION, occasion),
        )
        assertNull(ReminderLaunch.from(null))
        assertNull(launch(null))
        assertNull(launch("settings"))
        assertEquals(ReminderLaunch.Today, launch(ReminderLaunch.TODAY))
        assertEquals(ReminderLaunch.Medicines, launch(ReminderLaunch.MEDICINES))
        assertEquals(ReminderLaunch.LogMood(Occasion.BEDTIME), launch(ReminderLaunch.MOOD, Occasion.BEDTIME.wire))
        assertEquals(ReminderLaunch.Today, launch(ReminderLaunch.MOOD, "okänt"), "okänt tillfälle: bara Idag")
        assertNull(Intent().putExtra(ReminderIntents.EXTRA_SLOT, Slot.AS_NEEDED.wire).slot(), "vid behov har ingen påminnelse")
    }

    @Test
    fun `en påminnelse spelas upp en gång - inte igen från Senaste eller med samma Intent`() {
        val intent = Intent(context, MainActivity::class.java)
            .putExtra(ReminderIntents.EXTRA_OPEN, ReminderLaunch.MOOD)
            .putExtra(ReminderIntents.EXTRA_OCCASION, Occasion.LUNCH.wire)
        assertEquals(ReminderLaunch.LogMood(Occasion.LUNCH), ReminderLaunch.consume(intent))
        assertNull(ReminderLaunch.consume(intent), "förbrukat")

        val fromHistory = Intent(context, MainActivity::class.java)
            .putExtra(ReminderIntents.EXTRA_OPEN, ReminderLaunch.TODAY)
            .addFlags(Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY)
        assertNull(ReminderLaunch.consume(fromHistory))
        assertNull(ReminderLaunch.consume(null))
    }

    @Test
    fun `en ohanterad påminnelse överlever rotation - bara värdenas namn sparas`() {
        listOf(ReminderLaunch.Today, ReminderLaunch.Medicines, ReminderLaunch.LogMood(Occasion.DINNER)).forEach { launch ->
            val state = android.os.Bundle()
            ReminderLaunch.save(launch, state)
            assertEquals(launch, ReminderLaunch.restore(state))
            assertTrue(state.keySet().all { state.getString(it) in setOf(ReminderLaunch.TODAY, ReminderLaunch.MOOD, ReminderLaunch.MEDICINES, Occasion.DINNER.wire) })
        }
        val empty = android.os.Bundle()
        ReminderLaunch.save(null, empty)
        assertNull(ReminderLaunch.restore(empty))
        assertNull(ReminderLaunch.restore(null))
    }

    @Test
    fun `en tryckt påminnelse öppnar Idag, måendearket eller Mediciner`() {
        val events = mutableListOf<LogEvent>()
        val backStack = AppBackStack()
        backStack.select(TrendsKey)

        backStack.open(ReminderLaunch.Today, events::add)
        assertEquals(TodayKey, backStack.currentTab)

        backStack.open(ReminderLaunch.Medicines, events::add)
        assertEquals(MedicinesKey, backStack.currentTab)

        backStack.open(ReminderLaunch.LogMood(Occasion.LUNCH), events::add)
        assertEquals(TodayKey, backStack.currentTab)
        assertEquals(listOf<LogEvent>(LogEvent.LogScreening(Occasion.LUNCH, date = null, reminder = null)), events)
    }
}
