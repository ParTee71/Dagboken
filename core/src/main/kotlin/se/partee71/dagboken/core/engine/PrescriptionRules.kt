package se.partee71.dagboken.core.engine

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus
import se.partee71.dagboken.core.model.Boost
import se.partee71.dagboken.core.model.Prescription

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
 * REC-7, REC-9: första felet som hindrar att receptet sparas, eller `null` när det är giltigt. En
 * höjning utan slut gäller till periodens slut; en utan start (bara i gammal data) räknas inte i
 * slut- och överlappskontrollen, precis som den inte räknas i [boostFor].
 */
fun Prescription.validate(): PrescriptionError? {
    val start = period.start
    val end = period.end
    if (start != null && end != null && end < start) return PrescriptionError.END_BEFORE_START
    // En höjning utan start räknas inte i någon kontroll (som boostFor) – ett migrerat recept med en lös
    // odaterad höjning ska gå att spara. null som slut = tills vidare.
    val dated = boosts.filter { it.start != null }
    if (dated.any { it.dose.isBlank() }) return PrescriptionError.BOOST_WITHOUT_DOSE
    if (dated.isNotEmpty() && parseDose(dose) == null) return PrescriptionError.BASE_DOSE_NOT_NUMERIC
    if (dated.any { (parseDose(it.dose) ?: 0.0) <= 0.0 }) return PrescriptionError.BOOST_NOT_POSITIVE
    val outside = dated.any { b ->
        (start != null && b.start!! < start) || (end != null && (b.start!! > end || (b.end != null && b.end > end)))
    }
    if (outside) return PrescriptionError.BOOST_OUTSIDE_PERIOD
    val ranges = datedRanges()
    if (ranges.any { (s, e) -> e != null && e < s }) return PrescriptionError.BOOST_END_BEFORE_START
    val overlap = ranges.sortedBy { it.first }.zipWithNext().any { (a, b) -> a.second == null || b.first <= a.second!! }
    return if (overlap) PrescriptionError.BOOSTS_OVERLAP else null
}

/** De daterade höjningarnas dagar: start och sista dag ([boostEnd]; `null` = tills vidare). */
private fun Prescription.datedRanges(): List<Pair<LocalDate, LocalDate?>> =
    boosts.mapNotNull { boost -> boost.start?.let { it to boostEnd(boost) } }

/**
 * REC-9: förvalen för en ny höjning med [id] – start dagen efter den senast slutande höjningen (en
 * höjning utan eget slut slutar med perioden; höjningar utan start räknas inte), annars periodens
 * start, annars [today]; slut sex dagar senare (en vecka). Förslaget klipps till perioden: aldrig
 * före dess start eller efter dess slut. Dosen lämnas tom; enheten är receptets.
 *
 * `null` när det inte finns plats: förslaget skulle överlappa en befintlig höjning – perioden är
 * full, eller en höjning gäller tills vidare i en period utan slut. Ett förslag som direkt skulle
 * fälla [validate] ges aldrig.
 */
fun Prescription.nextBoostDefaults(id: String, today: LocalDate): Boost? {
    val ranges = datedRanges()
    // En höjning tills vidare har ingen sista dag – efter den finns ingen plats.
    if (ranges.any { it.second == null }) return null
    val proposed = ranges.mapNotNull { it.second }.maxOrNull()?.plus(1, DateTimeUnit.DAY) ?: period.start ?: today
    val start = clipToPeriod(proposed)
    val end = clipToPeriod(start.plus(NEW_BOOST_EXTRA_DAYS, DateTimeUnit.DAY))
    if (ranges.any { (s, e) -> start <= e!! && s <= end }) return null
    return Boost(id = id, start = start, end = end, dose = "", unit = unit)
}

private fun Prescription.clipToPeriod(date: LocalDate): LocalDate {
    val atLeastStart = period.start?.let { maxOf(it, date) } ?: date
    return period.end?.let { minOf(it, atLeastStart) } ?: atLeastStart
}

private const val NEW_BOOST_EXTRA_DAYS = 6
