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
import se.partee71.dagboken.domain.model.SjukdomsIncheckning

@OptIn(ExperimentalCoroutinesApi::class)
class SjukdomarViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    private lateinit var repo: SjukdomarRepository
    private lateinit var noteRepo: NoteRepository
    private lateinit var viewModel: SjukdomarViewModel

    private fun episod(id: String = "e1") = SjukdomsEpisod(
        id = id, typ = "migrän", startDatum = "2026-01-10", slutDatum = "",
    )

    private fun incheckning(id: String, episodId: String = "e1") = SjukdomsIncheckning(
        id = id, episodId = episodId, datum = "2026-01-10", tid = "10:00",
        svarighetsgrad = 5, symptom = "", somatiska = 0,
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

    @Test fun `delete removes the episod's own note`() = runTest {
        every { repo.incheckningarForEpisod("e1") } returns flowOf(emptyList())
        viewModel.delete(episod())
        coVerify { noteRepo.delete(NoteTarget.SJUKDOM_EPISOD, "e1") }
    }

    @Test fun `delete also removes notes for every incheckning belonging to the episod`() = runTest {
        every { repo.incheckningarForEpisod("e1") } returns flowOf(listOf(
            incheckning("i1"), incheckning("i2"),
        ))
        viewModel.delete(episod())
        coVerify { noteRepo.delete(NoteTarget.SJUKDOM_INCHECKNING, "i1") }
        coVerify { noteRepo.delete(NoteTarget.SJUKDOM_INCHECKNING, "i2") }
    }

    @Test fun `delete sets snackbar to typ plus borttagen`() = runTest {
        every { repo.incheckningarForEpisod("e1") } returns flowOf(emptyList())
        viewModel.delete(episod())
        assert(viewModel.snackbar.value == "migrän borttagen")
    }
}
