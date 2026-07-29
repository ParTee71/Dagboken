package se.partee71.dagboken.domain.usecase

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import se.partee71.dagboken.data.repository.MedicinerRepository
import se.partee71.dagboken.data.repository.NoteRepository
import se.partee71.dagboken.domain.model.Favorit
import se.partee71.dagboken.domain.model.Medicin
import se.partee71.dagboken.domain.model.NoteTarget

class LogVidBehovDosUseCaseTest {

    private val repo = mockk<MedicinerRepository>(relaxed = true)
    private val noteRepo = mockk<NoteRepository>(relaxed = true) {
        every { observe(NoteTarget.FAVORIT, any()) } returns flowOf("")
    }
    private val cooldown = mockk<CheckCooldownUseCase>()
    private val limit = mockk<CheckDailyLimitUseCase>()

    private lateinit var useCase: LogVidBehovDosUseCase

    private fun favorit(maxDoserPerDag: Int = 0, minTidMellan: Int = 0) = Favorit(
        id = "f1", namn = "Paracetamol", dos = "500", enhet = "mg", tidpunkt = "Vid behov",
        minTidMellan = minTidMellan, maxDoserPerDag = maxDoserPerDag, isFavorite = true,
    )

    @Before fun setUp() {
        useCase = LogVidBehovDosUseCase(repo, noteRepo, cooldown, limit)
        every { limit.limitReached(any(), any()) } returns false
        every { cooldown.remainingHours(any(), any(), any(), any()) } returns null
        coEvery { repo.countDailyDoses(any(), any()) } returns 0
        coEvery { repo.getLastTaken(any()) } returns null
    }

    @Test fun `logDose returns DailyLimitReached without saving when limit is hit`() = runTest {
        every { limit.limitReached(3, any()) } returns true
        coEvery { repo.countDailyDoses(any(), any()) } returns 3

        val result = useCase.logDose(favorit(maxDoserPerDag = 3))

        assertEquals(VidBehovLogResult.DailyLimitReached, result)
        coVerify(exactly = 0) { repo.saveMedicin(any()) }
    }

    @Test fun `logDose returns CooldownWarning without saving when cooldown active`() = runTest {
        every { cooldown.remainingHours(any(), any(), any(), any()) } returns 2.5

        val result = useCase.logDose(favorit(minTidMellan = 6))

        assertEquals(VidBehovLogResult.CooldownWarning(2.5), result)
        coVerify(exactly = 0) { repo.saveMedicin(any()) }
    }

    @Test fun `logDose with force skips cooldown check and saves`() = runTest {
        every { cooldown.remainingHours(any(), any(), any(), any()) } returns 2.5

        val result = useCase.logDose(favorit(minTidMellan = 6), force = true)

        assertEquals(VidBehovLogResult.Logged, result)
        coVerify { repo.saveMedicin(any()) }
    }

    @Test fun `logDose still blocks on daily limit even when forced`() = runTest {
        every { limit.limitReached(2, any()) } returns true
        coEvery { repo.countDailyDoses(any(), any()) } returns 2

        val result = useCase.logDose(favorit(maxDoserPerDag = 2), force = true)

        assertEquals(VidBehovLogResult.DailyLimitReached, result)
        coVerify(exactly = 0) { repo.saveMedicin(any()) }
    }

    @Test fun `logDose saves a taken dose with matching tagenTid`() = runTest {
        val slot = slot<Medicin>()
        coEvery { repo.saveMedicin(capture(slot)) } returns Unit

        val result = useCase.logDose(favorit())

        assertEquals(VidBehovLogResult.Logged, result)
        assertEquals("Paracetamol", slot.captured.namn)
        assertTrue(slot.captured.tagen)
        assertEquals(slot.captured.tid, slot.captured.tagenTid)
    }

    @Test fun `logDose copies the favorit's note onto the logged dose`() = runTest {
        every { noteRepo.observe(NoteTarget.FAVORIT, "f1") } returns flowOf("Ta med mat")

        useCase.logDose(favorit())

        coVerify { noteRepo.save(NoteTarget.MEDICATION, any(), "Ta med mat") }
    }

    @Test fun `logDose does not save a note when the favorit has none`() = runTest {
        useCase.logDose(favorit())

        coVerify(exactly = 0) { noteRepo.save(NoteTarget.MEDICATION, any(), any()) }
    }
}
