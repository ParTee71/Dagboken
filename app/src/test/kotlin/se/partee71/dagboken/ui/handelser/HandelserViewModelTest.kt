package se.partee71.dagboken.ui.handelser

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
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import se.partee71.dagboken.data.datastore.PreferencesRepository
import se.partee71.dagboken.data.datastore.SymptomOption
import se.partee71.dagboken.data.repository.HandelserRepository
import se.partee71.dagboken.data.repository.NoteRepository
import se.partee71.dagboken.domain.Timestamps
import se.partee71.dagboken.domain.model.Handelse
import se.partee71.dagboken.domain.model.NoteTarget
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalCoroutinesApi::class)
class HandelserViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    private lateinit var repo: HandelserRepository
    private lateinit var noteRepo: NoteRepository
    private lateinit var prefs: PreferencesRepository
    private lateinit var viewModel: HandelserViewModel

    private val handelseTypOptionsFlow = MutableStateFlow(listOf(SymptomOption("Yrsel")))

    @Before fun setUp() {
        Dispatchers.setMain(testDispatcher)
        repo = mockk(relaxed = true) {
            every { all } returns flowOf(emptyList())
        }
        noteRepo = mockk(relaxed = true) {
            every { observe(any(), any()) } returns flowOf("")
            every { observeMap(any()) } returns flowOf(emptyMap())
        }
        prefs = mockk(relaxed = true) {
            every { handelseTypOptions } returns handelseTypOptionsFlow
        }
        viewModel = HandelserViewModel(repo, noteRepo, prefs)
    }

    @After fun tearDown() { Dispatchers.resetMain() }

    private fun handelse(
        id: String = "h1",
        datum: String = "2026-06-21",
        tid: String = "10:00",
        typ: String = "Yrsel",
        svarighetsgrad: Int = 5,
        varaktighetMinuter: Int = 30,
        triggers: String = "",
        atgarder: String = "",
    ) = Handelse(
        id = id, timestamp = "${datum}T${tid}:00.000Z",
        datum = datum, tid = tid, typ = typ,
        svarighetsgrad = svarighetsgrad, varaktighetMinuter = varaktighetMinuter,
        triggers = triggers, atgarder = atgarder,
    )

    // ─── form defaults ────────────────────────────────────────────────────────

    @Test fun `initial form has blank typ`() {
        assertEquals("", viewModel.form.value.typ)
    }

    @Test fun `initial form svarighetsgrad is 5`() {
        assertEquals(5, viewModel.form.value.svarighetsgrad)
    }

    @Test fun `initial form duration is zero`() {
        assertEquals(0, viewModel.form.value.varaktighetTimmar)
        assertEquals(0, viewModel.form.value.varaktighetMinuter)
    }

    // ─── updateForm ───────────────────────────────────────────────────────────

    @Test fun `updateForm changes the specified field`() {
        viewModel.updateForm { copy(typ = "Blodtrycksfall") }
        assertEquals("Blodtrycksfall", viewModel.form.value.typ)
    }

    // ─── save – guard ─────────────────────────────────────────────────────────

    @Test fun `save does nothing when typ is blank`() = runTest {
        viewModel.updateForm { copy(typ = "") }
        var called = false
        viewModel.save { called = true }
        assertFalse(called)
        coVerify(exactly = 0) { repo.save(any()) }
    }

    @Test fun `save does nothing when typ is whitespace only`() = runTest {
        viewModel.updateForm { copy(typ = "   ") }
        var called = false
        viewModel.save { called = true }
        assertFalse(called)
    }

    // ─── save – happy path ────────────────────────────────────────────────────

    @Test fun `save calls repo and invokes onDone`() = runTest {
        viewModel.updateForm { copy(typ = "Yrsel") }
        var called = false
        viewModel.save { called = true }
        assertTrue(called)
        coVerify { repo.save(any()) }
    }

    @Test fun `save stores combined hours and minutes as total minutes`() = runTest {
        viewModel.updateForm { copy(typ = "Yrsel", varaktighetTimmar = 1, varaktighetMinuter = 30) }
        val saved = slot<Handelse>()
        coEvery { repo.save(capture(saved)) } returns Unit
        viewModel.save {}
        assertEquals(90, saved.captured.varaktighetMinuter)
    }

    @Test fun `save builds ISO timestamp from datum and tid`() = runTest {
        viewModel.updateForm { copy(typ = "Yrsel", datum = "2026-06-21", tid = "14:30") }
        val saved = slot<Handelse>()
        coEvery { repo.save(capture(saved)) } returns Unit
        viewModel.save {}
        assertEquals(Timestamps.of("2026-06-21", "14:30"), saved.captured.timestamp)
    }

    @Test fun `save resets form to defaults after saving`() = runTest {
        viewModel.updateForm { copy(typ = "Yrsel", svarighetsgrad = 9, triggers = "stress") }
        viewModel.save {}
        assertEquals("", viewModel.form.value.typ)
        assertEquals(5, viewModel.form.value.svarighetsgrad)
        assertEquals("", viewModel.form.value.triggers)
    }

    @Test fun `save preserves existing id when in edit mode`() = runTest {
        coEvery { repo.getById("h1") } returns handelse(id = "h1")
        viewModel.loadForEdit("h1")
        viewModel.updateForm { copy(typ = "Yrsel") }
        val saved = slot<Handelse>()
        coEvery { repo.save(capture(saved)) } returns Unit
        viewModel.save {}
        assertEquals("h1", saved.captured.id)
    }

    // ─── loadForEdit ──────────────────────────────────────────────────────────

    @Test fun `loadForEdit populates all form fields from domain model`() = runTest {
        coEvery { repo.getById("h1") } returns handelse(
            id = "h1", typ = "Blodtrycksfall", datum = "2026-06-20", tid = "09:30",
            svarighetsgrad = 7, varaktighetMinuter = 90,
            triggers = "stress", atgarder = "vila",
        )
        every { noteRepo.observe(NoteTarget.EVENT, "h1") } returns flowOf("notering")
        viewModel.loadForEdit("h1")
        val f = viewModel.form.value
        assertEquals("Blodtrycksfall", f.typ)
        assertEquals("2026-06-20", f.datum)
        assertEquals("09:30", f.tid)
        assertEquals(7, f.svarighetsgrad)
        assertEquals(1, f.varaktighetTimmar)   // 90 / 60
        assertEquals(30, f.varaktighetMinuter)  // 90 % 60
        assertEquals("stress", f.triggers)
        assertEquals("vila", f.atgarder)
        assertEquals("notering", f.anteckning)
    }

    // ─── anteckning ───────────────────────────────────────────────────────────

    @Test fun `save persists the note under EVENT target`() = runTest {
        viewModel.updateForm { copy(typ = "Yrsel", anteckning = "Kom efter möte") }
        viewModel.save {}
        coVerify { noteRepo.save(NoteTarget.EVENT, any(), "Kom efter möte") }
    }

    @Test fun `delete also deletes the note under EVENT target`() = runTest {
        val h = handelse(id = "h1")
        viewModel.delete(h)
        coVerify { noteRepo.delete(NoteTarget.EVENT, "h1") }
    }

    @Test fun `loadForEdit with zero duration sets both wheels to zero`() = runTest {
        coEvery { repo.getById("h1") } returns handelse(id = "h1", varaktighetMinuter = 0)
        viewModel.loadForEdit("h1")
        assertEquals(0, viewModel.form.value.varaktighetTimmar)
        assertEquals(0, viewModel.form.value.varaktighetMinuter)
    }

    @Test fun `loadForEdit with unknown id leaves form unchanged`() = runTest {
        coEvery { repo.getById(any()) } returns null
        val formBefore = viewModel.form.value
        viewModel.loadForEdit("nonexistent")
        assertEquals(formBefore, viewModel.form.value)
    }

    // ─── delete ───────────────────────────────────────────────────────────────

    @Test fun `delete calls repo with the given handelse`() = runTest {
        val h = handelse(typ = "Yrsel")
        viewModel.delete(h)
        coVerify { repo.delete(h) }
    }

    @Test fun `delete sets snackbar to typ plus borttagen`() = runTest {
        viewModel.delete(handelse(typ = "Hjärtklappning"))
        assertEquals("Hjärtklappning borttagen", viewModel.snackbar.value)
    }

    // ─── snackbar ─────────────────────────────────────────────────────────────

    @Test fun `snackbar is null initially`() {
        assertNull(viewModel.snackbar.value)
    }

    @Test fun `clearSnackbar nulls the message`() = runTest {
        viewModel.delete(handelse())
        viewModel.clearSnackbar()
        assertNull(viewModel.snackbar.value)
    }

    // ─── resetForm ────────────────────────────────────────────────────────────

    @Test fun `resetForm clears all form fields to defaults`() {
        viewModel.updateForm { copy(typ = "Yrsel", svarighetsgrad = 9, triggers = "kyla") }
        viewModel.resetForm()
        val f = viewModel.form.value
        assertEquals("", f.typ)
        assertEquals(5, f.svarighetsgrad)
        assertEquals("", f.triggers)
    }

    // ─── handelseTypOptions ───────────────────────────────────────────────────

    @Test fun `handelseTypOptions reflects prefs`() = runTest {
        assertEquals(listOf(SymptomOption("Yrsel")), viewModel.handelseTypOptions.value)
    }

    @Test fun `toggleHandelseTypFavorite delegates to prefs setHandelseTypOptions`() = runTest {
        viewModel.toggleHandelseTypFavorite("Yrsel")
        val saved = slot<List<SymptomOption>>()
        coVerify { prefs.setHandelseTypOptions(capture(saved)) }
        assertTrue(saved.captured.single { it.name == "Yrsel" }.isFavorite)
    }

    // ─── typPickerOptions ─────────────────────────────────────────────────────

    @Test fun `typPickerOptions splits favorites and non-favorites`() = runTest {
        val prefsWithOptions = mockk<PreferencesRepository>(relaxed = true) {
            every { handelseTypOptions } returns MutableStateFlow(listOf(
                SymptomOption("Yrsel", isFavorite = true),
                SymptomOption("Andnöd", isFavorite = false),
            ))
        }
        val vm = HandelserViewModel(mockk(relaxed = true) { every { all } returns flowOf(emptyList()) }, noteRepo, prefsWithOptions)
        vm.typPickerOptions.onEach { }.launchIn(backgroundScope)
        advanceUntilIdle()

        assertEquals(listOf("Yrsel"), vm.typPickerOptions.value.favorites)
        assertEquals(listOf("Andnöd"), vm.typPickerOptions.value.nonFavorites)
    }

    @Test fun `typPickerOptions includes custom db types not in the managed list`() = runTest {
        val prefsWithOptions = mockk<PreferencesRepository>(relaxed = true) {
            every { handelseTypOptions } returns MutableStateFlow(listOf(SymptomOption("Yrsel", isFavorite = true)))
        }
        val vm = HandelserViewModel(mockk(relaxed = true) {
            every { all } returns flowOf(listOf(handelse(id = "h1", typ = "Egen typ")))
        }, noteRepo, prefsWithOptions)
        vm.typPickerOptions.onEach { }.launchIn(backgroundScope)
        advanceUntilIdle()

        assertEquals(listOf("Egen typ"), vm.typPickerOptions.value.nonFavorites)
    }

    @Test fun `typPickerOptions does not duplicate managed types already logged in the db`() = runTest {
        val prefsWithOptions = mockk<PreferencesRepository>(relaxed = true) {
            every { handelseTypOptions } returns MutableStateFlow(listOf(SymptomOption("Yrsel", isFavorite = true)))
        }
        val vm = HandelserViewModel(mockk(relaxed = true) {
            every { all } returns flowOf(listOf(handelse(id = "h1", typ = "Yrsel")))
        }, noteRepo, prefsWithOptions)
        vm.typPickerOptions.onEach { }.launchIn(backgroundScope)
        advanceUntilIdle()

        assertEquals(1, vm.typPickerOptions.value.favorites.count { it == "Yrsel" })
        assertTrue(vm.typPickerOptions.value.nonFavorites.none { it == "Yrsel" })
    }

    // ─── filter setters ───────────────────────────────────────────────────────

    @Test fun `setDagFilter updates dagFilter in state`() = runTest {
        viewModel.state.test {
            awaitItem() // initial
            viewModel.setDagFilter(7)
            assertEquals(7, awaitItem().dagFilter)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test fun `setDagFilter to null clears the filter`() = runTest {
        viewModel.state.test {
            awaitItem()
            viewModel.setDagFilter(30)
            awaitItem()
            viewModel.setDagFilter(null)
            assertNull(awaitItem().dagFilter)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test fun `setTypFilter updates typFilter in state`() = runTest {
        viewModel.state.test {
            awaitItem()
            viewModel.setTypFilter("Yrsel")
            assertEquals("Yrsel", awaitItem().typFilter)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test fun `setTypFilter to null clears the filter`() = runTest {
        viewModel.state.test {
            awaitItem()
            viewModel.setTypFilter("Yrsel")
            awaitItem()
            viewModel.setTypFilter(null)
            assertNull(awaitItem().typFilter)
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ─── state filtering ──────────────────────────────────────────────────────

    @Test fun `dag filter hides events older than the cutoff`() = runTest {
        val today = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)
        val oldDate = LocalDate.now().minusDays(100).format(DateTimeFormatter.ISO_LOCAL_DATE)
        val vm = HandelserViewModel(mockk(relaxed = true) {
            every { all } returns flowOf(listOf(
                handelse(id = "recent", datum = today),
                handelse(id = "old",    datum = oldDate),
            ))
        }, noteRepo, prefs)
        vm.state.onEach { }.launchIn(backgroundScope)
        advanceUntilIdle()

        vm.setDagFilter(30)
        advanceUntilIdle()

        val filtered = vm.state.value.filteredHandelser
        assertTrue(filtered.any { it.id == "recent" })
        assertTrue(filtered.none { it.id == "old" })
    }

    @Test fun `typ filter shows only matching events`() = runTest {
        val vm = HandelserViewModel(mockk(relaxed = true) {
            every { all } returns flowOf(listOf(
                handelse(id = "h1", typ = "Yrsel"),
                handelse(id = "h2", typ = "Blodtrycksfall"),
            ))
        }, noteRepo, prefs)
        vm.state.onEach { }.launchIn(backgroundScope)
        advanceUntilIdle()

        vm.setTypFilter("Yrsel")
        advanceUntilIdle()

        val filtered = vm.state.value.filteredHandelser
        assertEquals(1, filtered.size)
        assertEquals("Yrsel", filtered.single().typ)
    }

    @Test fun `allTyper contains distinct types in alphabetical order`() = runTest {
        val vm = HandelserViewModel(mockk(relaxed = true) {
            every { all } returns flowOf(listOf(
                handelse(id = "h1", typ = "Yrsel"),
                handelse(id = "h2", typ = "Blodtrycksfall"),
                handelse(id = "h3", typ = "Yrsel"),
            ))
        }, noteRepo, prefs)
        vm.state.onEach { }.launchIn(backgroundScope)
        advanceUntilIdle()

        assertEquals(listOf("Blodtrycksfall", "Yrsel"), vm.state.value.allTyper)
    }
}
