package se.partee71.dagboken.core.engine

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import se.partee71.dagboken.core.model.Feeling
import se.partee71.dagboken.core.model.FeelingCatalog
import se.partee71.dagboken.core.model.HasFeelings
import se.partee71.dagboken.core.model.Occasion
import se.partee71.dagboken.core.model.Post
import se.partee71.dagboken.core.model.Screening

// Känslornas sammanställning (#320): Idags kort Dagens känslor och Trenders kort Känslor bygger på samma räkning,
// så att de aldrig säger olika saker (som `computeDailyEnergyStats`, HEM-7). Bara tokens och roller – meningen i ord
// formateras i appen (`ui/common/FeelingText.kt`), som har resurserna. Inga tal visas för känslor.

/**
 * En post med känslor som räknas: dagen, valfritt klockslag och måendetillfälle (en aktivitet har inget tillfälle)
 * och de loggade [feelings]. Byggs ur en måendelogg med [feelingEntry] och ur en annan post med [toFeelingEntry].
 */
data class FeelingEntry(
    val date: LocalDate,
    val feelings: List<Feeling>,
    val time: LocalTime? = null,
    val occasion: Occasion? = null
)

/** Postens känslor som [FeelingEntry] (måendelogg, aktivitet); en post utan datum hör inte till någon dag → `null`. */
fun <T> T.toFeelingEntry(time: LocalTime? = null, occasion: Occasion? = null): FeelingEntry? where T : Post<T>, T : HasFeelings<T> = date?.let { FeelingEntry(it, feelings, time, occasion) }

/** Måendeloggens känslor med dess klockslag och tillfälle; utan datum → `null`. */
fun Screening.feelingEntry(): FeelingEntry? = toFeelingEntry(time, occasion)

/** Hur många gånger en nyans valts under sin kärna. */
data class NuanceCount(val nuance: String, val count: Int)

/**
 * En kärnkänslas val: [count] gånger, [share] av alla val (0–1), [weight] mot den vanligaste kärnan (0–1, 1 för den
 * vanligaste) – fyllnaden i minihjulet och längden i ordträdet – och [nuances] med minst ett val, vanligast först
 * (lika många → katalogens ordning). Ett val utan nyans räknas bara på kärnan.
 */
data class CoreCount(
    val core: String,
    val count: Int,
    val share: Float,
    val weight: Float,
    val nuances: List<NuanceCount>
) {
    /** De [limit] vanligaste nyanserna, vanligast först – orden i värmekartans sektor och i ordträdets stapel. */
    fun topNuances(limit: Int = TOP_NUANCES): List<String> = nuances.take(limit).map { it.nuance }
}

/**
 * Räkningen för en mängd poster: [total] val och en [CoreCount] per kärna i hjulets ordning ([FeelingCatalog.cores]),
 * också kärnor utan val. En känsla med en kärna appen inte känner (ett token från en nyare app) räknas inte.
 */
data class FeelingTally(val total: Int, val cores: List<CoreCount>) {
    val isEmpty: Boolean get() = total == 0

    /** Kärnorna med minst ett val, vanligast först; lika många → hjulets ordning. */
    val ranked: List<CoreCount> get() = cores.filter { it.count > 0 }.sortedByDescending { it.count }

    /** Alla kärnor, vanligast först (lika många → hjulets ordning), de utan val sist – ordträdets rader. */
    val byCount: List<CoreCount> get() = cores.sortedByDescending { it.count }

    /** Kärnans räkning; en okänd kärna → `null`. */
    fun of(core: String): CoreCount? = cores.firstOrNull { it.core == core }

    companion object {
        /** Inga val: alla kärnor med noll. */
        val EMPTY: FeelingTally = tally(emptyList())
    }
}

/** En dags räkning ([FeelingTally]) – Trenders vecka med ett minihjul per dag. */
data class DayFeelings(val date: LocalDate, val tally: FeelingTally)

/**
 * En kärna i meningen: [nuance] när kärnan valts minst [MIN_HABIT_PICKS] gånger och till minst två tredjedelar med samma
 * nyans ("nästan alltid som Orolig"), [occasion] när alla dess val hör till samma måendetillfälle ("vid lunch").
 */
data class FeelingRole(val core: String, val nuance: String? = null, val occasion: Occasion? = null)

/**
 * Meningens struktur: [most] är de (upp till) två vanligaste kärnorna, [little] den tredje (`null` med färre än tre
 * kärnor), [untouched] kärnorna utan val när perioden har minst [MIN_PICKS_FOR_UNTOUCHED] val – och bara när de är
 * högst [MAX_UNTOUCHED] (annars säger "mest" redan allt). Idag använder [most] och [little] med tillfälle, Trender
 * [most], [little] med nyans och [untouched].
 */
data class FeelingSummary(val most: List<FeelingRole>, val little: FeelingRole?, val untouched: List<String>) {
    val isEmpty: Boolean get() = most.isEmpty()
}

/** Sammanställningen: räkningen för alla [entries], meningens struktur och fördelningen per dag (stigande datum). */
data class FeelingStats(val tally: FeelingTally, val summary: FeelingSummary, val days: List<DayFeelings>) {
    /** Dagsfördelningen utlagd på [days] (t.ex. veckan mån–sön); en dag utan känslor får [FeelingTally.EMPTY]. */
    fun alignTo(days: List<LocalDate>): List<DayFeelings> {
        val byDate = this.days.associateBy { it.date }
        return days.map { byDate[it] ?: DayFeelings(it, FeelingTally.EMPTY) }
    }
}

/** Räknar känslorna i [entries] – Idag med den visade dagens måendeloggar, Trender med periodens loggar och aktiviteter. */
fun computeFeelingStats(entries: List<FeelingEntry>): FeelingStats {
    val tally = tally(entries)
    val days = entries.groupBy { it.date }.entries.sortedBy { it.key }.map { (date, ofDay) -> DayFeelings(date, tally(ofDay)) }
    return FeelingStats(tally, summaryOf(tally, entries), days)
}

/**
 * En dag för sig: [stats] över dagens poster och dagens [words] i tidsordning (poster utan klockslag sist, varje ord en
 * gång) – Idags kort Dagens känslor och en dag i Trenders vecka (#320). [isEmpty] när dagen saknar känslor appen känner.
 */
data class FeelingDay(val date: LocalDate, val stats: FeelingStats, val words: List<Feeling>) {
    val isEmpty: Boolean get() = stats.tally.isEmpty
}

/** [date]s känslor ur [entries] (andra dagar räknas inte) – en [FeelingDay] också för en dag utan poster. */
fun feelingDay(entries: List<FeelingEntry>, date: LocalDate): FeelingDay {
    val ofDay = entries.filter { it.date == date }.sortedWith(compareBy(nullsLast()) { it.time })
    return FeelingDay(date, computeFeelingStats(ofDay), ofDay.flatMap { it.feelings }.distinct())
}

private fun tally(entries: List<FeelingEntry>): FeelingTally {
    val known = entries.flatMap { it.feelings }.filter { it.core in FeelingCatalog.cores }
    val byCore = known.groupBy { it.core }
    val max = byCore.values.maxOfOrNull { it.size } ?: 0
    val cores = FeelingCatalog.cores.map { core ->
        val picks = byCore[core].orEmpty()
        val nuances = FeelingCatalog.nuances(core)
            .map { nuance -> NuanceCount(nuance, picks.count { it.nuance == nuance }) }
            .filter { it.count > 0 }
            .sortedByDescending { it.count }
        CoreCount(core, picks.size, fraction(picks.size, known.size), fraction(picks.size, max), nuances)
    }
    return FeelingTally(known.size, cores)
}

private fun summaryOf(tally: FeelingTally, entries: List<FeelingEntry>): FeelingSummary {
    val roles = tally.ranked.take(MOST + 1).map { role(it, entries) }
    val untouched = if (tally.total >= MIN_PICKS_FOR_UNTOUCHED) tally.cores.filter { it.count == 0 }.map { it.core } else emptyList()
    return FeelingSummary(roles.take(MOST), roles.getOrNull(MOST), untouched.takeIf { it.size <= MAX_UNTOUCHED }.orEmpty())
}

private fun role(count: CoreCount, entries: List<FeelingEntry>): FeelingRole {
    val habit = count.nuances.firstOrNull()?.takeIf { count.count >= MIN_HABIT_PICKS && it.count * HABIT_DENOMINATOR >= count.count * HABIT_NUMERATOR }
    val occasions = entries.filter { entry -> entry.feelings.any { it.core == count.core } }.map { it.occasion }.distinct()
    return FeelingRole(count.core, habit?.nuance, occasions.singleOrNull())
}

private fun fraction(part: Int, whole: Int): Float = if (whole == 0) 0f else part.toFloat() / whole

/** Så många nyanser skrivs i värmekartans sektor. */
const val TOP_NUANCES = 3

/** Så många kärnor är "mest"; nästa är "lite". */
private const val MOST = 2

/** "<kärna> knappt rört" sägs först när perioden har så många val … */
const val MIN_PICKS_FOR_UNTOUCHED = 10

/** … och bara om högst så många kärnor saknar val. */
const val MAX_UNTOUCHED = 2

/** "Nästan alltid som <nyans>": minst så många val av kärnan, och minst två tredjedelar med nyansen. */
const val MIN_HABIT_PICKS = 3
private const val HABIT_NUMERATOR = 2
private const val HABIT_DENOMINATOR = 3
