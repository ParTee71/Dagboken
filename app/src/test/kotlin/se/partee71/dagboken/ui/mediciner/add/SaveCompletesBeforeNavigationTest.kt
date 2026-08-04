package se.partee71.dagboken.ui.mediciner.add

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import se.partee71.dagboken.data.repository.MedicinerRepository
import se.partee71.dagboken.data.repository.NoteRepository
import se.partee71.dagboken.domain.model.Favorit
import se.partee71.dagboken.domain.model.NoteTarget
import se.partee71.dagboken.domain.model.Recept

/**
 * NFR-12: formulären navigerade tidigare tillbaka direkt efter `save()`, medan skrivningen
 * fortfarande låg i viewModelScope. När skärmen stängdes rensades ViewModel:en och
 * skrivningen kunde avbrytas mitt i.
 *
 * Kontraktet är därför: `onDone` anropas **efter** att både posten och dess anteckning
 * skrivits — aldrig före.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SaveCompletesBeforeNavigationTest {

    private val dispatcher = StandardTestDispatcher()
    private lateinit var repo: MedicinerRepository
    private lateinit var noteRepo: NoteRepository

    @Before fun setUp() {
        Dispatchers.setMain(dispatcher)
        repo = mockk(relaxed = true)
        noteRepo = mockk(relaxed = true) {
            coEvery { observe(any(), any()) } returns flowOf("")
        }
    }

    @After fun tearDown() = Dispatchers.resetMain()

    // ─── Recept ───────────────────────────────────────────────────────────────

    @Test fun `recept save does not call onDone before the write finishes`() = runTest(dispatcher) {
        val vm = AddEditReceptViewModel(repo, noteRepo)
        vm.updateForm { copy(namn = "Metformin", dos = "500") }

        var done = 0
        vm.save(onDone = { done++ })

        // Skrivningen är schemalagd men inte körd ännu.
        assertEquals(0, done)

        advanceUntilIdle()

        assertEquals(1, done)
        coVerify(exactly = 1) { repo.saveRecept(any()) }
    }

    @Test fun `recept save writes the recept and its note before onDone`() = runTest(dispatcher) {
        val vm = AddEditReceptViewModel(repo, noteRepo)
        vm.updateForm { copy(namn = "Metformin", dos = "500", anteckning = "Med mat") }

        val order = mutableListOf<String>()
        coEvery { repo.saveRecept(any<Recept>()) } answers { order += "recept" }
        coEvery { noteRepo.save(NoteTarget.RECEPT, any(), any()) } answers { order += "note" }

        vm.save(onDone = { order += "done" })
        advanceUntilIdle()

        assertEquals(listOf("recept", "note", "done"), order)
    }

    @Test fun `recept save is skipped and onDone never fires when the form is invalid`() = runTest(dispatcher) {
        val vm = AddEditReceptViewModel(repo, noteRepo)
        // Slutdatum före startdatum är ett valideringsfel (SLUT_FORE_START).
        vm.updateForm {
            copy(
                namn = "Metformin", dos = "500",
                startDatum = "2026-03-01",
                periodMode = PeriodMode.SLUTDATUM,
                slutDatumVal = "2026-01-01",
            )
        }

        var done = 0
        vm.save(onDone = { done++ })
        advanceUntilIdle()

        assertEquals(0, done)
        coVerify(exactly = 0) { repo.saveRecept(any()) }
    }

    // ─── Favorit ──────────────────────────────────────────────────────────────

    @Test fun `favorit save does not call onDone before the write finishes`() = runTest(dispatcher) {
        val vm = AddEditFavoritViewModel(repo, noteRepo)
        vm.updateForm { copy(namn = "Ibuprofen", dos = "400") }

        var done = 0
        vm.save(onDone = { done++ })

        assertEquals(0, done)

        advanceUntilIdle()

        assertEquals(1, done)
        coVerify(exactly = 1) { repo.saveFavorit(any<Favorit>()) }
    }

    @Test fun `favorit save writes the favorit and its note before onDone`() = runTest(dispatcher) {
        val vm = AddEditFavoritViewModel(repo, noteRepo)
        vm.updateForm { copy(namn = "Ibuprofen", dos = "400", anteckning = "Max 3/dag") }

        val order = mutableListOf<String>()
        coEvery { repo.saveFavorit(any<Favorit>()) } answers { order += "favorit" }
        coEvery { noteRepo.save(NoteTarget.FAVORIT, any(), any()) } answers { order += "note" }

        vm.save(onDone = { order += "done" })
        advanceUntilIdle()

        assertEquals(listOf("favorit", "note", "done"), order)
    }
}
