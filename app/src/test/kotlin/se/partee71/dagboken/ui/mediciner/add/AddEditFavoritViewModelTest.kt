package se.partee71.dagboken.ui.mediciner.add

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
import se.partee71.dagboken.data.repository.MedicinerRepository
import se.partee71.dagboken.data.repository.NoteRepository
import se.partee71.dagboken.domain.model.Favorit
import se.partee71.dagboken.domain.model.NoteTarget

@OptIn(ExperimentalCoroutinesApi::class)
class AddEditFavoritViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private lateinit var repo: MedicinerRepository
    private lateinit var noteRepo: NoteRepository
    private lateinit var viewModel: AddEditFavoritViewModel

    @Before fun setUp() {
        Dispatchers.setMain(testDispatcher)
        repo = mockk(relaxed = true)
        noteRepo = mockk(relaxed = true) {
            every { observe(any(), any()) } returns flowOf("")
        }
        viewModel = AddEditFavoritViewModel(repo, noteRepo)
    }

    @After fun tearDown() { Dispatchers.resetMain() }

    private fun favorit(id: String = "f1", isFavorite: Boolean = false) = Favorit(
        id = id, namn = "Paracetamol", dos = "500", enhet = "mg", tidpunkt = "Vid behov",
        minTidMellan = 4, maxDoserPerDag = 0, isFavorite = isFavorite,
    )

    @Test fun `save on a new favorit defaults isFavorite to false`() = runTest {
        viewModel.updateForm { copy(namn = "Ibuprofen", dos = "400") }

        viewModel.save()

        val saved = slot<Favorit>()
        coVerify { repo.saveFavorit(capture(saved)) }
        assertEquals(false, saved.captured.isFavorite)
    }

    @Test fun `save after loadForEdit preserves the existing isFavorite status`() = runTest {
        coEvery { repo.getFavoritById("f1") } returns favorit(isFavorite = true)

        viewModel.loadForEdit("f1")
        viewModel.updateForm { copy(dos = "600") } // user edits an unrelated field
        viewModel.save()

        val saved = slot<Favorit>()
        coVerify { repo.saveFavorit(capture(saved)) }
        assertEquals(true, saved.captured.isFavorite)
        assertEquals("600", saved.captured.dos)
    }

    @Test fun `save after loadForEdit of a non-favorite keeps it non-favorite`() = runTest {
        coEvery { repo.getFavoritById("f1") } returns favorit(isFavorite = false)

        viewModel.loadForEdit("f1")
        viewModel.save()

        val saved = slot<Favorit>()
        coVerify { repo.saveFavorit(capture(saved)) }
        assertEquals(false, saved.captured.isFavorite)
    }

    // ─── anteckning ───────────────────────────────────────────────────────────

    @Test fun `loadForEdit populates form note from noteRepo`() = runTest {
        coEvery { repo.getFavoritById("f1") } returns favorit()
        every { noteRepo.observe(NoteTarget.FAVORIT, "f1") } returns flowOf("Ta med mat")

        viewModel.loadForEdit("f1")

        assertEquals("Ta med mat", viewModel.form.value.anteckning)
    }

    @Test fun `save persists the note under FAVORIT target`() = runTest {
        viewModel.updateForm { copy(namn = "Ibuprofen", dos = "400", anteckning = "Ta med mat") }

        viewModel.save()

        coVerify { noteRepo.save(NoteTarget.FAVORIT, any(), "Ta med mat") }
    }

    // ─── isDirty ──────────────────────────────────────────────────────────────

    @Test fun `isDirty is false on a fresh form`() = runTest {
        viewModel.isDirty.test {
            assertEquals(false, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test fun `isDirty becomes true after a field changes`() = runTest {
        viewModel.isDirty.test {
            assertEquals(false, awaitItem())
            viewModel.updateForm { copy(namn = "Ibuprofen") }
            assertEquals(true, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test fun `isDirty is false right after loadForEdit`() = runTest {
        coEvery { repo.getFavoritById("f1") } returns favorit()
        viewModel.isDirty.test {
            assertEquals(false, awaitItem())
            viewModel.loadForEdit("f1")
            expectNoEvents()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test fun `isDirty is false again after save`() = runTest {
        viewModel.isDirty.test {
            assertEquals(false, awaitItem())
            viewModel.updateForm { copy(namn = "Ibuprofen", dos = "400") }
            assertEquals(true, awaitItem())
            viewModel.save()
            assertEquals(false, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }
}
