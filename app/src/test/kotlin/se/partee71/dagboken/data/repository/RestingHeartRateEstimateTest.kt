package se.partee71.dagboken.data.repository

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Duration
import java.time.Instant

/**
 * Enhetstest för [estimateRestingHeartRate] — fallback-skattningen av vilopuls
 * när Health Connect saknar RestingHeartRateRecord (HLS-7, §19). Skattningen är
 * medelvärdet av den lägsta 5-percentilen av de **vakna** pulsproverna (minst ett
 * prov); prover inom ett [SleepWindow] sållas bort först (#154).
 */
class RestingHeartRateEstimateTest {

    private val night = Instant.parse("2026-07-21T21:00:00Z")
    private val morning = Instant.parse("2026-07-22T06:00:00Z")

    /** Bygger prover en minut isär från [from]. */
    private fun samplesFrom(from: Instant, bpm: List<Long>): List<TimedBpm> =
        bpm.mapIndexed { i, value -> TimedBpm(from.plus(Duration.ofMinutes(i.toLong())), value) }

    @Test fun `returns null for no samples`() {
        assertNull(estimateRestingHeartRate(emptyList()))
    }

    @Test fun `returns the single value for one sample`() {
        assertEquals(62L, estimateRestingHeartRate(samplesFrom(morning, listOf(62L))))
    }

    @Test fun `falls back to the lowest value with few samples`() {
        // n/20 = 0 -> minst 1 prov -> lägsta värdet.
        assertEquals(55L, estimateRestingHeartRate(samplesFrom(morning, listOf(80L, 55L, 120L, 66L))))
    }

    @Test fun `averages the low ventile and tolerates a single artefact low`() {
        // 101 prover: ett artefaktlågt (30) + 100 kring 60-159. Lägsta 5%:en är 5
        // prover: 30, 60, 61, 62, 63 -> medel 55.2 -> 55. Artefakten drar alltså
        // inte ner värdet till 30 tack vare medelvärdet.
        val samples = samplesFrom(morning, listOf(30L) + (60L..159L).toList())
        assertEquals(55L, estimateRestingHeartRate(samples))
    }

    /**
     * Regressionstest för #154: med klockan buren på natten bestod hela lågänden av
     * djupsömnsprover, så skattningen landade på sömnpulsen (46) i stället för den
     * vakna vilopulsen — flera slag under Health Connects eget värde.
     */
    @Test fun `excludes samples inside a sleep session`() {
        val sleep = SleepWindow(night, Instant.parse("2026-07-22T05:00:00Z"))
        val nightSamples = samplesFrom(night, List(100) { 46L })
        val awakeSamples = samplesFrom(morning, (50L..149L).toList())
        val all = nightSamples + awakeSamples

        // Utan sömnfiltret: 200 prover -> lägsta 10 är nattens 46:or -> 46 (buggen).
        assertEquals(46L, estimateRestingHeartRate(all))

        // Med sömnfiltret: 100 vakna prover -> lägsta 5 är 50..54 -> medel 52.
        assertEquals(52L, estimateRestingHeartRate(all, listOf(sleep)))
    }

    @Test fun `excludes a sleep session that crosses midnight from both days`() {
        val sleep = SleepWindow(
            Instant.parse("2026-07-21T22:00:00Z"),
            Instant.parse("2026-07-22T06:00:00Z"),
        )
        val samples = listOf(
            TimedBpm(Instant.parse("2026-07-21T23:00:00Z"), 44L), // före midnatt, i sömn
            TimedBpm(Instant.parse("2026-07-22T02:00:00Z"), 45L), // efter midnatt, i sömn
            TimedBpm(Instant.parse("2026-07-22T12:00:00Z"), 60L),
            TimedBpm(Instant.parse("2026-07-22T13:00:00Z"), 70L),
        )
        assertEquals(60L, estimateRestingHeartRate(samples, listOf(sleep)))
    }

    @Test fun `sleep window is half open - start excluded, end included`() {
        val sleep = SleepWindow(night, morning)
        val samples = listOf(
            TimedBpm(night, 40L), // exakt vid start -> i sömn
            TimedBpm(morning, 58L), // exakt vid slut -> vaken
            TimedBpm(morning.plus(Duration.ofHours(1)), 70L),
        )
        assertEquals(58L, estimateRestingHeartRate(samples, listOf(sleep)))
    }

    @Test fun `falls back to all samples when every sample is asleep`() {
        // Klockan bars bara under natten -> hellre en grov skattning än "—".
        val sleep = SleepWindow(night, morning)
        val samples = samplesFrom(night, listOf(46L, 47L, 48L))
        assertEquals(46L, estimateRestingHeartRate(samples, listOf(sleep)))
    }

    @Test fun `no sleep windows gives the same result as before`() {
        val samples = samplesFrom(night, (48L..147L).toList())
        assertEquals(
            estimateRestingHeartRate(samples),
            estimateRestingHeartRate(samples, emptyList()),
        )
    }
}
