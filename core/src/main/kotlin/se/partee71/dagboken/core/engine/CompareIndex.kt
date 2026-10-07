package se.partee71.dagboken.core.engine

import kotlinx.datetime.LocalDate
import se.partee71.dagboken.core.model.Activity
import se.partee71.dagboken.core.model.HealthHistory
import se.partee71.dagboken.core.model.Occasion
import se.partee71.dagboken.core.model.Screening
import se.partee71.dagboken.core.model.Sex

// Jämför (TRD-17) – portat från 3.x `normalizeSeries`, `ComparableSpec` och `buildComparison` i
// `TrenderViewModel`: valfria serier ur hela appen i samma diagram, var och en indexerad 0–100 mot sitt
// eget lägsta och högsta värde i perioden. Y-axeln visar index; de verkliga värdena står i legenden.

/** Jämför-diagrammets fasta y-axel: index 0–100 i steg om 25 (TRD-17). */
val COMPARE_AXIS = SmartYAxis(0f..COMPARE_MAX, COMPARE_STEP)

/**
 * Indexerar en serie **0–100 mot sina egna** min/max (TRD-17). Utan det plattar den största serien ut de
 * andra: steg ligger runt 10 000 och energi på 0–10. En serie med konstanta värden får mittlinjen (50) i
 * stället för en division med noll, och luckor förblir luckor – aldrig nollor.
 */
fun indexSeries(points: List<Float?>): List<Float?> {
    val known = points.filterNotNull()
    if (known.isEmpty()) return points
    val min = known.min()
    val max = known.max()
    if (max - min < CONSTANT_EPSILON) return points.map { value -> value?.let { COMPARE_MAX / 2 } }
    return points.map { value -> value?.let { (it - min) / (max - min) * COMPARE_MAX } }
}

/** En jämförd serie: [points] indexerade 0–100, och seriens **verkliga** lägsta och högsta värde för legenden. */
data class ComparedSerie(val key: String, val points: List<Float?>, val min: Float, val max: Float)

/** Indexerar [series] (TRD-17); en serie utan ett enda värde i perioden tas inte med – den har inget att visa. */
fun compareSeries(series: List<TrendSerie>): List<ComparedSerie> = series.mapNotNull { serie ->
    val summary = summarize(serie.points) ?: return@mapNotNull null
    ComparedSerie(serie.key, indexSeries(serie.points), summary.min, summary.max)
}

/**
 * Vad en serie i Jämför är (TRD-17) – dagbokens egna serier (enheten är skalan 0–10) eller klockans
 * (HLS-12, HLS-13). [wire] är nyckeln i `TrendSerie.key` och i kortets serieval; [parse] läser tillbaka den.
 */
sealed interface CompareKey {
    val wire: String
    val unit: WatchUnit

    /** Kommer ur klockan – kräver en hälsoläsning (TRD-17: klockan läses bara när någon valt den). */
    val fromWatch: Boolean get() = false

    /** Energi (dag): samma dagsvärde som TRD-8. */
    data object EnergyDay : CompareKey {
        override val wire = "energy"
        override val unit = WatchUnit.SCALE
    }

    data class EnergyOccasion(val occasion: Occasion) : CompareKey {
        override val wire = "$OCCASION:${occasion.wire}"
        override val unit = WatchUnit.SCALE
    }

    data class Stress(val series: StressSeries) : CompareKey {
        override val wire = "$STRESS:${series.name}"
        override val unit = WatchUnit.SCALE
    }

    /** Ett symptom ur Listor; namnet slås upp av skärmen (SET-11). */
    data class Symptom(val optionId: String) : CompareKey {
        override val wire = "$SYMPTOM:$optionId"
        override val unit = WatchUnit.SCALE
    }

    data class Watch(val metric: WatchMetric) : CompareKey {
        override val wire = "$WATCH:${metric.name}"
        override val unit = metric.unit
        override val fromWatch = true
    }

    /** Nattens sömnkvalitetspoäng (HLS-13). */
    data object SleepQuality : CompareKey {
        override val wire = "sleepQuality"
        override val unit = WatchUnit.POINTS
        override val fromWatch = true
    }

    companion object {
        private const val OCCASION = "occasion"
        private const val STRESS = "stress"
        private const val SYMPTOM = "symptom"
        private const val WATCH = "watch"

        /** Nyckeln tillbaka till sin serie; `null` för en nyckel som inte är en Jämför-serie. */
        fun parse(wire: String): CompareKey? {
            if (wire == EnergyDay.wire) return EnergyDay
            if (wire == SleepQuality.wire) return SleepQuality
            val (prefix, id) = wire.split(':', limit = 2).takeIf { it.size == 2 } ?: return null
            return when (prefix) {
                OCCASION -> Occasion.entries.firstOrNull { it.wire == id }?.let(::EnergyOccasion)
                STRESS -> StressSeries.entries.firstOrNull { it.name == id }?.let(::Stress)
                SYMPTOM -> Symptom(id).takeIf { id.isNotEmpty() }
                WATCH -> WatchMetric.entries.firstOrNull { it.name == id }?.let(::Watch)
                else -> null
            }
        }
    }
}

/**
 * Klockans valbara serier i Jämför, i menyns ordning (TRD-17): steg, pulserna, sömnlängd, djup, REM,
 * sömnkvalitet, träning, kalorier, sträcka och syremättnad. Lätt sömn och vaken tid
 * jämförs inte (som 3.x).
 */
val WATCH_COMPARE_KEYS: List<CompareKey> = listOf(
    CompareKey.Watch(WatchMetric.STEPS),
    CompareKey.Watch(WatchMetric.RESTING_HEART_RATE),
    CompareKey.Watch(WatchMetric.HEART_RATE_AVG),
    CompareKey.Watch(WatchMetric.SLEEP_TOTAL),
    CompareKey.Watch(WatchMetric.SLEEP_DEEP),
    CompareKey.Watch(WatchMetric.SLEEP_REM),
    CompareKey.SleepQuality,
    CompareKey.Watch(WatchMetric.EXERCISE),
    CompareKey.Watch(WatchMetric.ACTIVE_CALORIES),
    CompareKey.Watch(WatchMetric.DISTANCE),
    CompareKey.Watch(WatchMetric.OXYGEN_SATURATION),
)

/**
 * Dagbokens serier för Jämför över [days] (TRD-17), med [CompareKey]-nycklar: Energi (dag) (TRD-8), de fyra
 * tillfällena, de fyra stresserierna och periodens symptom – samma uträkningar som gruppen Måendes kort.
 */
fun moodCompareSeries(screenings: List<Screening>, activities: List<Activity>, days: List<LocalDate>): List<TrendSerie> =
    listOf(TrendSerie(CompareKey.EnergyDay.wire, dailyEnergyAverages(screenings, days))) +
        energyByOccasion(screenings, days).map { it.rekey { key -> CompareKey.EnergyOccasion(Occasion.entries.first { it.wire == key }) } } +
        stressSeries(screenings, activities, days).map { it.rekey { key -> CompareKey.Stress(StressSeries.valueOf(key)) } } +
        symptomSeries(screenings, activities, days).map { it.rekey(CompareKey::Symptom) }

/**
 * Klockans serier för Jämför över [days], bara för [keys] (de valda) i [WATCH_COMPARE_KEYS] ordning. Nätterna
 * poängsätts (`sleepQualitySeries`) bara när sömnkvaliteten är med – det är den dyra delen (TRD-17).
 */
fun watchCompareSeries(history: HealthHistory, age: Int?, sex: Sex, days: List<LocalDate>, keys: Collection<CompareKey> = WATCH_COMPARE_KEYS): List<TrendSerie> {
    val wanted = WATCH_COMPARE_KEYS.filter { it in keys }
    val metrics = watchSeries(history, wanted.filterIsInstance<CompareKey.Watch>().map { it.metric }, days).associateBy { it.key }
    val score = if (CompareKey.SleepQuality in wanted) sleepQualitySeries(history, age, sex, days, listOf(SLEEP_SCORE_KEY)).single() else null
    return wanted.map { key ->
        when (key) {
            is CompareKey.Watch -> metrics.getValue(key.metric.name).copy(key = key.wire)
            else -> checkNotNull(score).copy(key = key.wire)
        }
    }
}

private fun TrendSerie.rekey(key: (String) -> CompareKey) = copy(key = key(this.key).wire)

private const val COMPARE_MAX = 100f
private const val COMPARE_STEP = 25f
private const val CONSTANT_EPSILON = 1e-6f
