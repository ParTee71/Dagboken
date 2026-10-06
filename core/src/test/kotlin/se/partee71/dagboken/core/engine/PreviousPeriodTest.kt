package se.partee71.dagboken.core.engine

import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.datetime.LocalDate
import org.junit.Test

/** Föregående period (TRD-18): lika lång, omedelbart före, på samma x-index – och inte för "Allt". */
class PreviousPeriodTest {

    private val today = LocalDate(2026, 10, 6)

    @Test
    fun `föregående period är lika lång och slutar dagen före periodens första dag`() {
        val previous = TrendRange.SEVEN_DAYS.previousDays(today)!!
        assertEquals(7, previous.size)
        assertEquals(LocalDate(2026, 9, 23), previous.first())
        assertEquals(LocalDate(2026, 9, 29), previous.last())
        assertEquals(LocalDate(2026, 9, 30), TrendRange.SEVEN_DAYS.days(today).first())
        assertEquals(90, TrendRange.THREE_MONTHS.previousDays(today)!!.size)
    }

    @Test
    fun `Allt har ingen föregående period`() {
        assertNull(TrendRange.ALL.previousDays(today))
        assertFalse(TrendRange.ALL.hasPreviousPeriod)
        assertTrue(TrendRange.MONTH.hasPreviousPeriod)
    }

    @Test
    fun `läsningen täcker båda perioderna i ett svep, annars bara perioden`() {
        assertEquals(LocalDate(2026, 9, 23), TrendRange.SEVEN_DAYS.readFrom(today, withPrevious = true))
        assertEquals(LocalDate(2026, 9, 30), TrendRange.SEVEN_DAYS.readFrom(today, withPrevious = false))
        assertEquals(TrendRange.ALL_FROM, TrendRange.ALL.readFrom(today, withPrevious = true))
    }
}
