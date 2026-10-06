package se.partee71.dagboken.ui.diary

import app.cash.turbine.test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import org.junit.Rule
import org.junit.Test
import se.partee71.dagboken.core.engine.DayLabel
import se.partee71.dagboken.core.engine.DiaryEntry
import se.partee71.dagboken.core.engine.DiaryType
import se.partee71.dagboken.core.model.Activity
import se.partee71.dagboken.core.model.Checkin
import se.partee71.dagboken.core.model.Dose
import se.partee71.dagboken.core.model.DoseStatus
import se.partee71.dagboken.core.model.Event
import se.partee71.dagboken.core.model.IllnessEpisode
import se.partee71.dagboken.core.model.Option
import se.partee71.dagboken.core.model.OptionKind
import se.partee71.dagboken.core.model.Screening
import se.partee71.dagboken.core.model.Slot
import se.partee71.dagboken.data.FakeCollectionFactory
import se.partee71.dagboken.data.FixedClock
import se.partee71.dagboken.data.common.DataError
import se.partee71.dagboken.data.repository.ActivityRepository
import se.partee71.dagboken.data.repository.DefaultActivityRepository
import se.partee71.dagboken.data.repository.DefaultEventRepository
import se.partee71.dagboken.data.repository.DefaultIllnessRepository
import se.partee71.dagboken.data.repository.DefaultOptionsRepository
import se.partee71.dagboken.data.repository.DefaultScreeningRepository
import se.partee71.dagboken.data.repository.EventRepository
import se.partee71.dagboken.data.repository.ScreeningRepository
import se.partee71.dagboken.data.repository.testDoses
import se.partee71.dagboken.testing.MainDispatcherRule
import se.partee71.dagboken.ui.common.ListUiState

/**
 * Fliken Dagbok (HIST-1, HIST-2, HIST-5, HIST-6, HIST-7, HIST-8, HIST-9) mot `FakeCollection` och en fast
 * klocka: tisdag 6 oktober 2026 kl. 10:00 i Europe/Stockholm.
 */
class DiaryViewModelTest {

    @get:Rule
    val main = MainDispatcherRule()

    private val zone = TimeZone.of("Europe/Stockholm")
    private val today = LocalDate(2026, 10, 6)
    private val yesterday = LocalDate(2026, 10, 5)
    private val saturday = LocalDate(2026, 10, 3)
    private val lastYear = LocalDate(2025, 3, 14)
    private val clock = FixedClock(at(today, 10))
    private val factory = FakeCollectionFactory(clock = clock)
    private val doses = testDoses(factory, zone, clock)
    private val illnesses = DefaultIllnessRepository(factory, clock)

    /** Varje läsning av aktiviteter (från, till) – när den börjar lyssna. */
    private val activityReads = mutableListOf<ClosedRange<LocalDate>>()

    /** Satt: läsningar som börjar före ankarets år svarar inte förrän flödet släpps (ett år som läses). */
    private var holdOlder: MutableSharedFlow<List<Activity>>? = null
    private val countingActivities = object : ActivityRepository by DefaultActivityRepository(factory, clock) {
        private val real = DefaultActivityRepository(factory, clock)

        override fun observeDays(from: LocalDate, to: LocalDate): Flow<List<Activity>> {
            val held = holdOlder?.takeIf { from < LocalDate(2025, 10, 7) }
            return (held ?: real.observeDays(from, to)).onStart { activityReads += from..to }
        }
    }

    private fun at(date: LocalDate, hour: Int, minute: Int = 0): Instant = LocalDateTime(date, LocalTime(hour, minute)).toInstant(zone)

    private val flu = IllnessEpisode("flu", "Förkylning", start = saturday, end = today, createdAt = at(saturday, 9))
    private val mood = Screening("s1", today, LocalTime(8, 15), energy = 6, note = "Sov gott")
    private val walk = Activity("a1", yesterday, LocalTime(17, 30), optionId = "promenad", energy = 3, minutes = 45)
    private val migraine = Event("e1", saturday, LocalTime(14, 0), optionId = "migran", severity = 7)
    private val taken = Dose("taken", today, Slot.MORNING, "Levaxin", "100", "µg", status = DoseStatus.TAKEN, plannedTime = LocalTime(8, 0), takenAt = at(today, 7, 42))
    private val planned = Dose("planned", today, Slot.EVENING, "Levaxin", "100", "µg", plannedTime = LocalTime(20, 0))
    private val skipped = Dose("skipped", yesterday, Slot.MORNING, "Levaxin", "100", "µg", status = DoseStatus.SKIPPED)
    private val checkin = Checkin("c1", yesterday, LocalTime(9, 0), severity = 4)
    private val old = Activity("old", lastYear, LocalTime(12, 0), optionId = "promenad")

    private suspend fun seed() {
        factory.screenings().upsert(mood).getOrThrow()
        factory.activities().batch(listOf(walk, old)).getOrThrow()
        factory.events().upsert(migraine).getOrThrow()
        factory.doses().batch(listOf(taken, planned, skipped)).getOrThrow()
        factory.illnessEpisodes().upsert(flu).getOrThrow()
        factory.checkins(flu.id).upsert(checkin).getOrThrow()
        factory.options().batch(listOf(Option("promenad", OptionKind.ACTIVITY, "Promenad"), Option("migran", OptionKind.EVENT, "Migrän"))).getOrThrow()
    }

    private fun viewModel(
        screenings: ScreeningRepository = DefaultScreeningRepository(factory, clock),
        activities: ActivityRepository = countingActivities,
        events: EventRepository = DefaultEventRepository(factory, clock),
    ) = DiaryViewModel(screenings, activities, doses, events, illnesses, DefaultOptionsRepository(factory), clock) { zone }

    private fun TestScope.started(vm: DiaryViewModel): DiaryViewModel {
        backgroundScope.launch { vm.state.collect {} }
        backgroundScope.launch { vm.controls.collect {} }
        runCurrent()
        return vm
    }

    private val DiaryViewModel.rows: List<DiaryRow> get() = (state.value as ListUiState.Content).items

    private val DiaryViewModel.ids: List<String> get() = rows.map { it.key }

    @Test
    fun `alla typer i ett flöde per dag, bara tagna doser och episodens start och slut (HIST-1, HIST-7, HIST-9)`() = runTest(main.dispatcher) {
        seed()
        val vm = viewModel()
        assertEquals(ListUiState.Loading, vm.state.value)
        vm.state.test {
            // Laddning först (kan slås ihop med innehållet), sedan dagarna.
            var rows: List<DiaryRow>? = null
            while (rows == null) rows = (awaitItem() as? ListUiState.Content)?.items
            assertEquals(
                listOf("episode-end:flu", "screening:s1", "dose:taken", "activity:a1", "checkin:flu/c1", "event:e1", "episode-start:flu"),
                rows.map { it.key },
            )
            assertEquals(listOf(DayLabel.TODAY, DayLabel.YESTERDAY, DayLabel.OTHER), rows.map { it.label }.distinct())
            val names = rows.filterIsInstance<DiaryRow.Entry>().associate { it.key to it.optionName }
            assertEquals("Promenad", names["activity:a1"])
            assertEquals("Migrän", names["event:e1"])
        }
    }

    @Test
    fun `filtret – en typ, flera typer, sista typen kvar och Alla (HIST-2)`() = runTest(main.dispatcher) {
        seed()
        val vm = started(viewModel())
        vm.onEvent(DiaryEvent.Toggle(DiaryType.EVENT))
        assertEquals(listOf("event:e1"), vm.ids)
        assertEquals(setOf(saturday), vm.controls.value.datesWithEntries)
        vm.onEvent(DiaryEvent.Toggle(DiaryType.DOSE))
        assertEquals(listOf("dose:taken", "event:e1"), vm.ids)
        vm.onEvent(DiaryEvent.Toggle(DiaryType.DOSE))
        vm.onEvent(DiaryEvent.Toggle(DiaryType.EVENT))
        assertEquals(setOf(DiaryType.EVENT), vm.controls.value.filter.types, "den sista typen går inte att slå av")
        vm.onEvent(DiaryEvent.Toggle(DiaryType.SCREENING))
        vm.onEvent(DiaryEvent.Toggle(DiaryType.EVENT))
        assertEquals(listOf("screening:s1"), vm.ids)
        vm.onEvent(DiaryEvent.ShowAll)
        assertTrue(vm.controls.value.filter.showsAll)
        assertEquals(7, vm.rows.size)
    }

    @Test
    fun `tomt för filtret när typen saknas det senaste året`() = runTest(main.dispatcher) {
        factory.screenings().upsert(mood).getOrThrow()
        val vm = started(viewModel())
        vm.onEvent(DiaryEvent.Toggle(DiaryType.EVENT))
        assertEquals(ListUiState.Empty, vm.state.value)
    }

    @Test
    fun `kalendern – vald dag, dag utan poster, framtiden går inte att välja och punkterna följer filtret (HIST-6)`() = runTest(main.dispatcher) {
        seed()
        val vm = started(viewModel())
        vm.onEvent(DiaryEvent.ShowView(DiaryView.CALENDAR))
        assertEquals(listOf("episode-end:flu", "screening:s1", "dose:taken"), vm.ids, "idag är vald från början")
        assertEquals(LocalDate(2026, 10, 1), vm.controls.value.month)
        assertEquals(setOf(today, yesterday, saturday), vm.controls.value.datesWithEntries)

        vm.onEvent(DiaryEvent.SelectDate(saturday))
        assertEquals(listOf("event:e1", "episode-start:flu"), vm.ids)
        assertEquals(saturday, vm.controls.value.selected)

        vm.onEvent(DiaryEvent.SelectDate(LocalDate(2026, 10, 4)))
        assertEquals(listOf<DiaryRow>(DiaryRow.EmptyDay(LocalDate(2026, 10, 4), DayLabel.OTHER)), vm.rows)

        vm.onEvent(DiaryEvent.SelectDate(LocalDate(2026, 10, 7)))
        assertEquals(LocalDate(2026, 10, 4), vm.controls.value.selected, "en dag i framtiden väljs inte")

        vm.onEvent(DiaryEvent.ShowView(DiaryView.LIST))
        assertEquals(7, vm.rows.size, "listvyn visar alla dagar igen")
    }

    @Test
    fun `Visa äldre läser bara det nya året och listan laddar aldrig om (HIST-8)`() = runTest(main.dispatcher) {
        seed()
        val vm = started(viewModel())
        assertTrue("activity:old" !in vm.ids, "ett år bakåt")
        assertEquals(listOf<ClosedRange<LocalDate>>(LocalDate(2025, 10, 7)..today), activityReads)

        val states = mutableListOf<ListUiState<DiaryRow>>()
        backgroundScope.launch { vm.state.collect { states += it } }
        runCurrent()
        vm.onEvent(DiaryEvent.ShowOlder)
        runCurrent()

        assertEquals(listOf<ClosedRange<LocalDate>>(LocalDate(2025, 10, 7)..today, LocalDate(2024, 10, 7)..LocalDate(2025, 10, 6)), activityReads, "det första året läses inte om")
        assertTrue("activity:old" in vm.ids)
        assertEquals(2, vm.controls.value.years)
        assertTrue(states.none { it == ListUiState.Loading }, "listan står kvar medan det nya året läses: $states")
    }

    @Test
    fun `vid midnatt läses bara år 0 om – de äldre årens gränser står still (HIST-8)`() = runTest(main.dispatcher) {
        seed()
        val vm = started(viewModel())
        vm.onEvent(DiaryEvent.ShowOlder)
        runCurrent()
        activityReads.clear()

        val tomorrow = LocalDate(2026, 10, 7)
        clock.instant = at(tomorrow, 0, 0)
        advanceTimeBy(14.hours + 1.minutes)
        runCurrent()

        assertEquals(listOf<ClosedRange<LocalDate>>(LocalDate(2025, 10, 7)..tomorrow), activityReads, "bara år 0, förlängt till den nya dagen")
        assertEquals(tomorrow, vm.controls.value.today)
        assertTrue("activity:old" in vm.ids)
    }

    @Test
    fun `ett äldre år som läses visar laddning – inte en tom dag – och Visa äldre laddar (HIST-6, HIST-8)`() = runTest(main.dispatcher) {
        seed()
        val held = MutableSharedFlow<List<Activity>>(replay = 1)
        holdOlder = held
        val vm = started(viewModel())
        vm.onEvent(DiaryEvent.ShowView(DiaryView.CALENDAR))
        vm.onEvent(DiaryEvent.SelectDate(lastYear))
        runCurrent()
        assertEquals(listOf<DiaryRow>(DiaryRow.LoadingDay(lastYear, DayLabel.OTHER)), vm.rows)
        assertTrue(vm.controls.value.loadingOlder)

        held.emit(listOf(old))
        runCurrent()
        assertEquals(listOf("activity:old"), vm.ids)
        assertEquals(false, vm.controls.value.loadingOlder)
    }

    @Test
    fun `en dos planerad dagen före fönstret men tagen inom det kommer med (HIST-7, HIST-8)`() = runTest(main.dispatcher) {
        val edge = Dose("kant", LocalDate(2025, 10, 6), Slot.EVENING, "Atarax", status = DoseStatus.TAKEN, prescriptionId = "atarax", takenAt = at(LocalDate(2025, 10, 7), 0, 30))
        val outside = Dose("ute", LocalDate(2025, 10, 6), Slot.MORNING, "Atarax", status = DoseStatus.TAKEN, takenAt = at(LocalDate(2025, 10, 6), 8))
        factory.doses().batch(listOf(edge, outside)).getOrThrow()
        val vm = started(viewModel())
        assertEquals(listOf("dose:kant"), vm.ids)
    }

    @Test
    fun `en äldre månad i kalendern läser in åren som behövs (HIST-6, HIST-8)`() = runTest(main.dispatcher) {
        seed()
        val vm = started(viewModel())
        vm.onEvent(DiaryEvent.ShowView(DiaryView.CALENDAR))
        vm.onEvent(DiaryEvent.ShowMonth(LocalDate(2025, 3, 1)))
        assertEquals(2, vm.controls.value.years)
        assertTrue(lastYear in vm.controls.value.datesWithEntries)
        vm.onEvent(DiaryEvent.SelectDate(lastYear))
        assertEquals(listOf("activity:old"), vm.ids)
    }

    @Test
    fun `radering går till postens repository och tar anteckningen med – episodens start tas inte bort här (HIST-5, DAT-7)`() = runTest(main.dispatcher) {
        seed()
        val vm = started(viewModel())
        val entries = vm.rows.filterIsInstance<DiaryRow.Entry>().associate { it.key to it.entry }
        listOf("screening:s1", "dose:taken", "activity:a1", "event:e1", "checkin:flu/c1", "episode-start:flu").forEach {
            vm.onEvent(DiaryEvent.Delete(entries.getValue(it)))
        }
        runCurrent()
        assertNull(factory.screenings().get(mood.id).getOrThrow(), "måendeloggen med sin anteckning")
        assertNull(factory.doses().get(taken.id).getOrThrow())
        assertNull(factory.activities().get(walk.id).getOrThrow())
        assertNull(factory.events().get(migraine.id).getOrThrow())
        assertNull(factory.checkins(flu.id).get(checkin.id).getOrThrow())
        assertEquals(flu.id, factory.illnessEpisodes().get(flu.id).getOrThrow()?.id, "episoden tas bort i sjukdomsdetaljen (#240)")
        assertEquals(listOf("episode-end:flu", "episode-start:flu"), vm.ids)
    }

    @Test
    fun `en radering som misslyckas visas som meddelande`() = runTest(main.dispatcher) {
        seed()
        val failing = object : EventRepository by DefaultEventRepository(factory, clock) {
            override suspend fun delete(id: String): Result<Unit> = Result.failure(DataError.PermissionDenied)
        }
        val vm = started(viewModel(events = failing))
        val event = vm.rows.filterIsInstance<DiaryRow.Entry>().single { it.key == "event:e1" }.entry
        vm.onEvent(DiaryEvent.Delete(event))
        runCurrent()
        assertEquals(DataError.PermissionDenied, vm.failure.value?.error)
        vm.onEvent(DiaryEvent.ErrorShown)
        assertNull(vm.failure.value)
    }

    @Test
    fun `ett läsfel visar fel, och Försök igen läser om`() = runTest(main.dispatcher) {
        seed()
        var fail = true
        val real = DefaultScreeningRepository(factory, clock)
        val flaky = object : ScreeningRepository by real {
            override fun observeDays(from: LocalDate, to: LocalDate): Flow<List<Screening>> =
                if (fail) flow { throw DataError.Offline } else real.observeDays(from, to)
        }
        val vm = started(viewModel(screenings = flaky))
        assertEquals(ListUiState.Error(DataError.Offline), vm.state.value)
        fail = false
        vm.onEvent(DiaryEvent.Retry)
        runCurrent()
        assertEquals(7, vm.rows.size)
    }
}
