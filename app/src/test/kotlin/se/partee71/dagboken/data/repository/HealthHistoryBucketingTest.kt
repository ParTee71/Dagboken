package se.partee71.dagboken.data.repository

import androidx.health.connect.client.records.SleepSessionRecord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

/**
 * Enhetstest för dygnsfördelningen i hälsohistoriken (HLS-12) — de rena funktioner som
 * gör om ett enda svep per posttyp till ett värde per dygn. Poängen med svepet är att en
 * årsperiod inte ska bli tusentals anrop mot Health Connect; poängen med testerna är att
 * fördelningen ändå blir densamma som en läsning per dag skulle ha gett.
 */
class HealthHistoryBucketingTest {

    private val zone: ZoneId = ZoneId.of("Europe/Stockholm")

    private val day1: LocalDate = LocalDate.of(2026, 3, 10)
    private val day2: LocalDate = LocalDate.of(2026, 3, 11)
    private val day3: LocalDate = LocalDate.of(2026, 3, 12)

    private fun at(date: LocalDate, time: String) =
        LocalDateTime.of(date, LocalTime.parse(time)).atZone(zone).toInstant()

    // ─── Summerbara mått per dygn ─────────────────────────────────────────────

    @Test fun `samples are bucketed on the day their start time falls on`() {
        val byDay = mostCompleteSumByDay(
            listOf(
                OriginSample("watch", at(day1, "08:00"), 1000.0),
                OriginSample("watch", at(day1, "18:00"), 2000.0),
                OriginSample("watch", at(day2, "09:00"), 500.0),
            ),
            zone,
        )
        assertEquals(3000.0, byDay.getValue(day1), 0.001)
        assertEquals(500.0, byDay.getValue(day2), 0.001)
    }

    @Test fun `a day without samples is absent rather than zero`() {
        val byDay = mostCompleteSumByDay(
            listOf(OriginSample("watch", at(day1, "08:00"), 1000.0)),
            zone,
        )
        // En nolla vore ett påstående om ett dygn vi inte mätt — dagen ska saknas helt.
        assertFalse(byDay.containsKey(day2))
        assertNull(byDay[day2])
    }

    @Test fun `the most complete source wins per day and sources are never summed`() {
        val byDay = mostCompleteSumByDay(
            listOf(
                // Telefonen och klockan skriver samma dygn var för sig (HLS-2).
                OriginSample("phone", at(day1, "08:00"), 4000.0),
                OriginSample("watch", at(day1, "08:00"), 6000.0),
                OriginSample("watch", at(day1, "18:00"), 3000.0),
                // Nästa dygn bars bara telefonen.
                OriginSample("phone", at(day2, "08:00"), 2500.0),
            ),
            zone,
        )
        assertEquals("Klockans 9000 ska vinna, aldrig 13000", 9000.0, byDay.getValue(day1), 0.001)
        assertEquals(2500.0, byDay.getValue(day2), 0.001)
    }

    @Test fun `exercise sessions written by two sources are deduplicated per day`() {
        val byDay = mostCompleteExerciseByDay(
            listOf(
                // Morgonpasset ligger i båda källorna — klockans inspelning är den
                // längsta och får representera händelsen. Eftermiddagspasset är eget.
                OriginDaySession("phone", at(day1, "07:00"), Duration.ofMinutes(20)),
                OriginDaySession("watch", at(day1, "07:00"), Duration.ofMinutes(45)),
                OriginDaySession("watch", at(day1, "17:00"), Duration.ofMinutes(30)),
                OriginDaySession("watch", at(day2, "07:00"), Duration.ofMinutes(15)),
            ),
            zone,
        )
        assertEquals(2, byDay.getValue(day1).sessions)
        assertEquals(Duration.ofMinutes(75), byDay.getValue(day1).duration)
        assertEquals(1, byDay.getValue(day2).sessions)
        assertFalse(byDay.containsKey(day3))
    }

    @Test fun `complementary sessions from two sources are both kept for the day`() {
        // Regression för #220: det gamla urvalet valde en källa per dygn, så telefonens
        // promenad försvann helt när klockan hade längst sammanlagd tid den dagen.
        val byDay = mostCompleteExerciseByDay(
            listOf(
                OriginDaySession("watch", at(day1, "07:00"), Duration.ofMinutes(45)),
                OriginDaySession("phone", at(day1, "12:00"), Duration.ofMinutes(25)),
            ),
            zone,
        )
        assertEquals(2, byDay.getValue(day1).sessions)
        assertEquals(Duration.ofMinutes(70), byDay.getValue(day1).duration)
    }

    // ─── Puls per dygn ────────────────────────────────────────────────────────

    @Test fun `average heart rate is computed per day`() {
        val byDay = averageBpmByDay(
            listOf(
                TimedBpm(at(day1, "08:00"), 60),
                TimedBpm(at(day1, "12:00"), 80),
                TimedBpm(at(day2, "08:00"), 100),
            ),
            zone,
        )
        assertEquals(70L, byDay.getValue(day1))
        assertEquals(100L, byDay.getValue(day2))
    }

    @Test fun `a recorded resting heart rate wins over the estimate for the same day`() {
        val samples = listOf(
            TimedBpm(at(day1, "08:00"), 70),
            TimedBpm(at(day1, "12:00"), 90),
        )
        val byDay = restingHeartRateByDay(
            recorded = listOf(TimedBpm(at(day1, "22:00"), 52)),
            samples = samples,
            sleepWindows = emptyList(),
            zone = zone,
        )
        assertEquals(52L, byDay.getValue(day1))
    }

    @Test fun `the latest recorded value of the day wins`() {
        val byDay = restingHeartRateByDay(
            recorded = listOf(
                TimedBpm(at(day1, "07:00"), 60),
                TimedBpm(at(day1, "22:00"), 54),
            ),
            samples = emptyList(),
            sleepWindows = emptyList(),
            zone = zone,
        )
        assertEquals(54L, byDay.getValue(day1))
    }

    @Test fun `resting heart rate falls back to an estimate from the day's own samples`() {
        val byDay = restingHeartRateByDay(
            recorded = emptyList(),
            samples = (1..40).map { TimedBpm(at(day1, "08:00").plusSeconds(it * 60L), 60L + it) },
            sleepWindows = emptyList(),
            zone = zone,
        )
        // Lägsta 5-percentilen av 61..100 → de två lägsta, medelvärde 61,5 → 62.
        assertEquals(62L, byDay.getValue(day1))
    }

    @Test fun `sleeping samples are excluded from the daily resting estimate`() {
        val night = SleepWindow(at(day1, "01:00"), at(day1, "06:00"))
        val byDay = restingHeartRateByDay(
            recorded = emptyList(),
            samples = listOf(
                // Sovpulsen ligger under den verkliga vilopulsen (#154) och får inte
                // utgöra lågänden när klockan bars på natten.
                TimedBpm(at(day1, "03:00"), 44),
                TimedBpm(at(day1, "09:00"), 62),
                TimedBpm(at(day1, "15:00"), 88),
            ),
            sleepWindows = listOf(night),
            zone = zone,
        )
        assertEquals(62L, byDay.getValue(day1))
    }

    // ─── Nätter per dygn ──────────────────────────────────────────────────────

    private fun night(start: java.time.Instant, end: java.time.Instant, deepMinutes: Long = 0) = NightSession(
        start = start,
        end = end,
        stages = if (deepMinutes > 0) {
            listOf(StageSlice(SleepSessionRecord.STAGE_TYPE_DEEP, Duration.ofMinutes(deepMinutes)))
        } else {
            emptyList()
        },
    )

    @Test fun `a night crossing midnight belongs to the morning's date`() {
        val byDay = longestNightPerDay(
            listOf(night(at(day1, "23:10"), at(day2, "06:40"))),
            zone,
        )
        assertTrue("Natten ska dateras efter sitt slut", byDay.containsKey(day2))
        assertFalse(byDay.containsKey(day1))
        assertEquals(Duration.ofMinutes(450), byDay.getValue(day2).duration)
    }

    @Test fun `the longest session wins when a night is split in two`() {
        val byDay = longestNightPerDay(
            listOf(
                // Samsung delar ibland en avbruten sömn i två sessioner samma natt.
                night(at(day1, "22:30"), at(day2, "01:00")),
                night(at(day2, "01:30"), at(day2, "07:00"), deepMinutes = 60),
            ),
            zone,
        )
        assertEquals(1, byDay.size)
        assertEquals(Duration.ofMinutes(330), byDay.getValue(day2).duration)
        assertEquals(Duration.ofMinutes(60), summarizeSleepStages(byDay.getValue(day2).stages).deep)
    }

    @Test fun `separate nights land on separate days`() {
        val byDay = longestNightPerDay(
            listOf(
                night(at(day1, "23:00"), at(day2, "07:00")),
                night(at(day2, "23:00"), at(day3, "07:00")),
            ),
            zone,
        )
        assertEquals(setOf(day2, day3), byDay.keys)
    }

    @Test fun `the midpoint of a night is the clock time halfway through it`() {
        val midpoint = midpointOf(SleepWindow(at(day1, "23:00"), at(day2, "07:00")), zone)
        assertEquals(LocalTime.of(3, 0), midpoint)
    }

    // ─── Syremättnad per dygn ─────────────────────────────────────────────────

    @Test fun `oxygen saturation is averaged per day`() {
        val byDay = averageByDay(
            listOf(
                TimedValue(at(day1, "02:00"), 95.0),
                TimedValue(at(day1, "03:00"), 93.0),
                TimedValue(at(day2, "02:00"), 90.0),
            ),
            zone,
        )
        assertEquals(94.0, byDay.getValue(day1), 0.001)
        assertEquals(90.0, byDay.getValue(day2), 0.001)
    }
}
