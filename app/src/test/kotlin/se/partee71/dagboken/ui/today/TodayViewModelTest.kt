package se.partee71.dagboken.ui.today

import app.cash.turbine.test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
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
import se.partee71.dagboken.R
import se.partee71.dagboken.core.engine.Due
import se.partee71.dagboken.core.engine.EnergyTrend
import se.partee71.dagboken.core.engine.OccasionStatus
import se.partee71.dagboken.core.engine.WeekSummary
import se.partee71.dagboken.core.engine.computeDailyEnergyStats
import se.partee71.dagboken.core.model.Checkin
import se.partee71.dagboken.core.model.Dose
import se.partee71.dagboken.core.model.DoseIds
import se.partee71.dagboken.core.model.DoseStatus
import se.partee71.dagboken.core.model.IllnessEpisode
import se.partee71.dagboken.core.model.Occasion
import se.partee71.dagboken.core.model.Option
import se.partee71.dagboken.core.model.OptionKind
import se.partee71.dagboken.core.model.OccasionReminder
import se.partee71.dagboken.core.model.Period
import se.partee71.dagboken.core.model.Prescription
import se.partee71.dagboken.core.model.PrnMedicine
import se.partee71.dagboken.core.model.ReminderSettings
import se.partee71.dagboken.core.model.Schedule
import se.partee71.dagboken.core.model.Screening
import se.partee71.dagboken.core.model.Settings
import se.partee71.dagboken.core.model.Slot
import se.partee71.dagboken.core.model.SymptomScore
import se.partee71.dagboken.data.FakeCollectionFactory
import se.partee71.dagboken.data.FixedClock
import se.partee71.dagboken.data.common.DataError
import se.partee71.dagboken.data.repository.DefaultIllnessRepository
import se.partee71.dagboken.data.repository.DefaultOptionsRepository
import se.partee71.dagboken.data.repository.DefaultPrnMedicineRepository
import se.partee71.dagboken.data.repository.DefaultScreeningRepository
import se.partee71.dagboken.data.repository.DefaultSettingsRepository
import se.partee71.dagboken.data.repository.IllnessRepository
import se.partee71.dagboken.data.repository.OptionsRepository
import se.partee71.dagboken.data.repository.PrescriptionRepository
import se.partee71.dagboken.data.repository.ScreeningRepository
import se.partee71.dagboken.data.repository.testDoses
import se.partee71.dagboken.data.repository.testPrescriptions
import se.partee71.dagboken.testing.MainDispatcherRule
import se.partee71.dagboken.ui.log.CooldownPrompt
import se.partee71.dagboken.ui.common.DetailUiState
import se.partee71.dagboken.ui.common.SelectedDay

/**
 * Fliken Idag (HEM-4, HEM-5, HEM-7, HEM-10, HEM-12, HEM-13, HEM-14, HEM-18, HEM-19, SCR-1, SCR-6, MED-1–3, MED-5, MED-13, MED-14, FAV-2–6, FAV-8, FAV-11) mot
 * `FakeCollection` och en fast klocka: söndag 4 oktober 2026 kl. 10:30 i Europe/Stockholm.
 */
class TodayViewModelTest {

    @get:Rule
    val main = MainDispatcherRule()

    private val zone = TimeZone.of("Europe/Stockholm")
    private val today = LocalDate(2026, 10, 4)
    private val yesterday = LocalDate(2026, 10, 3)
    private val clock = FixedClock(at(today, 10, 30))
    private val factory = FakeCollectionFactory(clock = clock)
    private val doses = testDoses(factory, zone, clock)

    private val levaxin = Prescription(
        "levaxin", "Levaxin", "100", "µg", listOf(Slot.MORNING, Slot.EVENING), Schedule.Repeating(), Period(LocalDate(2026, 1, 1)),
    )
    private val alvedon = PrnMedicine("alvedon", "Alvedon", "500", "mg", minHoursBetween = 4, maxPerDay = 3, favorite = true)
    private val imigran = PrnMedicine("imigran", "Imigran", "50", "mg", minHoursBetween = 0, maxPerDay = 1)

    private fun at(date: LocalDate, hour: Int, minute: Int = 0): Instant = LocalDateTime(date, LocalTime(hour, minute)).toInstant(zone)

    private fun viewModel(
        prescriptions: PrescriptionRepository = testPrescriptions(factory, doses, zone, clock),
        screenings: ScreeningRepository = DefaultScreeningRepository(factory, clock),
        illnesses: IllnessRepository = DefaultIllnessRepository(factory, clock),
        selectedDay: SelectedDay = SelectedDay(factory.scope),
    ) = TodayViewModel(
        doses,
        prescriptions,
        DefaultPrnMedicineRepository(factory),
        screenings,
        DefaultSettingsRepository(factory),
        illnesses,
        selectedDay,
        clock,
    ) { zone }

    private suspend fun seed() {
        factory.prescriptions().upsert(levaxin).getOrThrow()
        factory.prnMedicines().batch(listOf(alvedon, imigran)).getOrThrow()
    }

    private fun TestScope.started(vm: TodayViewModel): TodayViewModel {
        backgroundScope.launch { vm.state.collect {} }
        runCurrent()
        return vm
    }

    private val TodayViewModel.content: TodayContent get() = (state.value as DetailUiState.Content).value

    private suspend fun stored(id: String): Dose? = factory.doses().get(id).getOrThrow()

    private fun morning(date: LocalDate = today) = DoseIds.prescribed(levaxin.id, date, Slot.MORNING)

    private fun evening(date: LocalDate = today) = DoseIds.prescribed(levaxin.id, date, Slot.EVENING)

    @Test
    fun `dagen laddas och dagens receptdoser skapas – försenat visas, kvällen är kommande (HEM-10, MED-1, MED-13)`() = runTest(main.dispatcher) {
        seed()
        val vm = viewModel()
        assertEquals(DetailUiState.Loading, vm.state.value)
        vm.state.test {
            // Laddning först (kan slås ihop med innehållet), sedan dagen – doserna kommer när de skapats.
            var content: TodayContent? = null
            while (content == null || content.checklist.isEmpty) content = (awaitItem() as? DetailUiState.Content)?.value
            assertEquals(today, content.date)
            assertEquals(listOf(morning() to Due.LATE), content.checklist.shown.map { it.dose.id to it.due })
            assertEquals(listOf(evening()), content.checklist.upcoming.map { it.dose.id })
            assertEquals(0, content.progress.done)
            assertEquals(2, content.progress.total)
            assertEquals(listOf(alvedon), content.choices.favorites)
            assertEquals(listOf(imigran), content.choices.others)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `en tidigare dag får sina doser när den väljs, en framtida dag går inte att välja (HEM-10, HEM-14)`() = runTest(main.dispatcher) {
        seed()
        val vm = started(viewModel())
        assertNull(stored(morning(yesterday)), "inget förrän dagen visas")

        vm.onEvent(TodayEvent.SelectDate(yesterday))
        runCurrent()
        assertEquals(yesterday, vm.content.date)
        assertNotNull(stored(morning(yesterday)))
        assertEquals(setOf(Due.PAST), vm.content.checklist.shown.map { it.due }.toSet(), "en tidigare dag är aldrig försenad (HEM-4)")
        assertTrue(vm.content.checklist.upcoming.isEmpty())

        vm.onEvent(TodayEvent.SelectDate(LocalDate(2026, 10, 5)))
        runCurrent()
        assertEquals(yesterday, vm.content.date)
        assertNull(stored(morning(LocalDate(2026, 10, 5))))
    }

    @Test
    fun `den valda dagen delas med plusknappen – idag är null, andra flikar loggar mot idag (HEM-14, NAV-10)`() = runTest(main.dispatcher) {
        seed()
        val selected = SelectedDay(factory.scope)
        val vm = started(viewModel(selectedDay = selected))
        vm.onEvent(TodayEvent.SelectDate(yesterday))
        runCurrent()
        assertEquals(yesterday, selected.logDay(onToday = true))
        assertNull(selected.logDay(onToday = false), "andra flikar loggar mot idag")
        vm.onEvent(TodayEvent.SelectDate(today))
        runCurrent()
        assertNull(selected.logDay(onToday = true), "idag lagras som null och följer med över midnatt")
        selected.select(yesterday)
        runCurrent()
        assertEquals(yesterday, vm.content.date, "Idag visar den delade dagen")
    }

    @Test
    fun `en ny Idag börjar på idag, också när en tidigare dag var vald (HEM-14)`() = runTest(main.dispatcher) {
        seed()
        val selected = SelectedDay(factory.scope)
        selected.select(yesterday)
        val vm = started(viewModel(selectedDay = selected))
        assertEquals(today, vm.content.date)
        assertNull(selected.logDay(onToday = true))
    }

    @Test
    fun `remsans vecka byts utan att dagen byts, och ett val visar dagens vecka igen (HEM-14)`() = runTest(main.dispatcher) {
        seed()
        val vm = started(viewModel())
        assertEquals(LocalDate(2026, 9, 28), vm.content.week)
        vm.onEvent(TodayEvent.ShowWeek(LocalDate(2026, 9, 21)))
        runCurrent()
        assertEquals(LocalDate(2026, 9, 21), vm.content.week)
        assertEquals(today, vm.content.date)
        vm.onEvent(TodayEvent.SelectDate(LocalDate(2026, 9, 23)))
        runCurrent()
        assertEquals(LocalDate(2026, 9, 21), vm.content.week)
        assertEquals(LocalDate(2026, 9, 23), vm.content.date)
    }

    @Test
    fun `vid midnatt följer idag med och nästa dags doser skapas`() = runTest(main.dispatcher) {
        seed()
        val vm = started(viewModel())
        assertEquals(today, vm.content.today)
        val tomorrow = LocalDate(2026, 10, 5)

        clock.instant = at(tomorrow, 0, 0)
        advanceTimeBy(1.minutes)
        runCurrent()

        assertEquals(tomorrow, vm.content.today)
        assertEquals(tomorrow, vm.content.date)
        assertNotNull(stored(morning(tomorrow)))
    }

    @Test
    fun `en vald tidigare dag står kvar över midnatt`() = runTest(main.dispatcher) {
        seed()
        val vm = started(viewModel())
        vm.onEvent(TodayEvent.SelectDate(yesterday))
        runCurrent()
        clock.instant = at(LocalDate(2026, 10, 5), 0, 0)
        advanceTimeBy(1.minutes)
        runCurrent()
        assertEquals(yesterday, vm.content.date)
        assertEquals(LocalDate(2026, 10, 5), vm.content.today)
    }

    @Test
    fun `avbockning sparar tagningstiden och kan ångras (MED-2, MED-14)`() = runTest(main.dispatcher) {
        seed()
        val vm = started(viewModel())
        val dose = vm.content.checklist.shown.single().dose

        vm.undo.test {
            assertNull(awaitItem())
            vm.onEvent(TodayEvent.SetTaken(dose, taken = true))
            val request = assertNotNull(awaitItem())
            assertEquals("Levaxin 100 µg", request.name)
            assertEquals(R.string.today_undo_taken_format, request.format)
            assertEquals(DoseStatus.TAKEN, stored(dose.id)?.status)
            assertEquals(clock.instant, stored(dose.id)?.takenAt)
            assertEquals(listOf(dose.id), vm.content.checklist.done.map { it.id }, "tagna döljs bakom Visa tagna (MED-5)")

            vm.onEvent(TodayEvent.Undo)
            assertNull(awaitItem())
            runCurrent()
            assertEquals(DoseStatus.PLANNED, stored(dose.id)?.status)
            assertNull(stored(dose.id)?.takenAt)
        }
    }

    @Test
    fun `att bocka av en tagen dos nollställer tagningstiden utan ångra (MED-14)`() = runTest(main.dispatcher) {
        seed()
        val vm = started(viewModel())
        val dose = vm.content.checklist.shown.single().dose
        vm.onEvent(TodayEvent.SetTaken(dose, taken = true))
        runCurrent()
        vm.onEvent(TodayEvent.SetTaken(stored(dose.id)!!, taken = false))
        runCurrent()
        assertEquals(DoseStatus.PLANNED, stored(dose.id)?.status)
        assertNull(stored(dose.id)?.takenAt)
        assertNull(vm.undo.value)
    }

    @Test
    fun `hoppa över markerar dosen som överhoppad och kan ångras (MED-3)`() = runTest(main.dispatcher) {
        seed()
        val vm = started(viewModel())
        val dose = vm.content.checklist.shown.single().dose
        vm.onEvent(TodayEvent.Skip(dose))
        runCurrent()
        assertEquals(DoseStatus.SKIPPED, stored(dose.id)?.status)
        assertEquals(R.string.today_undo_skipped_format, vm.undo.value?.format)
        assertEquals(1, vm.content.progress.done, "överhoppad räknas som klar (HEM-18)")

        vm.onEvent(TodayEvent.Undo)
        runCurrent()
        assertEquals(DoseStatus.PLANNED, stored(dose.id)?.status)
        assertNull(vm.undo.value)
    }

    @Test
    fun `två snabba avbockningar - det förra ångrats utgång släpper inte det nya`() = runTest(main.dispatcher) {
        seed()
        val vm = started(viewModel())
        val morning = vm.content.checklist.shown.single().dose
        val evening = stored(evening())!!
        vm.onEvent(TodayEvent.SetTaken(morning, true))
        runCurrent()
        val first = assertNotNull(vm.undo.value)
        vm.onEvent(TodayEvent.SetTaken(evening, true))
        runCurrent()
        val second = assertNotNull(vm.undo.value)
        assertTrue(first.id != second.id)

        vm.onEvent(TodayEvent.UndoDismissed(first.id))
        assertEquals(second, vm.undo.value, "det nya ångrat står kvar")
        vm.onEvent(TodayEvent.Undo)
        runCurrent()
        assertEquals(DoseStatus.PLANNED, stored(evening.id)?.status)
        assertEquals(DoseStatus.TAKEN, stored(morning.id)?.status)

        vm.onEvent(TodayEvent.SetTaken(stored(evening.id)!!, true))
        runCurrent()
        val third = assertNotNull(vm.undo.value)
        vm.onEvent(TodayEvent.UndoDismissed(third.id))
        assertNull(vm.undo.value)
    }

    @Test
    fun `en tidigare dag loggar inga snabbval – de loggas i efterhand (FAV-2, FAV-11, MED-16)`() = runTest(main.dispatcher) {
        seed()
        val vm = started(viewModel())
        vm.onEvent(TodayEvent.SelectDate(yesterday))
        runCurrent()
        vm.onEvent(TodayEvent.LogAsNeeded(imigran))
        vm.onEvent(TodayEvent.LogExtra(levaxin))
        runCurrent()
        assertTrue(factory.doses().getAll().getOrThrow().none { it.slot == Slot.AS_NEEDED })
        assertNull(vm.notice.value)

        vm.onEvent(TodayEvent.SelectDate(today))
        runCurrent()
        vm.onEvent(TodayEvent.LogAsNeeded(imigran))
        runCurrent()
        assertEquals(1, factory.doses().getAll().getOrThrow().count { it.prnId == imigran.id })
    }

    @Test
    fun `att bocka ur en annan dos lämnar ångra för den första (MED-2)`() = runTest(main.dispatcher) {
        seed()
        val vm = started(viewModel())
        val morning = vm.content.checklist.shown.single().dose
        val evening = stored(evening())!!
        vm.onEvent(TodayEvent.SetTaken(evening, true))
        runCurrent()
        vm.onEvent(TodayEvent.SetTaken(morning, true))
        runCurrent()
        val request = assertNotNull(vm.undo.value)
        vm.onEvent(TodayEvent.SetTaken(stored(evening.id)!!, false))
        runCurrent()
        assertEquals(request, vm.undo.value)
        vm.onEvent(TodayEvent.SetTaken(stored(morning.id)!!, false))
        runCurrent()
        assertNull(vm.undo.value, "ångra för morgonen är inaktuellt när den bockats ur")
    }

    @Test
    fun `en tidigare dag bockas av på dosens planerade tid (MED-14)`() = runTest(main.dispatcher) {
        seed()
        val vm = started(viewModel())
        vm.onEvent(TodayEvent.SelectDate(yesterday))
        runCurrent()
        val dose = vm.content.checklist.shown.first().dose
        vm.onEvent(TodayEvent.SetTaken(dose, true))
        runCurrent()
        assertEquals(at(yesterday, 7), stored(dose.id)?.takenAt)
    }

    @Test
    fun `en loggad vid behov-dos går inte att bocka ur (FAV-2, FAV-11)`() = runTest(main.dispatcher) {
        seed()
        val vm = started(viewModel())
        vm.onEvent(TodayEvent.LogAsNeeded(imigran))
        runCurrent()
        val logged = factory.doses().getAll().getOrThrow().single { it.prnId == imigran.id }
        val undo = vm.undo.value
        vm.onEvent(TodayEvent.SetTaken(logged, false))
        runCurrent()
        assertEquals(DoseStatus.TAKEN, stored(logged.id)?.status)
        assertEquals(undo, vm.undo.value, "loggningens Ångra står kvar")
    }

    @Test
    fun `en loggad vid behov-dos kan ångras – den tas bort igen (FAV-2)`() = runTest(main.dispatcher) {
        seed()
        val vm = started(viewModel())
        vm.onEvent(TodayEvent.LogAsNeeded(imigran))
        runCurrent()
        val logged = factory.doses().getAll().getOrThrow().single { it.prnId == imigran.id }
        assertEquals("Imigran 50 mg", vm.undo.value?.name)
        assertEquals(R.string.today_logged_format, vm.undo.value?.format)
        assertNull(vm.notice.value, "bekräftelsen är Ångra-meddelandet")
        vm.onEvent(TodayEvent.Undo)
        runCurrent()
        assertNull(stored(logged.id), "ångrad = borttagen med sin anteckning")
        assertNull(vm.undo.value)
    }

    @Test
    fun `en loggad vid behov- eller extrados raderas i radens meny, en receptdos aldrig (MED-3)`() = runTest(main.dispatcher) {
        seed()
        val vm = started(viewModel())
        vm.onEvent(TodayEvent.LogAsNeeded(imigran))
        vm.onEvent(TodayEvent.LogExtra(levaxin))
        runCurrent()
        val logged = factory.doses().getAll().getOrThrow().filter { it.slot == Slot.AS_NEEDED }
        assertEquals(2, logged.size)
        logged.forEach { vm.onEvent(TodayEvent.Delete(it)) }
        runCurrent()
        assertTrue(logged.all { stored(it.id) == null })
        assertNull(vm.undo.value, "Ångra för en raderad dos är inaktuellt")
        val scheduled = vm.content.checklist.shown.first().dose
        vm.onEvent(TodayEvent.Delete(scheduled))
        runCurrent()
        assertEquals(scheduled, stored(scheduled.id), "en receptdos hoppas över, den raderas inte här")
        val oneOff = Dose("o", today, Slot.EVENING, "Melatonin", "3", "mg", DoseStatus.TAKEN, takenAt = at(today, 9, 0))
        factory.doses().upsert(oneOff).getOrThrow()
        vm.onEvent(TodayEvent.Delete(oneOff))
        runCurrent()
        assertNull(stored(oneOff.id), "en engångsdos med tidpunkt är ingen receptdos – den raderas")
    }

    @Test
    fun `Visa tagna och Visa kommande växlar (MED-5, MED-13)`() = runTest(main.dispatcher) {
        seed()
        val vm = started(viewModel())
        assertFalse(vm.content.showDone)
        assertFalse(vm.content.showUpcoming)
        vm.onEvent(TodayEvent.ToggleDone)
        vm.onEvent(TodayEvent.ToggleUpcoming)
        runCurrent()
        assertTrue(vm.content.showDone)
        assertTrue(vm.content.showUpcoming)
    }

    @Test
    fun `inom kylperioden frågar snabbvalet, och Ta ändå loggar (FAV-4)`() = runTest(main.dispatcher) {
        seed()
        factory.doses().upsert(Dose("tidigare", today, Slot.AS_NEEDED, "Alvedon", "500", "mg", DoseStatus.TAKEN, takenAt = at(today, 9, 30), prnId = alvedon.id)).getOrThrow()
        val vm = started(viewModel())

        vm.onEvent(TodayEvent.LogAsNeeded(alvedon))
        runCurrent()
        assertEquals(CooldownPrompt(alvedon, 3.hours), vm.cooldown.value)
        assertEquals(1, factory.doses().getAll().getOrThrow().count { it.prnId == alvedon.id }, "inget loggat än")

        vm.onEvent(TodayEvent.ConfirmCooldown)
        runCurrent()
        assertNull(vm.cooldown.value)
        val logged = factory.doses().getAll().getOrThrow().filter { it.prnId == alvedon.id }
        assertEquals(2, logged.size)
        assertEquals(R.string.today_logged_format, vm.undo.value?.format)
        assertEquals("Alvedon 500 mg", vm.undo.value?.name)
    }

    @Test
    fun `avbruten kylperiodsfråga loggar inget (FAV-4)`() = runTest(main.dispatcher) {
        seed()
        factory.doses().upsert(Dose("tidigare", today, Slot.AS_NEEDED, "Alvedon", "500", "mg", DoseStatus.TAKEN, takenAt = at(today, 10, 0), prnId = alvedon.id)).getOrThrow()
        val vm = started(viewModel())
        vm.onEvent(TodayEvent.LogAsNeeded(alvedon))
        runCurrent()
        assertEquals(CooldownPrompt(alvedon, 3.hours + 30.minutes), vm.cooldown.value)
        vm.onEvent(TodayEvent.DismissCooldown)
        runCurrent()
        assertNull(vm.cooldown.value)
        assertEquals(1, factory.doses().getAll().getOrThrow().count { it.prnId == alvedon.id })
    }

    @Test
    fun `dagsgränsen stoppar med ett meddelande och sparar inget (FAV-5, FAV-6)`() = runTest(main.dispatcher) {
        seed()
        factory.doses().upsert(Dose("tidigare", today, Slot.AS_NEEDED, "Imigran", "50", "mg", DoseStatus.TAKEN, takenAt = at(today, 8, 0), prnId = imigran.id)).getOrThrow()
        val vm = started(viewModel())
        vm.onEvent(TodayEvent.LogAsNeeded(imigran))
        runCurrent()
        assertNull(vm.cooldown.value)
        assertEquals(R.string.today_limit_reached_format, vm.notice.value?.text)
        assertEquals(listOf<Any>(1, "Imigran"), vm.notice.value?.args?.toList())
        assertEquals(1, factory.doses().getAll().getOrThrow().count { it.prnId == imigran.id })
        vm.onEvent(TodayEvent.NoticeShown)
        assertNull(vm.notice.value)
    }

    @Test
    fun `ett recept i Fler loggas som extrados med dagens dos (FAV-11)`() = runTest(main.dispatcher) {
        seed()
        val vm = started(viewModel())
        assertEquals(listOf(levaxin), vm.content.choices.prescriptions)
        vm.onEvent(TodayEvent.LogExtra(levaxin))
        runCurrent()
        val extra = factory.doses().getAll().getOrThrow().single { it.slot == Slot.AS_NEEDED }
        assertEquals("100", extra.dose)
        assertNull(extra.prescriptionId)
        assertEquals(DoseStatus.TAKEN, extra.status)
        assertEquals("Levaxin 100 µg", vm.undo.value?.name, "loggad med Ångra")
        assertEquals(2, vm.content.progress.total, "en extrados räknas inte i framstegen (HEM-18)")
        vm.onEvent(TodayEvent.Undo)
        runCurrent()
        assertNull(stored(extra.id), "ångrad extrados tas bort")
    }

    @Test
    fun `stjärnan och radering i långtrycksmenyn (FAV-3, FAV-8)`() = runTest(main.dispatcher) {
        seed()
        val vm = started(viewModel())
        vm.onEvent(TodayEvent.ToggleFavorite(imigran))
        runCurrent()
        assertEquals(listOf("alvedon", "imigran"), vm.content.choices.favorites.map { it.id })
        vm.onEvent(TodayEvent.DeleteMedicine(alvedon))
        runCurrent()
        assertNull(factory.prnMedicines().get(alvedon.id).getOrThrow())
        assertEquals(listOf("imigran"), vm.content.choices.favorites.map { it.id })
    }

    @Test
    fun `allt klart idag ger belöningsläget och konfettin faller en gång (HEM-19, HEM-20)`() = runTest(main.dispatcher) {
        seed()
        // Igår helt klar och med en måendelogg, så att jämförelsen och sviten har underlag.
        val settings = Settings(reminders = ReminderSettings(screeningOccasions = Occasion.entries.map { OccasionReminder(it, enabled = it == Occasion.LUNCH) }))
        factory.settings().upsert(settings).getOrThrow()
        factory.screenings().batch(
            listOf(
                Screening("s1", yesterday, LocalTime(12, 0), Occasion.LUNCH, energy = 5),
                Screening("s2", today, LocalTime(10, 0), Occasion.LUNCH, energy = 7),
            ),
        ).getOrThrow()
        factory.doses().batch(
            listOf(morning(yesterday), evening(yesterday)).map { id ->
                Dose(id, yesterday, Slot.MORNING, "Levaxin", status = DoseStatus.TAKEN, takenAt = at(yesterday, 8), prescriptionId = levaxin.id)
            },
        ).getOrThrow()
        val vm = started(viewModel())
        assertNull(vm.content.dayDone)

        vm.content.checklist.shown.single().dose.let { vm.onEvent(TodayEvent.SetTaken(it, true)) }
        runCurrent()
        assertNull(vm.content.dayDone, "kvällsdosen återstår")
        vm.onEvent(TodayEvent.Skip(stored(evening())!!))
        runCurrent()

        val done = assertNotNull(vm.content.dayDone)
        assertTrue(vm.content.todayComplete)
        assertEquals(2, done.streak)
        assertEquals(7f, done.comparison?.average)
        assertEquals(2f, done.comparison?.changeFromYesterday)
        assertTrue(done.playConfetti)

        vm.onEvent(TodayEvent.ConfettiShown)
        runCurrent()
        assertFalse(vm.content.dayDone!!.playConfetti)

        // Ångra och klara igen: ingen ny konfetti samma dag.
        vm.onEvent(TodayEvent.Undo)
        runCurrent()
        assertNull(vm.content.dayDone)
        vm.onEvent(TodayEvent.Skip(stored(evening())!!))
        runCurrent()
        assertFalse(vm.content.dayDone!!.playConfetti)
    }

    @Test
    fun `en klar tidigare dag ger inget belöningsläge (HEM-19)`() = runTest(main.dispatcher) {
        seed()
        factory.doses().batch(
            listOf(morning(yesterday), evening(yesterday)).map { id ->
                Dose(id, yesterday, Slot.MORNING, "Levaxin", status = DoseStatus.TAKEN, takenAt = at(yesterday, 8), prescriptionId = levaxin.id)
            },
        ).getOrThrow()
        val vm = started(viewModel())
        vm.onEvent(TodayEvent.SelectDate(yesterday))
        runCurrent()
        assertTrue(vm.content.progress.isComplete)
        assertNull(vm.content.dayDone)
        assertFalse(vm.content.todayComplete)
        assertTrue(yesterday in vm.content.datesWithEntries)
    }

    @Test
    fun `tillfällena får status mot klockan, och en tidigare dag är ej loggad (HEM-4, HEM-5, NOT-4)`() = runTest(main.dispatcher) {
        seed()
        allOccasions()
        factory.screenings().upsert(Screening("s", today, LocalTime(8, 40), Occasion.BREAKFAST, energy = 6, stress = 3)).getOrThrow()
        val vm = started(viewModel())
        assertEquals(
            listOf(
                Occasion.BREAKFAST to OccasionStatus.LOGGED,
                Occasion.LUNCH to OccasionStatus.SOON,
                Occasion.DINNER to OccasionStatus.UPCOMING,
                Occasion.BEDTIME to OccasionStatus.UPCOMING,
            ),
            vm.content.occasions.map { it.occasion to it.status },
        )
        assertEquals(6, vm.content.progress.total, "två doser och fyra tillfällen (HEM-18)")

        clock.instant = at(today, 12, 5)
        advanceTimeBy(2.minutes)
        runCurrent()
        assertEquals(OccasionStatus.LATE, vm.content.occasions[1].status, "klockslaget nått")

        vm.onEvent(TodayEvent.SelectDate(yesterday))
        runCurrent()
        assertEquals(List(4) { OccasionStatus.NOT_LOGGED }, vm.content.occasions.map { it.status })
    }

    @Test
    fun `pågående sjukdom visas med dag och senaste incheckning, en avslutad inte (HEM-12)`() = runTest(main.dispatcher) {
        seed()
        val vm = started(viewModel())
        assertNull(vm.content.illness)

        val episode = IllnessEpisode("e", "Förkylning", start = LocalDate(2026, 10, 1))
        factory.illnessEpisodes().batch(listOf(episode, IllnessEpisode("f", "Migrän", start = LocalDate(2026, 9, 1), end = LocalDate(2026, 9, 3)))).getOrThrow()
        val checkin = Checkin("c2", LocalDate(2026, 10, 3), LocalTime(20, 0), severity = 4)
        factory.checkins(episode.id).batch(listOf(Checkin("c1", LocalDate(2026, 10, 2), LocalTime(9, 0), severity = 6), checkin)).getOrThrow()
        runCurrent()

        val illness = assertNotNull(vm.content.illness)
        assertEquals(episode, illness.episode)
        assertEquals(4, illness.day)
        assertEquals(checkin, illness.lastCheckin)

        factory.illnessEpisodes().upsert(episode.copy(end = today)).getOrThrow()
        runCurrent()
        assertNull(vm.content.illness)
    }

    @Test
    fun `Din vecka visas söndag och måndag med underlag, inte andra dagar eller en tidigare dag (HEM-13)`() = runTest(main.dispatcher) {
        val vm = started(viewModel())
        assertNull(vm.content.weekSummary, "söndag men inget underlag än")

        factory.screenings().upsert(Screening("s", LocalDate(2026, 10, 2), LocalTime(9, 0), Occasion.BREAKFAST, energy = 6)).getOrThrow()
        runCurrent()
        assertEquals(WeekSummary(EnergyTrend.SAME, dosesTakenPercent = null), vm.content.weekSummary, "söndag, utan doser ingen dosandel")

        // En förfallen morgondos som inte är tagen ger en dosandel.
        factory.doses().upsert(Dose(morning(), today, Slot.MORNING, "Levaxin", prescriptionId = levaxin.id)).getOrThrow()
        runCurrent()
        assertEquals(0, vm.content.weekSummary?.dosesTakenPercent)

        vm.onEvent(TodayEvent.SelectDate(yesterday))
        runCurrent()
        assertNull(vm.content.weekSummary, "en tidigare dag")
        vm.onEvent(TodayEvent.SelectDate(today))
        runCurrent()

        clock.instant = at(LocalDate(2026, 10, 5), 9, 0)
        advanceTimeBy(1.minutes)
        runCurrent()
        assertNotNull(vm.content.weekSummary, "måndag")

        clock.instant = at(LocalDate(2026, 10, 6), 9, 0)
        advanceTimeBy(1.minutes)
        runCurrent()
        assertNull(vm.content.weekSummary, "tisdag")
    }

    @Test
    fun `7-dagarstrenden är dagsvärdet de sju senaste dagarna, också när en tidigare dag visas (HEM-7)`() = runTest(main.dispatcher) {
        seed()
        val screenings = listOf(
            Screening("a", today, LocalTime(8, 0), Occasion.BREAKFAST, energy = 6),
            Screening("b", today, LocalTime(9, 0), Occasion.LUNCH, energy = 8),
            Screening("c", LocalDate(2026, 10, 1), LocalTime(8, 0), Occasion.BREAKFAST, energy = 3),
            Screening("d", LocalDate(2026, 9, 27), LocalTime(8, 0), Occasion.BREAKFAST, energy = 9),
        )
        factory.screenings().batch(screenings).getOrThrow()
        val vm = started(viewModel())
        assertEquals((28..30).map { LocalDate(2026, 9, it) } + (1..4).map { LocalDate(2026, 10, it) }, vm.content.energyDays)
        assertEquals(listOf(null, null, null, 3f, null, null, 7f), vm.content.energy)
        assertEquals(computeDailyEnergyStats(screenings).last().avg, vm.content.energy.last(), "samma dagsvärde som Trender (TRD-8)")

        vm.onEvent(TodayEvent.SelectDate(LocalDate(2026, 9, 29)))
        runCurrent()
        assertEquals(today, vm.content.energyDays.last())
    }


    @Test
    fun `episoderna går inte att läsa – Idag visas utan sjukdomskort (HEM-12)`() = runTest(main.dispatcher) {
        seed()
        val failing = object : IllnessRepository by DefaultIllnessRepository(factory, clock) {
            override fun observeEpisodes(): Flow<List<IllnessEpisode>> = denied
        }
        val vm = started(viewModel(illnesses = failing))
        runCurrent()
        assertNull(vm.content.illness)
        assertEquals(2, vm.content.progress.total, "resten av fliken visas")
        assertNull(vm.failure.value)
    }

    @Test
    fun `incheckningarna går inte att läsa – kortet visas utan incheckning (HEM-12)`() = runTest(main.dispatcher) {
        seed()
        val episode = IllnessEpisode("e", "Förkylning", start = LocalDate(2026, 10, 1))
        factory.illnessEpisodes().upsert(episode).getOrThrow()
        val failing = object : IllnessRepository by DefaultIllnessRepository(factory, clock) {
            override fun observeCheckins(episodeId: String): Flow<List<Checkin>> = denied
        }
        val vm = started(viewModel(illnesses = failing))
        runCurrent()
        assertEquals(episode, vm.content.illness?.episode)
        assertNull(vm.content.illness?.lastCheckin)
    }

    @Test
    fun `byte av pågående episod visar aldrig den nya episoden med den gamlas incheckningar (HEM-12)`() = runTest(main.dispatcher) {
        seed()
        val first = IllnessEpisode("a", "Förkylning", start = LocalDate(2026, 9, 20), createdAt = at(LocalDate(2026, 9, 20), 8))
        val second = IllnessEpisode("b", "Migrän", start = LocalDate(2026, 10, 3), createdAt = at(LocalDate(2026, 10, 3), 8))
        val episodes = MutableStateFlow(listOf(first))
        val checkins = mapOf(first.id to MutableSharedFlow<List<Checkin>>(replay = 1), second.id to MutableSharedFlow(replay = 1))
        val repository = object : IllnessRepository by DefaultIllnessRepository(factory, clock) {
            override fun observeEpisodes(): Flow<List<IllnessEpisode>> = episodes
            override fun observeCheckins(episodeId: String): Flow<List<Checkin>> = checkins.getValue(episodeId)
        }
        val firstCheckin = Checkin("c1", LocalDate(2026, 10, 2), LocalTime(9, 0), severity = 5)
        checkins.getValue(first.id).emit(listOf(firstCheckin))
        val vm = started(viewModel(illnesses = repository))
        val seen = mutableListOf<Pair<String, String?>>()
        backgroundScope.launch { vm.state.collect { state -> (state as? DetailUiState.Content)?.value?.illness?.let { seen += it.episode.id to it.lastCheckin?.id } } }
        runCurrent()
        assertEquals(first.id to firstCheckin.id, seen.last())

        episodes.value = listOf(first, second)
        runCurrent()
        assertEquals(first.id, vm.content.illness?.episode?.id, "väntar på den nya episodens incheckningar")
        val secondCheckin = Checkin("c2", LocalDate(2026, 10, 3), LocalTime(20, 0), severity = 7)
        checkins.getValue(second.id).emit(listOf(secondCheckin))
        runCurrent()
        assertEquals(second.id to secondCheckin.id, seen.last())
        assertTrue(seen.none { (episode, checkin) -> episode == second.id && checkin != secondCheckin.id }, "aldrig B med A:s eller inga incheckningar: $seen")
    }

    @Test
    fun `ett fel i städningen eller dagens doser kraschar inte fliken och visas inte`() = runTest(main.dispatcher) {
        seed()
        val throwing = object : PrescriptionRepository by testPrescriptions(factory, doses, zone, clock) {
            override suspend fun tidyUp(today: LocalDate): Result<Unit> = throw IllegalStateException("syntetiskt fel")
            override suspend fun ensureDay(date: LocalDate, today: LocalDate): Result<Unit> = throw IllegalStateException("syntetiskt fel")
        }
        val vm = started(viewModel(throwing))
        assertTrue(vm.state.value is DetailUiState.Content)
        assertNull(vm.failure.value)
    }

    // ── Mående, sjukdom, vecka och trend (HEM-4, HEM-5, HEM-7, HEM-12, HEM-13, SCR-1, SCR-6) ──

    /** Alla fyra tillfällen påslagna med standardtiderna 08:00, 12:00, 17:00 och 21:00. */
    private suspend fun allOccasions() {
        factory.settings().upsert(Settings(reminders = ReminderSettings(screeningOccasions = Occasion.entries.map { OccasionReminder(it, enabled = true) }))).getOrThrow()
    }


    private val denied: Flow<Nothing> = flow { throw DataError.PermissionDenied }

    @Test
    fun `episoderna har inte svarat än – Idag visas ändå (HEM-12, HEM-16)`() = runTest(main.dispatcher) {
        seed()
        val silentIllness = object : IllnessRepository by DefaultIllnessRepository(factory, clock) {
            override fun observeEpisodes(): Flow<List<IllnessEpisode>> = MutableSharedFlow()
            override fun observeCheckins(episodeId: String): Flow<List<Checkin>> = MutableSharedFlow()
        }
        val vm = started(viewModel(illnesses = silentIllness))
        runCurrent()
        assertTrue(vm.state.value is DetailUiState.Content)
        assertEquals(2, vm.content.progress.total, "medicinerna visas")
        assertNull(vm.content.illness)
    }
}
