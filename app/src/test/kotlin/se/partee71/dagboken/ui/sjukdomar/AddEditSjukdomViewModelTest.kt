package se.partee71.dagboken.ui.sjukdomar

import io.mockk.coEvery
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

@OptIn(ExperimentalCoroutinesApi::class)
class AddEditSjukdomViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    private lateinit var repo: SjukdomarRepository
    private lateinit var noteRepo: NoteRepository
    private lateinit var prefs: PreferencesRepository
    private lateinit var viewModel: AddEditSjukdomViewModel

    private fun episod(id: String = "e1") = SjukdomsEpisod(
        id = id, typ = "migrän", startDatum = "2026-01-10", slutDatum = "",
    )

    @Before fun setUp() {
        Dispatchers.setMain(testDispatcher)
        repo = mockk(relaxed = true) {
            every { all } returns flowOf(listOf(episod()))
        }
        noteRepo = mockk(relaxed = true) {
            every { observe(any(), any()) } returns flowOf("")
        }
        prefs = mockk(relaxed = true) {
            every { symptomOptions } returns flowOf(emptyList())
        }
        viewModel = AddEditSjukdomViewModel(repo, noteRepo, prefs)
    }

    @After fun tearDown() { Dispatchers.resetMain() }

    // ─── loadForEdit ──────────────────────────────────────────────────────────

    @Test fun `loadForEdit populates form note from noteRepo`() = runTest {
        every { noteRepo.observe(NoteTarget.SJUKDOM_EPISOD, "e1") } returns flowOf("Svår period")
        viewModel.loadForEdit("e1")
        assertEquals("Svår period", viewModel.form.value.anteckning)
    }

    // ─── save ─────────────────────────────────────────────────────────────────

    @Test fun `save persists the note under SJUKDOM_EPISOD target`() = runTest {
        viewModel.updateForm { copy(typ = "Migrän", anteckning = "Svår period") }
        viewModel.save {}
        coVerify { noteRepo.save(NoteTarget.SJUKDOM_EPISOD, any(), "Svår period") }
    }

    @Test fun `save does nothing when typ is blank`() = runTest {
        viewModel.updateForm { copy(typ = "") }
        var called = false
        viewModel.save { called = true }
        assert(!called)
        coVerify(exactly = 0) { repo.saveEpisod(any()) }
    }

    @Test fun `save preserves the episod id when editing`() = runTest {
        coEvery { repo.all } returns flowOf(listOf(episod(id = "e1")))
        viewModel.loadForEdit("e1")
        viewModel.updateForm { copy(typ = "Migrän") }
        viewModel.save {}
        coVerify { noteRepo.save(NoteTarget.SJUKDOM_EPISOD, "e1", any()) }
    }
}
