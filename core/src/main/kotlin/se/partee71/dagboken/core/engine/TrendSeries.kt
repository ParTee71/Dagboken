package se.partee71.dagboken.core.engine

import kotlinx.datetime.LocalDate
import se.partee71.dagboken.core.model.Activity
import se.partee71.dagboken.core.model.Checkin
import se.partee71.dagboken.core.model.Event
import se.partee71.dagboken.core.model.IllnessEpisode
import se.partee71.dagboken.core.model.Occasion
import se.partee71.dagboken.core.model.Screening
import se.partee71.dagboken.core.model.somatic

// Trenders serier i gruppen Mående (TRD-1, TRD-2, TRD-8, TRD-21) – portade från 3.x `computeCategoryDataFor`
// i `TrenderViewModel`, med en punkt per dag i periodens x-axel (`null` = lucka, aldrig en nolla, TRD-15).
// I 4.0 är måendeloggar och aktiviteter egna samlingar, så typfiltret `type == "screening"` behövs inte.

/**
 * En serie i ett av Trenders linjediagram: [key] identifierar serien – tillfällets `wire`, [StressSeries]-namnet
 * eller symptomets alternativ-id – och [points] är ett värde per dag i periodens ordning.
 */
data class TrendSerie(val key: String, val points: List<Float?>)

/** TRD-8: dagens spann och dagsvärde (samma `computeDailyEnergyStats` som Idag, HEM-7) utlagt på [days]. */
fun dailyEnergyPoints(screenings: List<Screening>, days: List<LocalDate>): List<IntervalPoint?> =
    computeDailyEnergyStats(screenings).alignTo(days).map { day -> day?.let { IntervalPoint(it.min, it.avg, it.max) } }

/**
 * Energi per tillfälle (TRD-1): en serie per [Occasion] i tillfällenas ordning, dagens snittenergi för loggarna
 * med det tillfället. En logg utan tillfälle (3.x-screening med eget namn, DAT-12) hör till ingen serie.
 */
fun energyByOccasion(screenings: List<Screening>, days: List<LocalDate>): List<TrendSerie> = Occasion.entries.map { occasion ->
    TrendSerie(occasion.wire, dailyAverage(screenings.filter { it.occasion == occasion }, days, { it.date }, { it.energy }))
}

/** Stress och belastning (TRD-1): de fyra serierna, i diagrammets ordning. */
enum class StressSeries { STRESS, SOMATIC, RECOVERING, DRAIN }

/** Det båda posttyperna bidrar med till Stress och belastning: 3.x räknade över alla rader i `aktiviteter`. */
private class MoodEntry(val date: LocalDate?, val stress: Int, val somatic: Int, val recovering: Boolean, val drain: Boolean)

private fun moodEntries(screenings: List<Screening>, activities: List<Activity>): List<MoodEntry> =
    screenings.map { MoodEntry(it.date, it.stress, it.symptoms.somatic, recovering = false, drain = false) } +
        activities.map { MoodEntry(it.date, it.stress, it.symptoms.somatic, it.recovering, it.drain) }

/**
 * Stress och belastning (TRD-1), som 3.x: dagens snitt av stress och av symptompoängens summa (`somatic`,
 * DAT-6) över måendeloggar **och** aktiviteter, och andelen av dagens poster som var återhämtande respektive
 * energitjuvar på skalan 0–10 (andel × 10). En dag utan poster är en lucka i alla fyra.
 */
fun stressSeries(screenings: List<Screening>, activities: List<Activity>, days: List<LocalDate>): List<TrendSerie> {
    val entries = moodEntries(screenings, activities)
    fun share(flag: (MoodEntry) -> Boolean) = dailyAverage(entries, days, { it.date }) { if (flag(it)) SCALE_MAX else 0 }
    return listOf(
        TrendSerie(StressSeries.STRESS.name, dailyAverage(entries, days, { it.date }, { it.stress })),
        TrendSerie(StressSeries.SOMATIC.name, dailyAverage(entries, days, { it.date }, { it.somatic })),
        TrendSerie(StressSeries.RECOVERING.name, share { it.recovering }),
        TrendSerie(StressSeries.DRAIN.name, share { it.drain }),
    )
}

/**
 * Symptom (TRD-1): en serie per symptom som loggats i perioden – nyckeln är alternativets id, så att namnet
 * kommer ur Listor (SET-11) – med dagens snittpoäng för det symptomet över måendeloggar och aktiviteter.
 * Dagar då symptomet inte loggats är luckor. Serierna i id-ordning; tom lista utan symptom.
 */
fun symptomSeries(screenings: List<Screening>, activities: List<Activity>, days: List<LocalDate>): List<TrendSerie> {
    val scored = screenings.flatMap { s -> s.symptoms.map { Triple(s.date, it.optionId, it.score) } } +
        activities.flatMap { a -> a.symptoms.map { Triple(a.date, it.optionId, it.score) } }
    val period = days.toSet()
    val inPeriod = scored.filter { it.first in period }
    return inPeriod.map { it.second }.distinct().sorted().map { id ->
        TrendSerie(id, dailyAverage(inPeriod.filter { it.second == id }, days, { it.first }, { it.third }))
    }
}

/**
 * En sjukdomsepisod i perioden (TRD-21): [from]…[to] är indexen på x-axeln den täcker, kapade till perioden.
 * En pågående episod ([IllnessEpisode.end] `null`) sträcker sig till periodens sista dag.
 */
data class EpisodeSpan(val episode: IllnessEpisode, val from: Int, val to: Int) {
    val ongoing: Boolean get() = episode.end == null
}

/**
 * Händelser och sjukdom (TRD-21): [events] är händelsernas snittsvårighet per dag (staplarna), [checkins]
 * incheckningarnas snittsvårighet per dag över alla episoder (linjen), [episodes] episoderna som ligger i
 * perioden (de tonade fälten) i startordning, [eventCount] antalet händelser i perioden och [averageSeverity]
 * deras snitt (`null` utan händelser).
 */
data class EventIllnessTrend(
    val events: List<Float?>,
    val checkins: List<Float?>,
    val episodes: List<EpisodeSpan>,
    val eventCount: Int,
    val averageSeverity: Float?,
)

/**
 * TRD-21 över [days]: händelserna med dag i perioden, episoderna som överlappar den (en episod utan startdatum
 * hör inte till någon dag) och [checkins] per episod-id.
 */
fun eventIllnessTrend(
    events: List<Event>,
    episodes: List<IllnessEpisode>,
    checkins: Map<String, List<Checkin>>,
    days: List<LocalDate>,
): EventIllnessTrend {
    val first = days.firstOrNull()
    val last = days.lastOrNull()
    val inPeriod = if (first == null || last == null) emptyList() else events.filter { it.date != null && it.date in first..last }
    val spans = if (first == null || last == null) {
        emptyList()
    } else {
        episodes.mapNotNull { episode ->
            val start = episode.start ?: return@mapNotNull null
            val end = episode.end ?: last
            if (end < first || start > last) return@mapNotNull null
            EpisodeSpan(episode, days.indexOf(maxOf(start, first)), days.indexOf(minOf(end, last)))
        }.sortedWith(compareBy({ it.episode.start }, { it.episode.id }))
    }
    return EventIllnessTrend(
        events = dailyAverage(inPeriod, days, { it.date }, { it.severity }),
        checkins = dailyAverage(checkins.values.flatten(), days, { it.date }, { it.severity }),
        episodes = spans,
        eventCount = inPeriod.size,
        averageSeverity = inPeriod.map { it.severity }.takeIf { it.isNotEmpty() }?.average()?.toFloat(),
    )
}

/** Dagens snitt av [value] över de av [entries] som hör till dagen, för var och en av [days]; `null` utan poster. */
private fun <T> dailyAverage(entries: List<T>, days: List<LocalDate>, date: (T) -> LocalDate?, value: (T) -> Int): List<Float?> {
    val byDay = entries.groupBy(date)
    return days.map { day -> byDay[day]?.map { value(it) }?.takeIf { it.isNotEmpty() }?.average()?.toFloat() }
}

/** Skalans tak (0–10) för andelarna i Stress och belastning. */
private const val SCALE_MAX = 10
