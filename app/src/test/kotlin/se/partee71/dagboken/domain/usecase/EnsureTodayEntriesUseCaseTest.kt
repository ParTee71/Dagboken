package se.partee71.dagboken.domain.usecase

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import se.partee71.dagboken.domain.model.Recept
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

class EnsureTodayEntriesUseCaseTest {

    private lateinit var useCase: EnsureTodayEntriesUseCase

    @Before fun setUp() { useCase = EnsureTodayEntriesUseCase() }

    private fun recept(
        id: String = "r1",
        namn: String = "Ibuprofen",
        tidpunkter: List<String> = listOf("Morgon"),
        upprepning: String = "dagligen",
        dagar: List<Int> = emptyList(),
        intervalDagar: Int = 2,
        skapad: String = LocalDate.now().toString(),
        aktiv: Boolean = true,
    ) = Recept(
        id = id, namn = namn, dos = "400", enhet = "mg",
        tidpunkter = tidpunkter, upprepning = upprepning,
        dagar = dagar, intervalDagar = intervalDagar,
        anteckning = "", aktiv = aktiv, skapad = skapad,
    )

    // ─── compute ──────────────────────────────────────────────────────────────

    @Test fun `generates entry for active dagligen recept`() {
        val result = useCase.compute(listOf(recept()), emptyList())
        assertEquals(1, result.size)
        assertEquals("Ibuprofen", result[0].namn)
    }

    @Test fun `skips inactive recept`() {
        val result = useCase.compute(listOf(recept(aktiv = false)), emptyList())
        assertTrue(result.isEmpty())
    }

    @Test fun `generates one entry per tidpunkt`() {
        val r = recept(tidpunkter = listOf("Morgon", "Kväll"))
        val result = useCase.compute(listOf(r), emptyList())
        assertEquals(2, result.size)
    }

    @Test fun `does not duplicate existing stable IDs`() {
        val today = LocalDate.now()
        val datum = today.toString()
        val existing = useCase.compute(listOf(recept()), emptyList(), today)
        val secondRun = useCase.compute(listOf(recept()), existing, today)
        assertTrue(secondRun.isEmpty())
    }

    @Test fun `stable ID format is recept_receptId_datum_tidpunkt`() {
        val today = LocalDate.of(2026, 1, 15)
        val result = useCase.compute(listOf(recept(id = "r1", tidpunkter = listOf("Morgon"))), emptyList(), today)
        assertEquals("recept_r1_2026-01-15_Morgon", result[0].id)
    }

    @Test fun `sets receptId on generated entry`() {
        val result = useCase.compute(listOf(recept(id = "recept42")), emptyList())
        assertEquals("recept42", result[0].receptId)
    }

    @Test fun `tagen is false on generated entry`() {
        val result = useCase.compute(listOf(recept()), emptyList())
        assertFalse(result[0].tagen)
    }

    // ─── shouldTakeToday — dagligen ───────────────────────────────────────────

    @Test fun `dagligen fires every day`() {
        val r = recept(upprepning = "dagligen")
        (0..6).forEach { offset ->
            assertTrue(useCase.shouldTakeToday(r, LocalDate.now().plusDays(offset.toLong())))
        }
    }

    // ─── shouldTakeToday — vardagar ───────────────────────────────────────────

    @Test fun `vardagar fires Monday through Friday`() {
        val r = recept(upprepning = "vardagar")
        val monday = LocalDate.now().with(TemporalAdjusters.nextOrSame(DayOfWeek.MONDAY))
        (0..4).forEach { i -> assertTrue("weekday $i", useCase.shouldTakeToday(r, monday.plusDays(i.toLong()))) }
    }

    @Test fun `vardagar does not fire on Saturday or Sunday`() {
        val r = recept(upprepning = "vardagar")
        val saturday = LocalDate.now().with(TemporalAdjusters.nextOrSame(DayOfWeek.SATURDAY))
        assertFalse(useCase.shouldTakeToday(r, saturday))
        assertFalse(useCase.shouldTakeToday(r, saturday.plusDays(1)))
    }

    // ─── shouldTakeToday — helger ─────────────────────────────────────────────

    @Test fun `helger fires on Saturday and Sunday`() {
        val r = recept(upprepning = "helger")
        val saturday = LocalDate.now().with(TemporalAdjusters.nextOrSame(DayOfWeek.SATURDAY))
        assertTrue(useCase.shouldTakeToday(r, saturday))
        assertTrue(useCase.shouldTakeToday(r, saturday.plusDays(1)))
    }

    @Test fun `helger does not fire Monday through Friday`() {
        val r = recept(upprepning = "helger")
        val monday = LocalDate.now().with(TemporalAdjusters.nextOrSame(DayOfWeek.MONDAY))
        (0..4).forEach { i -> assertFalse("weekday $i", useCase.shouldTakeToday(r, monday.plusDays(i.toLong()))) }
    }

    // ─── shouldTakeToday — anpassad ───────────────────────────────────────────

    @Test fun `anpassad fires only on specified days`() {
        val monday = LocalDate.now().with(TemporalAdjusters.nextOrSame(DayOfWeek.MONDAY))
        val dayIdx = monday.dayOfWeek.value - 1  // 0
        val r = recept(upprepning = "anpassad", dagar = listOf(dayIdx))
        assertTrue(useCase.shouldTakeToday(r, monday))
        assertFalse(useCase.shouldTakeToday(r, monday.plusDays(1)))
    }

    // ─── shouldTakeToday — intervall ──────────────────────────────────────────

    @Test fun `intervall fires on start day (day 0)`() {
        val today = LocalDate.now()
        val r = recept(upprepning = "intervall", intervalDagar = 2, skapad = today.toString())
        assertTrue(useCase.shouldTakeToday(r, today))
    }

    @Test fun `intervall does not fire on day 1 of 2-day interval`() {
        val yesterday = LocalDate.now().minusDays(1)
        val r = recept(upprepning = "intervall", intervalDagar = 2, skapad = yesterday.toString())
        assertFalse(useCase.shouldTakeToday(r, LocalDate.now()))
    }

    @Test fun `intervall fires on day 2 of 2-day interval`() {
        val twoDaysAgo = LocalDate.now().minusDays(2)
        val r = recept(upprepning = "intervall", intervalDagar = 2, skapad = twoDaysAgo.toString())
        assertTrue(useCase.shouldTakeToday(r, LocalDate.now()))
    }
}
