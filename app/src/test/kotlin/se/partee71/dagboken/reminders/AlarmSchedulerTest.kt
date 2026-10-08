package se.partee71.dagboken.reminders

import android.app.PendingIntent
import android.content.Intent
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.time.Duration.Companion.minutes
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalTime
import kotlinx.datetime.plus
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowAlarmManager
import se.partee71.dagboken.core.engine.Reminder
import se.partee71.dagboken.core.model.Occasion
import se.partee71.dagboken.core.model.Prescription
import se.partee71.dagboken.core.model.ReminderSettings
import se.partee71.dagboken.core.model.Schedule
import se.partee71.dagboken.core.model.Settings
import se.partee71.dagboken.core.model.Slot
import se.partee71.dagboken.data.legacy.LegacyMigrationPause
import se.partee71.dagboken.data.auth.AuthRepository
import se.partee71.dagboken.data.auth.AuthUser
import se.partee71.dagboken.data.common.DataError
import se.partee71.dagboken.data.repository.SettingsRepository

/**
 * Schemaläggaren mot Robolectrics AlarmManager och inställningarna i `FakeCollection` (NOT-2, NOT-4…NOT-8, NOT-12…NOT-15,
 * NOT-18, AUTH-6), och synken som håller larmen i takt med cachen (NOT-7, NOT-15).
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class AlarmSchedulerTest {

    private val f = ReminderFixture(RuntimeEnvironment.getApplication())
    private val today = f.today
    private val tomorrow = today.plus(1, DateTimeUnit.DAY)

    /** Inställningarna går inte att läsa – tom cache utan nät, som direkt efter en omstart. */
    private val unreadable = object : SettingsRepository by f.settings {
        override suspend fun get(): Result<Settings> = Result.failure(DataError.Offline)
    }

    @Test
    fun `påslagna påminnelser läggs på rätt tider, exakt och genom Doze`() = runTest {
        ShadowAlarmManager.setCanScheduleExactAlarms(true)
        f.enable(slots = setOf(Slot.MORNING, Slot.LUNCH), occasions = setOf(Occasion.DINNER))

        assertEquals(Rescheduled.SCHEDULED, f.scheduler().rescheduleAll())

        val alarms = f.alarms()
        assertEquals(setOf(Reminder.Med(Slot.MORNING), Reminder.Med(Slot.LUNCH), Reminder.Mood(Occasion.DINNER), Reminder.PeriodEnd), alarms.keys)
        // 09:30 nu: Morgon 06:45 har passerat → i morgon (NOT-5); Lunch 11:45 och Kvällsmat 17:00 idag; periodslut 09:00 i morgon.
        assertEquals(f.at(tomorrow, 6, 45).toEpochMilliseconds(), alarms.getValue(Reminder.Med(Slot.MORNING)).triggerAtTime)
        assertEquals(f.at(today, 11, 45).toEpochMilliseconds(), alarms.getValue(Reminder.Med(Slot.LUNCH)).triggerAtTime)
        assertEquals(f.at(today, 17, 0).toEpochMilliseconds(), alarms.getValue(Reminder.Mood(Occasion.DINNER)).triggerAtTime)
        assertEquals(f.at(tomorrow, 9, 0).toEpochMilliseconds(), alarms.getValue(Reminder.PeriodEnd).triggerAtTime)
        alarms.values.forEach { alarm ->
            assertTrue(alarm.isAllowWhileIdle, "…AndAllowWhileIdle (NOT-8)")
            assertEquals(ShadowAlarmManager.WINDOW_EXACT, alarm.windowLengthMs, "exakt när det är tillåtet")
            assertTrue(shadowOf(alarm.operation).isImmutable, "FLAG_IMMUTABLE")
            assertTrue(shadowOf(alarm.operation).isBroadcast)
        }
        // Klockslaget följer med, så att larmet kan läggas om när cachen är tom.
        assertEquals("12:00", shadowOf(alarms.getValue(Reminder.Med(Slot.LUNCH)).operation).savedIntent.getStringExtra(ReminderIntents.EXTRA_TIME))
    }

    @Test
    fun `utan rätt till exakta larm blir de inexakta - ingen krasch (NOT-8)`() = runTest {
        ShadowAlarmManager.setCanScheduleExactAlarms(false)
        f.enable(slots = setOf(Slot.MORNING))

        f.scheduler().rescheduleAll()

        val alarms = f.alarms()
        assertEquals(2, alarms.size)
        alarms.values.forEach { alarm ->
            assertTrue(alarm.isAllowWhileIdle)
            assertTrue(alarm.windowLengthMs != ShadowAlarmManager.WINDOW_EXACT, "inexakt")
        }
    }

    @Test
    fun `varje påminnelse har en egen requestCode`() {
        assertEquals(AlarmScheduler.ALL.size, AlarmScheduler.ALL.map(AlarmScheduler::requestCode).toSet().size)
        assertEquals(Slot.SCHEDULED.size + Occasion.entries.size + 1, AlarmScheduler.ALL.size)
    }

    @Test
    fun `alla avbokas före omschemaläggningen - en avslagen påminnelse lämnar inget larm (NOT-7)`() = runTest {
        f.enable(slots = Slot.SCHEDULED.toSet(), occasions = Occasion.entries.toSet())
        val scheduler = f.scheduler()
        scheduler.rescheduleAll()
        assertEquals(Slot.SCHEDULED.size + Occasion.entries.size + 1, f.alarms().size)

        f.enable(slots = setOf(Slot.EVENING))
        scheduler.rescheduleAll()

        assertEquals(setOf(Reminder.Med(Slot.EVENING), Reminder.PeriodEnd), f.alarms().keys)
    }

    @Test
    fun `huvudreglaget av tar bort medicinlarmen men inte de andra`() = runTest {
        f.enable(slots = setOf(Slot.MORNING), occasions = setOf(Occasion.LUNCH))
        val scheduler = f.scheduler()
        scheduler.rescheduleAll()
        f.settings.update { it.copy(reminders = it.reminders.copy(medsEnabled = false)) }.getOrThrow()

        scheduler.rescheduleAll()

        assertEquals(setOf(Reminder.Mood(Occasion.LUNCH), Reminder.PeriodEnd), f.alarms().keys)
    }

    @Test
    fun `tom cache vid omstart - befintliga larm står kvar, inget avbokas`() = runTest {
        f.enable(slots = setOf(Slot.MORNING))
        f.scheduler().rescheduleAll()
        val before = f.alarms().mapValues { it.value.triggerAtTime }

        assertEquals(Rescheduled.KEPT, f.scheduler(unreadable).rescheduleAll())

        assertEquals(before, f.alarms().mapValues { it.value.triggerAtTime })
    }

    @Test
    fun `utloggad - alla larm avbokas (AUTH-6)`() = runTest {
        f.enable(slots = setOf(Slot.MORNING), occasions = setOf(Occasion.LUNCH))
        val scheduler = f.scheduler()
        scheduler.rescheduleAll()
        f.user.uid.value = null
        f.auth.authState.value = null

        assertEquals(Rescheduled.SIGNED_OUT, scheduler.rescheduleAll())

        assertEquals(emptyMap(), f.alarms())
    }

    /** Inloggningen som ännu inte svarat – kallstart av en mottagare innan Firebase läst in användaren. */
    private val silentAuth = object : AuthRepository by f.auth {
        override val authState: Flow<AuthUser?> = flow { awaitCancellation() }
    }

    @Test
    fun `kallstart där inloggningen inte hunnit läsas in - befintliga larm står kvar`() = runTest {
        f.enable(slots = setOf(Slot.MORNING))
        f.scheduler().rescheduleAll()
        val before = f.alarms().mapValues { it.value.triggerAtTime }
        f.user.uid.value = null

        assertEquals(Rescheduled.KEPT, f.scheduler(authSource = silentAuth).rescheduleAll())
        assertEquals(Rescheduled.KEPT, f.scheduler().rescheduleAll(), "inloggad enligt Firebase men inte i sessionen än")

        assertEquals(before, f.alarms().mapValues { it.value.triggerAtTime })
    }

    @Test
    fun `kallstart där inloggningen inte hunnit läsas in - nästa larm läggs på senast kända klockslag (NOT-14)`() = runTest {
        f.user.uid.value = null

        f.scheduler(authSource = silentAuth).rescheduleNext(Reminder.Med(Slot.LUNCH), LocalTime(12, 0))

        assertEquals(f.at(today, 11, 45).toEpochMilliseconds(), f.alarms().getValue(Reminder.Med(Slot.LUNCH)).triggerAtTime)
    }

    @Test
    fun `bekräftat utloggad - en utlöst påminnelse lägger inget nytt larm`() = runTest {
        f.enable(occasions = setOf(Occasion.LUNCH))
        f.scheduler().rescheduleAll()
        f.user.uid.value = null
        f.auth.authState.value = null

        f.scheduler().rescheduleNext(Reminder.Mood(Occasion.LUNCH), LocalTime(12, 0))

        assertTrue(Reminder.Mood(Occasion.LUNCH) !in f.alarms())
    }

    @Test
    fun `en utlöst påminnelse lägger sitt nästa larm efter inställningarna (NOT-14)`() = runTest {
        f.enable(slots = setOf(Slot.LUNCH))
        val scheduler = f.scheduler()
        f.clock.instant = f.at(today, 11, 45)

        scheduler.rescheduleNext(Reminder.Med(Slot.LUNCH), LocalTime(12, 0))

        assertEquals(f.at(tomorrow, 11, 45).toEpochMilliseconds(), f.alarms().getValue(Reminder.Med(Slot.LUNCH)).triggerAtTime)
    }

    @Test
    fun `en utlöst påminnelse som slagits av lägger inget nytt larm`() = runTest {
        f.enable(slots = setOf(Slot.MORNING))
        f.scheduler().rescheduleNext(Reminder.Med(Slot.LUNCH), LocalTime(12, 0))
        f.scheduler().rescheduleNext(Reminder.Mood(Occasion.LUNCH), LocalTime(12, 0))

        assertEquals(emptyMap(), f.alarms())
    }

    @Test
    fun `med tom cache läggs nästa larm på det senast kända klockslaget`() = runTest {
        f.scheduler(unreadable).rescheduleNext(Reminder.Mood(Occasion.LUNCH), LocalTime(12, 30))

        assertEquals(f.at(today, 12, 30).toEpochMilliseconds(), f.alarms().getValue(Reminder.Mood(Occasion.LUNCH)).triggerAtTime)
    }

    @Test
    fun `avbokningen träffar larmet - samma Intent som när det lades`() = runTest {
        f.enable(occasions = setOf(Occasion.BREAKFAST))
        val scheduler = f.scheduler()
        scheduler.rescheduleAll()

        scheduler.cancelAll()

        assertEquals(emptyMap(), f.alarms())
        val probe = PendingIntent.getBroadcast(
            f.context,
            AlarmScheduler.requestCode(Reminder.Mood(Occasion.BREAKFAST)),
            Intent(f.context, ScreeningReminderReceiver::class.java).setAction(ReminderIntents.ACTION_MOOD_ALARM),
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
        )
        assertEquals(null, probe, "PendingIntent avbokad")
    }

    // ── Efter omstart (NOT-6) ─────────────────────────────────────────────────

    /** Som en omstart: systemet tömmer AlarmManager, men ledgern finns kvar på disken. */
    private fun rebootClearsAlarms() {
        val alarmManager = f.context!!.getSystemService(android.app.AlarmManager::class.java)
        f.alarms().values.forEach { alarmManager.cancel(checkNotNull(it.operation)) }
        assertEquals(emptyMap(), f.alarms())
    }

    @Test
    fun `efter omstart med tom cache läggs de senast schemalagda larmen om`() = runTest {
        f.enable(slots = setOf(Slot.LUNCH), occasions = setOf(Occasion.DINNER))
        f.scheduler().rescheduleAll()
        val before = f.alarms().mapValues { it.value.triggerAtTime }
        rebootClearsAlarms()

        assertEquals(Rescheduled.RESTORED, f.scheduler(unreadable).rescheduleAll(alarmsCleared = true))

        assertEquals(before, f.alarms().mapValues { it.value.triggerAtTime })
        assertEquals("12:00", shadowOf(f.alarms().getValue(Reminder.Med(Slot.LUNCH)).operation).savedIntent.getStringExtra(ReminderIntents.EXTRA_TIME))
    }

    @Test
    fun `efter omstart innan inloggningen lästs in läggs de senast schemalagda om`() = runTest {
        f.enable(slots = setOf(Slot.LUNCH))
        f.scheduler().rescheduleAll()
        rebootClearsAlarms()
        f.user.uid.value = null

        assertEquals(Rescheduled.RESTORED, f.scheduler(authSource = silentAuth).rescheduleAll(alarmsCleared = true))

        assertEquals(setOf(Reminder.Med(Slot.LUNCH), Reminder.PeriodEnd), f.alarms().keys)
    }

    @Test
    fun `utan omstart rörs larmen inte när inställningarna inte går att läsa`() = runTest {
        f.enable(slots = setOf(Slot.LUNCH))
        f.scheduler().rescheduleAll()
        rebootClearsAlarms()

        assertEquals(Rescheduled.KEPT, f.scheduler(unreadable).rescheduleAll())

        assertEquals(emptyMap(), f.alarms())
    }

    @Test
    fun `kända inställningar från synken används utan ny läsning, standardvärdena bekräftas`() = runTest {
        var reads = 0
        val counting = object : SettingsRepository by f.settings {
            override suspend fun get() = f.settings.get().also { reads++ }
        }
        f.enable(slots = setOf(Slot.LUNCH))
        val known = f.settings.get().getOrThrow().reminders

        assertEquals(Rescheduled.SCHEDULED, f.scheduler(counting).rescheduleAll(known = known))
        assertEquals(0, reads)
        assertTrue(Reminder.Med(Slot.LUNCH) in f.alarms())

        f.scheduler(counting).rescheduleAll(known = ReminderSettings())
        assertEquals(1, reads, "standardvärdena kan vara ett dokument som saknas i cachen")
    }

    @Test
    fun `utloggning tömmer också ledgern (AUTH-6)`() = runTest {
        f.enable(slots = setOf(Slot.LUNCH))
        val scheduler = f.scheduler()
        scheduler.rescheduleAll()
        scheduler.rescheduleNext(Reminder.Med(Slot.LUNCH), LocalTime(12, 0))

        scheduler.cancelAll()

        val ledger = AlarmLedger(f.context!!)
        assertEquals(emptyMap(), ledger.scheduled())
        assertEquals(emptyMap(), ledger.fired())
    }

    // ── ReminderSync (NOT-7, NOT-15, AUTH-6) ───────────────────────────────────

    private val migrationPause = LegacyMigrationPause()

    @Test
    fun `kallstart för en migrerad användare avbokar inget - pausen är av tills en flytt pågår (OMB-2)`() = runTest {
        f.enable(slots = setOf(Slot.LUNCH))
        assertFalse(migrationPause.paused.value, "startvärdet är av")
        val job = launch { sync(f.scheduler()).run() }
        runCurrent()
        assertTrue(Reminder.Med(Slot.LUNCH) in f.alarms(), "larmen läggs som vanligt")
        job.cancel()
    }

    private fun sync(scheduler: AlarmScheduler) =
        ReminderSync(f.user, f.settings, scheduler, NotificationHelper(f.context!!), migrationPause, CoroutineScope(Dispatchers.Unconfined))

    @Test
    fun `synken schemalägger inget medan migreringens skrivning pågår men avbokar aldrig, och lägger larmen när pausen släpps (OMB-2)`() = runTest {
        f.enable(slots = setOf(Slot.LUNCH))
        migrationPause.set(true)
        val job = launch { sync(f.scheduler()).run() }
        runCurrent()
        assertEquals(emptyMap(), f.alarms(), "inget nytt schemaläggs medan skrivningen pågår")

        migrationPause.set(false)
        runCurrent()
        assertEquals(setOf(Reminder.Med(Slot.LUNCH), Reminder.PeriodEnd), f.alarms().keys, "när pausen släpps läggs larmen av synken själv")

        // En ny skrivning pågår: befintliga larm rörs inte, och en ändring i inställningarna schemaläggs inte förrän pausen släpps.
        migrationPause.set(true)
        runCurrent()
        assertEquals(setOf(Reminder.Med(Slot.LUNCH), Reminder.PeriodEnd), f.alarms().keys, "pausen avbokar aldrig")
        f.enable(slots = setOf(Slot.NIGHT))
        runCurrent()
        assertEquals(setOf(Reminder.Med(Slot.LUNCH), Reminder.PeriodEnd), f.alarms().keys, "ingen omschemaläggning under pausen")

        migrationPause.set(false)
        runCurrent()
        assertEquals(setOf(Reminder.Med(Slot.NIGHT), Reminder.PeriodEnd), f.alarms().keys, "efteråt gäller inställningarna som vanligt")
        job.cancel()
    }

    @Test
    fun `synken lägger larmen vid start och igen när cachen ändras - också från servern`() = runTest {
        val sync = sync(f.scheduler())
        val job = launch { sync.run() }
        runCurrent()
        assertEquals(setOf(Reminder.PeriodEnd), f.alarms().keys)

        // Som när en annan enhet (eller en import, NOT-15) ändrat inställningarna: dokumentet ändras i cachen.
        f.enable(slots = setOf(Slot.NIGHT), occasions = setOf(Occasion.BEDTIME))
        runCurrent()
        assertEquals(setOf(Reminder.Med(Slot.NIGHT), Reminder.Mood(Occasion.BEDTIME), Reminder.PeriodEnd), f.alarms().keys)

        f.settings.update { it.copy(reminders = it.reminders.copy(periodReminderTime = LocalTime(7, 30))) }.getOrThrow()
        runCurrent()
        assertEquals(f.at(tomorrow, 7, 30).toEpochMilliseconds(), f.alarms().getValue(Reminder.PeriodEnd).triggerAtTime)
        job.cancel()
    }

    @Test
    fun `synken lägger inte om för recept eller ändringar som inte rör påminnelserna`() = runTest {
        var calls = 0
        val counting = object : SettingsRepository by f.settings {
            override suspend fun get() = f.settings.get().also { calls++ }
        }
        val job = launch { sync(f.scheduler(counting)).run() }
        runCurrent()
        val before = calls

        f.prescriptions.save(null, Prescription("p1", name = "Levaxin", dose = "100", unit = "µg", slots = listOf(Slot.MORNING), schedule = Schedule.Repeating())).getOrThrow()
        f.settings.update { it.copy(theme = it.theme.copy(lightStartHour = 6)) }.getOrThrow()
        runCurrent()

        assertEquals(before, calls)
        job.cancel()
    }

    // ── Sena larm (NOT-14) ────────────────────────────────────────────────────

    @Test
    fun `ett sent larm som inte utlösts flyttas inte till i morgon av en omschemaläggning`() = runTest {
        f.enable(slots = setOf(Slot.MORNING))
        val scheduler = f.scheduler()
        f.clock.instant = f.at(today, 6, 0)
        scheduler.rescheduleAll()
        f.clock.instant = f.at(today, 6, 55) // inexakt larm, ännu inte utlöst

        scheduler.rescheduleAll()
        assertEquals(f.at(today, 6, 45).toEpochMilliseconds(), f.alarms().getValue(Reminder.Med(Slot.MORNING)).triggerAtTime)

        scheduler.rescheduleNext(Reminder.Med(Slot.MORNING), LocalTime(7, 0)) // nu utlöst
        scheduler.rescheduleAll()
        assertEquals(f.at(tomorrow, 6, 45).toEpochMilliseconds(), f.alarms().getValue(Reminder.Med(Slot.MORNING)).triggerAtTime)
    }

    private val morning = Reminder.Med(Slot.MORNING)

    private fun morningAt() = f.alarms().getValue(morning).triggerAtTime

    /** 06:45-larmet lagt kl. 06:00, och klockan 06:55 – larmet är sent och har inte utlösts. */
    private suspend fun lateMorning() {
        f.enable(slots = setOf(Slot.MORNING))
        f.clock.instant = f.at(today, 6, 0)
        f.scheduler().rescheduleAll()
        f.clock.instant = f.at(today, 6, 55)
    }

    @Test
    fun `tom process - ett sent larm ligger kvar på dagens tid också i en ny schemaläggare`() = runTest {
        lateMorning()

        f.scheduler().rescheduleAll() // ny instans, som efter att processen dött eller en omstart

        assertEquals(f.at(today, 6, 45).toEpochMilliseconds(), morningAt())
    }

    @Test
    fun `omstart efter ett utlöst larm - det läggs inte igen`() = runTest {
        lateMorning()
        f.scheduler().rescheduleNext(morning, LocalTime(7, 0))

        f.scheduler().rescheduleAll() // ny instans

        assertEquals(f.at(tomorrow, 6, 45).toEpochMilliseconds(), morningAt())
    }

    @Test
    fun `en tid som användaren flyttar till strax före nu går till i morgon (NOT-5)`() = runTest {
        lateMorning()
        f.settings.update { s -> s.copy(reminders = s.reminders.copy(medSlots = s.reminders.medSlots.map { if (it.slot == Slot.MORNING) it.copy(time = LocalTime(6, 50)) else it })) }.getOrThrow()

        f.scheduler().rescheduleAll() // 06:35 passerades för 20 min sedan men var aldrig schemalagd

        assertEquals(f.at(tomorrow, 6, 35).toEpochMilliseconds(), morningAt())
    }

    @Test
    fun `en tid som slås på strax efter sin tid går till i morgon (NOT-5)`() = runTest {
        f.clock.instant = f.at(today, 6, 55)
        f.enable(slots = setOf(Slot.MORNING))

        f.scheduler().rescheduleAll()

        assertEquals(f.at(tomorrow, 6, 45).toEpochMilliseconds(), morningAt())
    }

    @Test
    fun `en utlöst påminnelses nästa larm väntar inte ut en omschemaläggning som läser (NOT-14)`() = runTest {
        f.enable(slots = setOf(Slot.MORNING), occasions = setOf(Occasion.LUNCH))
        val gate = CompletableDeferred<Unit>()
        var first = true
        val slow = object : SettingsRepository by f.settings {
            override suspend fun get(): Result<Settings> {
                if (first) {
                    first = false
                    gate.await()
                }
                return f.settings.get()
            }
        }
        val scheduler = f.scheduler(slow)
        val all = launch { scheduler.rescheduleAll() }
        runCurrent()
        assertTrue(all.isActive, "omschemaläggningen läser fortfarande")

        scheduler.rescheduleNext(Reminder.Mood(Occasion.LUNCH), LocalTime(12, 0))

        assertTrue(all.isActive)
        assertEquals(f.at(today, 12, 0).toEpochMilliseconds(), f.alarms().getValue(Reminder.Mood(Occasion.LUNCH)).triggerAtTime)
        gate.complete(Unit)
        all.join()
    }

    @Test
    fun `utloggning under nästa larms läsning - larmet läggs inte tillbaka (AUTH-6)`() = runTest {
        f.enable(occasions = setOf(Occasion.LUNCH))
        val gate = CompletableDeferred<Unit>()
        val slow = object : SettingsRepository by f.settings {
            override suspend fun get(): Result<Settings> {
                gate.await()
                return f.settings.get()
            }
        }
        val scheduler = f.scheduler(slow)
        val next = launch { scheduler.rescheduleNext(Reminder.Mood(Occasion.LUNCH), LocalTime(12, 0)) }
        runCurrent()

        scheduler.cancelAll()
        gate.complete(Unit)
        next.join()

        assertTrue(f.alarms().isEmpty(), "det inaktuella beslutet skriver inte över utloggningen")
    }

    @Test
    fun `ett larm äldre än marginalen läggs i morgon`() = runTest {
        f.enable(slots = setOf(Slot.MORNING))
        f.clock.instant = f.at(today, 6, 45) + AlarmScheduler.LATE_GRACE + 1.minutes

        f.scheduler().rescheduleAll()

        assertEquals(f.at(tomorrow, 6, 45).toEpochMilliseconds(), f.alarms().getValue(Reminder.Med(Slot.MORNING)).triggerAtTime)
    }

    @Test
    fun `utloggning avbokar alla larm, en kallstart utan inloggning gör det inte`() = runTest {
        f.enable(slots = setOf(Slot.MORNING))
        f.scheduler().rescheduleAll()
        f.user.uid.value = null
        val job = launch { sync(f.scheduler()).run() }
        advanceUntilIdle()
        assertEquals(2, f.alarms().size, "utloggad redan vid start: larmen rörs inte")

        f.user.uid.value = "uid-test"
        runCurrent()
        f.user.uid.value = null
        runCurrent()

        assertEquals(emptyMap(), f.alarms())
        job.cancel()
    }
}
