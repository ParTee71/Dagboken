package se.partee71.dagboken.core.engine

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import se.partee71.dagboken.core.model.Feeling
import se.partee71.dagboken.core.model.FeelingCatalog
import se.partee71.dagboken.core.model.HasFeelings
import se.partee71.dagboken.core.model.Occasion
import se.partee71.dagboken.core.model.Post
import se.partee71.dagboken.core.model.Screening

// Känslornas sammanställning (#320): Idags kort Dagens känslor och Trenders kort Känslor räknar på samma sätt och
// skriver samma sorts mening ur samma struktur (som `computeDailyEnergyStats`, HEM-7) – men ur olika källor: Idag bara
// den visade dagens måendeloggar (NFR-24, HEM-21), Trender periodens måendeloggar och aktiviteter (TRD-22). Bara tokens
// och roller – meningen i ord formateras i appen (`ui/common/FeelingText.kt`), som har resurserna. Inga tal visas för känslor.

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

    /** Kärnorna med minst ett val, vanligast först; lika många → hjulets ordning. Räknas en gång. */
    val ranked: List<CoreCount> = cores.filter { it.count > 0 }.sortedByDescending { it.count }

    /** Alla kärnor, vanligast först (lika många → hjulets ordning), de utan val sist – ordträdets rader. Räknas en gång. */
    val byCount: List<CoreCount> = ranked + cores.filter { it.count == 0 }

    /** Kärnans räkning; en okänd kärna → `null`. */
    fun of(core: String): CoreCount? = cores.firstOrNull { it.core == core }

    companion object {
        /** Inga val: alla kärnor med noll. */
        val EMPTY: FeelingTally = tally(emptyList())
    }
}

/**
 * En kärna i meningen: [nuance] när kärnan valts minst [MIN_HABIT_PICKS] gånger och till minst två tredjedelar med samma
 * nyans ("nästan alltid som Orolig"), [occasion] när alla dess val hör till samma måendetillfälle ("vid lunch").
 */
data class FeelingRole(val core: String, val nuance: String? = null, val occasion: Occasion? = null)

/**
 * Meningens struktur: [most] är de vanligaste kärnorna (normalt två), [little] den tredje, [untouched] kärnorna utan val
 * när perioden har minst [MIN_PICKS_FOR_UNTOUCHED] val – och bara när de är högst [MAX_UNTOUCHED] (annars säger "mest"
 * redan allt). Meningen påstår ingen skillnad som inte finns: har tredje kärnan lika många val som andra nämns ingen av dem
 * – [most] är då bara den vanligaste ("Mest ledsen.") och [little] `null`; har de tre vanligaste lika många är [most]
 * alla som delar förstaplatsen ("Mest glad, lugn och ledsen."); och har tredje kärnan lika många som fjärde är [little]
 * `null` (ingen "lite" som bara råkar komma först i hjulets ordning). [coreCount] är antalet kärnor med minst ett val;
 * [sole] härleds ur det – bara en kärna valts – och Idag säger då bara kärnan ("Lugn."). Idag använder [most] och [little]
 * med tillfälle, Trender [most], [little] med nyans och [untouched].
 */
data class FeelingSummary(val most: List<FeelingRole>, val little: FeelingRole?, val untouched: List<String>, val coreCount: Int) {
    val isEmpty: Boolean get() = most.isEmpty()

    /** Bara en kärna valts – och strukturen säger detsamma (en "mest", ingen "lite"), så en handbyggd struktur kan inte motsäga sig. */
    val sole: Boolean get() = coreCount == 1 && most.size == 1 && little == null

    companion object {
        /** Inga val: ingen mening. */
        val EMPTY: FeelingSummary = FeelingSummary(emptyList(), null, emptyList(), coreCount = 0)
    }
}

/**
 * En dag för sig: dagens räkning ([tally]), meningens struktur ([summary]) och dagens [words] i tidsordning (poster utan
 * klockslag sist, varje ord en gång) – Idags kort Dagens känslor och en dag i Trenders vecka (#320). [isEmpty] när dagen
 * saknar känslor appen känner.
 */
data class FeelingDay(val date: LocalDate, val tally: FeelingTally, val summary: FeelingSummary, val words: List<Feeling>) {
    val isEmpty: Boolean get() = tally.isEmpty

    companion object {
        /** En dag utan poster med känslor. */
        fun empty(date: LocalDate): FeelingDay = FeelingDay(date, FeelingTally.EMPTY, FeelingSummary.EMPTY, emptyList())
    }
}

/**
 * Sammanställningen: räkningen för alla poster ([tally]), meningens struktur ([summary]) och dagarna var för sig ([days],
 * stigande datum, bara dagar med poster) – ur en gruppering per dag.
 */
data class FeelingStats(val tally: FeelingTally, val summary: FeelingSummary, val days: List<FeelingDay>) {
    /** Dagarna utlagda på [dates] (t.ex. veckan i perioden); en dag utan poster får [FeelingDay.empty]. */
    fun alignTo(dates: List<LocalDate>): List<FeelingDay> {
        val byDate = days.associateBy { it.date }
        return dates.map { byDate[it] ?: FeelingDay.empty(it) }
    }

    /** [date]s dag – [FeelingDay.empty] utan poster den dagen. Ett uppslag i [days]. */
    fun day(date: LocalDate): FeelingDay = days.firstOrNull { it.date == date } ?: FeelingDay.empty(date)
}

/**
 * Räknar känslorna i [entries] – Idag med den visade dagens måendeloggar, Trender med periodens loggar och aktiviteter –
 * och grupperar dem per dag en gång: varje dags räkning, mening och ord i tidsordning.
 */
fun computeFeelingStats(entries: List<FeelingEntry>): FeelingStats {
    val days = entries.groupBy { it.date }.entries.sortedBy { it.key }.map { (date, ofDay) -> feelingDay(date, ofDay) }
    val tally = tally(entries)
    return FeelingStats(tally, summaryOf(tally, entries), days)
}

/**
 * Bara [date]s dag ur [entries] (poster andra dagar räknas inte) – Idags kort Dagens känslor, utan periodens räkning.
 * Samma dag som [computeFeelingStats] ger i [FeelingStats.days]; utan poster [FeelingDay.empty].
 */
fun computeFeelingDay(entries: List<FeelingEntry>, date: LocalDate): FeelingDay {
    val ofDay = entries.filter { it.date == date }
    return if (ofDay.isEmpty()) FeelingDay.empty(date) else feelingDay(date, ofDay)
}

/** En dags räkning, mening och ord i tidsordning (poster utan klockslag sist, varje ord en gång). */
private fun feelingDay(date: LocalDate, ofDay: List<FeelingEntry>): FeelingDay {
    val inOrder = ofDay.sortedWith(compareBy(nullsLast()) { it.time })
    val dayTally = tally(inOrder)
    return FeelingDay(date, dayTally, summaryOf(dayTally, inOrder), inOrder.flatMap { it.feelings }.distinct())
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
    val ranked = tally.ranked
    // Lika många på tredje som på andra plats: varken andra eller tredje nämns (ingen påhittad skillnad), och delar alla
    // tre förstaplatsen är "mest" alla som delar den. "Lite" bara när tredje platsen har fler val än fjärde.
    val tiedAtCut = ranked.size > MOST && ranked[MOST].count == ranked[MOST - 1].count
    val most = if (tiedAtCut) ranked.filter { it.count == ranked.first().count } else ranked.take(MOST)
    val little = ranked.getOrNull(MOST)?.takeIf { third -> !tiedAtCut && third.count > (ranked.getOrNull(MOST + 1)?.count ?: 0) }
    val untouched = if (tally.total >= MIN_PICKS_FOR_UNTOUCHED) tally.cores.filter { it.count == 0 }.map { it.core } else emptyList()
    return FeelingSummary(
        most.map { role(it, entries) },
        little?.let { role(it, entries) },
        untouched.takeIf { it.size <= MAX_UNTOUCHED }.orEmpty(),
        coreCount = ranked.size
    )
}

private fun role(count: CoreCount, entries: List<FeelingEntry>): FeelingRole {
    val habit = count.nuances.firstOrNull()?.takeIf { count.count >= MIN_HABIT_PICKS && it.count * HABIT_DENOMINATOR >= count.count * HABIT_NUMERATOR }
    val occasions = entries.filter { entry -> entry.feelings.any { it.core == count.core } }.map { it.occasion }.distinct()
    return FeelingRole(count.core, habit?.nuance, occasions.singleOrNull())
}

private fun fraction(part: Int, whole: Int): Float = if (whole == 0) 0f else part.toFloat() / whole

/** Så många nyanser skrivs i värmekartans sektor. */
const val TOP_NUANCES = 3

/** Så många kärnor är "mest"; nästa är "lite" (lika många på gränsen: se [FeelingSummary]). */
private const val MOST = 2

/** "<kärna> knappt rört" sägs först när perioden har så många val … */
const val MIN_PICKS_FOR_UNTOUCHED = 10

/** … och bara om högst så många kärnor saknar val. */
const val MAX_UNTOUCHED = 2

/** "Nästan alltid som <nyans>": minst så många val av kärnan, och minst två tredjedelar med nyansen. */
const val MIN_HABIT_PICKS = 3
private const val HABIT_NUMERATOR = 2
private const val HABIT_DENOMINATOR = 3
