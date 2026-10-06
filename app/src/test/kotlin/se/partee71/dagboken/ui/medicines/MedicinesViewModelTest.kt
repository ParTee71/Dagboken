package se.partee71.dagboken.ui.medicines

import app.cash.turbine.test
import app.cash.turbine.testIn
import app.cash.turbine.turbineScope
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import kotlinx.coroutines.test.advanceTimeBy
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import org.junit.Rule
import org.junit.Test
import se.partee71.dagboken.R
import se.partee71.dagboken.core.engine.PeriodEnding
import se.partee71.dagboken.core.model.Dose
import se.partee71.dagboken.core.model.DoseIds
import se.partee71.dagboken.core.model.DoseStatus
import se.partee71.dagboken.core.model.Period
import se.partee71.dagboken.core.model.Prescription
import se.partee71.dagboken.core.model.PrnMedicine
import se.partee71.dagboken.core.model.Schedule
import se.partee71.dagboken.core.model.Slot
import se.partee71.dagboken.core.schema.Schema
import se.partee71.dagboken.data.FakeCollectionFactory
import se.partee71.dagboken.data.FixedClock
import se.partee71.dagboken.data.TestUserScope
import se.partee71.dagboken.data.common.DataError
import se.partee71.dagboken.data.repository.testDoses
import se.partee71.dagboken.data.repository.testPrescriptions
import se.partee71.dagboken.data.repository.DefaultPrnMedicineRepository
import se.partee71.dagboken.data.repository.PrescriptionRepository
import se.partee71.dagboken.testing.MainDispatcherRule
import se.partee71.dagboken.ui.common.EditorEffect
import se.partee71.dagboken.ui.common.ListUiState

/** Fliken Mediciner (MEDF-1…MEDF-5, REC-5, FAV-2) och vid behov-formuläret (FAV-1, FAV-4, FAV-5, FAV-7). */
class MedicinesViewModelTest {

    @get:Rule
    val main = MainDispatcherRule()

    private val zone = TimeZone.of("Europe/Stockholm")

    /** FixedClock = måndag 21 september 2026. */
    private val today = LocalDate(2026, 9, 21)

    private val levaxin = Prescription("l", name = "Levaxin", schedule = Schedule.Repeating(), period = Period(LocalDate(2026, 1, 1)))
    private val kavepenin = Prescription("k", name = "Kåvepenin", schedule = Schedule.Repeating(), period = Period(LocalDate(2026, 9, 15), LocalDate(2026, 9, 22)))
    private val paused = Prescription("p", name = "Atarax", schedule = Schedule.Repeating(), active = false)
    private val ended = Prescription("e", name = "Amoxicillin", schedule = Schedule.Repeating(), period = Period(LocalDate(2026, 9, 1), LocalDate(2026, 9, 10)), active = false)
    private val stale = Prescription("s", name = "Prednisolon", schedule = Schedule.Repeating(), period = Period(LocalDate(2026, 8, 1), LocalDate(2026, 8, 2)))
    private val alvedon = PrnMedicine("a", name = "Alvedon", dose = "500", unit = "mg", maxPerDay = 8)

    private fun viewModel(factory: FakeCollectionFactory, clock: Clock = FixedClock()): MedicinesViewModel {
        val doses = testDoses(factory, zone)
        return MedicinesViewModel(testPrescriptions(factory, doses, zone, clock), DefaultPrnMedicineRepository(factory), clock) { zone }
    }

    @Test
    fun `laddningen avslutar utgångna recept och städar inaktivas planerade doser, inte tagna (REC-8, REC-5)`() = runTest(main.dispatcher) {
        val factory = FakeCollectionFactory()
        factory.prescriptions().batch(listOf(levaxin, paused, stale)).getOrThrow()
        fun dose(prescription: Prescription, status: DoseStatus) =
            Dose(DoseIds.prescribed(prescription.id, today, Slot.MORNING), today, Slot.MORNING, prescription.name, status = status, prescriptionId = prescription.id)
        val left = dose(paused, DoseStatus.PLANNED)
        val taken = dose(paused, DoseStatus.TAKEN).copy(id = DoseIds.prescribed(paused.id, today, Slot.EVENING), slot = Slot.EVENING)
        val active = dose(levaxin, DoseStatus.PLANNED)
        val expired = dose(stale, DoseStatus.PLANNED)
        factory.doses().batch(listOf(left, taken, active, expired)).getOrThrow()

        val vm = viewModel(factory)
        assertEquals(setOf(left.id, taken.id, active.id, expired.id), factory.doses().getAll().getOrThrow().map { it.id }.toSet(), "inget förrän fliken visas")
        backgroundScope.launch { vm.state.collect {} }
        runCurrent()

        assertEquals(setOf(taken.id, active.id), factory.doses().getAll().getOrThrow().map { it.id }.toSet())
        assertEquals(false, factory.prescriptions().get(stale.id).getOrThrow()?.active, "utgånget recept avslutat (REC-8)")
    }

    @Test
    fun `ett fel i städningen kraschar inte fliken och visas inte`() = runTest(main.dispatcher) {
        val factory = FakeCollectionFactory()
        factory.prescriptions().upsert(levaxin).getOrThrow()
        val doses = testDoses(factory, zone)
        val throwing = object : PrescriptionRepository by testPrescriptions(factory, doses, zone) {
            override suspend fun tidyUp(today: LocalDate): Result<Unit> = throw IllegalStateException("syntetiskt fel")
        }
        val vm = MedicinesViewModel(throwing, DefaultPrnMedicineRepository(factory), FixedClock()) { zone }
        backgroundScope.launch { vm.state.collect {} }
        runCurrent()
        assertTrue(vm.state.value is ListUiState.Content)
        assertNull(vm.failure.value)
    }

    @Test
    fun `raderna följer indelningen från core – recepten, vid behov och de avslutade sist (MEDF-1, MEDF-5)`() {
        val items = medicineItems(listOf(levaxin, stale, ended, paused, kavepenin), listOf(alvedon), today)
        assertEquals(
            listOf(
                MedicineItem.Recipe(paused),
                MedicineItem.Recipe(kavepenin),
                MedicineItem.Recipe(levaxin),
                MedicineItem.AsNeeded(alvedon),
                MedicineItem.Recipe(ended, ended = true),
                MedicineItem.Recipe(stale, ended = true),
            ),
            items,
        )
    }

    @Test
    fun `listan, bannern med periodsluten och reglaget (MEDF-2, REC-5)`() = runTest(main.dispatcher) {
        val factory = FakeCollectionFactory()
        factory.prescriptions().batch(listOf(levaxin, kavepenin)).getOrThrow()
        factory.prnMedicines().upsert(alvedon).getOrThrow()
        val vm = viewModel(factory)
        assertEquals(today, vm.today.value)
        vm.endings.test {
            assertEquals(listOf<PeriodEnding>(PeriodEnding.PrescriptionEnds("k", "Kåvepenin", LocalDate(2026, 9, 22))), expectMostRecentItem())
        }
        vm.state.test {
            assertEquals(3, (expectMostRecentItem() as ListUiState.Content).items.size)
            vm.onEvent(MedicinesEvent.ActiveChanged(levaxin, false))
            val recipe = (awaitItem() as ListUiState.Content).items.first { it.key == "recipe:l" } as MedicineItem.Recipe
            assertFalse(recipe.prescription.active)
            assertFalse(recipe.ended, "ett pausat recept står kvar bland recepten")

            vm.onEvent(MedicinesEvent.FavoriteToggled(alvedon))
            val prn = (awaitItem() as ListUiState.Content).items.first { it is MedicineItem.AsNeeded } as MedicineItem.AsNeeded
            assertTrue(prn.medicine.favorite)

            vm.onEvent(MedicinesEvent.DeletePrescription(kavepenin))
            assertEquals(2, (awaitItem() as ListUiState.Content).items.size)
        }
        assertNull(vm.failure.value)
    }

    @Test
    fun `dagen byts vid midnatt – periodslut, pills och avslutade räknas om utan att fliken öppnas på nytt`() = runTest(main.dispatcher) {
        val factory = FakeCollectionFactory()
        factory.prescriptions().batch(listOf(levaxin, kavepenin)).getOrThrow()
        // Klockan följer testets virtuella tid: 23:59:30 måndag 21 september (Stockholm, UTC+2).
        val start = Instant.parse("2026-09-21T21:59:30Z")
        var reads = 0
        val clock = object : Clock {
            override fun now(): Instant = start.also { reads++ } + testScheduler.currentTime.milliseconds
        }
        val vm = viewModel(factory, clock)
        turbineScope {
            val days = vm.today.testIn(backgroundScope)
            val endings = vm.endings.testIn(backgroundScope)
            val state = vm.state.testIn(backgroundScope)
            assertEquals(today, days.expectMostRecentItem())
            assertEquals(LocalDate(2026, 9, 22), endings.expectMostRecentItem().single().date)
            assertFalse((state.expectMostRecentItem() as ListUiState.Content).items.any { it is MedicineItem.Recipe && it.ended })

            reads = 0
            advanceTimeBy(31.seconds)
            assertEquals(2, reads, "en delad dagkälla: en läsning för dagen och en för nästa midnatt – inte en per lyssnare")
            assertEquals(LocalDate(2026, 9, 22), days.expectMostRecentItem())
            assertEquals(LocalDate(2026, 9, 22), vm.endings.value.single().date, "samma periodslut – nu idag")
            advanceTimeBy(1.days)
            assertEquals(LocalDate(2026, 9, 23), days.expectMostRecentItem())
            assertEquals(emptyList(), vm.endings.value)
            val ended = (state.expectMostRecentItem() as ListUiState.Content).items.filterIsInstance<MedicineItem.Recipe>().filter { it.ended }
            assertEquals(listOf("k"), ended.map { it.prescription.id }, "efter periodens sista dag står receptet bland de avslutade")
            assertEquals(false, factory.prescriptions().get("k").getOrThrow()?.active, "städningen körs igen vid ny dag och avslutar det utgångna (REC-8)")
            listOf(days, endings, state).forEach { it.cancelAndIgnoreRemainingEvents() }
        }
    }

    @Test
    fun `ett misslyckat reglage visas som meddelande tills det visats`() = runTest(main.dispatcher) {
        val factory = FakeCollectionFactory(scope = TestUserScope(version = Schema.CURRENT_VERSION + 1))
        val vm = viewModel(factory)
        vm.onEvent(MedicinesEvent.ActiveChanged(levaxin, false))
        assertEquals(DataError.UpdateRequired, vm.failure.value?.error)
        vm.onEvent(MedicinesEvent.ErrorShown)
        assertNull(vm.failure.value)
    }

    @Test
    fun `en ny vid behov-medicin kräver namn och dos och sparas med förvalen`() = runTest(main.dispatcher) {
        val factory = FakeCollectionFactory()
        val vm = PrnMedicineEditViewModel(DefaultPrnMedicineRepository(factory), id = null)
        assertTrue(vm.isNew)
        assertFalse(vm.editor.state.value.canSave)

        vm.onEvent(PrnEditEvent.Changed(PrnField.NAME) { it.copy(name = " ") })
        assertEquals(R.string.option_name_missing, vm.editor.state.value.errorFor(PrnField.NAME))
        vm.onEvent(PrnEditEvent.Changed(PrnField.NAME) { it.copy(name = " Alvedon ") })
        vm.onEvent(PrnEditEvent.Changed(PrnField.DOSE) { it.copy(dose = "500") })
        vm.onEvent(PrnEditEvent.Changed(null) { it.copy(unit = "sprut", minHoursBetween = 0, note = "") })
        vm.editor.effects.test {
            vm.onEvent(PrnEditEvent.Save)
            assertEquals(EditorEffect.Done, awaitItem())
        }
        val saved = factory.prnMedicines().getAll().getOrThrow().single()
        assertEquals(PrnMedicine(saved.id, name = "Alvedon", dose = "500", unit = "sprut", minHoursBetween = 0), saved)
    }

    @Test
    fun `att ändra en vid behov-medicin bevarar stjärnan och dispenseringstiden, och den kan tas bort (FAV-3, FAV-7)`() = runTest(main.dispatcher) {
        val factory = FakeCollectionFactory()
        val stored = alvedon.copy(favorite = true, dispensingTime = "30 min", unit = "tablett")
        factory.prnMedicines().upsert(stored).getOrThrow()
        val repository = DefaultPrnMedicineRepository(factory)
        val vm = PrnMedicineEditViewModel(repository, alvedon.id)
        assertEquals(stored, vm.editor.state.value.value)
        assertEquals(stored, vm.stored.value)

        vm.onEvent(PrnEditEvent.Changed(null) { it.copy(maxPerDay = 4) })
        vm.editor.effects.test {
            vm.onEvent(PrnEditEvent.Save)
            assertEquals(EditorEffect.Done, awaitItem())
            assertEquals(stored.copy(maxPerDay = 4), factory.prnMedicines().get(alvedon.id).getOrThrow())

            vm.onEvent(PrnEditEvent.Delete)
            assertEquals(EditorEffect.Done, awaitItem())
        }
        assertNull(factory.prnMedicines().get(alvedon.id).getOrThrow())
    }

    @Test
    fun `en vid behov-medicin som inte finns visar läsfel`() = runTest(main.dispatcher) {
        val vm = PrnMedicineEditViewModel(DefaultPrnMedicineRepository(FakeCollectionFactory()), "finns-inte")
        assertEquals(DataError.NotFound, vm.editor.state.value.loadError)
    }
}
