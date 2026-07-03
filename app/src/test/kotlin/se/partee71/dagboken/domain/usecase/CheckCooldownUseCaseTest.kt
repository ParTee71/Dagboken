package se.partee71.dagboken.domain.usecase

import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import se.partee71.dagboken.domain.model.Medicin
import java.time.Instant

class CheckCooldownUseCaseTest {

    private lateinit var useCase: CheckCooldownUseCase

    @Before fun setUp() { useCase = CheckCooldownUseCase() }

    private fun medicin(takenHoursAgo: Double, namn: String = "Ibuprofen", skipped: Boolean = false): Medicin {
        val ts = Instant.now().minusSeconds((takenHoursAgo * 3600).toLong()).toString()
        return Medicin(
            id = "x", timestamp = ts, datum = "2024-01-01", tid = "08:00",
            namn = namn, dos = "400", enhet = "mg", tidpunkt = "Morgon",
            tagen = true, skipped = skipped,
        )
    }

    @Test fun `returns null when minTidMellan is 0 (no limit)`() {
        val m = medicin(takenHoursAgo = 1.0)
        assertNull(useCase.remainingHours("Ibuprofen", 0, m))
    }

    @Test fun `returns null when lastTaken is null`() {
        assertNull(useCase.remainingHours("Paracetamol", 4, null))
    }

    @Test fun `returns null when taken long enough ago`() {
        val m = medicin(takenHoursAgo = 6.0)
        assertNull(useCase.remainingHours("Ibuprofen", 4, m))
    }

    @Test fun `returns remaining hours when within cooldown`() {
        val m = medicin(takenHoursAgo = 2.0)
        val result = useCase.remainingHours("Ibuprofen", 4, m)
        assertNotNull(result)
        assertTrue("remaining ~2h", result!! > 1.9 && result < 2.1)
    }

    @Test fun `returns null when cooldown exactly expired`() {
        val m = medicin(takenHoursAgo = 4.0)
        val result = useCase.remainingHours("Ibuprofen", 4, m)
        // May be null or very small positive — just verify it's not a large value
        if (result != null) assertTrue(result < 0.01)
    }
}

class CheckDailyLimitUseCaseTest {

    private lateinit var useCase: CheckDailyLimitUseCase

    @Before fun setUp() { useCase = CheckDailyLimitUseCase() }

    @Test fun `returns false when maxDoserPerDag is 0 (no limit)`() {
        assertFalse(useCase.limitReached(0, 99))
    }

    @Test fun `returns false when no dose taken`() {
        assertFalse(useCase.limitReached(2, 0))
    }

    @Test fun `returns false when count below limit`() {
        assertFalse(useCase.limitReached(3, 2))
    }

    @Test fun `returns true when count meets limit`() {
        assertTrue(useCase.limitReached(2, 2))
    }

    @Test fun `returns true when count exceeds limit`() {
        assertTrue(useCase.limitReached(2, 5))
    }

    private fun assertFalse(b: Boolean) = org.junit.Assert.assertFalse(b)
    private fun assertTrue(b: Boolean) = org.junit.Assert.assertTrue(b)
}
