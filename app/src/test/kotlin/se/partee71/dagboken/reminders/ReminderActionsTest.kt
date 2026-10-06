package se.partee71.dagboken.reminders

import android.Manifest
import android.app.Notification
import android.app.NotificationManager
import android.content.Intent
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import se.partee71.dagboken.core.engine.Reminder
import se.partee71.dagboken.core.engine.SlotDoses
import se.partee71.dagboken.core.model.Dose
import se.partee71.dagboken.core.model.DoseIds
import se.partee71.dagboken.core.model.DoseStatus
import se.partee71.dagboken.core.model.Occasion
import se.partee71.dagboken.core.model.Period
import se.partee71.dagboken.core.model.Prescription
import se.partee71.dagboken.core.model.Schedule
import se.partee71.dagboken.core.model.Slot
import se.partee71.dagboken.core.schema.DoseCodec
import se.partee71.dagboken.core.schema.PrescriptionCodec
import se.partee71.dagboken.data.common.DataError
import se.partee71.dagboken.data.firestore.Paths
import se.partee71.dagboken.data.repository.DoseRepository
import se.partee71.dagboken.data.repository.PrescriptionRepository

/**
 * Vad mottagarna gör (NOT-3, NOT-6, NOT-10, NOT-14, NOT-19) – genom [ReminderActions], med Intents som notisen och
 * schemaläggaren själva bygger, mot Robolectrics AlarmManager och NotificationManager och `FakeCollection`.
 */
@RunWith(RobolectricTestRunner::class)
class ReminderActionsTest {

    private val context = RuntimeEnvironment.getApplication()
    private val f = ReminderFixture(context)
    private val manager = context.getSystemService(NotificationManager::class.java)
    private val today = f.today
    private val uid get() = checkNotNull(f.user.uid.value)

    private val levaxin = Prescription(
        "lev", name = "Levaxin", dose = "100", unit = "µg", slots = listOf(Slot.MORNING),
        schedule = Schedule.Repeating(), period = Period(start = LocalDate(2026, 1, 1)),
    )

    @Before
    fun setUp() {
        shadowOf(context).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        f.factory.store.set(Paths.prescriptions(uid), levaxin.id, PrescriptionCodec.encode(levaxin), merge = false)
    }

    private fun posted(): List<Notification> = shadowOf(manager).allNotifications

    /** Morgonens påminnelse, postad som i appen, och dess "Markera tagen"-Intent. */
    private suspend fun morningReminderMarkIntent(): Intent {
        f.enable(slots = setOf(Slot.MORNING))
        val helper = NotificationHelper(context)
        helper.postMedReminder(Slot.MORNING, today, f.content.medDoses(Slot.MORNING, today).all)
        return shadowOf(posted().single().actions.single().actionIntent).savedIntent
    }

    private fun storedStatus(id: String) = f.factory.store.read(Paths.doses(uid), id)?.get(DoseCodec.STATUS)

    // ── Markera tagen (NOT-10) ────────────────────────────────────────────────

    @Test
    fun `markera tagen - doserna blir tagna och notisen stängs`() = runTest {
        val mark = morningReminderMarkIntent()

        f.actions().markTaken(mark)

        assertEquals(DoseStatus.TAKEN.wire, storedStatus(DoseIds.prescribed("lev", today, Slot.MORNING)))
        assertEquals(0, posted().size)
    }

    @Test
    fun `markera tagen - går recepten inte att läsa står notisen kvar med en rad om det`() = runTest {
        val mark = morningReminderMarkIntent()
        val silent = object : PrescriptionRepository by f.prescriptions {
            override fun observe(): Flow<List<Prescription>> = flow { awaitCancellation() }
        }
        val content = ReminderContent(f.settings, silent, f.doses, f.screenings, f.clock) { f.zone }

        f.actions(contentSource = content).markTaken(mark)

        val notification = posted().single()
        assertTrue("Kunde inte markera" in notification.extras.getCharSequence(Notification.EXTRA_BIG_TEXT).toString())
        assertTrue("Levaxin" in notification.extras.getCharSequence(Notification.EXTRA_BIG_TEXT).toString(), "doserna står kvar")
        assertEquals(1, notification.actions.size, "Markera tagen finns kvar")
        assertNull(storedStatus(DoseIds.prescribed("lev", today, Slot.MORNING)))
    }

    @Test
    fun `markera tagen - misslyckas skrivningen står notisen kvar med felraden, för alla fel`() = runTest {
        val mark = morningReminderMarkIntent()
        for (error in listOf(DataError.NotSignedIn, DataError.PermissionDenied, DataError.Unknown)) {
            val failing = object : DoseRepository by f.doses {
                override suspend fun markTaken(doses: SlotDoses, at: Instant): Result<Unit> = Result.failure(error)
            }
            val content = ReminderContent(f.settings, f.prescriptions, failing, f.screenings, f.clock) { f.zone }

            f.actions(contentSource = content).markTaken(mark)

            val text = posted().single().extras.getCharSequence(Notification.EXTRA_BIG_TEXT).toString()
            assertTrue(text.startsWith("Kunde inte markera"), "$error: $text")
            assertEquals(1, text.lines().count { it.startsWith("Kunde inte markera") }, "$error: en gång")
        }
    }

    @Test
    fun `markera tagen - inget kvar att markera räknas som klart`() = runTest {
        val mark = morningReminderMarkIntent()
        val taken = Dose(DoseIds.prescribed("lev", today, Slot.MORNING), date = today, slot = Slot.MORNING, status = DoseStatus.TAKEN, prescriptionId = "lev")
        f.factory.store.set(Paths.doses(uid), taken.id, DoseCodec.encode(taken), merge = false)

        f.actions().markTaken(mark)

        assertEquals(0, posted().size)
    }

    // ── Utlösta larm (NOT-3, NOT-14, NOT-19) ──────────────────────────────────

    @Test
    fun `ett utlöst medicinlarm lägger nästa larm och postar notisen`() = runTest {
        f.enable(slots = setOf(Slot.MORNING))
        val scheduler = f.scheduler()
        val actions = f.actions(scheduler)
        f.clock.instant = f.at(today, 6, 0)
        val alarm = alarmIntent(Reminder.Med(Slot.MORNING), scheduler)
        f.clock.instant = f.at(today, 6, 45)

        actions.scheduleNext(alarm)
        actions.notify(alarm, f.clock.instant)
        scheduler.rescheduleAll() // som när synken lägger om efteråt: dagens larm har gått

        assertEquals(f.at(LocalDate(2026, 5, 7), 6, 45).toEpochMilliseconds(), f.alarms().getValue(Reminder.Med(Slot.MORNING)).triggerAtTime)
        assertEquals("Dags för medicin – Morgon", posted().single().extras.getCharSequence(Notification.EXTRA_TITLE).toString())
    }

    @Test
    fun `ett loggat måendetillfälle ger ingen notis, men nästa larm läggs (NOT-19)`() = runTest {
        f.enable(occasions = setOf(Occasion.LUNCH))
        f.screenings.save(null, f.screenings.new(today, Occasion.LUNCH)).getOrThrow()
        val scheduler = f.scheduler()
        val actions = f.actions(scheduler)
        val alarm = alarmIntent(Reminder.Mood(Occasion.LUNCH), scheduler)

        actions.scheduleNext(alarm)
        actions.notify(alarm, f.clock.instant)

        assertEquals(0, posted().size)
        assertTrue(Reminder.Mood(Occasion.LUNCH) in f.alarms())
    }

    /** Larmets Intent som schemaläggaren lägger det. */
    private suspend fun alarmIntent(reminder: Reminder, scheduler: AlarmScheduler): Intent {
        scheduler.rescheduleAll()
        return shadowOf(f.alarms().getValue(reminder).operation).savedIntent
    }

    // ── Omstart och appuppdatering (NOT-6, NOT-14) ────────────────────────────

    @Test
    fun `varje omstartsåtgärd lägger om alla larm, andra Intents gör ingenting`() = runTest {
        f.enable(slots = setOf(Slot.LUNCH))
        val actions = f.actions()
        BootReceiver.RESCHEDULE_ACTIONS.forEach { action ->
            f.scheduler().cancelAll()
            assertEquals(Rescheduled.SCHEDULED, actions.onSystemEvent(Intent(action)), action)
            assertEquals(setOf(Reminder.Med(Slot.LUNCH), Reminder.PeriodEnd), f.alarms().keys, action)
        }
        f.scheduler().cancelAll()
        assertNull(actions.onSystemEvent(Intent(Intent.ACTION_SCREEN_ON)))
        assertNull(actions.onSystemEvent(Intent(ReminderIntents.ACTION_MARK_TAKEN)))
        assertEquals(emptyMap(), f.alarms())
    }

    @Test
    fun `med oläsbara inställningar - omstart och uppdatering lägger tillbaka larmen, övriga åtgärder rör inget (NOT-6)`() = runTest {
        f.enable(slots = setOf(Slot.LUNCH))
        f.scheduler().rescheduleAll()
        val unreadable = object : se.partee71.dagboken.data.repository.SettingsRepository by f.settings {
            override suspend fun get(): Result<se.partee71.dagboken.core.model.Settings> = Result.failure(DataError.Offline)
        }
        val actions = f.actions(f.scheduler(unreadable))
        val alarmManager = context.getSystemService(android.app.AlarmManager::class.java)
        BootReceiver.RESCHEDULE_ACTIONS.forEach { action ->
            f.alarms().values.forEach { alarmManager.cancel(checkNotNull(it.operation)) } // systemet har tömt larmen
            val expected = if (action in setOf(Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED)) Rescheduled.RESTORED else Rescheduled.KEPT
            assertEquals(expected, actions.onSystemEvent(Intent(action)), action)
            assertEquals(expected == Rescheduled.RESTORED, Reminder.Med(Slot.LUNCH) in f.alarms(), action)
        }
    }

    @Test
    fun `ett levererat larm registreras direkt - en omschemaläggning före mottagarens coroutine lägger det inte igen`() = runTest {
        f.enable(slots = setOf(Slot.LUNCH))
        val scheduler = f.scheduler()
        val actions = f.actions(scheduler)
        f.clock.instant = f.at(today, 11, 0)
        val alarm = alarmIntent(Reminder.Med(Slot.LUNCH), scheduler)
        f.clock.instant = f.at(today, 11, 45) // larmet levereras

        actions.markFired(alarm) // synkront i onReceive
        scheduler.rescheduleAll() // ReminderSync hinner före mottagarens coroutine

        assertEquals(f.at(LocalDate(2026, 5, 7), 11, 45).toEpochMilliseconds(), f.alarms().getValue(Reminder.Med(Slot.LUNCH)).triggerAtTime)
    }

    @Test
    fun `omstartsmottagarens åtgärder och manifestets intent-filter är samma lista`() {
        val parser = XmlPullParserFactory.newInstance().apply { isNamespaceAware = true }.newPullParser()
        parser.setInput(File("src/main/AndroidManifest.xml").reader())
        val actions = mutableSetOf<String>()
        var inBoot = false
        while (parser.next() != XmlPullParser.END_DOCUMENT) {
            val name = if (parser.eventType == XmlPullParser.START_TAG) parser.getAttributeValue(ANDROID, "name") else null
            when {
                parser.eventType == XmlPullParser.START_TAG && parser.name == "receiver" -> inBoot = name == ".reminders.BootReceiver"
                parser.eventType == XmlPullParser.END_TAG && parser.name == "receiver" -> inBoot = false
                parser.eventType == XmlPullParser.START_TAG && parser.name == "action" && inBoot -> actions += checkNotNull(name)
            }
        }
        assertEquals(BootReceiver.RESCHEDULE_ACTIONS, actions)
    }

    private companion object {
        const val ANDROID = "http://schemas.android.com/apk/res/android"
    }
}
