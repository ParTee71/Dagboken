package se.partee71.dagboken.ui.aktiviteter

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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import se.partee71.dagboken.data.datastore.PreferencesRepository
import se.partee71.dagboken.data.repository.AktiviteterRepository
import se.partee71.dagboken.domain.model.Aktivitet

@OptIn(ExperimentalCoroutinesApi::class)
class AktiviteterViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    private lateinit var repo: AktiviteterRepository
    private lateinit var prefs: PreferencesRepository
    private lateinit var viewModel: AktiviteterViewModel

    @Before fun setUp() {
        Dispatchers.setMain(testDispatcher)
        repo = mockk(relaxed = true) {
            every { all } returns flowOf(emptyList())
        }
        prefs = mockk(relaxed = true) {
            every { aktivitetOptions } returns flowOf(emptyList())
            every { symptomOptions } returns flowOf(emptyList())
        }
        viewModel = AktiviteterViewModel(repo, prefs)
    }

    @After fun tearDown() { Dispatchers.resetMain() }

    private fun aktivitet(
        id: String = "a1",
        aktivitet: String = "Promenad",
        type: String = "aktivitet",
    ) = Aktivitet(
        id = id, timestamp = "2026-01-15T09:00:00.000Z", datum = "2026-01-15", tid = "09:00",
        aktivitet = aktivitet, energy = 5, stress = 3, somatiska = 0,
        symptom = "", aterhamtande = false, energitjuv = false, type = type, spentTime = 0,
    )

    // ─── historyFilter ────────────────────────────────────────────────────────

    @Test fun `historyFilter initial value contains both types`() {
        assertTrue("aktivitet" in viewModel.historyFilter.value)
        assertTrue("screening" in viewModel.historyFilter.value)
    }

    @Test fun `toggleHistoryFilter removes a type when another remains`() {
        viewModel.toggleHistoryFilter("screening")
        assertFalse("screening" in viewModel.historyFilter.value)
    }

    @Test fun `toggleHistoryFilter does not remove the last remaining type`() {
        viewModel.toggleHistoryFilter("screening")      // leaves only aktivitet
        viewModel.toggleHistoryFilter("aktivitet")      // should be blocked
        assertTrue("aktivitet" in viewModel.historyFilter.value)
    }

    @Test fun `toggleHistoryFilter adds a type that was removed`() {
        viewModel.toggleHistoryFilter("screening")
        viewModel.toggleHistoryFilter("screening")
        assertTrue("screening" in viewModel.historyFilter.value)
    }

    // ─── save – guard conditions ──────────────────────────────────────────────

    @Test fun `save does nothing when aktivitet name is blank`() = runTest {
        viewModel.updateForm { copy(aktivitet = "") }
        var doneCalled = false
        viewModel.save { doneCalled = true }
        assertFalse(doneCalled)
        coVerify(exactly = 0) { repo.save(any()) }
    }

    @Test fun `save does nothing when aktivitet is Ovrigt and aktivitetAnnat is blank`() = runTest {
        viewModel.updateForm { copy(aktivitet = "Övrigt", aktivitetAnnat = "  ") }
        var doneCalled = false
        viewModel.save { doneCalled = true }
        assertFalse(doneCalled)
    }

    // ─── save – happy path ────────────────────────────────────────────────────

    @Test fun `save calls repo and invokes onDone for aktivitet type`() = runTest {
        viewModel.updateForm { copy(aktivitet = "Promenad", type = "aktivitet") }
        var doneCalled = false
        viewModel.save { doneCalled = true }
        assertTrue(doneCalled)
        coVerify { repo.save(any()) }
    }

    @Test fun `save uses aktivitetAnnat as name when aktivitet is Ovrigt`() = runTest {
        viewModel.updateForm { copy(aktivitet = "Övrigt", aktivitetAnnat = "Min aktivitet") }
        val saved = slot<Aktivitet>()
        coEvery { repo.save(capture(saved)) } returns Unit
        viewModel.save {}
        assertEquals("Min aktivitet", saved.captured.aktivitet)
    }

    @Test fun `save sets screening snackbar when type is screening`() = runTest {
        viewModel.updateForm { copy(aktivitet = "Morgonscreening", type = "screening") }
        viewModel.save {}
        assertEquals("Screening sparad ✓", viewModel.snackbar.value)
    }

    @Test fun `save resets form after saving`() = runTest {
        viewModel.updateForm { copy(aktivitet = "Promenad") }
        viewModel.save {}
        assertEquals("", viewModel.form.value.aktivitet)
    }

    // ─── delete ───────────────────────────────────────────────────────────────

    @Test fun `delete calls repo and sets snackbar`() = runTest {
        val a = aktivitet(id = "a1", aktivitet = "Promenad")
        viewModel.delete(a)
        coVerify { repo.delete(a) }
        assertEquals("Promenad borttagen", viewModel.snackbar.value)
    }

    @Test fun `clearSnackbar nulls the snackbar message`() = runTest {
        viewModel.delete(aktivitet())
        viewModel.clearSnackbar()
        assertNull(viewModel.snackbar.value)
    }

    // ─── resetForm ────────────────────────────────────────────────────────────

    @Test fun `resetForm clears form state`() = runTest {
        viewModel.updateForm { copy(aktivitet = "Promenad", energy = 8) }
        viewModel.resetForm()
        assertEquals("", viewModel.form.value.aktivitet)
        assertEquals(0, viewModel.form.value.energy)
    }

    // ─── snackbar ─────────────────────────────────────────────────────────────

    @Test fun `screening snackbar is null initially`() {
        assertNull(viewModel.snackbar.value)
    }

    @Test fun `delete snackbar contains aktivitet name`() = runTest {
        viewModel.delete(aktivitet(aktivitet = "Simning"))
        assertNotNull(viewModel.snackbar.value)
        assertTrue(viewModel.snackbar.value!!.contains("Simning"))
    }
}
