package se.partee71.dagboken.ui.today

import app.cash.turbine.test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant
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
import se.partee71.dagboken.core.model.Dose
import se.partee71.dagboken.core.model.DoseIds
import se.partee71.dagboken.core.model.DoseStatus
import se.partee71.dagboken.core.model.Occasion
import se.partee71.dagboken.core.model.OccasionReminder
import se.partee71.dagboken.core.model.Period
import se.partee71.dagboken.core.model.Prescription
import se.partee71.dagboken.core.model.PrnMedicine
import se.partee71.dagboken.core.model.ReminderSettings
import se.partee71.dagboken.core.model.Schedule
import se.partee71.dagboken.core.model.Screening
import se.partee71.dagboken.core.model.Settings
import se.partee71.dagboken.core.model.Slot
import se.partee71.dagboken.data.FakeCollectionFactory
import se.partee71.dagboken.data.FixedClock
import se.partee71.dagboken.data.repository.DefaultPrnMedicineRepository
import se.partee71.dagboken.data.repository.DefaultScreeningRepository
import se.partee71.dagboken.data.repository.DefaultSettingsRepository
import se.partee71.dagboken.data.repository.PrescriptionRepository
import se.partee71.dagboken.data.repository.testDoses
import se.partee71.dagboken.data.repository.testPrescriptions
import se.partee71.dagboken.testing.MainDispatcherRule
import se.partee71.dagboken.ui.common.DetailUiState

/**
 * Fliken Idag (HEM-10, HEM-14, HEM-18, HEM-19, MED-1–3, MED-5, MED-13, MED-14, FAV-2–6, FAV-8, FAV-11) mot
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

    private fun viewModel(prescriptions: PrescriptionRepository = testPrescriptions(factory, doses, zone, clock)) = TodayViewModel(
        doses,
        prescriptions,
        DefaultPrnMedicineRepository(factory),
        DefaultScreeningRepository(factory, clock),
        DefaultSettingsRepository(factory),
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
        vm.onEvent(TodayEvent.SetTaken(logged, false))
        runCurrent()
        assertEquals(DoseStatus.TAKEN, stored(logged.id)?.status)
        assertNull(vm.undo.value)
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
        assertEquals(R.string.today_logged_format, vm.notice.value?.text)
        assertEquals(listOf<Any>("Alvedon 500 mg"), vm.notice.value?.args?.toList())
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
        assertEquals(listOf<Any>("Levaxin 100 µg"), vm.notice.value?.args?.toList())
        assertEquals(2, vm.content.progress.total, "en extrados räknas inte i framstegen (HEM-18)")
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
}
