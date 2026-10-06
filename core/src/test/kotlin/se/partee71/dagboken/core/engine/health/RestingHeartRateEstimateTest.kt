package se.partee71.dagboken.core.engine.health

import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Vilopulsen (HLS-7): registrerat värde före skattning, och skattningen ur de **vakna** proverna –
 * medelvärdet av den lägsta 5-percentilen (minst ett prov), sömnfönstren bortsållade (#154). Portad från 3.x
 * `RestingHeartRateEstimateTest`.
 */
class RestingHeartRateEstimateTest {

    private val night = Instant.parse("2026-07-21T21:00:00Z")
    private val morning = Instant.parse("2026-07-22T06:00:00Z")

    private fun bpm(time: Instant, value: Long) = HeartRateSample("watch", time, value)

    /** Prover en minut isär från [from]. */
    private fun samplesFrom(from: Instant, values: List<Long>) = values.mapIndexed { i, v -> bpm(from + i.minutes, v) }

    @Test fun `returns null for no samples`() {
        assertNull(estimateRestingHeartRate(emptyList()))
    }

    @Test fun `returns the single value for one sample`() {
        assertEquals(62L, estimateRestingHeartRate(samplesFrom(morning, listOf(62L))))
    }

    @Test fun `falls back to the lowest value with few samples`() {
        assertEquals(55L, estimateRestingHeartRate(samplesFrom(morning, listOf(80L, 55L, 120L, 66L))))
    }

    @Test fun `averages the low ventile and tolerates a single artefact low`() {
        // 101 prover: 30 + 60..159. Lägsta fem: 30, 60, 61, 62, 63 → 55,2 → 55.
        assertEquals(55L, estimateRestingHeartRate(samplesFrom(morning, listOf(30L) + (60L..159L).toList())))
    }

    @Test fun `excludes samples inside a sleep session`() {
        // Regression för #154: utan sömnfiltret var hela lågänden nattens 46:or.
        val sleep = TimeWindow(night, Instant.parse("2026-07-22T05:00:00Z"))
        val all = samplesFrom(night, List(100) { 46L }) + samplesFrom(morning, (50L..149L).toList())
        assertEquals(46L, estimateRestingHeartRate(all))
        assertEquals(52L, estimateRestingHeartRate(all, listOf(sleep)))
    }

    @Test fun `excludes a sleep session that crosses midnight from both days`() {
        val sleep = TimeWindow(Instant.parse("2026-07-21T22:00:00Z"), Instant.parse("2026-07-22T06:00:00Z"))
        val samples = listOf(
            bpm(Instant.parse("2026-07-21T23:00:00Z"), 44L),
            bpm(Instant.parse("2026-07-22T02:00:00Z"), 45L),
            bpm(Instant.parse("2026-07-22T12:00:00Z"), 60L),
            bpm(Instant.parse("2026-07-22T13:00:00Z"), 70L),
        )
        assertEquals(60L, estimateRestingHeartRate(samples, listOf(sleep)))
    }

    @Test fun `sleep window is half open - a sample at the start is asleep, one at the end is awake`() {
        // Starten ligger i sömnen, slutet räknas som vaket.
        val samples = listOf(bpm(night, 40L), bpm(morning, 58L), bpm(morning + 1.hours, 70L))
        assertEquals(58L, estimateRestingHeartRate(samples, listOf(TimeWindow(night, morning))))
    }

    @Test fun `falls back to all samples when every sample is asleep`() {
        val samples = samplesFrom(night, listOf(46L, 47L, 48L))
        assertEquals(46L, estimateRestingHeartRate(samples, listOf(TimeWindow(night, morning))))
    }

    @Test fun `overlapping and unsorted windows give the same result as checking each window`() {
        // Indexet slår ihop och sorterar fönstren; svaret ska vara detsamma som en rak jämförelse mot varje fönster.
        val windows = listOf(
            TimeWindow(Instant.parse("2026-07-22T01:00:00Z"), Instant.parse("2026-07-22T03:00:00Z")),
            TimeWindow(night, Instant.parse("2026-07-22T02:00:00Z")),
            TimeWindow(Instant.parse("2026-07-22T10:00:00Z"), Instant.parse("2026-07-22T10:30:00Z")),
        )
        val samples = (0 until 24 * 60 step 7).map { bpm(night + it.minutes, 40L + it % 50) }
        val naive = samples.filterNot { s -> windows.any { s.time in it } }.map { it.bpm }.sorted()
        val expected = naive.take((naive.size / 20).coerceAtLeast(1)).average().let { kotlin.math.round(it).toLong() }
        assertEquals(expected, estimateRestingHeartRate(samples, windows))
        val index = SleepWindows(windows)
        samples.forEach { s -> assertEquals(windows.any { s.time in it }, s.time in index) }
    }

    @Test fun `no sleep windows gives the same result as before`() {
        val samples = samplesFrom(night, (48L..147L).toList())
        assertEquals(estimateRestingHeartRate(samples), estimateRestingHeartRate(samples, emptyList()))
    }

    @Test fun `a sleep session can be used directly as a window`() {
        val session = SleepSession("watch", night, morning)
        assertEquals(58L, estimateRestingHeartRate(listOf(bpm(night, 40L), bpm(morning, 58L)), listOf(session)))
    }

    // ─── Registrerat värde före skattning ─────────────────────────────────────

    @Test fun `the latest recorded resting heart rate wins over the estimate`() {
        val recorded = listOf(RestingHeartRateSample("phone", night, 60L), RestingHeartRateSample("watch", morning, 54L))
        assertEquals(54L, restingHeartRate(recorded, samplesFrom(morning, listOf(48L, 70L))))
    }

    @Test fun `without a recorded value the estimate is used`() {
        assertEquals(48L, restingHeartRate(emptyList(), samplesFrom(morning, listOf(48L, 70L))))
    }

    @Test fun `without recorded values or samples there is no resting heart rate`() {
        assertNull(restingHeartRate(emptyList(), emptyList()))
    }
}
