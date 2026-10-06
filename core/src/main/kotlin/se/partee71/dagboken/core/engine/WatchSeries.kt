package se.partee71.dagboken.core.engine

import kotlin.time.Duration
import kotlinx.datetime.LocalDate
import se.partee71.dagboken.core.model.DailyHealth
import se.partee71.dagboken.core.model.HealthHistory
import se.partee71.dagboken.core.model.Sex

// Trenders serier i gruppen Klocka (TRD-11, TRD-15, TRD-16, HLS-12, HLS-13) – portade från 3.x
// `HealthSeriesSpec` och `buildHealthTrend` i `TrenderViewModel`, med en punkt per dag på periodens
// x-axel (`null` = lucka, aldrig en nolla). Klockdatan läses live och persisteras aldrig (HLS-5);
// allt här är härlett ur `HealthHistory` i stunden.

/** Enheten ett klockmått eller en jämförd serie mäts i – texten sätter skärmen (TRD-15, TRD-17). */
enum class WatchUnit { STEPS, BPM, HOURS, MINUTES, KCAL, KM, PERCENT, MMHG, POINTS, SCALE }

/**
 * Klockans mått som diagramserier (HLS-12): varje mått plockas ur dygnet i diagrammets egen enhet –
 * timmar, minuter, kilometer, kcal, procent, mmHg eller bpm. Namnet är nyckeln i `TrendSerie.key`.
 */
enum class WatchMetric(val unit: WatchUnit) {
    STEPS(WatchUnit.STEPS),
    RESTING_HEART_RATE(WatchUnit.BPM),
    HEART_RATE_AVG(WatchUnit.BPM),
    SLEEP_TOTAL(WatchUnit.HOURS),
    SLEEP_DEEP(WatchUnit.HOURS),
    SLEEP_REM(WatchUnit.HOURS),
    SLEEP_LIGHT(WatchUnit.HOURS),
    SLEEP_AWAKE(WatchUnit.HOURS),
    EXERCISE(WatchUnit.MINUTES),
    ACTIVE_CALORIES(WatchUnit.KCAL),
    DISTANCE(WatchUnit.KM),
    OXYGEN_SATURATION(WatchUnit.PERCENT),
    SYSTOLIC(WatchUnit.MMHG),
    DIASTOLIC(WatchUnit.MMHG),
    ;

    /** Måttets värde ett dygn, i måttets enhet; `null` när dygnet saknar det (en lucka, HLS-12). */
    fun value(day: DailyHealth): Float? = when (this) {
        STEPS -> day.steps?.toFloat()
        RESTING_HEART_RATE -> day.restingHeartRate?.toFloat()
        HEART_RATE_AVG -> day.heartRateAvg?.toFloat()
        SLEEP_TOTAL -> day.sleepDuration?.hours()
        SLEEP_DEEP -> day.sleepStages.deep?.hours()
        SLEEP_REM -> day.sleepStages.rem?.hours()
        SLEEP_LIGHT -> day.sleepStages.light?.hours()
        SLEEP_AWAKE -> day.sleepStages.awake?.hours()
        EXERCISE -> day.exerciseDuration?.inWholeMinutes?.toFloat()
        ACTIVE_CALORIES -> day.activeEnergyKcal?.toFloat()
        DISTANCE -> day.distanceMeters?.let { (it / METERS_PER_KM).toFloat() }
        OXYGEN_SATURATION -> day.oxygenSaturationAvg?.toFloat()
        SYSTOLIC -> day.bloodPressure?.systolic?.toFloat()
        DIASTOLIC -> day.bloodPressure?.diastolic?.toFloat()
    }
}

/** Sömnstadierna nedifrån och upp i det staplade diagrammet (TRD-16): djup, REM, lätt, vaken. */
val SLEEP_STAGE_METRICS: List<WatchMetric> =
    listOf(WatchMetric.SLEEP_DEEP, WatchMetric.SLEEP_REM, WatchMetric.SLEEP_LIGHT, WatchMetric.SLEEP_AWAKE)

/** Sömn-diagrammets serier i timmar (TRD-15): totalen först, sedan stadierna. */
val SLEEP_METRICS: List<WatchMetric> = listOf(WatchMetric.SLEEP_TOTAL) + SLEEP_STAGE_METRICS

/** Klockans läsningar kapas vid ett år bakåt även för perioden "Allt" (TRD-3, TRD-15, TRD-17), som 3.x. */
const val WATCH_MAX_DAYS = 365

/** Perioden för ett klockkort eller Jämför: som [TrendRange.days], men "Allt" är de senaste [WATCH_MAX_DAYS] dagarna. */
fun TrendRange.cappedDays(today: LocalDate): List<LocalDate> = daysEnding(today, days ?: WATCH_MAX_DAYS)

/** Läsningens första dag för ett kapat kort: som [TrendRange.readFrom], men "Allt" börjar [WATCH_MAX_DAYS] dagar bakåt. */
fun TrendRange.cappedReadFrom(today: LocalDate, withPrevious: Boolean): LocalDate =
    if (days == null) cappedDays(today).first() else readFrom(today, withPrevious)

/**
 * Klockans serier för [metrics] över [days] (TRD-11, TRD-15): ett värde per dag ur [history], `null` där
 * dygnet saknar måttet eller ligger utanför historiken. Nycklarna är måttens namn.
 */
fun watchSeries(history: HealthHistory, metrics: List<WatchMetric>, days: List<LocalDate>): List<TrendSerie> {
    val byDate = history.days.associateBy { it.date }
    return metrics.map { metric -> TrendSerie(metric.name, days.map { day -> byDate[day]?.let(metric::value) }) }
}

/**
 * Sömnstadierna per natt som staplar (TRD-16) i [SLEEP_STAGE_METRICS] ordning, i timmar. Ett stadium som
 * saknas en natt är `null` och tar ingen höjd; en natt utan stadier är en lucka.
 */
fun sleepStagePoints(history: HealthHistory, days: List<LocalDate>): List<StackedPoint> {
    val stages = watchSeries(history, SLEEP_STAGE_METRICS, days)
    return days.indices.map { i -> StackedPoint(stages.map { it.points[i] }) }
}

/** Nyckeln för sömnkvalitetens totalpoäng; delpoängen har [SleepQualityKind]-namnet som nyckel. */
const val SLEEP_SCORE_KEY = "SCORE"

/**
 * Sömnkvalitetskortets serier i ordning: poängen först, sedan delpoängen (TRD-15, HLS-10). Regelbundenheten
 * (HLS-13) döljs tills dagshistoriken bär sömnens mittpunkter (#243) – en alltid tom serie vore bara brus.
 */
val SLEEP_QUALITY_KEYS: List<String> = listOf(SLEEP_SCORE_KEY) + SleepQualityKind.entries.filter { it != SleepQualityKind.REGULARITY }.map { it.name }

/**
 * Nätterna i [history] som underlag för sömnkvaliteten (HLS-13): tiden i säng är sömnsessionens längd,
 * vaken tid, djup och REM kommer ur stadierna. Mittpunktens spridning (regelbundenhet) finns inte i
 * dagshistoriken än, så den komponenten faller bort och vikterna normaliseras om (HLS-10). En natt utan
 * sömnlängd är ingen natt.
 */
fun sleepMeasurements(history: HealthHistory): List<NightlySleepMeasurements> = history.days.mapNotNull { day ->
    val timeInBed = day.sleepDuration ?: return@mapNotNull null
    NightlySleepMeasurements(
        day.date,
        SleepMeasurements(timeInBed = timeInBed, awake = day.sleepStages.awake, deep = day.sleepStages.deep, rem = day.sleepStages.rem),
    )
}

/**
 * Sömnkvalitet per natt över [days] (TRD-15, HLS-13): poängen ([SLEEP_SCORE_KEY]) och delpoängen i
 * [SLEEP_QUALITY_KEYS], alla 0–100, för de [keys] som efterfrågas (nätterna poängsätts en gång oavsett antal).
 * Utan [age] (födelseår saknas, HLS-11) är varje natt en lucka; en delkomponent som inte gick att räkna för en
 * natt är en lucka i sin serie.
 */
fun sleepQualitySeries(history: HealthHistory, age: Int?, sex: Sex, days: List<LocalDate>, keys: List<String> = SLEEP_QUALITY_KEYS): List<TrendSerie> {
    if (keys.isEmpty()) return emptyList()
    val byDate = scoreNightlySleep(sleepMeasurements(history), age, sex).associateBy { it.date }
    fun serie(key: String, value: (SleepQuality) -> Int?) = TrendSerie(key, days.map { day -> byDate[day]?.quality?.let(value)?.toFloat() })
    return keys.map { key ->
        if (key == SLEEP_SCORE_KEY) {
            serie(key) { it.score }
        } else {
            val kind = SleepQualityKind.valueOf(key)
            serie(key) { quality -> quality.components.firstOrNull { it.kind == kind }?.score }
        }
    }
}

private fun Duration.hours(): Float = inWholeMinutes / MINUTES_PER_HOUR

private const val MINUTES_PER_HOUR = 60f
private const val METERS_PER_KM = 1000.0
