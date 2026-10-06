package se.partee71.dagboken.reminders

import android.Manifest
import android.app.Notification
import android.app.NotificationManager
import androidx.core.app.NotificationCompat
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.datetime.LocalDate
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import se.partee71.dagboken.MainActivity
import se.partee71.dagboken.core.engine.PeriodEnding
import se.partee71.dagboken.core.model.Dose
import se.partee71.dagboken.core.model.Occasion
import se.partee71.dagboken.core.model.Slot
import se.partee71.dagboken.reminders.ReminderIntents.date
import se.partee71.dagboken.reminders.ReminderIntents.slot

/**
 * Kanalerna och notiserna (NOT-1, NOT-3, NOT-9…NOT-12, NOT-17): privata på låsskärmen utan hälsoinnehåll i den
 * offentliga versionen, explicita och oföränderliga PendingIntents (skill android-intent-security). Påhittad data.
 */
@RunWith(RobolectricTestRunner::class)
class NotificationHelperTest {

    private val context = RuntimeEnvironment.getApplication()
    private val helper = NotificationHelper(context)
    private val manager = context.getSystemService(NotificationManager::class.java)
    private val today = LocalDate(2026, 5, 6)

    private val doses = listOf(
        Dose("d1", date = today, slot = Slot.MORNING, name = "Levaxin", dose = "100", unit = "µg"),
        Dose("d2", date = today, slot = Slot.MORNING, name = "Prednisolon", dose = "15", unit = "mg"),
    )

    @Before
    fun grant() = shadowOf(context).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)

    private fun posted(): List<Notification> = shadowOf(manager).allNotifications

    private fun Notification.text(): String = listOfNotNull(
        extras.getCharSequence(NotificationCompat.EXTRA_TITLE),
        extras.getCharSequence(NotificationCompat.EXTRA_TEXT),
        extras.getCharSequence(NotificationCompat.EXTRA_BIG_TEXT),
    ).joinToString("\n")

    @Test
    fun `två kanaler med rätt prioritet (NOT-1)`() {
        helper.createChannels()
        helper.createChannels()

        val channels = shadowOf(manager).notificationChannels.associate { it.id to it.importance }
        assertEquals(mapOf(NotificationHelper.CHANNEL_MEDS to NotificationManager.IMPORTANCE_DEFAULT, NotificationHelper.CHANNEL_SCREENING to NotificationManager.IMPORTANCE_LOW), channels)
    }

    @Test
    fun `medicinpåminnelsen listar doserna med tidpunkten i rubriken, privat på låsskärmen (NOT-17)`() {
        helper.postMedReminder(Slot.MORNING, today, doses)

        val notification = posted().single()
        assertEquals(NotificationHelper.CHANNEL_MEDS, notification.channelId)
        assertEquals("Dags för medicin – Morgon", notification.extras.getCharSequence(NotificationCompat.EXTRA_TITLE).toString())
        assertEquals("Levaxin 100 µg\nPrednisolon 15 mg", notification.extras.getCharSequence(NotificationCompat.EXTRA_BIG_TEXT).toString())
        assertEquals(Notification.VISIBILITY_PRIVATE, notification.visibility)
        val public = checkNotNull(notification.publicVersion).text()
        assertFalse("Levaxin" in public || "Prednisolon" in public || "Morgon" in public, "inga hälsouppgifter på låsskärmen: $public")
    }

    @Test
    fun `markera tagen går till appens egen mottagare med tidpunkt och dag, tryck öppnar Idag (NOT-9, NOT-10)`() {
        helper.postMedReminder(Slot.EVENING, today, doses)

        val notification = posted().single()
        val action = notification.actions.single()
        assertEquals("Markera tagen", action.title.toString())
        val mark = shadowOf(action.actionIntent)
        assertTrue(mark.isBroadcast && mark.isImmutable)
        assertEquals(MedActionReceiver::class.java.name, mark.savedIntent.component?.className)
        assertEquals(Slot.EVENING, mark.savedIntent.slot())
        assertEquals(today, mark.savedIntent.date())

        val open = shadowOf(notification.contentIntent)
        assertTrue(open.isActivity && open.isImmutable)
        assertEquals(MainActivity::class.java.name, open.savedIntent.component?.className)
        assertEquals(ReminderLaunch.Today, ReminderLaunch.from(open.savedIntent))
    }

    @Test
    fun `ingen otagen dos ger ingen notis (NOT-3), och varje tidpunkt har sin egen`() {
        helper.postMedReminder(Slot.MORNING, today, emptyList())
        assertEquals(0, posted().size)

        helper.postMedReminder(Slot.MORNING, today, doses)
        helper.postMedReminder(Slot.LUNCH, today, doses)
        assertEquals(2, posted().size)

        helper.cancelMed(Slot.MORNING)
        assertEquals(listOf("Dags för medicin – Lunch"), posted().map { it.extras.getCharSequence(NotificationCompat.EXTRA_TITLE).toString() })
    }

    @Test
    fun `markera tagen misslyckades - raden läggs till en gång, också vid fler försök (NOT-10)`() {
        helper.postMedReminder(Slot.MORNING, today, doses)

        helper.markTakenFailed(Slot.MORNING)
        helper.markTakenFailed(Slot.MORNING)

        val notification = posted().single()
        assertEquals(
            "Kunde inte markera – öppna appen och bocka av där.\nLevaxin 100 µg\nPrednisolon 15 mg",
            notification.extras.getCharSequence(NotificationCompat.EXTRA_BIG_TEXT).toString(),
        )
        assertEquals(1, notification.actions.size, "Markera tagen finns kvar")
    }

    @Test
    fun `Logga nu stänger måendepåminnelsen när appen öppnas (NOT-11)`() {
        helper.postMoodReminder(Occasion.LUNCH)
        helper.postMoodReminder(Occasion.DINNER)

        helper.dismiss(ReminderLaunch.Today)
        assertEquals(2, posted().size)
        helper.dismiss(ReminderLaunch.LogMood(Occasion.LUNCH))

        assertEquals(listOf("Dags att logga mående – Kvällsmat"), posted().map { it.extras.getCharSequence(NotificationCompat.EXTRA_TITLE).toString() })
    }

    @Test
    fun `måendepåminnelsen - Logga nu öppnar tillfällets måendeark (NOT-11)`() {
        helper.postMoodReminder(Occasion.DINNER)

        val notification = posted().single()
        assertEquals(NotificationHelper.CHANNEL_SCREENING, notification.channelId)
        assertEquals("Dags att logga mående – Kvällsmat", notification.extras.getCharSequence(NotificationCompat.EXTRA_TITLE).toString())
        assertEquals(Notification.VISIBILITY_PRIVATE, notification.visibility)
        val logNow = shadowOf(notification.actions.single().actionIntent)
        assertTrue(logNow.isActivity && logNow.isImmutable)
        assertEquals(ReminderLaunch.LogMood(Occasion.DINNER), ReminderLaunch.from(logNow.savedIntent))
        assertEquals(ReminderLaunch.Today, ReminderLaunch.from(shadowOf(notification.contentIntent).savedIntent))
    }

    @Test
    fun `periodslut - en samlad notis i medicinkanalen som öppnar Mediciner, ingen när inget tar slut (NOT-12)`() {
        val tomorrow = LocalDate(2026, 5, 7)
        helper.postPeriodReminder(emptyList())
        assertEquals(0, posted().size)

        helper.postPeriodReminder(listOf(PeriodEnding.BoostEnds("p", "Prednisolon", tomorrow, "5", "mg")))
        var notification = posted().single()
        assertEquals("Doshöjningen för Prednisolon slutar i morgon", notification.extras.getCharSequence(NotificationCompat.EXTRA_TITLE).toString())
        assertEquals("Från och med dagen efter gäller 5 mg.", notification.extras.getCharSequence(NotificationCompat.EXTRA_TEXT).toString())
        assertEquals(ReminderLaunch.Medicines, ReminderLaunch.from(shadowOf(notification.contentIntent).savedIntent))

        helper.postPeriodReminder(listOf(PeriodEnding.PrescriptionEnds("a", "Amoxicillin", tomorrow), PeriodEnding.PrescriptionEnds("b", "Levaxin", tomorrow)))
        notification = posted().single()
        assertEquals(NotificationHelper.CHANNEL_MEDS, notification.channelId)
        assertEquals("2 medicinperioder tar slut i morgon", notification.extras.getCharSequence(NotificationCompat.EXTRA_TITLE).toString())
        assertEquals("Amoxicillin, Levaxin", notification.extras.getCharSequence(NotificationCompat.EXTRA_TEXT).toString())
        assertFalse("Amoxicillin" in checkNotNull(notification.publicVersion).text())
    }

    @Test
    fun `utan notisbehörighet postas ingenting och inget kraschar (NOT-16)`() {
        shadowOf(context).denyPermissions(Manifest.permission.POST_NOTIFICATIONS)

        helper.postMedReminder(Slot.MORNING, today, doses)
        helper.postMoodReminder(Occasion.LUNCH)

        assertEquals(0, posted().size)
        assertFalse(context.reminderAccess().notifications)
        assertTrue(context.needsNotificationPermission())
    }

    @Test
    fun `utloggad - alla notiser stängs (AUTH-6)`() {
        helper.postMedReminder(Slot.MORNING, today, doses)
        helper.postMoodReminder(Occasion.LUNCH)

        helper.cancelAll()

        assertEquals(0, posted().size)
    }
}
