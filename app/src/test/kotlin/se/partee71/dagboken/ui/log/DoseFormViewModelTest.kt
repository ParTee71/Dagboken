package se.partee71.dagboken.ui.log

import app.cash.turbine.test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import org.junit.Rule
import org.junit.Test
import se.partee71.dagboken.R
import se.partee71.dagboken.core.engine.PrnCheck
import se.partee71.dagboken.core.engine.atTime
import se.partee71.dagboken.core.engine.onDay
import se.partee71.dagboken.core.engine.withTaken
import se.partee71.dagboken.core.model.Dose
import se.partee71.dagboken.core.model.DoseIds
import se.partee71.dagboken.core.model.DoseStatus
import se.partee71.dagboken.core.model.PrnMedicine
import se.partee71.dagboken.core.model.Slot
import se.partee71.dagboken.data.FakeCollectionFactory
import se.partee71.dagboken.data.FixedClock
import se.partee71.dagboken.data.common.DataError
import se.partee71.dagboken.data.repository.DefaultPrnMedicineRepository
import se.partee71.dagboken.data.repository.DoseRepository
import se.partee71.dagboken.data.repository.PrnLog
import se.partee71.dagboken.data.repository.testDoses
import se.partee71.dagboken.testing.MainDispatcherRule
import se.partee71.dagboken.ui.common.EditorEffect
import se.partee71.dagboken.ui.common.EntryEditEvent

/**
 * Dosformuläret (MED-11, MED-15, MED-16, FAV-4, FAV-5, FAV-10) mot `FakeCollection` och en fast klocka: tisdag 6
 * oktober 2026 kl. 14:20 i Europe/Stockholm. Engångsdos, vid behov i efterhand med kylperiod och dagsgräns mot den
 * valda tiden, och en loggad dos som ändras, flyttas och raderas.
 */
class DoseFormViewModelTest {

    @get:Rule
    val main = MainDispatcherRule()

    private val zone = TimeZone.of("Europe/Stockholm")
    private val today = LocalDate(2026, 10, 6)
    private val yesterday = LocalDate(2026, 10, 5)
    private val clock = FixedClock(at(today, 14, 20))
    private val factory = FakeCollectionFactory(clock = clock)
    private val doses = testDoses(factory, zone, clock)
    private val medicines = DefaultPrnMedicineRepository(factory)

    private fun at(date: LocalDate, hour: Int, minute: Int = 0): Instant = LocalDateTime(date, LocalTime(hour, minute)).toInstant(zone)

    private fun form(id: String? = null, prnId: String? = null, date: LocalDate? = null) = DoseEditViewModel(doses, medicines, clock, { zone }, id, prnId, date)

    private val DoseEditViewModel.value: Dose get() = editor.state.value.value

    private fun DoseEditViewModel.change(field: String? = null, transform: (Dose) -> Dose) = form.onEvent(EntryEditEvent.Changed(field, transform))

    private suspend fun DoseEditViewModel.saved() = editor.effects.test {
        form.onEvent(EntryEditEvent.Save)
        assertEquals(EditorEffect.Done, awaitItem())
    }

    private val alvedon = PrnMedicine("alvedon", "Alvedon", "500", "mg", minHoursBetween = 4, maxPerDay = 2, note = "Med mat")

    private fun taken(id: String, date: LocalDate, hour: Int) = Dose(id, date, Slot.AS_NEEDED, "Alvedon", "500", "mg", DoseStatus.TAKEN, takenAt = at(date, hour), prnId = alvedon.id)

    // ── Engångsdos (MED-11, NAV-10) ───────────────────────────────────────

    @Test
    fun `en engångsdos loggas mot den visade dagen kl nu, och namnet krävs (MED-11, HEM-14)`() = runTest(main.dispatcher) {
        val vm = form(date = yesterday)
        assertEquals(DoseMode.NEW, vm.mode)
        assertEquals(yesterday, vm.value.date)
        assertEquals(at(yesterday, 14, 20), vm.value.takenAt)
        assertEquals(Slot.AS_NEEDED, vm.value.slot)
        assertFalse(vm.editor.state.value.canSave)
        vm.change { it.copy(dose = "3") }
        assertFalse(vm.editor.state.value.canSave, "inget namn")
        vm.editor.effects.test {
            vm.form.onEvent(EntryEditEvent.Save)
            expectNoEvents()
        }
        assertEquals(R.string.option_name_missing, vm.editor.state.value.errorFor(DoseField.NAME))
        vm.change(DoseField.NAME) { it.copy(name = " Melatonin ") }
        vm.change { it.copy(slot = Slot.NIGHT, note = "  ") }
        vm.change(DoseField.TAKEN_AT) { it.atTime(LocalTime(22, 30), zone) }
        vm.saved()
        assertEquals(
            Dose(vm.value.id, yesterday, Slot.NIGHT, "Melatonin", "3", "mg", DoseStatus.TAKEN, LocalTime(22, 30), at(yesterday, 22, 30), createdAt = clock.now()),
            factory.doses().get(vm.value.id).getOrThrow(),
            "trimmat, och en tom anteckning sparas som ingen",
        )
    }

    @Test
    fun `en dos tas aldrig i framtiden (MED-16)`() = runTest(main.dispatcher) {
        val vm = form()
        vm.change(DoseField.NAME) { it.copy(name = "Melatonin") }
        vm.change(DoseField.TAKEN_AT) { it.atTime(LocalTime(15, 0), zone) }
        assertEquals(R.string.dose_in_future, vm.editor.state.value.errorFor(DoseField.TAKEN_AT))
        assertFalse(vm.editor.state.value.canSave)
        vm.change(DoseField.TAKEN_AT) { it.atTime(LocalTime(14, 0), zone) }
        assertTrue(vm.editor.state.value.canSave)
    }

    // ── Vid behov i efterhand (MED-16, FAV-10) ────────────────────────────

    @Test
    fun `i efterhand förifylls medicinen mot den visade dagen och kan sparas som den är (MED-11, MED-16)`() = runTest(main.dispatcher) {
        factory.prnMedicines().upsert(alvedon).getOrThrow()
        val vm = form(prnId = alvedon.id, date = yesterday)
        assertEquals(DoseMode.AS_NEEDED, vm.mode)
        assertEquals(alvedon, vm.medicine.value)
        assertEquals(at(yesterday, 14, 20), vm.value.takenAt)
        assertEquals("Med mat", vm.value.note, "medicinens anteckning som förval")
        assertEquals(PrnCheck.Allowed, vm.check.value)
        assertTrue(vm.editor.state.value.canSave, "medicinen kl. nu är ett riktigt svar")
        vm.change(DoseField.NOTE) { it.copy(note = "Huvudvärk") }
        vm.saved()
        val logged = factory.doses().getAll().getOrThrow().single()
        assertEquals(Dose(logged.id, yesterday, Slot.AS_NEEDED, "Alvedon", "500", "mg", DoseStatus.TAKEN, LocalTime(14, 20), at(yesterday, 14, 20), prnId = alvedon.id, createdAt = at(yesterday, 14, 20), note = "Huvudvärk"), logged)
    }

    @Test
    fun `kylperioden räknas mot den valda tiden – För tidigt frågar, och Ta ändå sparar (FAV-4, MED-16)`() = runTest(main.dispatcher) {
        factory.prnMedicines().upsert(alvedon).getOrThrow()
        factory.doses().upsert(taken("tidigare", yesterday, 20)).getOrThrow()
        val vm = form(prnId = alvedon.id, date = yesterday)
        assertEquals(PrnCheck.Allowed, vm.check.value, "14:20 ligger före dosen kl. 20")
        vm.change(DoseField.TAKEN_AT) { it.atTime(LocalTime(22, 0), zone) }
        assertEquals(PrnCheck.Cooldown(2.hours), vm.check.value)
        assertTrue(vm.editor.state.value.canSave, "kylperioden går att bekräfta bort")

        vm.form.onEvent(EntryEditEvent.Save)
        assertEquals(CooldownPrompt(alvedon, 2.hours), vm.cooldown.value)
        assertEquals(1, factory.doses().getAll().getOrThrow().size, "inget sparas förrän det bekräftats")
        vm.dismissCooldown()
        assertNull(vm.cooldown.value)

        vm.form.onEvent(EntryEditEvent.Save)
        vm.editor.effects.test {
            vm.confirmCooldown()
            assertEquals(EditorEffect.Done, awaitItem())
        }
        assertEquals(2, factory.doses().getAll().getOrThrow().size)
    }

    @Test
    fun `Ta ändå gäller bara den tiden – ett misslyckat sparande eller en ny tid frågar igen (FAV-4)`() = runTest(main.dispatcher) {
        factory.prnMedicines().upsert(alvedon).getOrThrow()
        factory.doses().upsert(taken("tidigare", yesterday, 20)).getOrThrow()
        val offline = object : DoseRepository by doses {
            override suspend fun logAsNeeded(medicine: PrnMedicine, at: Instant, force: Boolean, note: String?) = Result.failure<PrnLog>(DataError.Offline)
        }
        val vm = DoseEditViewModel(offline, medicines, clock, { zone }, null, alvedon.id, yesterday)
        vm.change(DoseField.TAKEN_AT) { it.atTime(LocalTime(21, 0), zone) }
        vm.form.onEvent(EntryEditEvent.Save)
        assertEquals(CooldownPrompt(alvedon, 3.hours), vm.cooldown.value)
        vm.editor.effects.test {
            vm.confirmCooldown()
            assertEquals(DataError.Offline, (awaitItem() as EditorEffect.Failed).failure.error)
        }
        vm.form.onEvent(EntryEditEvent.Save)
        assertEquals(CooldownPrompt(alvedon, 3.hours), vm.cooldown.value, "efter ett sparfel frågar den igen")
        vm.dismissCooldown()
        vm.change(DoseField.TAKEN_AT) { it.atTime(LocalTime(22, 0), zone) }
        vm.form.onEvent(EntryEditEvent.Save)
        assertEquals(CooldownPrompt(alvedon, 2.hours), vm.cooldown.value, "en ny tid frågar igen")
    }

    @Test
    fun `dagsgränsen den valda dagen gör Spara inaktiv, en annan dag släpper (FAV-5, MED-16)`() = runTest(main.dispatcher) {
        factory.prnMedicines().upsert(alvedon).getOrThrow()
        factory.doses().batch(listOf(taken("a", yesterday, 6), taken("b", yesterday, 11))).getOrThrow()
        val vm = form(prnId = alvedon.id, date = yesterday)
        assertEquals(PrnCheck.DailyLimitReached, vm.check.value)
        assertFalse(vm.editor.state.value.isValid, "gränsen gäller alltid – Spara inaktiv")
        assertFalse(vm.editor.state.value.canSave)
        vm.change(DoseField.TAKEN_AT) { it.onDay(today, zone).atTime(LocalTime(9, 0), zone) }
        assertEquals(PrnCheck.Allowed, vm.check.value)
        assertTrue(vm.editor.state.value.canSave)
    }

    @Test
    fun `en medicin som inte finns visar läsfel med Försök igen som skapar på nytt`() = runTest(main.dispatcher) {
        val vm = form(prnId = alvedon.id)
        assertEquals(DataError.NotFound, vm.editor.state.value.loadError)
        factory.prnMedicines().upsert(alvedon).getOrThrow()
        vm.form.onEvent(EntryEditEvent.Retry)
        assertNull(vm.editor.state.value.loadError)
        assertEquals(alvedon, vm.medicine.value)
    }

    // ── Loggad dos från Dagbok (MED-15) ───────────────────────────────────

    private val recipe = Dose(
        DoseIds.prescribed("levaxin", yesterday, Slot.MORNING), yesterday, Slot.MORNING, "Levaxin", "100", "µg", DoseStatus.TAKEN,
        plannedTime = LocalTime(7, 0), takenAt = at(yesterday, 7, 12), prescriptionId = "levaxin", createdAt = at(yesterday, 7), note = "Med frukost",
    )

    @Test
    fun `en receptdos flyttad till en annan dag får ett nytt id med receptet kvar (MED-15)`() = runTest(main.dispatcher) {
        factory.doses().upsert(recipe).getOrThrow()
        val vm = form(id = recipe.id)
        assertEquals(DoseMode.EDIT, vm.mode)
        assertEquals(recipe, vm.value)
        vm.change(DoseField.TAKEN_AT) { it.onDay(today, zone) }
        vm.saved()
        val stored = factory.doses().getAll().getOrThrow().single()
        assertTrue(stored.id != recipe.id)
        assertEquals(recipe.copy(id = stored.id, date = today, takenAt = at(today, 7, 12)), stored)
    }

    @Test
    fun `en receptdos flyttad till en dag där tidpunkten redan har en dos sparas inte och säger varför (MED-15)`() = runTest(main.dispatcher) {
        val taken = recipe.copy(id = DoseIds.prescribed("levaxin", today, Slot.MORNING), date = today, takenAt = at(today, 7, 5))
        factory.doses().batch(listOf(recipe, taken)).getOrThrow()
        val vm = form(id = recipe.id)
        vm.change(DoseField.TAKEN_AT) { it.onDay(today, zone) }
        vm.editor.effects.test {
            vm.form.onEvent(EntryEditEvent.Save)
            assertEquals(R.string.dose_slot_taken, (awaitItem() as EditorEffect.Failed).failure.message, "Det finns redan en dos för den tidpunkten den dagen.")
        }
        assertEquals(recipe, factory.doses().get(recipe.id).getOrThrow(), "ingenting flyttades")
        assertEquals(taken, factory.doses().get(taken.id).getOrThrow())
    }

    @Test
    fun `Tagen av markerar dosen som överhoppad, och Radera hoppar över en receptdos (MED-3, MED-15)`() = runTest(main.dispatcher) {
        factory.doses().upsert(recipe).getOrThrow()
        val vm = form(id = recipe.id)
        vm.change(DoseField.TAKEN_AT) { it.withTaken(false, zone) }
        vm.saved()
        assertEquals(recipe.copy(status = DoseStatus.SKIPPED, takenAt = null), factory.doses().get(recipe.id).getOrThrow())

        val again = form(id = recipe.id)
        again.editor.effects.test {
            again.form.onEvent(EntryEditEvent.Delete)
            assertEquals(EditorEffect.Done, awaitItem())
        }
        assertEquals(DoseStatus.SKIPPED, factory.doses().get(recipe.id).getOrThrow()?.status, "en receptdos raderas aldrig")
    }

    @Test
    fun `en vid behov-dos ändras fältvis och raderas efter bekräftelse (MED-15, HIST-5)`() = runTest(main.dispatcher) {
        val prn = taken("p", yesterday, 21).copy(createdAt = at(yesterday, 21))
        factory.doses().upsert(prn).getOrThrow()
        val vm = form(id = "p")
        vm.change(DoseField.DOSE) { it.copy(dose = "1000") }
        vm.saved()
        assertEquals(prn.copy(dose = "1000"), factory.doses().get("p").getOrThrow())
        vm.editor.effects.test {
            vm.form.onEvent(EntryEditEvent.Delete)
            assertEquals(EditorEffect.Done, awaitItem())
        }
        assertNull(factory.doses().get("p").getOrThrow())
    }
}
