package se.partee71.dagboken.core.time

import kotlin.test.assertEquals
import kotlinx.datetime.LocalDate
import org.junit.Test

/** Läsbudgetens uppdelning av en period i lyssnade delar och historikdelar (NFR-24). */
class ReadPartsTest {

    private fun d(day: Int, month: Int = 10) = LocalDate(2026, month, day)

    private fun live(days: ClosedRange<LocalDate>) = ReadPart(days, live = true)

    private fun history(days: ClosedRange<LocalDate>) = ReadPart(days, live = false)

    @Test
    fun `utan lyssnade intervall är hela perioden historik`() {
        assertEquals(listOf(history(d(1)..d(31))), readParts(d(1), d(31), emptyList()))
    }

    @Test
    fun `ett lyssnat slut ger historik före och lyssnat efter – varje dag i exakt en del`() {
        assertEquals(listOf(history(d(1)..d(9)), live(d(10)..d(31))), readParts(d(1), d(31), listOf(d(10)..d(31))))
    }

    @Test
    fun `ett lyssnat intervall i mitten ger historik på båda sidor`() {
        assertEquals(
            listOf(history(d(1)..d(4)), live(d(5)..d(11)), history(d(12)..d(31))),
            readParts(d(1), d(31), listOf(d(5)..d(11))),
        )
    }

    @Test
    fun `lyssnade intervall utanför perioden kapas – helt utanför blir ingen del`() {
        assertEquals(listOf(live(d(1)..d(3)), history(d(4)..d(6))), readParts(d(1), d(6), listOf(d(25, 9)..d(3), d(7)..d(13))))
    }

    @Test
    fun `hela perioden lyssnad ger en lyssnad del`() {
        assertEquals(listOf(live(d(5)..d(6))), readParts(d(5), d(6), listOf(d(1)..d(31))))
    }

    @Test
    fun `överlappande och angränsande intervall slås ihop, i vilken ordning de än kommer`() {
        assertEquals(
            listOf(history(d(1)..d(1)), live(d(2)..d(10)), history(d(11)..d(14)), live(d(15)..d(15)), history(d(16)..d(20))),
            readParts(d(1), d(20), listOf(d(15)..d(15), d(6)..d(10), d(2)..d(5), d(3)..d(4))),
        )
    }

    @Test
    fun `en period som slutar före sin början är tom, liksom ett tomt lyssnat intervall`() {
        assertEquals(emptyList(), readParts(d(2), d(1), listOf(d(1)..d(2))))
        assertEquals(listOf(history(d(1)..d(3))), readParts(d(1), d(3), listOf(d(3)..d(2))))
    }

    @Test
    fun `mergeDays sorterar, slår ihop och tar bort tomma`() {
        assertEquals(listOf(d(1)..d(7), d(9)..d(9)), mergeDays(listOf(d(9)..d(9), d(4)..d(7), d(1)..d(3), d(6)..d(5))))
    }

    @Test
    fun `de senaste 30 dagarna till och med idag, också över ett månadsskifte`() {
        assertEquals(d(7, 9)..d(6), recentDays(d(6)))
        assertEquals(RECENT_DAYS, datesBetween(recentDays(d(6)).start, d(6)).size)
    }
}
