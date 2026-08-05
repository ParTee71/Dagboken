package se.partee71.dagboken.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration

/** Enhetstest för sömnkvalitetens poängmodell (HLS-10, §19). */
class SleepQualityTest {

    private val age55 = 55

    /** En natt som ligger mitt i målbandet på varje komponent. */
    private fun idealNight() = SleepMeasurements(
        timeInBed = Duration.ofHours(8),
        awake = Duration.ofMinutes(20),
        // 460 min sömn: 11 % djupsömn ≈ 51 min, 21 % REM ≈ 97 min.
        deep = Duration.ofMinutes(51),
        rem = Duration.ofMinutes(97),
        midpointSdMinutes = 15.0,
    )

    @Test fun `an ideal night scores at or near 100`() {
        val quality = scoreSleepQuality(idealNight(), age55, Sex.MAN)
        assertNotNull(quality)
        assertTrue("Förväntade nära full poäng, fick ${quality!!.score}", quality.score >= 95)
    }

    @Test fun `a short night is penalised on duration`() {
        val quality = scoreSleepQuality(
            idealNight().copy(timeInBed = Duration.ofHours(5), awake = Duration.ofMinutes(20)),
            age55,
            Sex.MAN,
        )!!
        val duration = quality.components.first { it.kind == SleepQualityKind.DURATION }
        assertTrue("Kort natt ska tappa poäng på längd", duration.score < 50)
    }

    @Test fun `no measurable night gives null rather than zero`() {
        // Noll poäng skulle läsas som "usel sömn"; det korrekta är "inget att visa".
        assertNull(scoreSleepQuality(SleepMeasurements(timeInBed = Duration.ZERO), age55, Sex.MAN))
    }

    // ─── Viktnormalisering ───────────────────────────────────────────────────

    @Test fun `a night without stages is scored on its own components`() {
        // Utan stadier saknas effektivitet, WASO, djupsömn och REM. Natten ska bedömas
        // på det som faktiskt mättes i stället för att straffas för det som inte mättes.
        val quality = scoreSleepQuality(
            SleepMeasurements(timeInBed = Duration.ofHours(8), midpointSdMinutes = 10.0),
            age55,
            Sex.MAN,
        )!!
        assertEquals(
            listOf(SleepQualityKind.DURATION, SleepQualityKind.REGULARITY),
            quality.components.map { it.kind },
        )
        assertEquals(100, quality.score)
    }

    @Test fun `too few nights drops the regularity component instead of scoring it zero`() {
        val quality = scoreSleepQuality(idealNight().copy(midpointSdMinutes = null), age55, Sex.MAN)!!
        assertTrue(quality.components.none { it.kind == SleepQualityKind.REGULARITY })
        assertEquals(100, quality.score)
    }

    // ─── Ålders- och könsnormer ──────────────────────────────────────────────

    @Test fun `deep sleep band drops with age`() {
        val young = deepSleepBand(25, Sex.MAN)
        val older = deepSleepBand(55, Sex.MAN)
        assertTrue("Normen ska sjunka med åldern", older.start < young.start)
    }

    @Test fun `men lose deep sleep faster than women`() {
        // Ohayon m.fl. 2004: nedgången i N3 är brantare hos män.
        assertTrue(deepSleepBand(55, Sex.MAN).start < deepSleepBand(55, Sex.KVINNA).start)
    }

    @Test fun `unspecified sex lands between the two`() {
        val neutral = deepSleepBand(55, Sex.EJ_ANGIVET).start
        assertTrue(neutral > deepSleepBand(55, Sex.MAN).start)
        assertTrue(neutral < deepSleepBand(55, Sex.KVINNA).start)
    }

    @Test fun `the deep sleep band stops falling after 60`() {
        assertEquals(deepSleepBand(60, Sex.MAN).start, deepSleepBand(80, Sex.MAN).start, 0.001)
    }

    @Test fun `deep sleep above the band is not penalised`() {
        // Mer djupsömn än normen är inget problem — bara mindre är det.
        val quality = scoreSleepQuality(idealNight().copy(deep = Duration.ofMinutes(140)), age55, Sex.MAN)!!
        assertEquals(100, quality.components.first { it.kind == SleepQualityKind.DEEP }.score)
    }

    @Test fun `waso target grows with age`() {
        assertTrue(wasoTargetMinutes(55) > wasoTargetMinutes(30))
    }

    @Test fun `the same night scores higher for a 55 year old than for a 25 year old`() {
        // 11 % djupsömn är normalt vid 55 men lågt vid 25 — poängen måste skilja.
        val night = idealNight()
        val older = scoreSleepQuality(night, 55, Sex.MAN)!!.score
        val younger = scoreSleepQuality(night, 25, Sex.MAN)!!.score
        assertTrue("Förväntade lägre poäng för den yngre: $younger vs $older", younger < older)
    }

    // ─── Flaggor ─────────────────────────────────────────────────────────────

    @Test fun `low oxygen saturation is flagged, not folded into the score`() {
        val withFlag = scoreSleepQuality(idealNight().copy(meanOxygenSaturation = 88.0), age55, Sex.MAN)!!
        val without = scoreSleepQuality(idealNight(), age55, Sex.MAN)!!
        assertTrue(SleepFlag.LOW_OXYGEN_SATURATION in withFlag.flags)
        assertEquals("Flaggan får inte ändra poängen", without.score, withFlag.score)
    }

    @Test fun `normal oxygen saturation raises no flag`() {
        val quality = scoreSleepQuality(idealNight().copy(meanOxygenSaturation = 95.0), age55, Sex.MAN)!!
        assertTrue(quality.flags.isEmpty())
    }

    @Test fun `sleeping heart rate well above baseline is flagged`() {
        val quality = scoreSleepQuality(
            idealNight().copy(sleepingHeartRate = 66, baselineRestingHeartRate = 58),
            age55,
            Sex.MAN,
        )!!
        assertTrue(SleepFlag.ELEVATED_SLEEPING_HEART_RATE in quality.flags)
    }

    @Test fun `a small heart rate difference is not flagged`() {
        val quality = scoreSleepQuality(
            idealNight().copy(sleepingHeartRate = 60, baselineRestingHeartRate = 58),
            age55,
            Sex.MAN,
        )!!
        assertTrue(quality.flags.isEmpty())
    }

    @Test fun `a missing baseline cannot raise the heart rate flag`() {
        val quality = scoreSleepQuality(
            idealNight().copy(sleepingHeartRate = 90, baselineRestingHeartRate = null),
            age55,
            Sex.MAN,
        )!!
        assertTrue(quality.flags.isEmpty())
    }

    // ─── Poängramper ─────────────────────────────────────────────────────────

    @Test fun `rampUp is clamped at both ends`() {
        assertEquals(0, rampUp(60.0, zeroAt = 70.0, fullAt = 90.0))
        assertEquals(50, rampUp(80.0, zeroAt = 70.0, fullAt = 90.0))
        assertEquals(100, rampUp(95.0, zeroAt = 70.0, fullAt = 90.0))
    }

    @Test fun `rampDown rewards lower values`() {
        assertEquals(100, rampDown(20.0, fullAt = 30.0, zeroAt = 90.0))
        assertEquals(50, rampDown(60.0, fullAt = 30.0, zeroAt = 90.0))
        assertEquals(0, rampDown(120.0, fullAt = 30.0, zeroAt = 90.0))
    }

    @Test fun `plateau gives full score inside the band and tapers outside it`() {
        assertEquals(100, plateau(7.5, zeroLow = 4.0, fullLow = 7.0, fullHigh = 8.5, zeroHigh = 10.0))
        assertEquals(0, plateau(4.0, zeroLow = 4.0, fullLow = 7.0, fullHigh = 8.5, zeroHigh = 10.0))
        assertEquals(0, plateau(10.0, zeroLow = 4.0, fullLow = 7.0, fullHigh = 8.5, zeroHigh = 10.0))
        assertTrue(plateau(9.0, zeroLow = 4.0, fullLow = 7.0, fullHigh = 8.5, zeroHigh = 10.0) in 1..99)
    }
}
