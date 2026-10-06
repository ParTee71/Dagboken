package se.partee71.dagboken.ui.log

import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.minutes
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import org.junit.Rule
import org.junit.Test
import se.partee71.dagboken.core.engine.OccasionStatus
import se.partee71.dagboken.core.model.Occasion
import se.partee71.dagboken.core.model.OccasionReminder
import se.partee71.dagboken.core.model.ReminderSettings
import se.partee71.dagboken.core.model.Screening
import se.partee71.dagboken.core.model.Settings
import se.partee71.dagboken.data.FakeCollectionFactory
import se.partee71.dagboken.data.FixedClock
import se.partee71.dagboken.data.TestUserScope
import se.partee71.dagboken.data.repository.DefaultOptionsRepository
import se.partee71.dagboken.data.repository.DefaultScreeningRepository
import se.partee71.dagboken.data.repository.DefaultSettingsRepository
import se.partee71.dagboken.testing.MainDispatcherRule
import se.partee71.dagboken.ui.common.SelectedDay

/**
 * Plusknappens Mående (HEM-8b, NAV-10, SCR-1, SCR-3, SCR-6) och Dagbokens måendepost (HIST-3) mot `FakeCollection`
 * och en fast klocka: tisdag 6 oktober 2026 kl. 12:30 i Europe/Stockholm.
 */
class LogViewModelTest {

    @get:Rule
    val main = MainDispatcherRule()

    private val zone = TimeZone.of("Europe/Stockholm")
    private val today = LocalDate(2026, 10, 6)
    private val yesterday = LocalDate(2026, 10, 5)
    private val clock = FixedClock(LocalDateTime(today, LocalTime(12, 30)).toInstant(zone))
    private val factory = FakeCollectionFactory(clock = clock)
    private val screenings = DefaultScreeningRepository(factory, clock)
    private val selectedDay = SelectedDay(factory.scope)

    /** Frukost 08:00 och lunch 12:00 påminns; kvällsmat och läggdags inte. */
    private val reminders = ReminderSettings(
        screeningOccasions = Occasion.entries.map { OccasionReminder(it, enabled = it == Occasion.BREAKFAST || it == Occasion.LUNCH) },
    )

    private suspend fun seed() {
        factory.settings().upsert(Settings(reminders = reminders)).getOrThrow()
    }

    private fun TestScope.started(): LogViewModel {
        val vm = LogViewModel(screenings, DefaultSettingsRepository(factory), DefaultOptionsRepository(factory), selectedDay, clock) { zone }
        backgroundScope.launch { vm.picker.collect {} }
        backgroundScope.launch { vm.screening.collect {} }
        runCurrent()
        return vm
    }

    @Test
    fun `plusknappen loggar mot dagen Idag visar, andra flikar mot idag (NAV-10, HEM-14)`() = runTest(main.dispatcher) {
        val vm = started()
        assertNull(vm.logDay(onToday = true), "ingen vald dag = idag")
        selectedDay.select(yesterday)
        assertEquals(yesterday, vm.logDay(onToday = true))
        assertNull(vm.logDay(onToday = false))
    }

    @Test
    fun `väljaren visar alla fyra tillfällen med status för dagen – de påminda som på Idag (HEM-8b)`() = runTest(main.dispatcher) {
        seed()
        factory.screenings().upsert(Screening("s1", today, LocalTime(12, 10), Occasion.LUNCH, energy = 6)).getOrThrow()
        val vm = started()
        assertNull(vm.picker.value)
        vm.onEvent(LogEvent.PickOccasion(null))
        runCurrent()
        val picker = assertNotNull(vm.picker.value)
        assertEquals(today, picker.date)
        assertTrue(picker.isToday)
        assertEquals(Occasion.entries.toList(), picker.occasions.map { it.occasion })
        assertEquals(
            listOf(OccasionStatus.LATE, OccasionStatus.LOGGED, OccasionStatus.NOT_LOGGED, OccasionStatus.NOT_LOGGED),
            picker.occasions.map { it.status },
        )

        vm.onEvent(LogEvent.ClosePicker)
        runCurrent()
        assertNull(vm.picker.value)

        vm.onEvent(LogEvent.PickOccasion(yesterday))
        runCurrent()
        assertEquals(yesterday, vm.picker.value?.date)
        assertFalse(vm.picker.value!!.isToday)
    }

    @Test
    fun `ett tillfälle öppnar arket med en ny logg – idag kl nu, en tidigare dag på påminnelsens tid, också när det redan är loggat (HEM-8b, SCR-6)`() = runTest(main.dispatcher) {
        seed()
        factory.screenings().upsert(Screening("s1", today, LocalTime(12, 10), Occasion.LUNCH)).getOrThrow()
        val vm = started()
        vm.onEvent(LogEvent.PickOccasion(null))
        runCurrent()
        vm.onEvent(LogEvent.LogOccasion(vm.picker.value!!.occasions[1]))
        runCurrent()
        assertNull(vm.picker.value, "väljaren stängs när arket öppnas")
        val sheet = assertNotNull(vm.screening.value)
        val new = sheet.editor.state.value.value
        assertNull(sheet.loaded, "en ny logg, fast lunchen redan är loggad")
        assertEquals(Triple(today, LocalTime(12, 30), Occasion.LUNCH), Triple(new.date, new.time, new.occasion))
        assertEquals(ScreeningSheetInfo(Occasion.LUNCH, null), sheet.context)
        assertTrue(sheet.editor.state.value.canSave, "en ny logg får sparas med förvalen (SCR-1)")

        vm.onEvent(LogEvent.CloseScreening)
        vm.onEvent(LogEvent.PickOccasion(yesterday))
        runCurrent()
        vm.onEvent(LogEvent.LogOccasion(vm.picker.value!!.occasions[0]))
        runCurrent()
        val past = assertNotNull(vm.screening.value)
        assertEquals(yesterday to LocalTime(8, 0), past.editor.state.value.value.let { it.date to it.time })
        assertEquals(ScreeningSheetInfo(Occasion.BREAKFAST, yesterday), past.context, "dagen i rubriken")
    }

    @Test
    fun `sparat stänger arket och visar Mående sparat (SCR-1, SCR-3)`() = runTest(main.dispatcher) {
        val vm = started()
        vm.onEvent(LogEvent.PickOccasion(null))
        runCurrent()
        vm.onEvent(LogEvent.LogOccasion(vm.picker.value!!.occasions[3]))
        runCurrent()
        vm.onEvent(LogEvent.ChangeEnergy(7))
        vm.onEvent(LogEvent.ChangeStress(2))
        vm.onEvent(LogEvent.SaveScreening)
        runCurrent()
        val saved = factory.screenings().getAll().getOrThrow().single()
        assertEquals(Triple(Occasion.BEDTIME, 7, 2), Triple(saved.occasion, saved.energy, saved.stress))
        assertTrue(vm.notice.value)
        assertTrue(vm.screening.value!!.closing.value)
        vm.onEvent(LogEvent.CloseScreening)
        vm.onEvent(LogEvent.NoticeShown)
        assertNull(vm.screening.value)
        assertFalse(vm.notice.value)
    }

    @Test
    fun `en måendepost i Dagbok öppnas i arket för ändring och sparas fältvis (HIST-3, SCR-1)`() = runTest(main.dispatcher) {
        val stored = Screening("s1", yesterday, LocalTime(21, 5), Occasion.BEDTIME, energy = 4, stress = 6, note = "Trött")
        factory.screenings().upsert(stored).getOrThrow()
        val vm = started()
        vm.onEvent(LogEvent.EditScreening(stored))
        val sheet = assertNotNull(vm.screening.value)
        assertEquals(stored, sheet.loaded)
        assertEquals(ScreeningSheetInfo(Occasion.BEDTIME, yesterday), sheet.context)
        assertFalse(sheet.editor.state.value.canSave, "oförändrad")
        vm.onEvent(LogEvent.ChangeStress(3))
        vm.onEvent(LogEvent.SaveScreening)
        runCurrent()
        assertEquals(stored.copy(stress = 3), factory.screenings().get("s1").getOrThrow())
    }

    @Test
    fun `väljaren öppnad för idag följer midnatt – dagen bestäms när raden trycks (HEM-8b, SCR-6)`() = runTest(main.dispatcher) {
        val vm = started()
        clock.instant = LocalDateTime(today, LocalTime(23, 58)).toInstant(zone)
        vm.onEvent(LogEvent.PickOccasion(null))
        runCurrent()
        assertEquals(today, vm.picker.value?.date)
        val tomorrow = LocalDate(2026, 10, 7)
        clock.instant = LocalDateTime(tomorrow, LocalTime(0, 1)).toInstant(zone)
        advanceTimeBy(3.minutes)
        runCurrent()
        assertEquals(tomorrow, vm.picker.value?.date, "väljaren följer med över midnatt")
        assertTrue(vm.picker.value!!.isToday)
        vm.onEvent(LogEvent.LogOccasion(vm.picker.value!!.occasions[0]))
        val new = assertNotNull(vm.screening.value).editor.state.value.value
        assertEquals(tomorrow to LocalTime(0, 1), new.date to new.time)
    }

    @Test
    fun `väljaren öppnad för en tidigare dag står kvar på den dagen över midnatt`() = runTest(main.dispatcher) {
        val vm = started()
        vm.onEvent(LogEvent.PickOccasion(yesterday))
        runCurrent()
        clock.instant = LocalDateTime(LocalDate(2026, 10, 7), LocalTime(0, 1)).toInstant(zone)
        advanceTimeBy(1.days)
        runCurrent()
        assertEquals(yesterday, vm.picker.value?.date)
        vm.onEvent(LogEvent.LogOccasion(vm.picker.value!!.occasions[3]))
        assertEquals(yesterday, vm.screening.value?.editor?.state?.value?.value?.date)
    }

    @Test
    fun `utloggning eller kontobyte glömmer den valda dagen (HEM-14)`() = runTest(main.dispatcher) {
        val vm = started()
        selectedDay.select(yesterday)
        assertEquals(yesterday, vm.logDay(onToday = true))
        (factory.scope as TestUserScope).uid.value = "annan"
        assertNull(vm.logDay(onToday = true))
        (factory.scope as TestUserScope).uid.value = "uid-test"
        assertNull(vm.logDay(onToday = true), "samma konto igen börjar också på idag")
    }
}
