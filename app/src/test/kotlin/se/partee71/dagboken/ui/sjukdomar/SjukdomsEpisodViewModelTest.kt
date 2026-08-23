package se.partee71.dagboken.ui.sjukdomar

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import se.partee71.dagboken.data.datastore.PreferencesRepository
import se.partee71.dagboken.data.repository.NoteRepository
import se.partee71.dagboken.data.repository.SjukdomarRepository
import se.partee71.dagboken.domain.model.NoteTarget
import se.partee71.dagboken.domain.model.SjukdomsEpisod
import se.partee71.dagboken.domain.model.SjukdomsIncheckning

@OptIn(ExperimentalCoroutinesApi::class)
class SjukdomsEpisodViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    private lateinit var repo: SjukdomarRepository
    private lateinit var noteRepo: NoteRepository
    private lateinit var prefs: PreferencesRepository
    private lateinit var viewModel: SjukdomsEpisodViewModel

    private fun episod(id: String = "e1") = SjukdomsEpisod(
        id = id, typ = "migrän", startDatum = "2026-01-10", slutDatum = "",
    )

    @Before fun setUp() {
        Dispatchers.setMain(testDispatcher)
        repo = mockk(relaxed = true) {
            every { all } returns flowOf(listOf(episod()))
            every { incheckningarForEpisod("e1") } returns flowOf(emptyList())
        }
        noteRepo = mockk(relaxed = true) {
            every { observe(any(), any()) } returns flowOf("")
            every { observeMap(any()) } returns flowOf(emptyMap())
        }
        prefs = mockk(relaxed = true) {
            every { symptomOptions } returns flowOf(emptyList())
        }
        viewModel = SjukdomsEpisodViewModel(SavedStateHandle(mapOf("episodId" to "e1")), repo, noteRepo, prefs)
    }

    @After fun tearDown() { Dispatchers.resetMain() }

    // ─── episodNote ───────────────────────────────────────────────────────────

    @Test fun `episodNote reflects the note stored under SJUKDOM_EPISOD target for this episod`() = runTest {
        val vm = SjukdomsEpisodViewModel(
            SavedStateHandle(mapOf("episodId" to "e1")),
            repo,
            mockk(relaxed = true) {
                every { observe(NoteTarget.SJUKDOM_EPISOD, "e1") } returns flowOf("Svår period")
                every { observeMap(any()) } returns flowOf(emptyMap())
            },
            prefs,
        )
        vm.episodNote.test {
            assertEquals("Svår period", awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ─── saveIncheckning ──────────────────────────────────────────────────────

    @Test fun `saveIncheckning persists the note under SJUKDOM_INCHECKNING target`() = runTest {
        viewModel.updateForm { copy(anteckning = "Tog medicin") }
        viewModel.saveIncheckning()
        coVerify { noteRepo.save(NoteTarget.SJUKDOM_INCHECKNING, any(), "Tog medicin") }
    }

    @Test fun `saveIncheckning resets the form after saving`() = runTest {
        viewModel.updateForm { copy(anteckning = "Tog medicin", svarighetsgrad = 8) }
        viewModel.saveIncheckning()
        assertEquals("", viewModel.incheckningForm.value.anteckning)
        assertEquals(5, viewModel.incheckningForm.value.svarighetsgrad)
    }

    // ─── deleteIncheckning ────────────────────────────────────────────────────

    // Anteckningen raderas av SjukdomarRepository.deleteIncheckning (DAT-4).
    @Test fun `deleteIncheckning delegates the deletion to the repository`() = runTest {
        val incheckning = SjukdomsIncheckning(
            id = "i1", episodId = "e1", datum = "2026-01-10", tid = "10:00",
            svarighetsgrad = 5, symptom = "", somatiska = 0,
        )
        viewModel.deleteIncheckning(incheckning)
        coVerify { repo.deleteIncheckning(incheckning) }
        coVerify(exactly = 0) { noteRepo.delete(any(), any()) }
    }

    // ─── isIncheckningFormDirty ───────────────────────────────────────────────

    @Test fun `isIncheckningFormDirty is false on a fresh form`() = runTest {
        viewModel.isIncheckningFormDirty.test {
            assertEquals(false, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test fun `isIncheckningFormDirty becomes true after a field changes`() = runTest {
        viewModel.isIncheckningFormDirty.test {
            assertEquals(false, awaitItem())
            viewModel.updateForm { copy(svarighetsgrad = 8) }
            assertEquals(true, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test fun `isIncheckningFormDirty is false again after saveIncheckning resets the form`() = runTest {
        viewModel.isIncheckningFormDirty.test {
            assertEquals(false, awaitItem())
            viewModel.updateForm { copy(anteckning = "Tog medicin") }
            assertEquals(true, awaitItem())
            viewModel.saveIncheckning()
            assertEquals(false, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ─── Redigering av en incheckning (SJ-11, #192) ───────────────────────────

    private fun incheckning(
        id: String = "i1",
        timestamp: Long = 1_700_000_000_000L,
    ) = SjukdomsIncheckning(
        id = id, episodId = "e1", datum = "2026-01-10", tid = "10:00",
        svarighetsgrad = 7, symptom = "Huvudvärk:3", somatiska = 3, timestamp = timestamp,
    )

    @Test fun `loadIncheckningForEdit fills the form from the stored incheckning`() = runTest {
        coEvery { repo.getIncheckning("i1") } returns incheckning()
        every { noteRepo.observe(NoteTarget.SJUKDOM_INCHECKNING, "i1") } returns flowOf("Kändes tungt")

        viewModel.loadIncheckningForEdit("i1")

        val form = viewModel.incheckningForm.value
        assertEquals("2026-01-10", form.datum)
        assertEquals("10:00", form.tid)
        assertEquals(7, form.svarighetsgrad)
        assertEquals(mapOf("Huvudvärk" to 3), form.symptomScores)
        assertEquals("Kändes tungt", form.anteckning)
    }

    @Test fun `loadIncheckningForEdit leaves the form clean`() = runTest {
        coEvery { repo.getIncheckning("i1") } returns incheckning()

        viewModel.loadIncheckningForEdit("i1")

        assertEquals(false, viewModel.isIncheckningFormDirty.value)
    }

    @Test fun `saveIncheckning keeps the id and timestamp when editing`() = runTest {
        coEvery { repo.getIncheckning("i1") } returns incheckning(timestamp = 1_700_000_000_000L)
        viewModel.loadIncheckningForEdit("i1")
        viewModel.updateForm { copy(svarighetsgrad = 2) }

        viewModel.saveIncheckning()

        val saved = slot<SjukdomsIncheckning>()
        coVerify { repo.saveIncheckning(capture(saved)) }
        assertEquals("i1", saved.captured.id)
        assertEquals(1_700_000_000_000L, saved.captured.timestamp)
        assertEquals(2, saved.captured.svarighetsgrad)
    }

    @Test fun `saveIncheckning recomputes somatiska from the edited symptoms`() = runTest {
        coEvery { repo.getIncheckning("i1") } returns incheckning()
        viewModel.loadIncheckningForEdit("i1")
        viewModel.updateForm { copy(symptomScores = mapOf("Huvudvärk" to 4, "Illamående" to 2)) }

        viewModel.saveIncheckning()

        val saved = slot<SjukdomsIncheckning>()
        coVerify { repo.saveIncheckning(capture(saved)) }
        assertEquals(6, saved.captured.somatiska)
    }

    @Test fun `saveIncheckning stores the edited note under the same id`() = runTest {
        coEvery { repo.getIncheckning("i1") } returns incheckning()
        viewModel.loadIncheckningForEdit("i1")
        viewModel.updateForm { copy(anteckning = "Bättre nu") }

        viewModel.saveIncheckning()

        coVerify { noteRepo.save(NoteTarget.SJUKDOM_INCHECKNING, "i1", "Bättre nu") }
    }

    @Test fun `saveIncheckning clears the note when it is emptied`() = runTest {
        coEvery { repo.getIncheckning("i1") } returns incheckning()
        every { noteRepo.observe(NoteTarget.SJUKDOM_INCHECKNING, "i1") } returns flowOf("Kändes tungt")
        viewModel.loadIncheckningForEdit("i1")
        viewModel.updateForm { copy(anteckning = "") }

        viewModel.saveIncheckning()

        coVerify { noteRepo.save(NoteTarget.SJUKDOM_INCHECKNING, "i1", "") }
    }

    @Test fun `saveIncheckning creates a new post again after an edit was saved`() = runTest {
        coEvery { repo.getIncheckning("i1") } returns incheckning()
        viewModel.loadIncheckningForEdit("i1")
        viewModel.saveIncheckning()

        viewModel.updateForm { copy(svarighetsgrad = 9) }
        viewModel.saveIncheckning()

        val saved = mutableListOf<SjukdomsIncheckning>()
        coVerify { repo.saveIncheckning(capture(saved)) }
        assertEquals("i1", saved.first().id)
        assert(saved.last().id != "i1") {
            "Andra sparandet ska skapa en ny post, inte skriva över den redigerade"
        }
    }

    @Test fun `saveIncheckning without an edit creates a post with a fresh id`() = runTest {
        viewModel.updateForm { copy(svarighetsgrad = 8) }

        viewModel.saveIncheckning()

        val saved = slot<SjukdomsIncheckning>()
        coVerify { repo.saveIncheckning(capture(saved)) }
        assertEquals("e1", saved.captured.episodId)
        assert(saved.captured.id.isNotBlank())
    }
}
