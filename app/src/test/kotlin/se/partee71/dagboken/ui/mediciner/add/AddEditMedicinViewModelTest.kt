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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import se.partee71.dagboken.data.repository.MedicinerRepository
import se.partee71.dagboken.data.repository.NoteRepository
import se.partee71.dagboken.domain.Timestamps
import se.partee71.dagboken.domain.model.Favorit
import se.partee71.dagboken.domain.model.Medicin
import se.partee71.dagboken.domain.model.NoteTarget
import se.partee71.dagboken.domain.usecase.CheckCooldownUseCase
import se.partee71.dagboken.domain.usecase.CheckDailyLimitUseCase

@OptIn(ExperimentalCoroutinesApi::class)
class AddEditMedicinViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private lateinit var repo: MedicinerRepository
    private lateinit var noteRepo: NoteRepository
    private lateinit var cooldown: CheckCooldownUseCase
    private lateinit var limit: CheckDailyLimitUseCase
    private lateinit var viewModel: AddEditMedicinViewModel

    @Before fun setUp() {
        Dispatchers.setMain(testDispatcher)
        repo = mockk(relaxed = true)
        noteRepo = mockk(relaxed = true) {
            every { observe(any(), any()) } returns flowOf("")
        }
        cooldown = mockk(relaxed = true) {
            every { remainingHours(any(), any(), any(), any()) } returns null
        }
        limit = mockk(relaxed = true) {
            every { limitReached(any(), any()) } returns false
        }
        viewModel = AddEditMedicinViewModel(repo, noteRepo, cooldown, limit)
    }

    @After fun tearDown() { Dispatchers.resetMain() }

    private fun medicin(
        id: String = "m1",
        namn: String = "Ibuprofen",
        receptId: String? = "r1",
        tagenTid: String? = null,
    ) = Medicin(
        id = id, timestamp = "2026-01-15T09:00:00.000Z", datum = "2026-01-15", tid = "09:00",
        namn = namn, dos = "400", enhet = "mg", tidpunkt = "Morgon",
        tagen = true, receptId = receptId, skipped = false, tagenTid = tagenTid,
    )

    private fun favorit(
        namn: String = "Paracetamol",
        minTidMellan: Int = 0,
        maxDoserPerDag: Int = 0,
    ) = Favorit(
        id = "f1", namn = namn, dos = "500", enhet = "mg", tidpunkt = "Vid behov",
        minTidMellan = minTidMellan, maxDoserPerDag = maxDoserPerDag,
    )

    // ─── Ny medicinlogg (manuell, ingen favorit) ───────────────────────────────

    @Test fun `save without loadForEdit creates a new untaken entry with ISO timestamp`() = runTest {
        viewModel.updateForm { copy(namn = "Aspirin", dos = "500", enhet = "mg", tidpunkt = "Kväll") }
        viewModel.save()

        val slot = slot<Medicin>()
        coVerify { repo.saveMedicin(capture(slot)) }
        val saved = slot.captured
        assertEquals("Aspirin", saved.namn)
        assertEquals(false, saved.tagen)
        assertEquals(null, saved.receptId)
        assertNull(saved.tagenTid)
        val expected = Timestamps.of(saved.datum, saved.tid)
        assertTrue(
            "Expected ISO timestamp '$expected' but got ${saved.timestamp}",
            saved.timestamp == expected,
        )
    }

    // ─── Redigera en receptgenererad dos (MED-15 — bara tagningen, aldrig receptet) ──

    @Test fun `editing a recept dose updates dos, enhet, tagen and tagenTid but preserves namn, tidpunkt, tid and receptId`() = runTest {
        val original = medicin()
        coEvery { repo.getMedicinById("m1") } returns original

        viewModel.loadForEdit("m1")
        // Even though the UI keeps namn/tidpunkt read-only for recept doses, the
        // ViewModel must not trust that — assert it ignores any change to them too.
        viewModel.updateForm { copy(namn = "Ibuprofen 600", tidpunkt = "Kväll", dos = "600", enhet = "st") }
        viewModel.save()

        val slot = slot<Medicin>()
        coVerify { repo.saveMedicin(capture(slot)) }
        val saved = slot.captured
        assertEquals("m1", saved.id)
        assertEquals(original.timestamp, saved.timestamp)
        assertEquals(original.datum, saved.datum)
        assertEquals(original.tid, saved.tid)
        assertEquals(original.receptId, saved.receptId)
        assertEquals(original.skipped, saved.skipped)
        assertEquals("namn must stay the recept's, not the form's edited value", "Ibuprofen", saved.namn)
        assertEquals("tidpunkt slot must stay the recept's", "Morgon", saved.tidpunkt)
        assertEquals("600", saved.dos)
        assertEquals("st", saved.enhet)
    }

    @Test fun `unmarking tagen on a recept dose clears tagenTid`() = runTest {
        coEvery { repo.getMedicinById("m1") } returns medicin(tagenTid = "09:04")

        viewModel.loadForEdit("m1")
        viewModel.updateForm { copy(tagen = false) }
        viewModel.save()

        val slot = slot<Medicin>()
        coVerify { repo.saveMedicin(capture(slot)) }
        assertFalse(slot.captured.tagen)
        assertNull(slot.captured.tagenTid)
    }

    @Test fun `re-marking tagen on a recept dose sets tagenTid from the form time`() = runTest {
        coEvery { repo.getMedicinById("m1") } returns medicin().copy(tagen = false, tagenTid = null)

        viewModel.loadForEdit("m1")
        viewModel.updateForm { copy(tagen = true, tid = "10:30") }
        viewModel.save()

        val slot = slot<Medicin>()
        coVerify { repo.saveMedicin(capture(slot)) }
        assertTrue(slot.captured.tagen)
        assertEquals("10:30", slot.captured.tagenTid)
        assertEquals("the scheduled tid is untouched", "09:00", slot.captured.tid)
    }

    @Test fun `moving a recept dose to another date saves under a new id, keeps receptId, and deletes the original`() = runTest {
        val original = medicin(tagenTid = "09:04")
        coEvery { repo.getMedicinById("m1") } returns original
        every { noteRepo.observe(NoteTarget.MEDICATION, "m1") } returns flowOf("Med mat")

        viewModel.loadForEdit("m1")
        viewModel.updateForm { copy(datum = "2026-01-16") }
        viewModel.save()

        val savedSlot = slot<Medicin>()
        coVerify { repo.saveMedicin(capture(savedSlot)) }
        val saved = savedSlot.captured
        assertTrue("New row must get a fresh id", saved.id != "m1")
        assertEquals("r1", saved.receptId)
        assertEquals("2026-01-16", saved.datum)
        assertEquals("the scheduled tid is carried over unchanged", "09:00", saved.tid)

        coVerify { repo.deleteMedicin(original) }
        coVerify { noteRepo.delete(NoteTarget.MEDICATION, "m1") }
        coVerify { noteRepo.save(NoteTarget.MEDICATION, saved.id, "Med mat") }
    }

    @Test fun `changing only the time (not the date) on a recept dose keeps the same id`() = runTest {
        coEvery { repo.getMedicinById("m1") } returns medicin()

        viewModel.loadForEdit("m1")
        viewModel.updateForm { copy(tid = "09:45") }
        viewModel.save()

        val slot = slot<Medicin>()
        coVerify { repo.saveMedicin(capture(slot)) }
        assertEquals("m1", slot.captured.id)
        coVerify(exactly = 0) { repo.deleteMedicin(any()) }
    }

    // ─── Redigera en vid behov/engångsdos (namn/dos/enhet/datum/tid redigerbara) ──

    @Test fun `editing a non-recept dose updates namn, datum and tid freely`() = runTest {
        coEvery { repo.getMedicinById("m2") } returns medicin(id = "m2", receptId = null, tagenTid = "09:00")

        viewModel.loadForEdit("m2")
        viewModel.updateForm { copy(namn = "Paracetamol", datum = "2026-01-16", tid = "11:15") }
        viewModel.save()

        val slot = slot<Medicin>()
        coVerify { repo.saveMedicin(capture(slot)) }
        val saved = slot.captured
        assertEquals("non-recept doses keep their id even when moved", "m2", saved.id)
        assertEquals("Paracetamol", saved.namn)
        assertEquals("2026-01-16", saved.datum)
        assertEquals("11:15", saved.tid)
        assertEquals("11:15", saved.tagenTid)
    }

    @Test fun `loadForEdit populates form from existing entry`() = runTest {
        coEvery { repo.getMedicinById("m1") } returns medicin(namn = "Paracetamol")
        every { noteRepo.observe(NoteTarget.MEDICATION, "m1") } returns flowOf("Gammal anteckning")

        viewModel.loadForEdit("m1")

        assertEquals("Paracetamol", viewModel.form.value.namn)
        assertEquals("400", viewModel.form.value.dos)
        assertEquals("mg", viewModel.form.value.enhet)
        assertEquals("Morgon", viewModel.form.value.tidpunkt)
        assertEquals("Gammal anteckning", viewModel.form.value.anteckning)
        assertEquals("2026-01-15", viewModel.form.value.datum)
        assertTrue(viewModel.form.value.tagen)
    }

    @Test fun `loadForEdit falls back to the scheduled tid when tagenTid is absent`() = runTest {
        coEvery { repo.getMedicinById("m1") } returns medicin(tagenTid = null)
        viewModel.loadForEdit("m1")
        assertEquals("09:00", viewModel.form.value.tid)
    }

    @Test fun `loadForEdit prefers tagenTid over the scheduled tid when present`() = runTest {
        coEvery { repo.getMedicinById("m1") } returns medicin(tagenTid = "09:07")
        viewModel.loadForEdit("m1")
        assertEquals("09:07", viewModel.form.value.tid)
    }

    @Test fun `isEditingExisting and isFromRecept reflect the loaded entry`() = runTest {
        coEvery { repo.getMedicinById("m1") } returns medicin(receptId = "r1")
        viewModel.loadForEdit("m1")
        assertTrue(viewModel.isEditingExisting())
        assertTrue(viewModel.isFromRecept())
    }

    @Test fun `isFromRecept is false for a non-recept entry`() = runTest {
        coEvery { repo.getMedicinById("m1") } returns medicin(receptId = null)
        viewModel.loadForEdit("m1")
        assertFalse(viewModel.isFromRecept())
    }

    @Test fun `save persists the note under MEDICATION target`() = runTest {
        viewModel.updateForm { copy(namn = "Aspirin", dos = "500", enhet = "mg", tidpunkt = "Kväll", anteckning = "Efter mat") }
        viewModel.save()

        coVerify { noteRepo.save(NoteTarget.MEDICATION, any(), "Efter mat") }
    }

    // ─── loadForFavorit / efterhandsloggning (MED-16) ────────────────────────

    @Test fun `loadForFavorit prefills namn, dos, enhet, tidpunkt and note from the favorit`() = runTest {
        coEvery { repo.getFavoritById("f1") } returns favorit(namn = "Ipren")
        every { noteRepo.observe(NoteTarget.FAVORIT, "f1") } returns flowOf("Ta med mat")

        viewModel.loadForFavorit("f1")

        assertEquals("Ipren", viewModel.form.value.namn)
        assertEquals("500", viewModel.form.value.dos)
        assertEquals("mg", viewModel.form.value.enhet)
        assertEquals("Vid behov", viewModel.form.value.tidpunkt)
        assertEquals("Ta med mat", viewModel.form.value.anteckning)
        assertTrue(viewModel.form.value.tagen)
        assertTrue(viewModel.isFromFavorit())
        assertFalse(viewModel.isEditingExisting())
    }

    @Test fun `save from a favorit prefill creates a taken dose at the chosen datum and tid`() = runTest {
        coEvery { repo.getFavoritById("f1") } returns favorit()
        coEvery { repo.countDailyDoses(any(), any()) } returns 0
        coEvery { repo.getLastTakenBefore(any(), any()) } returns null

        viewModel.loadForFavorit("f1")
        viewModel.updateForm { copy(datum = "2026-01-10", tid = "14:00") }
        viewModel.save()

        val slot = slot<Medicin>()
        coVerify { repo.saveMedicin(capture(slot)) }
        val saved = slot.captured
        assertEquals("2026-01-10", saved.datum)
        assertEquals("14:00", saved.tid)
        assertEquals("14:00", saved.tagenTid)
        assertTrue(saved.tagen)
        assertEquals(null, saved.receptId)
    }

    @Test fun `save from a favorit prefill is blocked by the daily limit and does not save`() = runTest {
        coEvery { repo.getFavoritById("f1") } returns favorit(maxDoserPerDag = 2)
        coEvery { repo.countDailyDoses(any(), any()) } returns 2
        every { limit.limitReached(2, 2) } returns true

        viewModel.loadForFavorit("f1")
        viewModel.save()

        coVerify(exactly = 0) { repo.saveMedicin(any()) }
        assertNotNull(viewModel.blockedMessage.value)
        assertEquals(0, viewModel.saveCompleted.value)
    }

    @Test fun `save from a favorit prefill sets a cooldown warning instead of saving`() = runTest {
        coEvery { repo.getFavoritById("f1") } returns favorit(minTidMellan = 6)
        coEvery { repo.countDailyDoses(any(), any()) } returns 0
        val lastTaken = medicin(receptId = null)
        coEvery { repo.getLastTakenBefore(any(), any()) } returns lastTaken
        every { cooldown.remainingHours(any(), any(), any(), any()) } returns 2.5

        viewModel.loadForFavorit("f1")
        viewModel.save()

        coVerify(exactly = 0) { repo.saveMedicin(any()) }
        assertNotNull(viewModel.cooldownWarning.value)
        assertEquals(2.5, viewModel.cooldownWarning.value?.remainingHours ?: 0.0, 0.001)
    }

    @Test fun `save with force true bypasses the cooldown warning`() = runTest {
        coEvery { repo.getFavoritById("f1") } returns favorit(minTidMellan = 6)
        coEvery { repo.countDailyDoses(any(), any()) } returns 0
        coEvery { repo.getLastTakenBefore(any(), any()) } returns medicin(receptId = null)
        every { cooldown.remainingHours(any(), any(), any(), any()) } returns 2.5

        viewModel.loadForFavorit("f1")
        viewModel.save(force = true)

        coVerify { repo.saveMedicin(any()) }
        assertEquals(1, viewModel.saveCompleted.value)
    }

    @Test fun `dismissCooldownWarning clears the warning`() = runTest {
        coEvery { repo.getFavoritById("f1") } returns favorit(minTidMellan = 6)
        coEvery { repo.countDailyDoses(any(), any()) } returns 0
        coEvery { repo.getLastTakenBefore(any(), any()) } returns medicin(receptId = null)
        every { cooldown.remainingHours(any(), any(), any(), any()) } returns 2.5
        viewModel.loadForFavorit("f1")
        viewModel.save()
        assertNotNull(viewModel.cooldownWarning.value)

        viewModel.dismissCooldownWarning()

        assertNull(viewModel.cooldownWarning.value)
    }

    // ─── saveCompleted (styr navigering, ersätter fire-and-forget för favorit-läget) ─

    @Test fun `saveCompleted increments only on an actual save`() = runTest {
        viewModel.updateForm { copy(namn = "Aspirin") }
        assertEquals(0, viewModel.saveCompleted.value)
        viewModel.save()
        assertEquals(1, viewModel.saveCompleted.value)
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
            viewModel.updateForm { copy(namn = "Aspirin") }
            assertEquals(true, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test fun `isDirty is false right after loadForEdit`() = runTest {
        coEvery { repo.getMedicinById("m1") } returns medicin()
        viewModel.isDirty.test {
            assertEquals(false, awaitItem())
            viewModel.loadForEdit("m1")
            expectNoEvents()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test fun `isDirty is false again after save`() = runTest {
        viewModel.isDirty.test {
            assertEquals(false, awaitItem())
            viewModel.updateForm { copy(namn = "Aspirin") }
            assertEquals(true, awaitItem())
            viewModel.save()
            assertEquals(false, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }
}
