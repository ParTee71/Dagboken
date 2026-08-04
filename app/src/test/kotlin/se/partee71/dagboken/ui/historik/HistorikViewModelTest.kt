package se.partee71.dagboken.ui.historik

import app.cash.turbine.test
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import se.partee71.dagboken.data.repository.AktiviteterRepository
import se.partee71.dagboken.data.repository.HandelserRepository
import se.partee71.dagboken.data.repository.MedicinerRepository
import se.partee71.dagboken.data.repository.SjukdomarRepository
import se.partee71.dagboken.domain.model.Aktivitet
import se.partee71.dagboken.domain.model.Handelse
import se.partee71.dagboken.domain.model.Medicin
import se.partee71.dagboken.domain.model.SjukdomsEpisod
import se.partee71.dagboken.domain.model.SjukdomsIncheckning
import java.time.LocalDate

@OptIn(ExperimentalCoroutinesApi::class)
class HistorikViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    private lateinit var aktiviteterRepo: AktiviteterRepository
    private lateinit var medicinerRepo: MedicinerRepository
    private lateinit var handelserRepo: HandelserRepository
    private lateinit var sjukdomarRepo: SjukdomarRepository

    private val aktiviteterFlow = MutableStateFlow<List<Aktivitet>>(emptyList())
    private val medicinerFlow = MutableStateFlow<List<Medicin>>(emptyList())
    private val handelserFlow = MutableStateFlow<List<Handelse>>(emptyList())
    private val incheckningarFlow = MutableStateFlow<List<SjukdomsIncheckning>>(emptyList())
    private val episoderFlow = MutableStateFlow<List<SjukdomsEpisod>>(emptyList())

    private lateinit var viewModel: HistorikViewModel

    @Before fun setUp() {
        Dispatchers.setMain(testDispatcher)
        aktiviteterRepo = mockk(relaxed = true) { every { all } returns aktiviteterFlow }
        medicinerRepo = mockk(relaxed = true) { every { takenMediciner } returns medicinerFlow }
        handelserRepo = mockk(relaxed = true) { every { all } returns handelserFlow }
        sjukdomarRepo = mockk(relaxed = true) {
            every { allIncheckningar } returns incheckningarFlow
            every { all } returns episoderFlow
        }
        viewModel = HistorikViewModel(aktiviteterRepo, medicinerRepo, handelserRepo, sjukdomarRepo)
    }

    @After fun tearDown() { Dispatchers.resetMain() }

    /** Fast referenspunkt inom historikens fönster, oavsett när testet körs. */
    private val idag = LocalDate.now().toString()

    private fun aktivitet(id: String, type: String = "aktivitet", datum: String = idag, tid: String = "08:00") =
        Aktivitet(
            id = id, timestamp = "x", datum = datum, tid = tid, aktivitet = "Promenad",
            energy = 3, stress = 2, somatiska = 0, symptom = "", type = type,
        )

    private fun medicin(id: String, datum: String = idag, tid: String = "09:00", tagenTid: String? = null) = Medicin(
        id = id, timestamp = "x", datum = datum, tid = tid, namn = "Ibuprofen",
        dos = "400", enhet = "mg", tidpunkt = "Morgon", tagen = true, tagenTid = tagenTid,
    )

    private fun handelse(id: String, datum: String = idag, tid: String = "10:00") = Handelse(
        id = id, timestamp = "x", datum = datum, tid = tid, typ = "Migrän",
        svarighetsgrad = 5, varaktighetMinuter = 30, triggers = "", atgarder = "",
    )

    private fun incheckning(id: String, episodId: String = "ep1", datum: String = idag, tid: String = "11:00") =
        SjukdomsIncheckning(
            id = id, episodId = episodId, datum = datum, tid = tid,
            svarighetsgrad = 4, symptom = "", somatiska = 0,
        )

    private fun episod(id: String = "ep1", typ: String = "Förkylning") = SjukdomsEpisod(
        id = id, typ = typ, startDatum = idag, slutDatum = "",
    )

    // ─── Sammanslagning ───────────────────────────────────────────────────────

    @Test fun `filteredEntries merges all four sources`() = runTest {
        aktiviteterFlow.value = listOf(aktivitet("a1"))
        medicinerFlow.value = listOf(medicin("m1"))
        handelserFlow.value = listOf(handelse("h1"))
        incheckningarFlow.value = listOf(incheckning("i1"))
        episoderFlow.value = listOf(episod())

        viewModel.filteredEntries.test {
            val entries = awaitItem()
            assertEquals(4, entries.size)
            assertTrue(entries.any { it.id == "a1" })
            assertTrue(entries.any { it.id == "m1" })
            assertTrue(entries.any { it.id == "h1" })
            assertTrue(entries.any { it.id == "i1" })
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test fun `IncheckningEntry carries the parent episode's typ`() = runTest {
        incheckningarFlow.value = listOf(incheckning("i1", episodId = "ep1"))
        episoderFlow.value = listOf(episod(id = "ep1", typ = "Migrän"))

        viewModel.filteredEntries.test {
            val entry = awaitItem().first() as HistorikEntry.IncheckningEntry
            assertEquals("Migrän", entry.episodTyp)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test fun `aktivitet with type screening is classified as HistorikType SCREENING`() = runTest {
        aktiviteterFlow.value = listOf(aktivitet("s1", type = "screening"))

        viewModel.filteredEntries.test {
            assertEquals(HistorikType.SCREENING, awaitItem().first().entryType)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test fun `aktivitet with type aktivitet is classified as HistorikType AKTIVITET`() = runTest {
        aktiviteterFlow.value = listOf(aktivitet("a1", type = "aktivitet"))

        viewModel.filteredEntries.test {
            assertEquals(HistorikType.AKTIVITET, awaitItem().first().entryType)
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ─── MedicinEntry.tid (HIST-7 / MED-14 — tagningstid, inte schemalagd tid) ─

    @Test fun `MedicinEntry tid shows the tagenTid when set`() = runTest {
        medicinerFlow.value = listOf(medicin("m1", tid = "22:00", tagenTid = "21:15"))

        viewModel.filteredEntries.test {
            val entry = awaitItem().first() as HistorikEntry.MedicinEntry
            assertEquals("21:15", entry.tid)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test fun `MedicinEntry tid falls back to the scheduled tid when tagenTid is absent`() = runTest {
        medicinerFlow.value = listOf(medicin("m1", tid = "22:00", tagenTid = null))

        viewModel.filteredEntries.test {
            val entry = awaitItem().first() as HistorikEntry.MedicinEntry
            assertEquals("22:00", entry.tid)
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ─── Filter ───────────────────────────────────────────────────────────────

    @Test fun `toggleFilter removes a type from the active filter`() = runTest {
        aktiviteterFlow.value = listOf(aktivitet("a1", type = "aktivitet"))
        medicinerFlow.value = listOf(medicin("m1"))

        viewModel.toggleFilter(HistorikType.AKTIVITET)

        viewModel.filteredEntries.test {
            val entries = awaitItem()
            assertTrue(entries.none { it.entryType == HistorikType.AKTIVITET })
            assertTrue(entries.any { it.entryType == HistorikType.MEDICIN })
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test fun `toggleFilter does not remove the last active type`() = runTest {
        HistorikType.entries.forEach {
            if (it != HistorikType.MEDICIN) viewModel.toggleFilter(it)
        }
        assertEquals(setOf(HistorikType.MEDICIN), viewModel.typeFilter.value)

        viewModel.toggleFilter(HistorikType.MEDICIN)

        assertEquals(setOf(HistorikType.MEDICIN), viewModel.typeFilter.value)
    }

    @Test fun `initial filter includes all types`() {
        assertEquals(HistorikType.entries.toSet(), viewModel.typeFilter.value)
    }

    // ─── Radera ───────────────────────────────────────────────────────────────

    @Test fun `delete on AktivitetEntry calls AktiviteterRepository delete`() = runTest {
        val a = aktivitet("a1")
        viewModel.delete(HistorikEntry.AktivitetEntry(a))
        coVerify { aktiviteterRepo.delete(a) }
    }

    @Test fun `delete on MedicinEntry calls MedicinerRepository deleteMedicin`() = runTest {
        val m = medicin("m1")
        viewModel.delete(HistorikEntry.MedicinEntry(m))
        coVerify { medicinerRepo.deleteMedicin(m) }
    }

    @Test fun `delete on HandelseEntry calls HandelserRepository delete`() = runTest {
        val h = handelse("h1")
        viewModel.delete(HistorikEntry.HandelseEntry(h))
        coVerify { handelserRepo.delete(h) }
    }

    @Test fun `delete on IncheckningEntry calls SjukdomarRepository deleteIncheckning`() = runTest {
        val i = incheckning("i1")
        viewModel.delete(HistorikEntry.IncheckningEntry(i, episodTyp = "Migrän"))
        coVerify { sjukdomarRepo.deleteIncheckning(i) }
    }

    // ─── Vy-läge / kalender (HIST-6) ────────────────────────────────────────────

    @Test fun `initial view mode is LISTA with no selected date`() {
        assertEquals(HistorikViewMode.LISTA, viewModel.viewMode.value)
        assertEquals(null, viewModel.selectedDate.value)
    }

    @Test fun `setViewMode switches to KALENDER`() {
        viewModel.setViewMode(HistorikViewMode.KALENDER)
        assertEquals(HistorikViewMode.KALENDER, viewModel.viewMode.value)
    }

    @Test fun `setViewMode back to LISTA clears the selected date`() {
        viewModel.setViewMode(HistorikViewMode.KALENDER)
        viewModel.selectDate(LocalDate.of(2026, 1, 1))
        assertEquals(LocalDate.of(2026, 1, 1), viewModel.selectedDate.value)

        viewModel.setViewMode(HistorikViewMode.LISTA)

        assertEquals(null, viewModel.selectedDate.value)
    }

    @Test fun `selectDate sets the selected date`() {
        val date = LocalDate.of(2026, 3, 15)
        viewModel.selectDate(date)
        assertEquals(date, viewModel.selectedDate.value)
    }

    @Test fun `selectDate on an already-selected date deselects it`() {
        val date = LocalDate.of(2026, 3, 15)
        viewModel.selectDate(date)
        viewModel.selectDate(date)
        assertEquals(null, viewModel.selectedDate.value)
    }

    // ─── Fönster bakåt (HIST-9) ─────────────────────────────────────────────────
    // Historiken läste tidigare in hela databasen utan gräns; nu läses ett fönster
    // bakåt som användaren kan utöka.

    @Test fun `entries inside the default window are included`() = runTest {
        aktiviteterFlow.value = listOf(aktivitet("a1", datum = LocalDate.now().minusDays(30).toString()))

        viewModel.filteredEntries.test {
            assertEquals(1, awaitItem().size)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test fun `entries older than the window are excluded`() = runTest {
        aktiviteterFlow.value = listOf(
            aktivitet("nyligen", datum = LocalDate.now().minusDays(10).toString()),
            aktivitet("gammal", datum = LocalDate.now().minusYears(3).toString()),
        )

        viewModel.filteredEntries.test {
            val ids = awaitItem().map { it.id }
            assertTrue("nyligen" in ids)
            assertTrue("gammal" !in ids)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test fun `loadMore widens the window so older entries appear`() = runTest {
        aktiviteterFlow.value = listOf(aktivitet("gammal", datum = LocalDate.now().minusDays(400).toString()))

        viewModel.filteredEntries.test {
            assertEquals(0, awaitItem().size)

            viewModel.loadMore()

            assertEquals(1, awaitItem().size)
            cancelAndIgnoreRemainingEvents()
        }
    }
}
