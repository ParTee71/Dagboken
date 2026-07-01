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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import se.partee71.dagboken.data.repository.MedicinerRepository
import se.partee71.dagboken.domain.model.Medicin

@OptIn(ExperimentalCoroutinesApi::class)
class AddEditMedicinViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private lateinit var repo: MedicinerRepository
    private lateinit var viewModel: AddEditMedicinViewModel

    @Before fun setUp() {
        Dispatchers.setMain(testDispatcher)
        repo = mockk(relaxed = true)
        viewModel = AddEditMedicinViewModel(repo)
    }

    @After fun tearDown() { Dispatchers.resetMain() }

    private fun medicin(
        id: String = "m1",
        namn: String = "Ibuprofen",
        receptId: String? = "r1",
    ) = Medicin(
        id = id, timestamp = "2026-01-15T09:00:00.000Z", datum = "2026-01-15", tid = "09:00",
        namn = namn, dos = "400", enhet = "mg", tidpunkt = "Morgon",
        tagen = true, anteckning = "Gammal anteckning", receptId = receptId, skipped = false,
    )

    // ─── Ny medicinlogg ───────────────────────────────────────────────────────

    @Test fun `save without loadForEdit creates a new untaken entry with ISO timestamp`() = runTest {
        viewModel.updateForm { copy(namn = "Aspirin", dos = "500", enhet = "mg", tidpunkt = "Kväll") }
        viewModel.save()

        val slot = slot<Medicin>()
        coVerify { repo.saveMedicin(capture(slot)) }
        val saved = slot.captured
        assertEquals("Aspirin", saved.namn)
        assertEquals(false, saved.tagen)
        assertEquals(null, saved.receptId)
        assertTrue(
            "Expected ISO timestamp matching '${saved.datum}T${saved.tid}:00.000Z' but got ${saved.timestamp}",
            saved.timestamp == "${saved.datum}T${saved.tid}:00.000Z",
        )
    }

    // ─── Redigera befintlig post ──────────────────────────────────────────────

    @Test fun `save after loadForEdit preserves id, datum, tid, tagen, receptId and skipped`() = runTest {
        val original = medicin()
        coEvery { repo.getMedicinById("m1") } returns original

        viewModel.loadForEdit("m1")
        viewModel.updateForm { copy(namn = "Ibuprofen 600", dos = "600") }
        viewModel.save()

        val slot = slot<Medicin>()
        coVerify { repo.saveMedicin(capture(slot)) }
        val saved = slot.captured
        assertEquals("m1", saved.id)
        assertEquals(original.timestamp, saved.timestamp)
        assertEquals(original.datum, saved.datum)
        assertEquals(original.tid, saved.tid)
        assertEquals(original.tagen, saved.tagen)
        assertEquals(original.receptId, saved.receptId)
        assertEquals(original.skipped, saved.skipped)
        assertEquals("Ibuprofen 600", saved.namn)
        assertEquals("600", saved.dos)
    }

    @Test fun `loadForEdit populates form from existing entry`() = runTest {
        coEvery { repo.getMedicinById("m1") } returns medicin(namn = "Paracetamol")

        viewModel.loadForEdit("m1")

        assertEquals("Paracetamol", viewModel.form.value.namn)
        assertEquals("400", viewModel.form.value.dos)
        assertEquals("mg", viewModel.form.value.enhet)
        assertEquals("Morgon", viewModel.form.value.tidpunkt)
        assertEquals("Gammal anteckning", viewModel.form.value.anteckning)
    }
}
