package se.partee71.dagboken.ui.mediciner

import io.mockk.coEvery
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
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test
import se.partee71.dagboken.data.repository.MedicinerRepository
import se.partee71.dagboken.data.repository.NoteRepository
import se.partee71.dagboken.domain.model.Favorit
import se.partee71.dagboken.domain.model.Medicin
import se.partee71.dagboken.domain.usecase.CheckCooldownUseCase
import se.partee71.dagboken.domain.usecase.CheckDailyLimitUseCase

@OptIn(ExperimentalCoroutinesApi::class)
class MedicinerViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    private lateinit var repo: MedicinerRepository
    private val noteRepo = mockk<NoteRepository>(relaxed = true) {
        every { observe(any(), any()) } returns kotlinx.coroutines.flow.flowOf("")
    }
    private val cooldown = mockk<CheckCooldownUseCase>()
    private val limit = mockk<CheckDailyLimitUseCase>()

    private lateinit var viewModel: MedicinerViewModel

    @Before fun setUp() {
        Dispatchers.setMain(testDispatcher)
        repo = mockk(relaxed = true) {
            every { todayFlow() } returns flowOf(emptyList())
            every { allRecept } returns flowOf(emptyList())
            every { allFavoriter } returns flowOf(emptyList())
        }
        every { limit.limitReached(any(), any()) } returns false
        every { cooldown.remainingHours(any(), any(), any()) } returns null
        viewModel = MedicinerViewModel(repo, noteRepo, cooldown, limit)
    }

    @After fun tearDown() { Dispatchers.resetMain() }

    private fun medicin(
        id: String = "m1",
        namn: String = "Ibuprofen",
        receptId: String? = null,
    ) = Medicin(
        id = id, timestamp = "2026-01-15T07:00:00.000Z", datum = "2026-01-15", tid = "07:00",
        namn = namn, dos = "400", enhet = "mg", tidpunkt = "Morgon",
        tagen = false, anteckning = "", receptId = receptId, skipped = false,
    )

    private fun favorit(
        namn: String = "Paracetamol",
        maxDoserPerDag: Int = 0,
        minTidMellan: Int = 0,
    ) = Favorit(
        id = "f1", namn = namn, dos = "500", enhet = "mg", tidpunkt = "Vid behov",
        anteckning = "", minTidMellan = minTidMellan, maxDoserPerDag = maxDoserPerDag,
    )

    // ─── deleteMedicin ────────────────────────────────────────────────────────

    @Test fun `deleteMedicin with receptId marks as skipped and sets snackbar`() = runTest {
        val med = medicin(receptId = "r1")
        viewModel.deleteMedicin(med)
        coVerify { repo.skipMedicin("m1") }
        coVerify(exactly = 0) { repo.deleteMedicin(any()) }
        assertEquals("Ibuprofen markerad som hoppad", viewModel.snackbar.value)
    }

    @Test fun `deleteMedicin without receptId deletes entry and sets snackbar`() = runTest {
        val med = medicin(receptId = null)
        viewModel.deleteMedicin(med)
        coVerify { repo.deleteMedicin(med) }
        coVerify(exactly = 0) { repo.skipMedicin(any()) }
        assertEquals("Ibuprofen borttagen", viewModel.snackbar.value)
    }

    // ─── quickDos – daily limit ───────────────────────────────────────────────

    @Test fun `quickDos is blocked when daily limit is reached`() = runTest {
        every { limit.limitReached(3, any()) } returns true
        coEvery { repo.countDailyDoses(any(), any()) } returns 3

        viewModel.quickDos(favorit(maxDoserPerDag = 3))

        val msg = viewModel.snackbar.value
        assertNotNull(msg)
        assertTrue("Expected limit message, got: $msg", msg!!.contains("Max 3"))
        coVerify(exactly = 0) { repo.saveMedicin(any()) }
    }

    // ─── quickDos – cooldown ──────────────────────────────────────────────────

    @Test fun `quickDos sets cooldownWarning when cooldown is active`() = runTest {
        every { cooldown.remainingHours(any(), any(), any()) } returns 2.5
        coEvery { repo.countDailyDoses(any(), any()) } returns 0

        viewModel.quickDos(favorit(minTidMellan = 6))

        val warning = viewModel.cooldownWarning.value
        assertNotNull(warning)
        assertEquals(2.5, warning!!.remainingHours, 0.001)
        assertNull("Snackbar should stay null for cooldown", viewModel.snackbar.value)
        coVerify(exactly = 0) { repo.saveMedicin(any()) }
    }

    @Test fun `quickDos cooldownWarning contains the favorit`() = runTest {
        every { cooldown.remainingHours(any(), any(), any()) } returns 1.5
        coEvery { repo.countDailyDoses(any(), any()) } returns 0
        val fav = favorit(namn = "Ibuprofen", minTidMellan = 6)

        viewModel.quickDos(fav)

        assertEquals("Ibuprofen", viewModel.cooldownWarning.value?.favorit?.namn)
    }

    @Test fun `dismissCooldownWarning clears the warning`() = runTest {
        every { cooldown.remainingHours(any(), any(), any()) } returns 1.0
        coEvery { repo.countDailyDoses(any(), any()) } returns 0
        viewModel.quickDos(favorit(minTidMellan = 4))
        assertNotNull(viewModel.cooldownWarning.value)

        viewModel.dismissCooldownWarning()

        assertNull(viewModel.cooldownWarning.value)
    }

    @Test fun `forceDos saves dose and clears warning`() = runTest {
        every { cooldown.remainingHours(any(), any(), any()) } returns 1.0
        coEvery { repo.countDailyDoses(any(), any()) } returns 0
        coEvery { repo.getLastTaken(any()) } returns null
        val fav = favorit()
        viewModel.quickDos(fav)
        assertNotNull(viewModel.cooldownWarning.value)

        viewModel.forceDos(fav)

        assertNull(viewModel.cooldownWarning.value)
        coVerify { repo.saveMedicin(any()) }
        assertNotNull(viewModel.snackbar.value)
        assertTrue(viewModel.snackbar.value!!.contains("loggad"))
    }

    @Test fun `forceDos still blocks when daily limit is reached`() = runTest {
        every { limit.limitReached(2, any()) } returns true
        coEvery { repo.countDailyDoses(any(), any()) } returns 2

        viewModel.forceDos(favorit(maxDoserPerDag = 2))

        coVerify(exactly = 0) { repo.saveMedicin(any()) }
        assertNotNull(viewModel.snackbar.value)
        assertTrue(viewModel.snackbar.value!!.contains("Max 2"))
    }

    // ─── quickDos – happy path ────────────────────────────────────────────────

    @Test fun `quickDos saves dose and sets snackbar when not blocked`() = runTest {
        coEvery { repo.countDailyDoses(any(), any()) } returns 0
        coEvery { repo.getLastTaken(any()) } returns null

        viewModel.quickDos(favorit())

        coVerify { repo.saveMedicin(any()) }
        assertNotNull(viewModel.snackbar.value)
        assertTrue(viewModel.snackbar.value!!.contains("loggad"))
    }

    @Test fun `quickDos snackbar contains favorit name`() = runTest {
        coEvery { repo.countDailyDoses(any(), any()) } returns 0
        coEvery { repo.getLastTaken(any()) } returns null

        viewModel.quickDos(favorit(namn = "Ipren"))

        assertTrue(viewModel.snackbar.value?.contains("Ipren") == true)
    }

    // ─── clearSnackbar ────────────────────────────────────────────────────────

    @Test fun `clearSnackbar nulls the snackbar`() = runTest {
        viewModel.deleteMedicin(medicin())
        viewModel.clearSnackbar()
        assertNull(viewModel.snackbar.value)
    }

    // ─── logSingleDose ────────────────────────────────────────────────────────

    @Test fun `openSingleDoseDialog sets showSingleDoseDialog true`() {
        viewModel.openSingleDoseDialog()
        assertTrue(viewModel.showSingleDoseDialog.value)
    }

    @Test fun `closeSingleDoseDialog sets showSingleDoseDialog false`() {
        viewModel.openSingleDoseDialog()
        viewModel.closeSingleDoseDialog()
        assertFalse(viewModel.showSingleDoseDialog.value)
    }

    @Test fun `logSingleDose saves medicin and sets snackbar`() = runTest {
        viewModel.logSingleDose("Aspirin", "500", "mg", "14:30")

        coVerify { repo.saveMedicin(any()) }
        assertNotNull(viewModel.snackbar.value)
        assertTrue(viewModel.snackbar.value!!.contains("Aspirin"))
        assertTrue(viewModel.snackbar.value!!.contains("loggad"))
    }

    @Test fun `logSingleDose closes dialog`() = runTest {
        viewModel.openSingleDoseDialog()
        viewModel.logSingleDose("Aspirin", "500", "mg", "14:30")
        assertFalse(viewModel.showSingleDoseDialog.value)
    }

    @Test fun `logSingleDose trims whitespace from name`() = runTest {
        viewModel.logSingleDose("  Ipren  ", "400", "mg", "08:00")
        assertTrue(viewModel.snackbar.value?.contains("Ipren") == true)
        assertFalse(viewModel.snackbar.value?.contains("  ") == true)
    }
}
