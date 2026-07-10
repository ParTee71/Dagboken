package se.partee71.dagboken.ui.aktiviteter

import app.cash.turbine.test
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
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
import se.partee71.dagboken.data.repository.NoteRepository
import se.partee71.dagboken.domain.model.Aktivitet
import se.partee71.dagboken.domain.model.NoteTarget

@OptIn(ExperimentalCoroutinesApi::class)
class AktiviteterViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    private val allFlow = MutableStateFlow<List<Aktivitet>>(emptyList())

    private lateinit var repo: AktiviteterRepository
    private lateinit var noteRepo: NoteRepository
    private lateinit var prefs: PreferencesRepository
    private lateinit var viewModel: AktiviteterViewModel

    @Before fun setUp() {
        Dispatchers.setMain(testDispatcher)
        repo = mockk(relaxed = true) {
            every { all } returns allFlow
        }
        noteRepo = mockk(relaxed = true) {
            every { observe(any(), any()) } returns flowOf("")
            every { observeMap(any()) } returns flowOf(emptyMap())
        }
        prefs = mockk(relaxed = true) {
            every { aktivitetOptions } returns flowOf(emptyList())
            every { symptomOptions } returns flowOf(emptyList())
        }
        viewModel = AktiviteterViewModel(repo, noteRepo, prefs)
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

    // ─── prefillNewAktivitet (smarta FAB-förval) ─────────────────────────────

    @Test fun `prefillNewAktivitet fills type and duration from the latest aktivitet`() = runTest {
        allFlow.value = listOf(
            aktivitet(id = "s1", type = "screening"),                   // newest, ignored
            aktivitet(id = "a1", aktivitet = "Löpning").copy(spentTime = 90),
        )
        viewModel.prefillNewAktivitet()
        val form = viewModel.form.value
        assertEquals("Löpning", form.aktivitet)
        assertEquals(1, form.spentTimeHours)
        assertEquals(30, form.spentTimeMinutes)
    }

    @Test fun `prefillNewAktivitet leaves an empty form when there is no prior aktivitet`() = runTest {
        allFlow.value = emptyList()
        viewModel.prefillNewAktivitet()
        val form = viewModel.form.value
        assertEquals("", form.aktivitet)
        assertEquals(0, form.spentTimeHours)
        assertEquals(0, form.spentTimeMinutes)
    }

    // ─── recentEntries ────────────────────────────────────────────────────────

    @Test fun `recentEntries is empty when no entries exist`() = runTest {
        viewModel.recentEntries.test {
            assertEquals(emptyList<Aktivitet>(), awaitItem())
        }
    }

    @Test fun `recentEntries returns at most 3 entries`() = runTest {
        allFlow.value = listOf(
            aktivitet(id = "a1"), aktivitet(id = "a2"), aktivitet(id = "a3"), aktivitet(id = "a4"),
        )
        viewModel.recentEntries.test {
            assertEquals(listOf("a1", "a2", "a3"), awaitItem().map { it.id })
        }
    }

    @Test fun `recentEntries mixes aktivitet and screening types`() = runTest {
        allFlow.value = listOf(
            aktivitet(id = "s1", type = "screening"),
            aktivitet(id = "a1", type = "aktivitet"),
        )
        viewModel.recentEntries.test {
            assertEquals(listOf("s1", "a1"), awaitItem().map { it.id })
        }
    }

    @Test fun `recentEntries updates reactively when the underlying data changes`() = runTest {
        viewModel.recentEntries.test {
            assertEquals(emptyList<Aktivitet>(), awaitItem())
            allFlow.value = listOf(aktivitet(id = "a1"))
            assertEquals(listOf("a1"), awaitItem().map { it.id })
        }
    }

    // ─── noteMap ──────────────────────────────────────────────────────────────

    @Test fun `noteMap combines ACTIVITY and SCREENING note maps`() = runTest {
        every { noteRepo.observeMap(NoteTarget.ACTIVITY) } returns flowOf(mapOf("a1" to "Regnigt"))
        every { noteRepo.observeMap(NoteTarget.SCREENING) } returns flowOf(mapOf("s1" to "Yr"))
        val vm = AktiviteterViewModel(repo, noteRepo, prefs)
        vm.noteMap.test {
            assertEquals(mapOf("a1" to "Regnigt", "s1" to "Yr"), awaitItem())
        }
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

    @Test fun `save sets aktivitet snackbar containing name when type is aktivitet`() = runTest {
        viewModel.updateForm { copy(aktivitet = "Promenad", type = "aktivitet") }
        viewModel.save {}
        val msg = viewModel.snackbar.value
        assertNotNull(msg)
        assertTrue("Expected snackbar to contain activity name, got: $msg", msg!!.contains("Promenad"))
    }

    @Test fun `save resets form after saving`() = runTest {
        viewModel.updateForm { copy(aktivitet = "Promenad") }
        viewModel.save {}
        assertEquals("", viewModel.form.value.aktivitet)
    }

    // ─── note ─────────────────────────────────────────────────────────────────

    @Test fun `save persists note under SCREENING target for screening entries`() = runTest {
        viewModel.updateForm { copy(aktivitet = "Morgonscreening", type = "screening", note = "Kände mig yr") }
        viewModel.save {}
        coVerify { noteRepo.save(NoteTarget.SCREENING, any(), "Kände mig yr") }
    }

    @Test fun `save persists note under ACTIVITY target for aktivitet entries`() = runTest {
        viewModel.updateForm { copy(aktivitet = "Promenad", type = "aktivitet", note = "Regnigt väder") }
        viewModel.save {}
        coVerify { noteRepo.save(NoteTarget.ACTIVITY, any(), "Regnigt väder") }
    }

    @Test fun `loadForEdit populates form note from noteRepo`() = runTest {
        val a = aktivitet(id = "a1", type = "screening")
        coEvery { repo.getById("a1") } returns a
        every { noteRepo.observe(NoteTarget.SCREENING, "a1") } returns flowOf("Befintlig anteckning")
        viewModel.loadForEdit("a1")
        assertEquals("Befintlig anteckning", viewModel.form.value.note)
    }

    @Test fun `delete removes note under matching target`() = runTest {
        val a = aktivitet(id = "a1", aktivitet = "Promenad", type = "aktivitet")
        viewModel.delete(a)
        coVerify { noteRepo.delete(NoteTarget.ACTIVITY, "a1") }
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
            viewModel.updateForm { copy(aktivitet = "Promenad") }
            assertEquals(true, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test fun `isDirty is false right after loadForEdit`() = runTest {
        coEvery { repo.getById("a1") } returns aktivitet(id = "a1")
        viewModel.isDirty.test {
            assertEquals(false, awaitItem())
            viewModel.loadForEdit("a1")
            expectNoEvents()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test fun `isDirty is false right after prefillNewAktivitet`() = runTest {
        allFlow.value = listOf(aktivitet(id = "a1", aktivitet = "Löpning"))
        viewModel.isDirty.test {
            assertEquals(false, awaitItem())
            viewModel.prefillNewAktivitet()
            expectNoEvents()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test fun `isDirty is false again after save resets the form`() = runTest {
        viewModel.isDirty.test {
            assertEquals(false, awaitItem())
            viewModel.updateForm { copy(aktivitet = "Promenad") }
            assertEquals(true, awaitItem())
            viewModel.save {}
            assertEquals(false, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ─── startScreening (inline-screening på Idag) ───────────────────────────

    @Test fun `startScreening resets form and dirty-state atomically`() = runTest {
        viewModel.updateForm { copy(aktivitet = "Gammal", energy = 4) }
        viewModel.isDirty.test {
            assertEquals(true, awaitItem())
            viewModel.startScreening("Morgon")
            expectNoEvents()
            cancelAndIgnoreRemainingEvents()
        }
        assertEquals("Morgon", viewModel.form.value.aktivitet)
        assertEquals("screening", viewModel.form.value.type)
        assertEquals(0, viewModel.form.value.energy)
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
