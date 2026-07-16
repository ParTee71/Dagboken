package se.partee71.dagboken.data.repository

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Enhetstest för [estimateRestingHeartRate] — fallback-skattningen av vilopuls
 * när Health Connect saknar RestingHeartRateRecord (HLS-7, §19).
 */
class RestingHeartRateEstimateTest {

    @Test fun `returns null for no samples`() {
        assertNull(estimateRestingHeartRate(emptyList()))
    }

    @Test fun `returns the single value for one sample`() {
        assertEquals(62L, estimateRestingHeartRate(listOf(62L)))
    }

    @Test fun `falls back to the minimum with few samples`() {
        // Heltalspercentilen ((n-1)*5)/100 = 0 för små n -> lägsta värdet.
        assertEquals(55L, estimateRestingHeartRate(listOf(80L, 55L, 120L, 66L)))
    }

    @Test fun `picks the low percentile and ignores a single artefact low`() {
        // 101 prover: 30 (artefakt) + 100 stycken kring 60-159. 5:e percentilen
        // (index (100*5)/100 = 5) hamnar bland de låga men inte på artefakten.
        val samples = listOf(30L) + (60L..159L).toList()
        val result = estimateRestingHeartRate(samples)
        assertEquals(64L, result)
    }
}
