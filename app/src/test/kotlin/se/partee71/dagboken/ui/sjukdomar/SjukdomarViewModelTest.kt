package se.partee71.dagboken.ui.sjukdomar

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
import org.junit.Before
import org.junit.Test
import se.partee71.dagboken.data.repository.NoteRepository
import se.partee71.dagboken.data.repository.SjukdomarRepository
import se.partee71.dagboken.domain.model.NoteTarget
import se.partee71.dagboken.domain.model.SjukdomsEpisod

@OptIn(ExperimentalCoroutinesApi::class)
class SjukdomarViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    private lateinit var repo: SjukdomarRepository
    private lateinit var noteRepo: NoteRepository
    private lateinit var viewModel: SjukdomarViewModel

    private fun episod(id: String = "e1") = SjukdomsEpisod(
        id = id, typ = "migrän", startDatum = "2026-01-10", slutDatum = "",
    )

    @Before fun setUp() {
        Dispatchers.setMain(testDispatcher)
        repo = mockk(relaxed = true) {
            every { all } returns flowOf(emptyList())
            every { pagaende } returns flowOf(null)
        }
        noteRepo = mockk(relaxed = true)
        viewModel = SjukdomarViewModel(repo, noteRepo)
    }

    @After fun tearDown() { Dispatchers.resetMain() }

    /**
     * Episoden, dess incheckningar och samtliga anteckningar raderas numera av
     * SjukdomarRepository.deleteEpisod (DAT-4) — täcks av NoteCleanupOnDeleteTest.
     * Här verifieras att ViewModel:en delegerar dit i stället för att städa själv.
     */
    @Test fun `delete delegates to the repository, which also removes the notes`() = runTest {
        val e = episod()
        viewModel.delete(e)
        coVerify { repo.deleteEpisod(e) }
        coVerify(exactly = 0) { noteRepo.delete(any(), any()) }
    }

    @Test fun `delete sets snackbar to typ plus borttagen`() = runTest {
        viewModel.delete(episod())
        assert(viewModel.snackbar.value == "migrän borttagen")
    }

    @Test fun `episodNotes exposes NoteRepository observeMap for SJUKDOM_EPISOD`() = runTest {
        every { noteRepo.observeMap(NoteTarget.SJUKDOM_EPISOD) } returns flowOf(mapOf("e1" to "Migrän efter stress"))
        val vm2 = SjukdomarViewModel(repo, noteRepo)
        assert(vm2.episodNotes.first { it.isNotEmpty() } == mapOf("e1" to "Migrän efter stress"))
    }
}
