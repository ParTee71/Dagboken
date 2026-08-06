package se.partee71.dagboken.domain.usecase

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import se.partee71.dagboken.domain.model.Dosperiod
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
        startDatum: String = "",
        slutDatum: String? = null,
        dosperioder: List<Dosperiod> = emptyList(),
    ) = Recept(
        id = id, namn = namn, dos = "400", enhet = "mg",
        tidpunkter = tidpunkter, upprepning = upprepning,
        dagar = dagar, intervalDagar = intervalDagar,
        aktiv = aktiv, skapad = skapad,
        startDatum = startDatum, slutDatum = slutDatum, dosperioder = dosperioder,
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

    @Test fun `intervall counts from startDatum when the recept has one`() {
        val today = LocalDate.now()
        // skapad ligger en dag fel jämfört med startDatum — startDatum ska vinna (REC-4/REC-7)
        val r = recept(
            upprepning = "intervall", intervalDagar = 2,
            skapad = today.minusDays(1).toString(),
            startDatum = today.minusDays(2).toString(),
        )
        assertTrue(useCase.shouldTakeToday(r, today))
    }

    // ─── period (REC-7) ───────────────────────────────────────────────────────

    @Test fun `period fires on first and last day`() {
        val start = LocalDate.of(2026, 5, 1)
        val slut  = LocalDate.of(2026, 5, 10)
        val r = recept(startDatum = start.toString(), slutDatum = slut.toString())
        assertTrue(useCase.shouldTakeToday(r, start))
        assertTrue(useCase.shouldTakeToday(r, slut))
    }

    @Test fun `period does not fire before start or after end`() {
        val start = LocalDate.of(2026, 5, 1)
        val slut  = LocalDate.of(2026, 5, 10)
        val r = recept(startDatum = start.toString(), slutDatum = slut.toString())
        assertFalse(useCase.shouldTakeToday(r, start.minusDays(1)))
        assertFalse(useCase.shouldTakeToday(r, slut.plusDays(1)))
    }

    @Test fun `a recept without an explicit startDatum has no lower bound`() {
        // Bakåtbläddring i Idag (HEM-14) ska fortsätta seeda doser för dagar före
        // receptet skapades — periodgrindningen gäller bara ett uttalat startdatum.
        val r = recept(skapad = "2026-08-01", startDatum = "")
        assertTrue(useCase.shouldTakeToday(r, LocalDate.of(2026, 1, 15)))
        assertEquals(1, useCase.compute(listOf(r), emptyList(), LocalDate.of(2026, 1, 15)).size)
    }

    @Test fun `no slutDatum means tills vidare`() {
        val r = recept(startDatum = LocalDate.of(2020, 1, 1).toString(), slutDatum = null)
        assertTrue(useCase.shouldTakeToday(r, LocalDate.of(2030, 1, 1)))
    }

    @Test fun `compute generates nothing outside the period`() {
        val r = recept(
            startDatum = LocalDate.of(2026, 5, 1).toString(),
            slutDatum  = LocalDate.of(2026, 5, 10).toString(),
        )
        assertTrue(useCase.compute(listOf(r), emptyList(), LocalDate.of(2026, 5, 11)).isEmpty())
        assertEquals(1, useCase.compute(listOf(r), emptyList(), LocalDate.of(2026, 5, 10)).size)
    }

    // ─── doshöjningar (REC-9) ─────────────────────────────────────────────────

    @Test fun `compute adds the increase to the base dose inside its range`() {
        val r = recept(
            startDatum  = "2026-05-01",
            slutDatum   = "2026-05-14",
            dosperioder = listOf(
                Dosperiod("d1", "2026-05-01", "2026-05-05", "400", "mg"),
            ),
        )
        val inside = useCase.compute(listOf(r), emptyList(), LocalDate.of(2026, 5, 3))
        assertEquals("800", inside[0].dos)
        assertEquals("mg", inside[0].enhet)
    }

    @Test fun `compute falls back to the base dose after the increase ends`() {
        val r = recept(
            startDatum  = "2026-05-01",
            slutDatum   = "2026-05-14",
            dosperioder = listOf(
                Dosperiod("d1", "2026-05-01", "2026-05-05", "400", "mg"),
            ),
        )
        val after = useCase.compute(listOf(r), emptyList(), LocalDate.of(2026, 5, 6))
        assertEquals("400", after[0].dos)
    }

    @Test fun `increase boundaries are inclusive`() {
        val r = recept(
            startDatum  = "2026-05-01",
            dosperioder = listOf(Dosperiod("d1", "2026-05-02", "2026-05-04", "400", "mg")),
        )
        assertEquals("400", useCase.compute(listOf(r), emptyList(), LocalDate.of(2026, 5, 1))[0].dos)
        assertEquals("800", useCase.compute(listOf(r), emptyList(), LocalDate.of(2026, 5, 2))[0].dos)
        assertEquals("800", useCase.compute(listOf(r), emptyList(), LocalDate.of(2026, 5, 4))[0].dos)
        assertEquals("400", useCase.compute(listOf(r), emptyList(), LocalDate.of(2026, 5, 5))[0].dos)
    }

    @Test fun `two increases in sequence give their own totals`() {
        val r = recept(
            startDatum  = "2026-05-01",
            slutDatum   = "2026-05-10",
            dosperioder = listOf(
                Dosperiod("d1", "2026-05-01", "2026-05-05", "400", "mg"),
                Dosperiod("d2", "2026-05-06", "2026-05-10", "200", "mg"),
            ),
        )
        assertEquals("800", useCase.compute(listOf(r), emptyList(), LocalDate.of(2026, 5, 5))[0].dos)
        assertEquals("600", useCase.compute(listOf(r), emptyList(), LocalDate.of(2026, 5, 6))[0].dos)
    }

    @Test fun `increase without slutDatum runs to the end of the recept period`() {
        val r = recept(
            startDatum  = "2026-05-01",
            slutDatum   = "2026-05-10",
            dosperioder = listOf(Dosperiod("d1", "2026-05-04", null, "400", "mg")),
        )
        assertEquals("800", useCase.compute(listOf(r), emptyList(), LocalDate.of(2026, 5, 10))[0].dos)
    }
}
