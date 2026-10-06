package se.partee71.dagboken.ui.medicines

import app.cash.turbine.test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import org.junit.Rule
import org.junit.Test
import se.partee71.dagboken.R
import se.partee71.dagboken.core.engine.PeriodChoice
import se.partee71.dagboken.core.engine.RepeatChoice
import se.partee71.dagboken.core.engine.WORKWEEK
import se.partee71.dagboken.core.model.Boost
import se.partee71.dagboken.core.model.Dose
import se.partee71.dagboken.core.model.DoseIds
import se.partee71.dagboken.core.model.DoseStatus
import se.partee71.dagboken.core.model.Period
import se.partee71.dagboken.core.model.Prescription
import se.partee71.dagboken.core.model.Repeat
import se.partee71.dagboken.core.model.Schedule
import se.partee71.dagboken.core.model.Slot
import se.partee71.dagboken.core.schema.PrescriptionCodec
import se.partee71.dagboken.data.FakeCollectionFactory
import se.partee71.dagboken.data.FixedClock
import se.partee71.dagboken.data.common.DataError
import se.partee71.dagboken.data.firestore.Paths
import se.partee71.dagboken.data.repository.testDoses
import se.partee71.dagboken.data.repository.testPrescriptions
import se.partee71.dagboken.data.repository.PrescriptionRepository
import se.partee71.dagboken.testing.MainDispatcherRule
import se.partee71.dagboken.ui.common.EditorEffect
import se.partee71.dagboken.ui.medicines.PrescriptionEditEvent as Event

/** Receptformuläret (REC-1…REC-10, MEDF-5) mot `FakeCollection`: valen, reglerna, sparningen och dossynken. */
class PrescriptionEditViewModelTest {

    @get:Rule
    val main = MainDispatcherRule()

    private val zone = TimeZone.of("Europe/Stockholm")

    /** FixedClock = måndag 21 september 2026. */
    private val today = LocalDate(2026, 9, 21)

    private val factory = FakeCollectionFactory()
    private val repository = testPrescriptions(factory, testDoses(factory, zone), zone)

    private val sertralin = Prescription(
        "s", "Sertralin", "50", "mg", listOf(Slot.MORNING), Schedule.Repeating(), Period(LocalDate(2026, 9, 1)),
        note = "Tas med frukost",
    )

    private fun viewModel(id: String? = null, extend: Boolean = false, prescriptions: PrescriptionRepository = repository) =
        PrescriptionEditViewModel(prescriptions, FixedClock(), { zone }, id, extend)

    private val PrescriptionEditViewModel.value get() = editor.state.value.value

    private fun prescriptionsPath() = Paths.prescriptions(factory.scope.uid.value!!)

    private fun doseToday(p: Prescription, slot: Slot = Slot.MORNING) = DoseIds.prescribed(p.id, today, slot)

    @Test
    fun `ett nytt recept har förvalen och sparas med id från samlingen, och dagens dos skapas (REC-1, REC-4, REC-10)`() = runTest(main.dispatcher) {
        val vm = viewModel()
        assertTrue(vm.isNew)
        assertEquals(newPrescription(today), vm.value)
        assertEquals(Period(today), vm.value.period, "startdatum alltid satt, idag som förval")
        assertEquals(listOf(Slot.MORNING), vm.value.slots)
        assertEquals("mg", vm.value.unit)
        assertEquals(PeriodChoice.UNTIL_FURTHER_NOTICE, vm.periodChoice.value)
        assertFalse(vm.editor.state.value.canSave)

        vm.onEvent(Event.NameChanged(" Sertralin "))
        vm.onEvent(Event.DoseChanged("50 "))
        vm.onEvent(Event.NoteChanged(" Tas med frukost "))
        assertTrue(vm.editor.state.value.canSave)
        vm.editor.effects.test {
            vm.onEvent(Event.Save)
            assertEquals(EditorEffect.Done, awaitItem())
        }
        val saved = factory.prescriptions().getAll().getOrThrow().single()
        assertEquals(newPrescription(today).copy(id = saved.id, name = "Sertralin", dose = "50", note = "Tas med frukost", createdAt = FixedClock().now()), saved)
        val dose = factory.doses().get(doseToday(saved)).getOrThrow()
        assertEquals("Sertralin", dose?.name, "sparningen synkar doserna (syncFromServer)")
        assertEquals("Tas med frukost", dose?.note)
    }

    @Test
    fun `namn och minst en tidpunkt krävs`() = runTest(main.dispatcher) {
        val vm = viewModel()
        vm.onEvent(Event.NameChanged(" "))
        assertEquals(R.string.option_name_missing, vm.editor.state.value.errorFor(PrescriptionField.NAME))
        vm.onEvent(Event.SlotToggled(Slot.MORNING))
        assertEquals(R.string.prescription_slots_missing, vm.editor.state.value.errorFor(PrescriptionField.SLOTS))
        vm.onEvent(Event.SlotToggled(Slot.EVENING))
        vm.onEvent(Event.SlotToggled(Slot.MORNING))
        assertEquals(listOf(Slot.EVENING, Slot.MORNING), vm.value.slots, "i den ordning de valts")
        assertNull(vm.editor.state.value.errorFor(PrescriptionField.SLOTS))
    }

    @Test
    fun `upprepningen är ett val - vardagar, helger, anpassade dagar och intervall (REC-2, REC-3, REC-4)`() = runTest(main.dispatcher) {
        val vm = viewModel()
        fun schedule() = vm.value.schedule as Schedule.Repeating
        assertEquals(Repeat.DAILY, schedule().repeat)

        vm.onEvent(Event.RepeatChosen(RepeatChoice.WEEKDAYS))
        assertEquals(Schedule.Repeating(Repeat.CUSTOM, emptySet()), schedule())
        assertEquals(R.string.prescription_days_missing, vm.editor.state.value.errorFor(PrescriptionField.DAYS))
        WORKWEEK.forEach { vm.onEvent(Event.DayToggled(it)) }
        assertEquals(Schedule.Repeating(Repeat.WEEKDAYS, WORKWEEK), schedule(), "mån–fre sparas som vardagar")
        vm.onEvent(Event.DayToggled(DayOfWeek.FRIDAY))
        assertEquals(Schedule.Repeating(Repeat.CUSTOM, WORKWEEK - DayOfWeek.FRIDAY), schedule())
        (WORKWEEK - DayOfWeek.FRIDAY).forEach { vm.onEvent(Event.DayToggled(it)) }
        vm.onEvent(Event.DayToggled(DayOfWeek.SUNDAY))
        vm.onEvent(Event.DayToggled(DayOfWeek.SATURDAY))
        assertEquals(Schedule.Repeating(Repeat.WEEKENDS, setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY)), schedule(), "lör–sön sparas som helger")

        vm.onEvent(Event.RepeatChosen(RepeatChoice.INTERVAL))
        assertEquals(Repeat.INTERVAL, schedule().repeat)
        assertEquals(2, schedule().intervalDays)
        vm.onEvent(Event.IntervalChanged(3))
        vm.onEvent(Event.IntervalChanged(1))
        assertEquals(2, schedule().intervalDays, "minst varannan dag")
        vm.onEvent(Event.IntervalChanged(3))

        vm.onEvent(Event.RepeatChosen(RepeatChoice.EVERY_DAY))
        assertEquals(Schedule.Repeating(Repeat.DAILY, setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY), 3), schedule(), "dagarna och intervallet bevaras")
        vm.onEvent(Event.RepeatChosen(RepeatChoice.WEEKDAYS))
        assertEquals(Repeat.WEEKENDS, schedule().repeat)
    }

    @Test
    fun `perioden - tills vidare, längd och tom (REC-7)`() = runTest(main.dispatcher) {
        val vm = viewModel()
        vm.onEvent(Event.PeriodChosen(PeriodChoice.LENGTH))
        assertEquals(PeriodChoice.LENGTH, vm.periodChoice.value)
        assertEquals(Period(today, LocalDate(2026, 10, 4)), vm.value.period, "14 dagar som förval")
        vm.onEvent(Event.LengthChanged(10))
        assertEquals(Period(today, LocalDate(2026, 9, 30)), vm.value.period)
        vm.onEvent(Event.StartChanged(LocalDate(2026, 9, 25)))
        assertEquals(Period(LocalDate(2026, 9, 25), LocalDate(2026, 10, 4)), vm.value.period, "Längd: slutet flyttas med")

        vm.onEvent(Event.PeriodChosen(PeriodChoice.END_DATE))
        assertEquals(Period(LocalDate(2026, 9, 25), LocalDate(2026, 10, 4)), vm.value.period, "T.o.m. visar samma period")
        vm.onEvent(Event.StartChanged(LocalDate(2026, 9, 27)))
        assertEquals(LocalDate(2026, 10, 4), vm.value.period.end, "T.o.m.: slutet står kvar")
        vm.onEvent(Event.EndChanged(LocalDate(2026, 9, 26)))
        assertEquals(R.string.prescription_error_end_before_start, vm.editor.state.value.errorFor(PrescriptionField.PERIOD_END))

        vm.onEvent(Event.PeriodChosen(PeriodChoice.UNTIL_FURTHER_NOTICE))
        assertEquals(Period(LocalDate(2026, 9, 27)), vm.value.period)
        assertNull(vm.editor.state.value.errorFor(PrescriptionField.PERIOD_END))
    }

    @Test
    fun `valideringsfelen visas i ordning och under fältet de gäller (REC-9)`() = runTest(main.dispatcher) {
        val vm = viewModel()
        vm.onEvent(Event.NameChanged("Sertralin"))
        vm.onEvent(Event.DoseChanged("50"))
        vm.onEvent(Event.PeriodChosen(PeriodChoice.END_DATE))
        vm.onEvent(Event.BoostAdded)
        val boost = vm.value.boosts.single()
        assertEquals(Boost(boost.id, today, LocalDate(2026, 9, 27), "", "mg"), boost)
        fun error(field: String) = vm.editor.state.value.errorFor(field)

        vm.onEvent(Event.EndChanged(LocalDate(2026, 9, 20)))
        assertEquals(R.string.prescription_error_end_before_start, error(PrescriptionField.PERIOD_END))
        assertNull(error(PrescriptionField.boostDose(0)), "bara det första felet")

        vm.onEvent(Event.EndChanged(LocalDate(2026, 10, 4)))
        assertEquals(R.string.prescription_error_boost_without_dose, error(PrescriptionField.boostDose(0)))
        vm.onEvent(Event.BoostDoseChanged(0, "0"))
        assertEquals(R.string.prescription_error_boost_not_positive, error(PrescriptionField.boostDose(0)))
        vm.onEvent(Event.DoseChanged("1 tablett"))
        assertEquals(R.string.prescription_error_base_dose, error(PrescriptionField.DOSE))
        vm.onEvent(Event.DoseChanged("50"))
        vm.onEvent(Event.BoostDoseChanged(0, "25"))
        assertTrue(vm.editor.state.value.isValid)

        vm.onEvent(Event.BoostEndChanged(0, LocalDate(2026, 10, 5)))
        assertEquals(R.string.prescription_error_boost_outside, error(PrescriptionField.boostDates(0)))
        vm.onEvent(Event.BoostEndChanged(0, LocalDate(2026, 9, 20)))
        assertEquals(R.string.prescription_error_boost_end_before_start, error(PrescriptionField.boostDates(0)))
        vm.onEvent(Event.BoostEndChanged(0, null))
        assertNull(vm.value.boosts.single().end, "tillbaka till periodens slut")
        vm.onEvent(Event.BoostEndChanged(0, LocalDate(2026, 9, 27)))

        vm.onEvent(Event.BoostAdded)
        assertEquals(LocalDate(2026, 9, 28), vm.value.boosts[1].start, "dagen efter den förra")
        vm.onEvent(Event.BoostDoseChanged(1, "25"))
        vm.onEvent(Event.BoostStartChanged(1, LocalDate(2026, 9, 27)))
        assertEquals(R.string.prescription_error_boosts_overlap, error(PrescriptionField.boostDates(1)), "den senare av två")
        assertNull(error(PrescriptionField.boostDates(0)))
        assertFalse(vm.editor.state.value.canSave)

        vm.onEvent(Event.BoostRemoved(1))
        assertEquals(1, vm.value.boosts.size)
        assertTrue(vm.editor.state.value.canSave)

        vm.onEvent(Event.UnitChanged("ml"))
        assertEquals("ml", vm.value.boosts.single().unit, "höjningen följer receptets enhet")
    }

    @Test
    fun `Lägg till doshöjning är inaktiv när det inte finns plats`() = runTest(main.dispatcher) {
        val vm = viewModel()
        assertTrue(vm.canAddBoost(vm.value))
        vm.onEvent(Event.BoostAdded)
        vm.onEvent(Event.BoostEndChanged(0, LocalDate(2026, 9, 21)))
        val open = vm.value.copy(boosts = vm.value.boosts.map { it.copy(end = null) })
        assertFalse(vm.canAddBoost(open), "en höjning tills vidare i en period utan slut")

        vm.onEvent(Event.PeriodChosen(PeriodChoice.LENGTH))
        vm.onEvent(Event.LengthChanged(1))
        assertFalse(vm.canAddBoost(vm.value), "perioden är full")
        val before = vm.value
        vm.onEvent(Event.BoostAdded)
        assertEquals(before, vm.value, "ingen höjning utan förval")
    }

    @Test
    fun `att ändra ett recept skriver bara det ändrade och uppdaterar dagens planerade dos, inte tagna (REC-10, DAT-10)`() = runTest(main.dispatcher) {
        factory.store.set(prescriptionsPath(), sertralin.id, PrescriptionCodec.encode(sertralin.copy(slots = listOf(Slot.MORNING, Slot.EVENING))) + ("framtidaFält" to "kvar"), merge = false)
        val planned = Dose(doseToday(sertralin), today, Slot.MORNING, "Sertralin", "50", "mg", DoseStatus.PLANNED, Slot.MORNING.defaultTime, prescriptionId = sertralin.id)
        val taken = planned.copy(id = doseToday(sertralin, Slot.EVENING), slot = Slot.EVENING, status = DoseStatus.TAKEN, plannedTime = Slot.EVENING.defaultTime)
        factory.doses().batch(listOf(planned, taken)).getOrThrow()

        val vm = viewModel(sertralin.id)
        assertFalse(vm.isNew)
        assertEquals(PeriodChoice.UNTIL_FURTHER_NOTICE, vm.periodChoice.value)
        assertFalse(vm.editor.state.value.isDirty)
        vm.onEvent(Event.DoseChanged("75"))
        vm.editor.effects.test {
            vm.onEvent(Event.Save)
            assertEquals(EditorEffect.Done, awaitItem())
        }
        val stored = factory.store.read(prescriptionsPath(), sertralin.id)!!
        assertEquals("75", stored["dose"])
        assertEquals("kvar", stored["framtidaFält"], "okända fält bevaras")
        assertEquals("75", factory.doses().get(planned.id).getOrThrow()?.dose, "dagens planerade dos följer receptet")
        assertEquals(taken, factory.doses().get(taken.id).getOrThrow(), "tagen dos orörd")
    }

    @Test
    fun `en okänd upprepning visas inte som ett val och skrivs tillbaka orörd (REC-2, DAT-10)`() = runTest(main.dispatcher) {
        val newer = mapOf("repeat" to "biweekly", "anchor" to "2026-09-01")
        factory.store.set(prescriptionsPath(), sertralin.id, PrescriptionCodec.encode(sertralin.copy(schedule = Schedule.Unknown(newer))), merge = false)
        val vm = viewModel(sertralin.id)
        assertEquals(Schedule.Unknown(newer), vm.value.schedule)
        vm.onEvent(Event.RepeatChosen(RepeatChoice.EVERY_DAY))
        vm.onEvent(Event.DayToggled(DayOfWeek.MONDAY))
        assertEquals(Schedule.Unknown(newer), vm.value.schedule, "formuläret kan inte ändra den")
        assertFalse(vm.editor.state.value.isDirty)

        vm.onEvent(Event.NameChanged("Sertralin Teva"))
        vm.editor.effects.test {
            vm.onEvent(Event.Save)
            assertEquals(EditorEffect.Done, awaitItem())
        }
        val stored = factory.store.read(prescriptionsPath(), sertralin.id)!!
        assertEquals(newer, stored["schedule"])
        assertEquals("Sertralin Teva", stored["name"])
    }

    @Test
    fun `ett recept utan start får intervallets ankare, och inga tidpunkter visas som Morgon`() = runTest(main.dispatcher) {
        val migrated = sertralin.copy(slots = emptyList(), period = Period(), createdAt = kotlin.time.Instant.parse("2025-03-01T23:00:00Z"))
        factory.prescriptions().upsert(migrated).getOrThrow()
        val vm = viewModel(sertralin.id)
        assertEquals(LocalDate(2025, 3, 2), vm.value.period.start)
        assertEquals(listOf(Slot.MORNING), vm.value.slots)
        assertFalse(vm.editor.state.value.isDirty)
    }

    @Test
    fun `ett namnbyte på ett migrerat recept skriver bara namnet – formulärets förval är inga ändringar`() = runTest(main.dispatcher) {
        val migrated = sertralin.copy(slots = emptyList(), period = Period(), createdAt = kotlin.time.Instant.parse("2025-03-01T23:00:00Z"))
        factory.prescriptions().upsert(migrated).getOrThrow()
        val before = factory.store.read(prescriptionsPath(), sertralin.id)!!
        val vm = viewModel(sertralin.id)
        vm.onEvent(Event.NameChanged("Sertralin Teva"))
        vm.editor.effects.test {
            vm.onEvent(Event.Save)
            assertEquals(EditorEffect.Done, awaitItem())
        }
        assertEquals(before + ("name" to "Sertralin Teva"), factory.store.read(prescriptionsPath(), sertralin.id), "ingen start, inga tidpunkter skrivna")
    }

    @Test
    fun `okända tidpunkter kan inte ändras i formuläret och skrivs tillbaka orörda`() = runTest(main.dispatcher) {
        factory.store.set(prescriptionsPath(), sertralin.id, PrescriptionCodec.encode(sertralin) + ("slots" to listOf("morning", "brunch")), merge = false)
        val vm = viewModel(sertralin.id)
        assertTrue(vm.value.hasUnknownSlots, "UI:t visar tidpunkterna som kan inte visas")
        vm.onEvent(Event.SlotToggled(Slot.EVENING))
        assertFalse(vm.editor.state.value.isDirty, "tidpunkterna går inte att ändra")
        vm.onEvent(Event.NameChanged("Sertralin Teva"))
        vm.editor.effects.test {
            vm.onEvent(Event.Save)
            assertEquals(EditorEffect.Done, awaitItem())
        }
        assertEquals(listOf("morning", "brunch"), factory.store.read(prescriptionsPath(), sertralin.id)!!["slots"])
    }

    @Test
    fun `ett nytt recepts createdAt sätts en gång – ett nytt försök skriver inte om den`() = runTest(main.dispatcher) {
        var now = kotlin.time.Instant.parse("2026-09-21T08:00:00Z")
        val clock = object : kotlin.time.Clock {
            override fun now() = now
        }
        var fail = true
        val flaky = object : PrescriptionRepository by repository {
            override suspend fun save(loaded: Prescription?, edited: Prescription, extended: Boolean): Result<Unit> =
                if (fail) Result.failure(DataError.Offline) else repository.save(loaded, edited, extended)
        }
        val vm = PrescriptionEditViewModel(flaky, clock, { zone }, null, false)
        vm.onEvent(Event.NameChanged("Sertralin"))
        vm.editor.effects.test {
            vm.onEvent(Event.Save)
            assertTrue(awaitItem() is EditorEffect.Failed)
            now = kotlin.time.Instant.parse("2026-09-21T09:30:00Z")
            fail = false
            vm.onEvent(Event.Save)
            assertEquals(EditorEffect.Done, awaitItem())
        }
        assertEquals(kotlin.time.Instant.parse("2026-09-21T08:00:00Z"), factory.prescriptions().getAll().getOrThrow().single().createdAt)
    }

    @Test
    fun `förläng och aktivera öppnar ett avslutat recept med ny period från idag och Aktiv på, och sparas som vanligt (MEDF-5)`() = runTest(main.dispatcher) {
        val ended = sertralin.copy(
            period = Period(LocalDate(2026, 9, 1), LocalDate(2026, 9, 10)),
            boosts = listOf(Boost("b", LocalDate(2026, 9, 3), LocalDate(2026, 9, 5), "25", "mg")),
            active = false,
        )
        factory.prescriptions().upsert(ended).getOrThrow()
        val vm = viewModel(ended.id, extend = true)
        assertEquals(Period(today, LocalDate(2026, 9, 30)), vm.value.period, "lika långt som förra perioden (10 dagar)")
        assertEquals(PeriodChoice.LENGTH, vm.periodChoice.value)
        assertTrue(vm.value.active)
        assertEquals(LocalDate(2026, 9, 23) to LocalDate(2026, 9, 25), vm.value.boosts.single().let { it.start to it.end })
        assertTrue(vm.editor.state.value.canSave, "förlängningen är en ändring som kan sparas direkt")
        assertEquals(ended, vm.stored.value)

        vm.editor.effects.test {
            vm.onEvent(Event.Save)
            assertEquals(EditorEffect.Done, awaitItem())
        }
        val saved = factory.prescriptions().get(ended.id).getOrThrow()!!
        assertTrue(saved.active)
        assertEquals(Period(today, LocalDate(2026, 9, 30)), saved.period)
        assertNotNull(factory.doses().get(doseToday(ended)).getOrThrow(), "dagens dos skapas av synken")
    }

    @Test
    fun `förläng på ett recept som inte längre är avslutat öppnar det som vanlig redigering (MEDF-5)`() = runTest(main.dispatcher) {
        val current = sertralin.copy(period = Period(LocalDate(2026, 9, 1), LocalDate(2026, 9, 30)))
        factory.prescriptions().upsert(current).getOrThrow()
        val vm = viewModel(current.id, extend = true)
        assertEquals(current.period, vm.value.period)
        assertEquals(PeriodChoice.END_DATE, vm.periodChoice.value)
        assertFalse(vm.editor.state.value.isDirty)
    }

    @Test
    fun `tidpunkter som inte kan visas hindrar inte att receptet sparas och ändras inte (DAT-10)`() = runTest(main.dispatcher) {
        factory.prescriptions().upsert(sertralin.copy(slots = listOf(Slot.AS_NEEDED))).getOrThrow()
        val vm = viewModel(sertralin.id)
        vm.onEvent(Event.SlotToggled(Slot.MORNING))
        assertEquals(listOf(Slot.AS_NEEDED), vm.value.slots)
        vm.onEvent(Event.NameChanged("Sertralin Teva"))
        assertNull(vm.editor.state.value.errorFor(PrescriptionField.SLOTS))
        assertTrue(vm.editor.state.value.canSave)
    }

    @Test
    fun `förlängningen görs också när receptet läses efter Försök igen (MEDF-5)`() = runTest(main.dispatcher) {
        val ended = sertralin.copy(period = Period(LocalDate(2026, 9, 1), LocalDate(2026, 9, 10)), active = false)
        factory.prescriptions().upsert(ended).getOrThrow()
        var offline = true
        val flaky = object : PrescriptionRepository by repository {
            override suspend fun get(id: String): Result<Prescription?> = if (offline) Result.failure(DataError.Offline) else repository.get(id)
        }
        val vm = viewModel(ended.id, extend = true, prescriptions = flaky)
        assertEquals(DataError.Offline, vm.editor.state.value.loadError)

        offline = false
        vm.onEvent(Event.Retry)
        assertEquals(Period(today, LocalDate(2026, 9, 30)), vm.value.period)
        assertTrue(vm.value.active)
        assertEquals(PeriodChoice.LENGTH, vm.periodChoice.value)
        assertTrue(vm.editor.state.value.canSave)
    }

    @Test
    fun `ett recept som inte finns visar läsfel, och förläng gör då ingenting`() = runTest(main.dispatcher) {
        val vm = viewModel("finns-inte", extend = true)
        assertEquals(DataError.NotFound, vm.editor.state.value.loadError)
        assertFalse(vm.editor.state.value.isDirty)
    }

    @Test
    fun `ett sparfel stänger inte formuläret, och ett nytt försök skriver samma recept`() = runTest(main.dispatcher) {
        var failSync = true
        val flaky = object : PrescriptionRepository by repository {
            override suspend fun save(loaded: Prescription?, edited: Prescription, extended: Boolean): Result<Unit> {
                val result = repository.save(loaded, edited, extended)
                return if (failSync) Result.failure(DataError.Unknown) else result
            }
        }
        val vm = viewModel(prescriptions = flaky)
        vm.onEvent(Event.NameChanged("Sertralin"))
        vm.editor.effects.test {
            vm.onEvent(Event.Save)
            assertEquals(DataError.Unknown, (awaitItem() as EditorEffect.Failed).failure.error)
            failSync = false
            vm.onEvent(Event.Save)
            assertEquals(EditorEffect.Done, awaitItem())
        }
        assertEquals(1, factory.prescriptions().getAll().getOrThrow().size, "inget dubblettrecept")
    }
}
