package se.partee71.dagboken.reminders

import android.app.AlarmManager
import android.content.Context
import kotlin.time.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowAlarmManager
import se.partee71.dagboken.core.engine.Reminder
import se.partee71.dagboken.core.model.Occasion
import se.partee71.dagboken.core.model.Slot
import se.partee71.dagboken.data.FakeCollectionFactory
import se.partee71.dagboken.data.auth.AuthRepository
import se.partee71.dagboken.data.auth.AuthUser
import se.partee71.dagboken.testing.FakeAuthRepository
import se.partee71.dagboken.data.FixedClock
import se.partee71.dagboken.data.TestUserScope
import se.partee71.dagboken.data.repository.DefaultScreeningRepository
import se.partee71.dagboken.data.repository.DefaultSettingsRepository
import se.partee71.dagboken.data.repository.SettingsRepository
import se.partee71.dagboken.data.repository.testDoses
import se.partee71.dagboken.data.repository.testPrescriptions

/**
 * Påminnelsernas delar mot `FakeCollection` – samma repositories som UI:t – för alla påminnelsetester. "Nu" är
 * onsdag 6 maj 2026 kl. 09:30 i Europe/Stockholm. [scheduler] kräver Robolectric ([context]); [content] gör det inte.
 */
class ReminderFixture(val context: Context? = null) {
    val zone: TimeZone = TimeZone.of("Europe/Stockholm")
    val today = LocalDate(2026, 5, 6)
    val user = TestUserScope()
    /** Inloggningens eget läge; `null` = utloggad. */
    val auth = FakeAuthRepository(AuthUser("uid-test"))
    val factory = FakeCollectionFactory(scope = user)
    val clock = FixedClock(at(today, 9, 30))

    val settings = DefaultSettingsRepository(factory)
    val doses = testDoses(factory, zone, clock)
    val prescriptions = testPrescriptions(factory, doses, zone, clock)
    val screenings = DefaultScreeningRepository(factory, clock)

    val content = ReminderContent(settings, prescriptions, doses, screenings, clock) { zone }

    /** Schemaläggaren över [settingsSource] (standard: de riktiga inställningarna i fejkdatabasen). */
    fun scheduler(settingsSource: SettingsRepository = settings, authSource: AuthRepository = auth) = AlarmScheduler(checkNotNull(context), settingsSource, user, authSource, clock, AlarmLedger(checkNotNull(context))) { zone }

    /** Mottagarnas handlingar över [contentSource] (standard: [content]). */
    fun actions(scheduler: AlarmScheduler = scheduler(), contentSource: ReminderContent = content) =
        ReminderActions(scheduler, contentSource, NotificationHelper(checkNotNull(context)), clock) { zone }

    fun at(date: LocalDate, hour: Int, minute: Int = 0): Instant = LocalDateTime(date, LocalTime(hour, minute)).toInstant(zone)

    /** Larmen som ligger i AlarmManager just nu, per påminnelse. */
    fun alarms(): Map<Reminder, ShadowAlarmManager.ScheduledAlarm> {
        val alarmManager = checkNotNull(context).getSystemService(AlarmManager::class.java)
        return shadowOf(alarmManager).scheduledAlarms.associateBy { alarm ->
            val intent = shadowOf(alarm.operation).savedIntent
            with(ReminderIntents) { checkNotNull(intent.reminder()) { "Okänt larm ${intent.action}" } }
        }
    }

    /** Medicinpåminnelserna på för [slots] (övriga av), och måendepåminnelserna på för [occasions]. */
    suspend fun enable(slots: Set<Slot> = emptySet(), occasions: Set<Occasion> = emptySet()) = settings.update { s ->
        s.copy(
            reminders = s.reminders.copy(
                medsEnabled = slots.isNotEmpty(),
                medSlots = s.reminders.medSlots.map { it.copy(enabled = it.slot in slots) },
                screeningOccasions = s.reminders.screeningOccasions.map { it.copy(enabled = it.occasion in occasions) },
            ),
        )
    }.getOrThrow()
}
