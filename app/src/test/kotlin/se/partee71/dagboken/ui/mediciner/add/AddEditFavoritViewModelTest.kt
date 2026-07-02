package se.partee71.dagboken.ui.mediciner.add

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import se.partee71.dagboken.data.repository.MedicinerRepository
import se.partee71.dagboken.domain.model.Favorit

@OptIn(ExperimentalCoroutinesApi::class)
class AddEditFavoritViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private lateinit var repo: MedicinerRepository
    private lateinit var viewModel: AddEditFavoritViewModel

    @Before fun setUp() {
        Dispatchers.setMain(testDispatcher)
        repo = mockk(relaxed = true)
        viewModel = AddEditFavoritViewModel(repo)
    }

    @After fun tearDown() { Dispatchers.resetMain() }

    private fun favorit(id: String = "f1", isFavorite: Boolean = false) = Favorit(
        id = id, namn = "Paracetamol", dos = "500", enhet = "mg", tidpunkt = "Vid behov",
        anteckning = "", minTidMellan = 4, maxDoserPerDag = 0, isFavorite = isFavorite,
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
}
