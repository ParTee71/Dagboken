package se.partee71.dagboken.ui.illness

import app.cash.turbine.test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
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
import se.partee71.dagboken.core.engine.EndDateError
import se.partee71.dagboken.core.model.Checkin
import se.partee71.dagboken.core.model.IllnessEpisode
import se.partee71.dagboken.core.model.Option
import se.partee71.dagboken.core.model.OptionKind
import se.partee71.dagboken.core.model.SymptomScore
import se.partee71.dagboken.data.FakeCollectionFactory
import se.partee71.dagboken.data.FixedClock
import se.partee71.dagboken.data.common.DataError
import se.partee71.dagboken.data.repository.DefaultIllnessRepository
import se.partee71.dagboken.data.repository.DefaultOptionsRepository
import se.partee71.dagboken.data.repository.IllnessRepository
import se.partee71.dagboken.testing.MainDispatcherRule
import se.partee71.dagboken.ui.common.DetailUiState
import se.partee71.dagboken.ui.common.STOP_TIMEOUT_MILLIS

/**
 * Sjukdomsdetaljens ViewModel (SJ-4, SJ-5, SJ-9, SJ-13) mot `FakeCollection` och en fast klocka: tisdag 6 oktober
 * 2026 kl. 14:20 i Europe/Stockholm. Läsning, avsluta, radera episod (kaskad, kräver nät) och radera incheckning.
 * Syntetisk data.
 */
class IllnessDetailViewModelTest {

    @get:Rule
    val main = MainDispatcherRule()

    private val zone = TimeZone.of("Europe/Stockholm")
    private val today = LocalDate(2026, 10, 6)
    private val clock = FixedClock(LocalDateTime(today, LocalTime(14, 20)).toInstant(zone))
    private val factory = FakeCollectionFactory(clock = clock)
    private val illnesses = DefaultIllnessRepository(factory, clock)
    private val created = Instant.fromEpochSeconds(1_790_000_000)
    private val flu = IllnessEpisode("flu", "Förkylning", LocalDate(2026, 10, 1), createdAt = created)
    private val first = Checkin("c1", LocalDate(2026, 10, 1), LocalTime(19, 0), 5, listOf(SymptomScore("snuva", 4)), created)
    private val latest = Checkin("c2", LocalDate(2026, 10, 5), LocalTime(21, 40), 4, createdAt = created)

    private suspend fun seed() {
        factory.illnessEpisodes().upsert(flu).getOrThrow()
        factory.checkins("flu").batch(listOf(first, latest)).getOrThrow()
        factory.options().upsert(Option("snuva", OptionKind.SYMPTOM, "Snuva")).getOrThrow()
    }

    private fun TestScope.started(id: String = "flu"): IllnessDetailViewModel {
        val vm = IllnessDetailViewModel(illnesses, DefaultOptionsRepository(factory), clock, { zone }, id)
        backgroundScope.launch { vm.state.collect {} }
        runCurrent()
        return vm
    }

    private val IllnessDetailViewModel.detail: IllnessDetail get() = (state.value as DetailUiState.Content).value

    @Test
    fun `episoden med incheckningarna senaste först, varaktigheten till idag och symptomens namn (SJ-5, SJ-13)`() = runTest(main.dispatcher) {
        seed()
        val vm = IllnessDetailViewModel(illnesses, DefaultOptionsRepository(factory), clock, { zone }, "flu")
        vm.state.test {
            var content: IllnessDetail? = null
            while (content == null) content = (awaitItem() as? DetailUiState.Content)?.value
            assertEquals(flu, content.summary.episode)
            assertEquals(listOf("c2", "c1"), content.summary.checkins.map { it.id })
            assertTrue(content.summary.ongoing)
            assertEquals(6, content.summary.durationDays)
            assertEquals(4, content.summary.latestSeverity)
            assertEquals(today, content.today)
            assertEquals(mapOf("snuva" to "Snuva"), content.symptomNames)
        }
    }

    @Test
    fun `en episod som inte finns är ett läsfel med Försök igen`() = runTest(main.dispatcher) {
        val vm = started("saknas")
        assertEquals(DetailUiState.Error(DataError.NotFound), vm.state.value)
    }

    @Test
    fun `Avsluta episod föreslår idag och skriver bara slutdatumet (SJ-4)`() = runTest(main.dispatcher) {
        seed()
        val vm = started()
        vm.onEvent(IllnessDetailEvent.Finish)
        assertEquals(IllnessPrompt.Finish(today), vm.prompt.value)
        vm.onEvent(IllnessDetailEvent.FinishDate(LocalDate(2026, 10, 5)))
        vm.onEvent(IllnessDetailEvent.ConfirmFinish)
        runCurrent()
        assertNull(vm.prompt.value)
        assertEquals(flu.copy(end = LocalDate(2026, 10, 5)), illnesses.getEpisode("flu").getOrThrow())
        assertFalse(vm.detail.summary.ongoing)
        assertEquals(5, vm.detail.summary.durationDays)
    }

    @Test
    fun `ett slutdatum före starten stänger inte frågan och skriver ingenting (SJ-4)`() = runTest(main.dispatcher) {
        seed()
        val vm = started()
        vm.onEvent(IllnessDetailEvent.Finish)
        vm.onEvent(IllnessDetailEvent.FinishDate(LocalDate(2026, 9, 30)))
        vm.onEvent(IllnessDetailEvent.ConfirmFinish)
        runCurrent()
        assertEquals(IllnessPrompt.Finish(LocalDate(2026, 9, 30), EndDateError.BEFORE_START), vm.prompt.value)
        vm.onEvent(IllnessDetailEvent.FinishDate(LocalDate(2026, 10, 7)))
        vm.onEvent(IllnessDetailEvent.ConfirmFinish)
        runCurrent()
        assertEquals(IllnessPrompt.Finish(LocalDate(2026, 10, 7), EndDateError.AFTER_TODAY), vm.prompt.value, "ett slut efter idag")
        assertEquals(flu, illnesses.getEpisode("flu").getOrThrow())
        vm.onEvent(IllnessDetailEvent.DismissPrompt)
        assertNull(vm.prompt.value)
    }

    @Test
    fun `en episod som börjar efter idag går inte att avsluta (SJ-4)`() = runTest(main.dispatcher) {
        factory.illnessEpisodes().upsert(flu.copy(start = LocalDate(2026, 10, 8))).getOrThrow()
        val vm = started()
        assertFalse(vm.detail.canFinish)
        vm.onEvent(IllnessDetailEvent.Finish)
        assertNull(vm.prompt.value)
    }

    @Test
    fun `en öppen fråga gäller det senast laddade också när skärmen prenumererat om (SJ-4, SJ-9)`() = runTest(main.dispatcher) {
        seed()
        val vm = IllnessDetailViewModel(illnesses, DefaultOptionsRepository(factory), clock, { zone }, "flu")
        val subscription = backgroundScope.launch { vm.state.collect {} }
        runCurrent()
        vm.onEvent(IllnessDetailEvent.Finish)
        subscription.cancel()
        advanceTimeBy(STOP_TIMEOUT_MILLIS + 1)
        runCurrent()
        assertEquals(DetailUiState.Loading, vm.state.value, "utan prenumerant går läget tillbaka till laddning")
        vm.onEvent(IllnessDetailEvent.ConfirmFinish)
        runCurrent()
        assertNull(vm.prompt.value)
        assertEquals(today, illnesses.getEpisode("flu").getOrThrow()?.end)
        vm.onEvent(IllnessDetailEvent.Delete)
        runCurrent()
        assertEquals(IllnessPrompt.Delete(2), vm.prompt.value)
    }

    @Test
    fun `under en radering finns inga åtgärder och ingen incheckning raderas eller avslutas (SJ-9)`() = runTest(main.dispatcher) {
        seed()
        val gate = CompletableDeferred<Unit>()
        val held = object : IllnessRepository by illnesses {
            override suspend fun deleteEpisode(id: String): Result<Unit> {
                gate.await()
                return illnesses.deleteEpisode(id)
            }
        }
        val vm = IllnessDetailViewModel(held, DefaultOptionsRepository(factory), clock, { zone }, "flu")
        backgroundScope.launch { vm.state.collect {} }
        runCurrent()
        vm.onEvent(IllnessDetailEvent.Delete)
        runCurrent()
        vm.onEvent(IllnessDetailEvent.ConfirmDelete)
        runCurrent()
        assertTrue(vm.detail.deleting, "skärmen döljer Ny incheckning, Redigera, Avsluta och incheckningarnas meny")
        vm.onEvent(IllnessDetailEvent.Finish)
        assertNull(vm.prompt.value, "Avsluta under radering")
        vm.onEvent(IllnessDetailEvent.DeleteCheckin("c1"))
        vm.onEvent(IllnessDetailEvent.Delete)
        runCurrent()
        assertNull(vm.prompt.value, "Radera en gång till")
        assertEquals(2, illnesses.observeCheckins("flu").first().size, "ingen incheckning raderad för sig")
        gate.complete(Unit)
        runCurrent()
        assertTrue(vm.closed.value)
    }

    @Test
    fun `Radera frågar med antalet incheckningar och raderar episoden med dem, sedan stängs detaljen (SJ-9)`() = runTest(main.dispatcher) {
        seed()
        val vm = started()
        vm.onEvent(IllnessDetailEvent.Delete)
        runCurrent()
        assertEquals(IllnessPrompt.Delete(2), vm.prompt.value)
        vm.closed.test {
            assertFalse(awaitItem())
            vm.onEvent(IllnessDetailEvent.ConfirmDelete)
            assertTrue(awaitItem())
        }
        assertNull(vm.prompt.value)
        assertNull(illnesses.getEpisode("flu").getOrThrow())
        assertEquals(emptyList(), illnesses.observeCheckins("flu").first())
        assertEquals("flu", vm.detail.summary.episode.id, "det som visades står kvar tills detaljen stängts")
    }

    @Test
    fun `utan nät raderas ingenting, felet visas och detaljen står kvar (SJ-9)`() = runTest(main.dispatcher) {
        seed()
        val vm = started()
        factory.store.online = false
        vm.onEvent(IllnessDetailEvent.Delete)
        runCurrent()
        vm.onEvent(IllnessDetailEvent.ConfirmDelete)
        runCurrent()
        assertEquals(DataError.Offline, vm.failure.value?.error)
        assertFalse(vm.closed.value)
        assertEquals(flu, illnesses.getEpisode("flu").getOrThrow())
        assertEquals(2, illnesses.observeCheckins("flu").first().size)
        vm.onEvent(IllnessDetailEvent.ErrorShown)
        assertNull(vm.failure.value)
    }

    @Test
    fun `en incheckning raderas och episoden står kvar (HIST-5)`() = runTest(main.dispatcher) {
        seed()
        val vm = started()
        vm.onEvent(IllnessDetailEvent.DeleteCheckin("c2"))
        runCurrent()
        assertEquals(listOf("c1"), vm.detail.summary.checkins.map { it.id })
        assertEquals(flu, illnesses.getEpisode("flu").getOrThrow())
    }
}
