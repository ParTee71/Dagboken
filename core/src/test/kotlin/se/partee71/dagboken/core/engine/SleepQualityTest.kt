package se.partee71.dagboken.core.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import se.partee71.dagboken.core.model.Sex
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/** Enhetstest för sömnkvalitetens poängmodell (HLS-10, §19). */
class SleepQualityTest {

    private val age55 = 55

    /** En natt som ligger mitt i målbandet på varje komponent. */
    private fun idealNight() = SleepMeasurements(
        timeInBed = 8.hours,
        awake = 20.minutes,
        // 460 min sömn: 11 % djupsömn ≈ 51 min, 21 % REM ≈ 97 min.
        deep = 51.minutes,
        rem = 97.minutes,
        midpointSdMinutes = 15.0,
    )

    @Test fun `an ideal night scores at or near 100`() {
        val quality = scoreSleepQuality(idealNight(), age55, Sex.MALE)
        assertNotNull(quality)
        assertTrue("Förväntade nära full poäng, fick ${quality!!.score}", quality.score >= 95)
    }

    @Test fun `a short night is penalised on duration`() {
        val quality = scoreSleepQuality(
            idealNight().copy(timeInBed = 5.hours, awake = 20.minutes),
            age55,
            Sex.MALE,
        )!!
        val duration = quality.components.first { it.kind == SleepQualityKind.DURATION }
        assertTrue("Kort natt ska tappa poäng på längd", duration.score < 50)
    }

    @Test fun `no measurable night gives null rather than zero`() {
        // Noll poäng skulle läsas som "usel sömn"; det korrekta är "inget att visa".
        assertNull(scoreSleepQuality(SleepMeasurements(timeInBed = Duration.ZERO), age55, Sex.MALE))
    }

    // ─── Viktnormalisering ───────────────────────────────────────────────────

    @Test fun `a night without stages is scored on its own components`() {
        // Utan stadier saknas effektivitet, WASO, djupsömn och REM. Natten ska bedömas
        // på det som faktiskt mättes i stället för att straffas för det som inte mättes.
        val quality = scoreSleepQuality(
            SleepMeasurements(timeInBed = 8.hours, midpointSdMinutes = 10.0),
            age55,
            Sex.MALE,
        )!!
        assertEquals(
            listOf(SleepQualityKind.DURATION, SleepQualityKind.REGULARITY),
            quality.components.map { it.kind },
        )
        assertEquals(100, quality.score)
    }

    @Test fun `too few nights drops the regularity component instead of scoring it zero`() {
        val quality = scoreSleepQuality(idealNight().copy(midpointSdMinutes = null), age55, Sex.MALE)!!
        assertTrue(quality.components.none { it.kind == SleepQualityKind.REGULARITY })
        assertEquals(100, quality.score)
    }

    // ─── Ålders- och könsnormer ──────────────────────────────────────────────

    @Test fun `deep sleep band drops with age`() {
        val young = deepSleepBand(25, Sex.MALE)
        val older = deepSleepBand(55, Sex.MALE)
        assertTrue("Normen ska sjunka med åldern", older.start < young.start)
    }

    @Test fun `men lose deep sleep faster than women`() {
        // Ohayon m.fl. 2004: nedgången i N3 är brantare hos män.
        assertTrue(deepSleepBand(55, Sex.MALE).start < deepSleepBand(55, Sex.FEMALE).start)
    }

    @Test fun `unspecified sex lands between the two`() {
        val neutral = deepSleepBand(55, Sex.UNSPECIFIED).start
        assertTrue(neutral > deepSleepBand(55, Sex.MALE).start)
        assertTrue(neutral < deepSleepBand(55, Sex.FEMALE).start)
    }

    @Test fun `the deep sleep band stops falling after 60`() {
        assertEquals(deepSleepBand(60, Sex.MALE).start, deepSleepBand(80, Sex.MALE).start, 0.001)
    }

    // ─── Skalans nedre svansar ───────────────────────────────────────────────

    @Test fun `deep sleep far below the norm scores near zero, not half marks`() {
        // Nollpunkten ligger på halva normens nedre gräns (3,5 % vid 55 år). Med 0 %
        // som nollpunkt gav 3,9 % djupsömn — en tydligt dålig natt — fortfarande 55 p.
        val quality = scoreSleepQuality(
            // 17 min av 440 min sömn ≈ 3,9 %.
            idealNight().copy(
                timeInBed = 500.minutes,
                awake = 60.minutes,
                deep = 17.minutes,
            ),
            age55,
            Sex.MALE,
        )!!
        val deep = quality.components.first { it.kind == SleepQualityKind.DEEP }
        assertTrue("Förväntade låg poäng, fick ${deep.score}", deep.score <= 15)
    }

    @Test fun `deep sleep at half the norm is the zero point`() {
        val band = deepSleepBand(age55, Sex.MALE)
        val sleepMinutes = 480.0
        val quality = scoreSleepQuality(
            idealNight().copy(
                timeInBed = 480.minutes,
                awake = Duration.ZERO,
                deep = ((band.start / 2.0 / 100.0 * sleepMinutes).toLong()).minutes,
            ),
            age55,
            Sex.MALE,
        )!!
        assertEquals(0, quality.components.first { it.kind == SleepQualityKind.DEEP }.score)
    }

    @Test fun `an hour and a half awake scores zero regardless of age`() {
        val quality = scoreSleepQuality(
            idealNight().copy(timeInBed = 9.hours, awake = 90.minutes),
            age55,
            Sex.MALE,
        )!!
        assertEquals(0, quality.components.first { it.kind == SleepQualityKind.WASO }.score)
    }

    @Test fun `the clinical efficiency threshold no longer scores high`() {
        // 85 % är gränsen för kliniskt störd sömn — den ska inte ge 75 p som förut.
        val quality = scoreSleepQuality(
            // 408 av 480 min = 85 %.
            idealNight().copy(timeInBed = 480.minutes, awake = 72.minutes),
            age55,
            Sex.MALE,
        )!!
        val efficiency = quality.components.first { it.kind == SleepQualityKind.EFFICIENCY }
        assertEquals(67, efficiency.score)
    }

    @Test fun `a real mediocre night lands in the low eighties, not near ninety`() {
        // Regression mot den natt som avslöjade att svansarna var för snälla:
        // 7 h 20 min sömn, 88 % effektivitet, 60 min vaken, 3,9 % djupsömn, 21 % REM,
        // 32 min spridning. Gav 89 p innan svansarna skärptes.
        val quality = scoreSleepQuality(
            SleepMeasurements(
                timeInBed = 500.minutes,
                awake = 60.minutes,
                deep = 17.minutes,
                rem = 92.minutes,
                midpointSdMinutes = 32.0,
            ),
            age55,
            Sex.MALE,
        )!!
        assertEquals(81, quality.score)
    }

    @Test fun `deep sleep above the band is not penalised`() {
        // Mer djupsömn än normen är inget problem — bara mindre är det.
        val quality = scoreSleepQuality(idealNight().copy(deep = 140.minutes), age55, Sex.MALE)!!
        assertEquals(100, quality.components.first { it.kind == SleepQualityKind.DEEP }.score)
    }

    @Test fun `waso target grows with age`() {
        assertTrue(wasoTargetMinutes(55) > wasoTargetMinutes(30))
    }

    @Test fun `the same night scores higher for a 55 year old than for a 25 year old`() {
        // 11 % djupsömn är normalt vid 55 men lågt vid 25 — poängen måste skilja.
        val night = idealNight()
        val older = scoreSleepQuality(night, 55, Sex.MALE)!!.score
        val younger = scoreSleepQuality(night, 25, Sex.MALE)!!.score
        assertTrue("Förväntade lägre poäng för den yngre: $younger vs $older", younger < older)
    }

    // ─── Flaggor ─────────────────────────────────────────────────────────────

    @Test fun `low oxygen saturation is flagged, not folded into the score`() {
        val withFlag = scoreSleepQuality(idealNight().copy(meanOxygenSaturation = 88.0), age55, Sex.MALE)!!
        val without = scoreSleepQuality(idealNight(), age55, Sex.MALE)!!
        assertTrue(SleepFlag.LOW_OXYGEN_SATURATION in withFlag.flags)
        assertEquals("Flaggan får inte ändra poängen", without.score, withFlag.score)
    }

    @Test fun `normal oxygen saturation raises no flag`() {
        val quality = scoreSleepQuality(idealNight().copy(meanOxygenSaturation = 95.0), age55, Sex.MALE)!!
        assertTrue(quality.flags.isEmpty())
    }

    @Test fun `sleeping heart rate well above baseline is flagged`() {
        val quality = scoreSleepQuality(
            idealNight().copy(sleepingHeartRate = 66, baselineRestingHeartRate = 58),
            age55,
            Sex.MALE,
        )!!
        assertTrue(SleepFlag.ELEVATED_SLEEPING_HEART_RATE in quality.flags)
    }

    @Test fun `a small heart rate difference is not flagged`() {
        val quality = scoreSleepQuality(
            idealNight().copy(sleepingHeartRate = 60, baselineRestingHeartRate = 58),
            age55,
            Sex.MALE,
        )!!
        assertTrue(quality.flags.isEmpty())
    }

    @Test fun `a missing baseline cannot raise the heart rate flag`() {
        val quality = scoreSleepQuality(
            idealNight().copy(sleepingHeartRate = 90, baselineRestingHeartRate = null),
            age55,
            Sex.MALE,
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

    // ─── 4.0: kotlin.time i stället för java.time ────────────────────────────

    @Test fun `durations are truncated to whole minutes like java time toMinutes in 3x`() {
        val exact = scoreSleepQuality(idealNight().copy(timeInBed = 480.minutes), age55, Sex.MALE)!!
        val withSeconds = scoreSleepQuality(idealNight().copy(timeInBed = 480.minutes + 59.seconds), age55, Sex.MALE)!!
        assertEquals(exact, withSeconds)
    }

    @Test fun `a night shorter than a minute cannot be scored`() {
        assertNull(scoreSleepQuality(SleepMeasurements(timeInBed = 59.seconds), age55, Sex.MALE))
    }

    @Test fun `awake as long as the time in bed leaves no sleep to score`() {
        assertNull(scoreSleepQuality(SleepMeasurements(timeInBed = 8.hours, awake = 8.hours), age55, Sex.MALE))
    }
}
