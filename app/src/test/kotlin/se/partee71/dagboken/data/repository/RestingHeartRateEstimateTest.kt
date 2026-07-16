package se.partee71.dagboken.data.repository

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Enhetstest för [estimateRestingHeartRate] — fallback-skattningen av vilopuls
 * när Health Connect saknar RestingHeartRateRecord (HLS-7, §19). Skattningen är
 * medelvärdet av den lägsta 5-percentilen av pulsproverna (minst ett prov).
 */
class RestingHeartRateEstimateTest {

    @Test fun `returns null for no samples`() {
        assertNull(estimateRestingHeartRate(emptyList()))
    }

    @Test fun `returns the single value for one sample`() {
        assertEquals(62L, estimateRestingHeartRate(listOf(62L)))
    }

    @Test fun `falls back to the lowest value with few samples`() {
        // n/20 = 0 -> minst 1 prov -> lägsta värdet.
        assertEquals(55L, estimateRestingHeartRate(listOf(80L, 55L, 120L, 66L)))
    }

    @Test fun `averages the low ventile and tolerates a single artefact low`() {
        // 101 prover: ett artefaktlågt (30) + 100 kring 60-159. Lägsta 5%:en är 5
        // prover: 30, 60, 61, 62, 63 -> medel 55.2 -> 55. Artefakten drar alltså
        // inte ner värdet till 30 tack vare medelvärdet.
        val samples = listOf(30L) + (60L..159L).toList()
        assertEquals(55L, estimateRestingHeartRate(samples))
    }
}
