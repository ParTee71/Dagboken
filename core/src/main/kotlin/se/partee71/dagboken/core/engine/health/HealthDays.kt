package se.partee71.dagboken.core.engine.health

import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.plus
import se.partee71.dagboken.core.model.DailyHealth
import se.partee71.dagboken.core.model.HealthHistory
import se.partee71.dagboken.core.model.SleepStages

// Dygnen (HLS-8, HLS-12): ur en periods poster (`HealthRecords`) blir ett `DailyHealth` per dygn – samma
// modell som `HealthRepository` lämnar ut. Portat från 3.x `readToday`, `readCoreHistory` och
// `readHealthHistory` i `HealthConnectRepository.kt`, med zonen som parameter i stället för systemets.

/** Syremättnaden mäts under natten och läses över samma fönster som sömnen (HLS-8). */
val NIGHT_WINDOW: Duration = 24.hours

/** Snittet av syremättnaden i [samples], `null` utan mätningar. */
fun averageOxygen(samples: List<OxygenSample>): Double? = samples.takeIf { it.isNotEmpty() }?.map { it.percent }?.average()

/**
 * Syremättnaden per dygn (HLS-12). Ett prov inom en sömnsession i [sleep] dateras efter sessionens **slut**, så
 * att hela natten – också proven före midnatt – hamnar på morgonens datum, som nattens sömn och som
 * 24-timmarsfönstret i [healthDay]. Prov utanför sömnen dateras efter sin egen tid.
 */
fun averageOxygenByDay(samples: List<OxygenSample>, sleep: List<TimeSpan>, zone: TimeZone): Map<LocalDate, Double> {
    val windows = SleepWindows(sleep)
    return samples.perDay(zone, { sample -> windows.containing(sample.time)?.end ?: sample.time }, ::averageOxygen)
}

/**
 * Dagshistoriken för [from]…[to] (HLS-12): exakt ett [DailyHealth] per dygn i datumordning, tomt där inget
 * mättes – en lucka, aldrig en nolla. Summerbara mått väljs per källa per dygn (HLS-2, HLS-8), träningspass
 * dedupliceras på tidsöverlapp över hela perioden och fördelas sedan per dygn, vilopulsen tar registrerat värde före skattning (HLS-7) och en
 * natt dateras efter sessionens slut med den längsta sessionen som natten. En post hör annars till dygnet
 * dess starttid faller på i [zone].
 */
fun healthHistory(records: HealthRecords, from: LocalDate, to: LocalDate, zone: TimeZone): HealthHistory {
    val steps = mostCompleteSumByDay(records.steps, zone)
    val heartRateAvg = averageBpmByDay(records.heartRate, zone)
    val restingHr = restingHeartRateByDay(records.restingHeartRate, records.heartRate, records.sleep, zone)
    val nights = longestNightPerDay(records.sleep, zone)
    val exercise = mostCompleteExerciseByDay(records.exercise, zone)
    val calories = mostCompleteSumByDay(records.calories, zone)
    val distance = mostCompleteSumByDay(records.distance, zone)
    val oxygen = averageOxygenByDay(records.oxygen, records.sleep, zone)

    val dates = steps.keys + heartRateAvg.keys + restingHr.keys + nights.keys + exercise.keys +
        calories.keys + distance.keys + oxygen.keys
    val measured = dates.associateWith { date ->
        val night = nights[date]
        DailyHealth(
            date = date,
            steps = steps[date]?.toStepCount(),
            restingHeartRate = restingHr[date],
            heartRateAvg = heartRateAvg[date],
            sleepDuration = night?.duration?.takeIf { it.isPositive() },
            sleepStages = night?.let { summarizeSleepStages(it.stages) } ?: SleepStages(),
            exerciseSessions = exercise[date]?.sessions ?: 0,
            exerciseDuration = exercise[date]?.duration,
            activeEnergyKcal = calories[date],
            distanceMeters = distance[date],
            oxygenSaturationAvg = oxygen[date],
        )
    }
    return HealthHistory.of(from, to, measured)
}

/**
 * Dygnet [date] som Hälsa idag visar det (HLS-6, HLS-8). Alla fönster är halvöppna `[start, end)` som
 * [TimeSpan], så en post exakt vid nästa midnatt hör bara till nästa datum:
 * - dygnets mått (steg, puls, vilopuls, träning, kalorier, sträcka) från midnatt fram till [now], eller till
 *   dygnets slut för ett tidigare datum;
 * - sömnen som **summan** av längd och stadier över alla sessioner som slutar inom [NIGHT_WINDOW] före
 *   dygnets slut – paritet med 3.x `readToday`;
 * - syremättnaden över samma fönster.
 *
 * **Skillnad mot [healthHistory]:** historiken tar den längsta sessionen per natt (HLS-12), så en natt delad i
 * två sessioner ger kortare sömn där än här; syremättnaden räknas där per dygn i stället för över ett
 * glidande fönster. Övriga mått är desamma för ett avslutat dygn.
 *
 * [records] ska läsa sömnen med `from` minus 24 timmar (som [HealthRecords] säger), annars saknas nattens
 * början och vilopulsens sömnfilter. Inget mätt ger ett tomt [DailyHealth].
 */
fun healthDay(records: HealthRecords, date: LocalDate, now: Instant, zone: TimeZone): DailyHealth {
    val dayStart = date.atStartOfDayIn(zone)
    val dayEnd = minOf(date.plus(1, DateTimeUnit.DAY).atStartOfDayIn(zone), now)
    val day = TimeWindow(dayStart, dayEnd)
    val night = TimeWindow(dayEnd - NIGHT_WINDOW, dayEnd)
    fun <T : TimeSpan> List<T>.startingInDay() = filter { it.start in day }

    val heartRate = records.heartRate.filter { it.time in day }
    val lastNight = records.sleep.filter { it.end in night }
    val sleepDuration = lastNight.fold(Duration.ZERO) { acc, session -> acc + session.duration }
    val exercise = mostCompleteExercise(records.exercise.startingInDay())
    return DailyHealth(
        date = date,
        steps = mostCompleteSteps(records.steps.startingInDay()),
        restingHeartRate = restingHeartRate(records.restingHeartRate.filter { it.time in day }, heartRate, records.sleep),
        heartRateAvg = averageBpm(heartRate),
        sleepDuration = sleepDuration.takeIf { it.isPositive() },
        sleepStages = summarizeSleepStages(lastNight.flatMap { it.stages }),
        exerciseSessions = exercise?.sessions ?: 0,
        exerciseDuration = exercise?.duration,
        activeEnergyKcal = mostCompleteSum(records.calories.startingInDay()),
        distanceMeters = mostCompleteSum(records.distance.startingInDay()),
        oxygenSaturationAvg = averageOxygen(records.oxygen.filter { it.time in night }),
    )
}
