package se.partee71.dagboken.ui.mediciner.add

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
import se.partee71.dagboken.data.repository.MedicinerRepository
import se.partee71.dagboken.data.repository.NoteRepository
import se.partee71.dagboken.domain.model.NoteTarget
import se.partee71.dagboken.domain.model.Recept

@OptIn(ExperimentalCoroutinesApi::class)
class AddEditReceptViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private lateinit var repo: MedicinerRepository
    private lateinit var noteRepo: NoteRepository
    private lateinit var viewModel: AddEditReceptViewModel

    @Before fun setUp() {
        Dispatchers.setMain(testDispatcher)
        repo = mockk(relaxed = true)
        noteRepo = mockk(relaxed = true) {
            every { observe(any(), any()) } returns flowOf("")
        }
        viewModel = AddEditReceptViewModel(repo, noteRepo)
    }

    @After fun tearDown() { Dispatchers.resetMain() }

    private fun recept(id: String = "r1") = Recept(
        id = id, namn = "Metformin", dos = "500", enhet = "mg",
        tidpunkter = listOf("Morgon"), upprepning = "dagligen",
        dagar = emptyList(), intervalDagar = 2, aktiv = true, skapad = "2026-01-01",
    )

    @Test fun `save persists the entered recept`() = runTest {
        viewModel.updateForm { copy(namn = "Metformin", dos = "500") }

        viewModel.save()

        val saved = slot<Recept>()
        coVerify { repo.saveRecept(capture(saved)) }
        assertEquals("Metformin", saved.captured.namn)
    }

    // ─── anteckning ───────────────────────────────────────────────────────────

    @Test fun `loadForEdit populates form note from noteRepo`() = runTest {
        coEvery { repo.getReceptById("r1") } returns recept()
        every { noteRepo.observe(NoteTarget.RECEPT, "r1") } returns flowOf("Ta med mat")

        viewModel.loadForEdit("r1")

        assertEquals("Ta med mat", viewModel.form.value.anteckning)
    }

    @Test fun `save persists the note under RECEPT target`() = runTest {
        viewModel.updateForm { copy(namn = "Metformin", dos = "500", anteckning = "Ta med mat") }

        viewModel.save()

        coVerify { noteRepo.save(NoteTarget.RECEPT, any(), "Ta med mat") }
    }
}
