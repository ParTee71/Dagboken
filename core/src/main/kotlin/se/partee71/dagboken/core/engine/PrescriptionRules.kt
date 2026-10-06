package se.partee71.dagboken.core.engine

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus
import se.partee71.dagboken.core.model.Boost
import se.partee71.dagboken.core.model.Prescription
import se.partee71.dagboken.core.schema.DocumentRules

// Receptformulärets regler (REC-7, REC-9) – port av 3.x `ReceptForm.validate()` och
// `AddEditReceptViewModel.addDosperiod()`. Felorsaken översätts till text i `:app`.

/** Varför ett recept inte kan sparas – i den ordning de kontrolleras (som 3.x). */
enum class PrescriptionError {
    /** REC-7: periodens slut ligger före dess start. */
    END_BEFORE_START,

    /** REC-9: en höjning saknar dos. */
    BOOST_WITHOUT_DOSE,

    /** REC-9: det finns höjningar men grunddosen är inte ett tal – de går inte att lägga ihop. */
    BASE_DOSE_NOT_NUMERIC,

    /** REC-9: en höjning är inte ett tal större än 0. */
    BOOST_NOT_POSITIVE,

    /** REC-9: en höjning börjar före perioden, efter den, eller har ett eget slut efter periodens slut. */
    BOOST_OUTSIDE_PERIOD,

    /** REC-9: en höjnings slut ligger före dess start. */
    BOOST_END_BEFORE_START,

    /** REC-9: två höjningar överlappar. */
    BOOSTS_OVERLAP,
}

/**
 * Ett fel i receptet och – när det gäller en höjning – dess plats i [Prescription.boosts], så att
 * formuläret kan visa felet under rätt höjning (REC-9).
 */
data class PrescriptionProblem(val error: PrescriptionError, val boostIndex: Int? = null)

/**
 * REC-7, REC-9: första felet som hindrar att receptet sparas, eller `null` när det är giltigt. En
 * höjning utan slut gäller till periodens slut; en utan start (bara i gammal data) räknas inte i
 * slut- och överlappskontrollen, precis som den inte räknas i [boostFor].
 */
fun Prescription.validate(): PrescriptionError? = problem()?.error

/**
 * [validate] med platsen: vilken höjning felet gäller (den första i listan som har felet; vid
 * överlapp den senare av två). Fel på perioden och grunddosen har ingen höjning.
 */
fun Prescription.problem(): PrescriptionProblem? {
    val start = period.start
    val end = period.end
    if (start != null && end != null && end < start) return PrescriptionProblem(PrescriptionError.END_BEFORE_START)
    // En höjning utan start räknas inte i någon kontroll (som boostFor) – ett migrerat recept med en lös
    // odaterad höjning ska gå att spara. null som slut = tills vidare.
    val dated = boosts.withIndex().filter { it.value.start != null }
    fun firstWith(error: PrescriptionError, test: (Boost) -> Boolean) =
        dated.firstOrNull { test(it.value) }?.let { PrescriptionProblem(error, it.index) }
    firstWith(PrescriptionError.BOOST_WITHOUT_DOSE) { it.dose.isBlank() }?.let { return it }
    if (dated.isNotEmpty() && parseDose(dose) == null) return PrescriptionProblem(PrescriptionError.BASE_DOSE_NOT_NUMERIC)
    firstWith(PrescriptionError.BOOST_NOT_POSITIVE) { (parseDose(it.dose) ?: 0.0) <= 0.0 }?.let { return it }
    firstWith(PrescriptionError.BOOST_OUTSIDE_PERIOD) { b ->
        (start != null && b.start!! < start) || (end != null && (b.start!! > end || (b.end != null && b.end > end)))
    }?.let { return it }
    val ranges = datedRanges()
    ranges.firstOrNull { it.end != null && it.end < it.start }?.let { return PrescriptionProblem(PrescriptionError.BOOST_END_BEFORE_START, it.index) }
    val overlap = ranges.sortedBy { it.start }.zipWithNext().firstOrNull { (a, b) -> a.end == null || b.start <= a.end }
    return overlap?.let { (_, later) -> PrescriptionProblem(PrescriptionError.BOOSTS_OVERLAP, later.index) }
}

/** En daterad höjnings dagar: plats i `boosts`, start och sista dag ([boostEnd]; `null` = tills vidare). */
private data class BoostRange(val index: Int, val start: LocalDate, val end: LocalDate?)

/** De daterade höjningarnas dagar – en gång för [problem] och [nextBoostDefaults]. */
private fun Prescription.datedRanges(): List<BoostRange> =
    boosts.mapIndexedNotNull { index, boost -> boost.start?.let { BoostRange(index, it, boostEnd(boost)) } }

/**
 * REC-9: förvalen för en ny höjning med [id] – start dagen efter den senast slutande höjningen (en
 * höjning utan eget slut slutar med perioden; höjningar utan start räknas inte), annars periodens
 * start, annars [today]; slut sex dagar senare (en vecka). Förslaget klipps till perioden: aldrig
 * före dess start eller efter dess slut. Dosen lämnas tom; enheten är receptets.
 *
 * `null` när det inte finns plats: förslaget skulle överlappa en befintlig höjning – perioden är
 * full, eller en höjning gäller tills vidare i en period utan slut – eller receptet redan har så många
 * höjningar som rules tillåter ([DocumentRules.MAX_BOOSTS]). Ett förslag som direkt skulle fälla
 * [validate] ges aldrig.
 */
fun Prescription.nextBoostDefaults(id: String, today: LocalDate): Boost? {
    if (boosts.size >= DocumentRules.MAX_BOOSTS) return null
    val ranges = datedRanges()
    // En höjning tills vidare har ingen sista dag – efter den finns ingen plats.
    if (ranges.any { it.end == null }) return null
    val proposed = ranges.mapNotNull { it.end }.maxOrNull()?.plus(1, DateTimeUnit.DAY) ?: period.start ?: today
    val start = clipToPeriod(proposed)
    val end = clipToPeriod(start.plus(NEW_BOOST_EXTRA_DAYS, DateTimeUnit.DAY))
    if (ranges.any { range -> start <= range.end!! && range.start <= end }) return null
    return Boost(id = id, start = start, end = end, dose = "", unit = unit)
}

private fun Prescription.clipToPeriod(date: LocalDate): LocalDate {
    val atLeastStart = period.start?.let { maxOf(it, date) } ?: date
    return period.end?.let { minOf(it, atLeastStart) } ?: atLeastStart
}

private const val NEW_BOOST_EXTRA_DAYS = 6
