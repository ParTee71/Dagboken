package se.partee71.dagboken.ui.sjukdomar

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
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

    @Test fun `deleteIncheckning also deletes its note`() = runTest {
        val incheckning = SjukdomsIncheckning(
            id = "i1", episodId = "e1", datum = "2026-01-10", tid = "10:00",
            svarighetsgrad = 5, symptom = "", somatiska = 0,
        )
        viewModel.deleteIncheckning(incheckning)
        coVerify { noteRepo.delete(NoteTarget.SJUKDOM_INCHECKNING, "i1") }
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
}
