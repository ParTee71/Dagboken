package se.partee71.dagboken.core.engine

import java.math.BigDecimal
import java.math.RoundingMode
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.daysUntil
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import se.partee71.dagboken.core.model.Boost
import se.partee71.dagboken.core.model.Period
import se.partee71.dagboken.core.model.Prescription
import se.partee71.dagboken.core.model.Repeat
import se.partee71.dagboken.core.model.Schedule
import se.partee71.dagboken.core.time.HOME_ZONE

// Receptets kalender och dos (REC-2…REC-4, REC-7…REC-9, REC-11, REC-12) – en gång, här, så att
// dosgenereringen (MED-4), REC-10-diffen, periodsluten (NOT-12, MEDF-2) och formulären räknar
// likadant. Port av 3.x `domain/model/Medicin.kt` och `EnsureTodayEntriesUseCase.shouldTakeToday`.

/**
 * REC-7: om [date] ligger inom perioden, båda gränserna inräknade. Utan [Period.start] finns ingen
 * bakre gräns (3.x-recept från före periodstödet – bakåtbläddring i Idag, HEM-14, ska fortsätta ge
 * doser); utan [Period.end] gäller perioden tills vidare.
 */
fun Period.covers(date: LocalDate): Boolean {
    if (start != null && date < start) return false
    return end == null || date <= end
}

/** REC-8: om periodens sista dag redan passerats sett från [today]. Tills vidare passeras aldrig. */
fun Prescription.hasExpiredOn(today: LocalDate): Boolean = period.end?.let { today > it } ?: false

/**
 * REC-8: de aktiva recepten delade på om perioden passerats sett från [today] – `first` ska avslutas
 * (bara `active = false`), `second` gäller fortfarande. Regeln finns bara här.
 */
fun List<Prescription>.activeByExpiryOn(today: LocalDate): Pair<List<Prescription>, List<Prescription>> =
    filter { it.active }.partition { it.hasExpiredOn(today) }

/** REC-8: de aktiva recepten vars period passerats – första halvan av [activeByExpiryOn]. */
fun List<Prescription>.toDeactivateOn(today: LocalDate): List<Prescription> = activeByExpiryOn(today).first

/**
 * Dagen intervallet räknas från (REC-4): periodens start, annars skapandedagen (3.x `periodStart` =
 * `startDatum.ifBlank { skapad }`). `null` när receptet saknar båda.
 *
 * Nya 4.0-recept har alltid [Period.start] – receptformuläret (etapp 5.2c) sätter den, med idag som
 * förval – så skapandedagen gäller bara migrerade och äldre 3.x-recept utan periodstart. Den läses i
 * [HOME_ZONE], samma zon som konverteraren skriver 3.x `skapad` i (midnatt), så att samma recept ger
 * samma dosdagar på alla enheter oavsett enhetens tidszon.
 */
fun Prescription.intervalAnchor(): LocalDate? = period.start ?: createdAt?.toLocalDateTime(HOME_ZONE)?.date

/**
 * REC-2…REC-4: om upprepningen ger en dos på [date]. [anchor] är dagen ett intervall räknas från
 * ([intervalAnchor]); saknas den räknas [date] som dag 0, som i 3.x. Intervall på 1 dag eller
 * mindre är dagligen. [Schedule.Unknown] (okänd upprepning från en nyare app) ger aldrig en dos.
 */
fun Schedule.appliesOn(date: LocalDate, anchor: LocalDate?): Boolean = when (this) {
    is Schedule.Unknown -> false
    is Schedule.Repeating -> when (repeat) {
        Repeat.DAILY -> true
        Repeat.WEEKDAYS -> date.dayOfWeek !in WEEKEND
        Repeat.WEEKENDS -> date.dayOfWeek in WEEKEND
        Repeat.CUSTOM -> date.dayOfWeek in days
        // Före ankaret (recept utan periodstart) blir resten negativ eller 0 – som 3.x `%`.
        Repeat.INTERVAL -> intervalDays <= 1 || anchor == null || anchor.daysUntil(date) % intervalDays == 0
    }
}

/** Lördag och söndag – [Repeat.WEEKENDS]. */
internal val WEEKEND = setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY)

/**
 * MED-4, REC-7: om receptets kalender ger doser på [date] – inom perioden och enligt upprepningen.
 * Aktivt/inaktivt ([Prescription.active]) avgörs av anroparen, som i 3.x.
 */
fun Prescription.appliesOn(date: LocalDate): Boolean =
    period.covers(date) && schedule.appliesOn(date, intervalAnchor())

/**
 * NOT-12, MEDF-2: periodens sista dag som faktiskt ger en dos – periodens slut eller den närmaste
 * dosdagen före den (t.ex. söndagen för ett helgrecept som slutar en onsdag). `null` när perioden är
 * tills vidare, upprepningen är okänd eller ingen dag i perioden ger dos. Konstant tid: intervall
 * räknas med modulo från ankaret, veckomönster prövar högst sju dagar.
 */
fun Prescription.lastDoseDay(): LocalDate? = period.end?.let(::lastDoseDayOnOrBefore)

/**
 * NOT-12: höjningens sista dag som faktiskt ger en dos – dess slut ([boostEnd]) eller närmaste dosdag
 * före den, men inte före höjningens start. `null` för en höjning utan start eller slut, eller utan
 * dosdag.
 */
fun Prescription.lastDoseDayOf(boost: Boost): LocalDate? {
    val start = boost.start ?: return null
    return boostEnd(boost)?.let(::lastDoseDayOnOrBefore)?.takeIf { it >= start }
}

private fun Prescription.lastDoseDayOnOrBefore(date: LocalDate): LocalDate? =
    doseDayNear(date, forward = false)?.takeIf { period.covers(it) }

/**
 * NOT-12: första dagen efter [date] som ger en dos inom perioden, eller `null` (perioden slut,
 * okänd upprepning eller inga dosdagar). Konstant tid, som [lastDoseDay].
 */
fun Prescription.nextDoseDayAfter(date: LocalDate): LocalDate? =
    doseDayNear(date.plus(1, DateTimeUnit.DAY), forward = true)?.takeIf { period.covers(it) }

/** Närmaste dag enligt upprepningen på eller efter ([forward]) respektive på eller före [from]; perioden ignoreras. */
private fun Prescription.doseDayNear(from: LocalDate, forward: Boolean): LocalDate? {
    val repeating = schedule as? Schedule.Repeating ?: return null
    val anchor = intervalAnchor()
    if (repeating.repeat == Repeat.INTERVAL && repeating.intervalDays > 1 && anchor != null) {
        val n = repeating.intervalDays
        val offset = Math.floorMod(anchor.daysUntil(from), n)
        return if (forward) from.plus((n - offset) % n, DateTimeUnit.DAY) else from.minus(offset, DateTimeUnit.DAY)
    }
    val step = if (forward) 1 else -1
    return generateSequence(from) { it.plus(step, DateTimeUnit.DAY) }
        .take(DAYS_PER_WEEK)
        .firstOrNull { repeating.appliesOn(it, anchor) }
}

private const val DAYS_PER_WEEK = 7

/**
 * REC-9, REC-11: doshöjningen som gäller [date], eller `null` när grunddosen gäller. En höjning utan
 * start räknas inte; utan slut gäller den till periodens slut. Överlappar höjningar (äldre eller
 * importerad data – formuläret hindrar det) vinner den senast påbörjade, den mer specifika.
 */
fun Prescription.boostFor(date: LocalDate): Boost? =
    boosts
        .filter { boost ->
            val start = boost.start ?: return@filter false
            val end = boostEnd(boost)
            date >= start && (end == null || date <= end)
        }
        .maxByOrNull { it.start!! }

/** Höjningens sista dag: dess eget slut, annars periodens; `null` = tills vidare. */
fun Prescription.boostEnd(boost: Boost): LocalDate? = boost.end ?: period.end

/**
 * REC-9, REC-12: dagens totala dos – grunddosen plus höjningen som gäller [date]. Enheten är alltid
 * receptets ([Prescription.unit]). Går grunddosen eller höjningen inte att räkna som tal
 * ("1 tablett") gäller grunddosen oförändrad.
 */
fun Prescription.doseFor(date: LocalDate): String = boostFor(date)?.let(::totalWith) ?: dose

/**
 * REC-9: den totala dosen medan [boost] gäller – grunddosen plus höjningen ("75") – eller `null` när
 * någon av dem inte går att räkna som tal. Receptformulärets "Total dos under perioden" och [doseFor].
 */
fun Prescription.totalWith(boost: Boost): String? {
    val base = parseDose(dose) ?: return null
    val extra = parseDose(boost.dose) ?: return null
    return (base + extra).takeIf { it.isFinite() }?.let(::formatDose)
}

/**
 * En handskriven dos som tal: siffror med punkt eller komma som decimaltecken, blanktecken runt om
 * tillåtna ("0,5", " 1.5 "). `null` för allt annat ("1 tablett", "", "NaN", "1e3", "5d", "-1") och
 * för tal för stora för att räknas med (hundratals siffror blir oändligt).
 */
fun parseDose(value: String?): Double? =
    value?.trim()?.takeIf(DOSE_NUMBER::matches)?.replace(',', '.')?.toDouble()?.takeIf { it.isFinite() }

/** Siffror med valfri decimal (komma eller punkt) – inga tecken, exponenter, hex eller suffix ("5d"). */
private val DOSE_NUMBER = Regex("""\d+([.,]\d*)?|[.,]\d+""")

/**
 * En uträknad dos för visning: heltal utan decimaler, annars svenskt decimalkomma utan
 * avslutande nollor. Avrundas till sex decimaler, så att flyttalsbrus (`0,1 + 0,2`) inte syns. Kastar
 * aldrig: ett värde som inte är ändligt (som [parseDose] aldrig ger) skrivs som det är.
 */
fun formatDose(value: Double): String =
    if (!value.isFinite()) value.toString()
    else BigDecimal.valueOf(value).setScale(DOSE_DECIMALS, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString().replace('.', ',')

private const val DOSE_DECIMALS = 6
