package se.partee71.dagboken.data.common

import kotlin.test.assertEquals
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test

/** "Senaste vinner" per nyckel: en i taget, inaktuella hoppas över, och inga poster blir kvar. */
@OptIn(ExperimentalCoroutinesApi::class)
class LatestWinsTest {

    @Test
    fun `en inaktuell körning hoppas över eller får veta det, och posten tas bort efteråt`() = runTest {
        val runs = LatestWins<String>()
        val seen = mutableListOf<String>()
        val first = async { runs.run("a", "hoppad") { isCurrent -> delay(100); seen += "första"; if (isCurrent()) "klar" else "inaktuell" } }
        runCurrent()
        val second = async { runs.run("a", "hoppad") { seen += "andra"; "klar" } }
        val third = async { runs.run("a", "hoppad") { seen += "tredje"; "klar" } }
        val other = async { runs.run("b", "hoppad") { seen += "annan nyckel"; "klar" } }

        assertEquals(listOf("inaktuell", "hoppad", "klar", "klar"), listOf(first.await(), second.await(), third.await(), other.await()))
        assertEquals(listOf("annan nyckel", "första", "tredje"), seen, "andra nycklar väntar inte; den mellersta körs aldrig")
        assertEquals(0, runs.activeKeys, "inga poster kvar när ingen väntar")
    }
}
