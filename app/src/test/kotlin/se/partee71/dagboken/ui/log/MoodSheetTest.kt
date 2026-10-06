package se.partee71.dagboken.ui.log

import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlin.time.Instant
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import org.junit.Rule
import org.junit.Test
import se.partee71.dagboken.core.model.Occasion
import se.partee71.dagboken.core.model.OccasionReminder
import se.partee71.dagboken.core.model.Option
import se.partee71.dagboken.core.model.OptionKind
import se.partee71.dagboken.core.model.ReminderSettings
import se.partee71.dagboken.core.model.Screening
import se.partee71.dagboken.core.model.Settings
import se.partee71.dagboken.core.model.SymptomScore
import se.partee71.dagboken.data.FakeCollectionFactory
import se.partee71.dagboken.data.FixedClock
import se.partee71.dagboken.data.common.DataError
import se.partee71.dagboken.data.repository.DefaultIllnessRepository
import se.partee71.dagboken.data.repository.DefaultOptionsRepository
import se.partee71.dagboken.data.repository.DefaultPrnMedicineRepository
import se.partee71.dagboken.data.repository.DefaultScreeningRepository
import se.partee71.dagboken.data.repository.DefaultSettingsRepository
import se.partee71.dagboken.data.repository.OptionsRepository
import se.partee71.dagboken.data.repository.ScreeningRepository
import se.partee71.dagboken.testing.MainDispatcherRule
import se.partee71.dagboken.ui.common.EditorSheetState
import se.partee71.dagboken.ui.common.SelectedDay

/**
 * Måendearket (HEM-5, SCR-1, SCR-2, SCR-5, SCR-6, NFR-10) – ett ark för Idag, plusknappen och Dagbok (`LogViewModel`).
 * Flyttade hit från `TodayViewModelTest` när Idag började använda samma ark. Fast klocka: söndag 4 oktober 2026
 * kl. 10:30 i Europe/Stockholm.
 */
class MoodSheetTest {

    @get:Rule
    val main = MainDispatcherRule()

    private val zone = TimeZone.of("Europe/Stockholm")
    private val today = LocalDate(2026, 10, 4)
    private val yesterday = LocalDate(2026, 10, 3)
    private val clock = FixedClock(at(today, 10, 30))
    private val factory = FakeCollectionFactory(clock = clock)

    private fun at(date: LocalDate, hour: Int, minute: Int = 0): Instant = LocalDateTime(date, LocalTime(hour, minute)).toInstant(zone)

    private fun TestScope.started(
        screenings: ScreeningRepository = DefaultScreeningRepository(factory, clock),
        options: OptionsRepository = DefaultOptionsRepository(factory),
    ): LogViewModel {
        val vm = LogViewModel(screenings, DefaultSettingsRepository(factory), options, DefaultPrnMedicineRepository(factory), DefaultIllnessRepository(factory, clock), SelectedDay(factory.scope), clock) { zone }
        backgroundScope.launch { vm.screening.collect {} }
        runCurrent()
        return vm
    }

    private val EditorSheetState<Screening, ScreeningSheetInfo>.value: Screening get() = editor.state.value.value

    /** Ett repository vars sparning väntar på [gate] – sparningen pågår tills testet släpper den. */
    private fun holding(gate: CompletableDeferred<Unit>) = object : ScreeningRepository by DefaultScreeningRepository(factory, clock) {
        override suspend fun save(loaded: Screening?, edited: Screening): Result<Unit> {
            gate.await()
            return DefaultScreeningRepository(factory, clock).save(loaded, edited)
        }
    }

    /** Alla fyra tillfällen påslagna med standardtiderna 08:00, 12:00, 17:00 och 21:00. */
    private suspend fun allOccasions() {
        factory.settings().upsert(Settings(reminders = ReminderSettings(screeningOccasions = Occasion.entries.map { OccasionReminder(it, enabled = true) }))).getOrThrow()
    }

    /** Sparat: arket ska döljas (`closing`), och när skärmen dolt det stängs det (`CloseScreening`). */
    private fun LogViewModel.assertSavedAndClosed() {
        assertEquals(true, screening.value?.closing?.value, "arket döljs när det är sparat")
        onEvent(LogEvent.CloseScreening)
        assertNull(screening.value)
    }

    private suspend fun storedScreenings(): List<Screening> = factory.screenings().getAll().getOrThrow()

    @Test
    fun `Logga nu en tidigare dag sparar en ny logg mot den dagen och tillfället (HEM-5, SCR-1, SCR-6)`() = runTest(main.dispatcher) {
        allOccasions()
        val vm = started()
        // Idag skickar den visade dagen och tillfällets påminnelsetid (TodayScreenTest).
        vm.onEvent(LogEvent.LogScreening(Occasion.LUNCH, yesterday, LocalTime(12, 0)))
        val sheet = assertNotNull(vm.screening.value)
        assertNull(sheet.loaded)
        val new = sheet.value
        assertEquals(yesterday, new.date)
        assertEquals(Occasion.LUNCH, new.occasion)
        assertEquals(LocalTime(12, 0), new.time, "en tidigare dag: tillfällets klockslag")
        assertTrue(sheet.editor.state.value.canSave, "en ny logg kan sparas direkt")

        vm.onEvent(LogEvent.ChangeEnergy(7))
        vm.onEvent(LogEvent.ChangeStress(2))
        vm.onEvent(LogEvent.ChangeSymptoms(listOf(SymptomScore("huvudvark", 4))))
        vm.onEvent(LogEvent.SaveScreening)
        runCurrent()

        vm.assertSavedAndClosed()
        assertTrue(vm.notice.value, "Mående sparat (SCR-3)")
        val saved = storedScreenings().single()
        assertEquals(new.id, saved.id)
        assertEquals(yesterday, saved.date)
        assertEquals(Occasion.LUNCH, saved.occasion)
        assertEquals(7, saved.energy)
        assertEquals(2, saved.stress)
        assertEquals(listOf(SymptomScore("huvudvark", 4)), saved.symptoms)
        assertEquals(clock.instant, saved.createdAt)
    }

    @Test
    fun `Logga nu idag tar klockslaget nu (SCR-6)`() = runTest(main.dispatcher) {
        allOccasions()
        val vm = started()
        vm.onEvent(LogEvent.LogScreening(Occasion.DINNER, null, null))
        val sheet = assertNotNull(vm.screening.value)
        assertEquals(today, sheet.value.date)
        assertEquals(LocalTime(10, 30), sheet.value.time)
        vm.onEvent(LogEvent.CloseScreening)
        assertNull(vm.screening.value)
        runCurrent()
        assertTrue(storedScreenings().isEmpty(), "stängt utan att spara skriver inget")
    }

    @Test
    fun `en loggad logg ändras fältvis – det som ändrats på annat håll står kvar (SCR-1)`() = runTest(main.dispatcher) {
        allOccasions()
        val logged = Screening("s", today, LocalTime(8, 40), Occasion.BREAKFAST, energy = 6, stress = 3, createdAt = at(today, 8, 40))
        factory.screenings().upsert(logged).getOrThrow()
        val vm = started()
        vm.onEvent(LogEvent.EditScreening(logged))
        val sheet = assertNotNull(vm.screening.value)
        assertFalse(sheet.editor.state.value.canSave, "inget ändrat än")

        // En annan enhet skriver en anteckning medan arket är öppet.
        factory.screenings().upsert(logged.copy(note = "Sov dåligt")).getOrThrow()
        vm.onEvent(LogEvent.ChangeStress(5))
        assertTrue(sheet.editor.state.value.canSave)
        vm.onEvent(LogEvent.SaveScreening)
        runCurrent()

        val saved = storedScreenings().single()
        assertEquals(5, saved.stress)
        assertEquals(6, saved.energy)
        assertEquals("Sov dåligt", saved.note)
        vm.assertSavedAndClosed()
    }

    @Test
    fun `ett sparfel visas i arket, som står kvar (SCR-1)`() = runTest(main.dispatcher) {
        allOccasions()
        val failing = object : ScreeningRepository by DefaultScreeningRepository(factory, clock) {
            override suspend fun save(loaded: Screening?, edited: Screening): Result<Unit> = Result.failure(DataError.PermissionDenied)
        }
        val vm = started(screenings = failing)
        vm.onEvent(LogEvent.LogScreening(Occasion.LUNCH, null, null))
        vm.onEvent(LogEvent.SaveScreening)
        runCurrent()
        val sheet = assertNotNull(vm.screening.value)
        assertEquals(DataError.PermissionDenied, sheet.error.value?.error)
        assertFalse(sheet.editor.state.value.saving)
        vm.onEvent(LogEvent.ChangeEnergy(3))
        assertNull(sheet.error.value, "en ändring tar bort felet")
        assertFalse(vm.notice.value)
    }

    @Test
    fun `ändringar i arket gör det ändrat, och stängt slängs de utan att något sparas (NFR-10)`() = runTest(main.dispatcher) {
        allOccasions()
        val vm = started()
        vm.onEvent(LogEvent.LogScreening(Occasion.LUNCH, null, null))
        val sheet = assertNotNull(vm.screening.value)
        assertFalse(sheet.editor.state.value.isDirty, "ett nyöppnat ark frågar inte vid stängning")
        vm.onEvent(LogEvent.ChangeEnergy(4))
        vm.onEvent(LogEvent.ChangeStress(6))
        assertTrue(sheet.editor.state.value.isDirty)
        assertEquals(4 to 6, sheet.value.energy to sheet.value.stress, "varje ändring på det aktuella värdet")
        // "Släng" i dialogen (AppBottomSheet) stänger arket.
        vm.onEvent(LogEvent.CloseScreening)
        runCurrent()
        assertNull(vm.screening.value)
        assertTrue(storedScreenings().isEmpty())
    }

    @Test
    fun `Spara markerar arket som sparande direkt – det går inte att stänga eller ändra förrän det är sparat (SCR-1)`() = runTest(main.dispatcher) {
        allOccasions()
        val gate = CompletableDeferred<Unit>()
        val vm = started(screenings = holding(gate))
        vm.onEvent(LogEvent.LogScreening(Occasion.LUNCH, null, null))
        val sheet = assertNotNull(vm.screening.value)
        vm.onEvent(LogEvent.ChangeEnergy(8))
        vm.onEvent(LogEvent.SaveScreening)
        // Ingen dispatch: i samma stund som "Spara" (samma bildruta som ett bakåt) går arket inte att stänga.
        assertTrue(sheet.editor.state.value.saving)
        assertFalse(sheet.canDismiss())

        vm.onEvent(LogEvent.CloseScreening)
        vm.onEvent(LogEvent.ChangeEnergy(1))
        runCurrent()
        assertSame(sheet, vm.screening.value, "arket står kvar")
        assertEquals(8, sheet.value.energy)

        gate.complete(Unit)
        runCurrent()
        vm.assertSavedAndClosed()
        assertEquals(8, storedScreenings().single().energy)
        assertTrue(vm.notice.value, "Mående sparat (SCR-3)")

        // Ett nytt ark efteråt öppnas som vanligt.
        vm.onEvent(LogEvent.LogScreening(Occasion.DINNER, null, null))
        assertEquals(Occasion.DINNER, vm.screening.value?.value?.occasion)
    }

    @Test
    fun `arkets rubrik har dagen bara när loggen inte är idag enligt samma klocka (HEM-5)`() = runTest(main.dispatcher) {
        allOccasions()
        val vm = started()
        vm.onEvent(LogEvent.LogScreening(Occasion.LUNCH, null, null))
        assertNull(vm.screening.value?.context?.date, "idag – bara tillfället")
        vm.onEvent(LogEvent.CloseScreening)
        // Midnatt har passerat men vyn har inte följt med: loggen och rubriken tar båda den nya dagen.
        clock.instant = at(LocalDate(2026, 10, 5), 0, 5)
        vm.onEvent(LogEvent.LogScreening(Occasion.BEDTIME, null, null))
        assertEquals(LocalDate(2026, 10, 5), vm.screening.value?.value?.date)
        assertNull(vm.screening.value?.context?.date)
        vm.onEvent(LogEvent.CloseScreening)
        vm.onEvent(LogEvent.EditScreening(Screening("s", yesterday, LocalTime(12, 0), Occasion.LUNCH)))
        assertEquals(yesterday, vm.screening.value?.context?.date)
    }

    @Test
    fun `strax efter midnatt får en ny logg den nya dagen och klockslaget, inte gårdagens datum (SCR-6)`() = runTest(main.dispatcher) {
        allOccasions()
        val vm = started()
        // Klockan har passerat midnatt men Idag har inte hunnit följa med: den skickar ingen dag (idag).
        clock.instant = at(LocalDate(2026, 10, 5), 0, 5)
        vm.onEvent(LogEvent.LogScreening(Occasion.BEDTIME, null, null))
        val new = assertNotNull(vm.screening.value).value
        assertEquals(LocalDate(2026, 10, 5), new.date)
        assertEquals(LocalTime(0, 5), new.time)
    }

    // ── Läsfel och anteckningen (SCR-1, SCR-2, SCR-5) ──

    private val denied: Flow<Nothing> = flow { throw DataError.PermissionDenied }

    @Test
    fun `symptomlistan går inte att läsa – arket fungerar med en tom lista (SCR-2)`() = runTest(main.dispatcher) {
        allOccasions()
        val failing = object : OptionsRepository by DefaultOptionsRepository(factory) {
            override fun observe(kind: OptionKind): Flow<List<Option>> = denied
        }
        val vm = started(options = failing)
        runCurrent()
        backgroundScope.launch { vm.symptomOptions.collect {} }
        runCurrent()
        assertTrue(vm.symptomOptions.value.isEmpty())
        vm.onEvent(LogEvent.LogScreening(Occasion.LUNCH, null, null))
        vm.onEvent(LogEvent.ChangeEnergy(5))
        vm.onEvent(LogEvent.SaveScreening)
        runCurrent()
        assertEquals(5, storedScreenings().single().energy)
    }

    @Test
    fun `en ändring i arket behåller loggens anteckning (SCR-1, SCR-5)`() = runTest(main.dispatcher) {
        allOccasions()
        val logged = Screening("s", today, LocalTime(8, 40), Occasion.BREAKFAST, energy = 6, stress = 3, createdAt = at(today, 8, 40), note = "Sov dåligt")
        factory.screenings().upsert(logged).getOrThrow()
        val vm = started()
        vm.onEvent(LogEvent.EditScreening(logged))
        vm.onEvent(LogEvent.ChangeEnergy(8))
        vm.onEvent(LogEvent.SaveScreening)
        runCurrent()
        val saved = storedScreenings().single()
        assertEquals(8, saved.energy)
        assertEquals("Sov dåligt", saved.note)
    }
}
