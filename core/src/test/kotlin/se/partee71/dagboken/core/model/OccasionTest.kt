package se.partee71.dagboken.core.model

import kotlin.test.assertEquals
import kotlinx.datetime.LocalTime
import org.junit.Test

/** Måendets tillfällen (NOT-4, DAT-12): de lagrade namnen och påminnelsernas standardtider. */
class OccasionTest {

    @Test
    fun `de lagrade namnen är unika och standardtiderna ligger i dygnets ordning`() {
        assertEquals(listOf("breakfast", "lunch", "dinner", "bedtime"), Occasion.entries.map { it.wire })
        assertEquals(listOf(LocalTime(8, 0), LocalTime(12, 0), LocalTime(17, 0), LocalTime(21, 0)), Occasion.entries.map { it.defaultTime })
    }
}
