package se.kodhast.dagboken

import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import se.kodhast.dagboken.reminders.ReminderIntents
import se.kodhast.dagboken.ui.moment.MomentActivity

/**
 * Appens identitet utåt efter paketbytet (Dagboken #418): Kotlin-paketet heter `se.kodhast.dagboken`, men det som
 * installerade appar, hemskärmar och väntande larm känner igen behåller sina gamla namn. Värdena står som text, inte
 * ur koden, så att en "städning" fäller testet.
 */
@RunWith(RobolectricTestRunner::class)
class AppIdentityTest {

    private val context = RuntimeEnvironment.getApplication()

    @Test
    fun `applicationId är oförändrat - appen uppdateras i stället för att installeras bredvid`() {
        assertEquals("se.partee71.dagboken", context.packageName)
        assertEquals("se.partee71.dagboken", BuildConfig.APPLICATION_ID)
    }

    @Test
    fun `hemskärmsikonen pekar på det gamla komponentnamnet, ett alias mot MainActivity`() {
        val launcher = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER).setPackage(context.packageName)
        val resolved = context.packageManager.queryIntentActivities(launcher, 0).map { it.activityInfo }
        assertEquals(listOf("se.partee71.dagboken.MainActivity"), resolved.map { it.name }, "en startikon, med det gamla namnet")

        val alias = context.packageManager.getActivityInfo(ComponentName("se.partee71.dagboken", "se.partee71.dagboken.MainActivity"), 0)
        assertEquals("se.kodhast.dagboken.MainActivity", alias.targetActivity)
        assertEquals(MainActivity::class.java.name, alias.targetActivity)
        assertTrue(alias.exported, "startikonen måste vara exporterad")
    }

    @Test
    fun `påminnelsernas actions och extras behåller det gamla prefixet - väntande larm och notiser känns igen`() {
        val strings = mapOf(
            "MED_ALARM" to ReminderIntents.ACTION_MED_ALARM,
            "MOOD_ALARM" to ReminderIntents.ACTION_MOOD_ALARM,
            "PERIOD_ALARM" to ReminderIntents.ACTION_PERIOD_ALARM,
            "MARK_TAKEN" to ReminderIntents.ACTION_MARK_TAKEN,
            "SLOT" to ReminderIntents.EXTRA_SLOT,
            "OCCASION" to ReminderIntents.EXTRA_OCCASION,
            "TIME" to ReminderIntents.EXTRA_TIME,
            "DATE" to ReminderIntents.EXTRA_DATE,
            "OPEN" to ReminderIntents.EXTRA_OPEN,
            "SECONDS" to ReminderIntents.EXTRA_SECONDS,
            "QUESTION" to ReminderIntents.EXTRA_QUESTION
        )
        strings.forEach { (suffix, value) -> assertEquals("se.partee71.dagboken.reminders.$suffix", value) }
    }

    @Test
    fun `mottagarnas gamla namn finns, är inte exporterade och gör samma sak som de nya (#418)`() {
        val receivers = context.packageManager.getPackageInfo(context.packageName, PackageManager.GET_RECEIVERS).receivers.orEmpty()
        val legacy = receivers.filter { it.name.startsWith("se.partee71.dagboken.") }
        assertEquals(
            setOf("MedAlarmReceiver", "ScreeningReminderReceiver", "PeriodReminderReceiver", "MedActionReceiver").map { "se.partee71.dagboken.reminders.$it" }.toSet(),
            legacy.map { it.name }.toSet(),
            "varje mottagare som ett larm eller en notisknapp pekar på explicit"
        )
        legacy.forEach { old ->
            assertFalse(old.exported, old.name)
            val current = Class.forName(old.name.replace("se.partee71.", "se.kodhast."))
            assertTrue(current.isAssignableFrom(Class.forName(old.name)) && current != Class.forName(old.name), "${old.name} ärver ${current.name}")
        }
    }

    @Test
    fun `Stundens gamla namn är ett icke-exporterat alias mot MomentActivity (#418, NOT-20)`() {
        val alias = context.packageManager.getActivityInfo(ComponentName(context.packageName, "se.partee71.dagboken.ui.moment.MomentActivity"), 0)
        assertEquals(MomentActivity::class.java.name, alias.targetActivity)
        assertFalse(alias.exported)
    }
}
