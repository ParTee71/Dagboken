package se.partee71.dagboken.core.engine

import kotlin.time.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import se.partee71.dagboken.core.model.Dose
import se.partee71.dagboken.core.model.Prescription
import se.partee71.dagboken.core.model.PrnMedicine
import se.partee71.dagboken.core.model.Screening
import se.partee71.dagboken.core.model.Slot

// Idags listor (HEM-1, HEM-14, MED-1, MED-5, MED-13, FAV-2, FAV-11) – urval och ordning en gång, här,
// så att fliken Idag bara visar.

/** Hälsningens del av dygnet (HEM-1) – samma timmar som 3.x `greetingRes`. */
enum class DayPart { MORNING, AFTERNOON, EVENING, NIGHT }

/** HEM-1: dygnsdelen för timmen [hour] (0–23): morgon 5–11, eftermiddag 12–16, kväll 17–20, annars natt. */
fun dayPartAt(hour: Int): DayPart = when (hour) {
    in MORNING_HOURS -> DayPart.MORNING
    in AFTERNOON_HOURS -> DayPart.AFTERNOON
    in EVENING_HOURS -> DayPart.EVENING
    else -> DayPart.NIGHT
}

private val MORNING_HOURS = 5..11
private val AFTERNOON_HOURS = 12..16
private val EVENING_HOURS = 17..20

/** En dos som inte är avklarad och hur den står mot nu ([dueAt]); `null` för vid behov, som inte väntar på ett klockslag. */
data class OpenDose(val dose: Dose, val due: Due?)

/**
 * Mediciner-kortet på Idag (MED-1, MED-5, MED-13), varje del i tidpunktsordning (Morgon → Natt → Vid behov).
 * [shown] är det som är att göra – försenat, snart, vid behov, eller en tidigare dags ej tagna; [done] tagna
 * och överhoppade (döljs bakom "Visa tagna"); [upcoming] dagens doser mer än [SOON_WINDOW] fram (döljs
 * bakom "Visa kommande").
 */
data class DoseChecklist(
    val shown: List<OpenDose> = emptyList(),
    val done: List<Dose> = emptyList(),
    val upcoming: List<OpenDose> = emptyList(),
) {
    val isEmpty: Boolean get() = shown.isEmpty() && done.isEmpty() && upcoming.isEmpty()
}

/**
 * MED-1, MED-5, MED-13: [date]s doser ur [doses] (andra dagar hoppas över) delade och ordnade för
 * Mediciner-kortet. Avklarade ([isDone]) står för sig; en öppen dos med klockslag mer än [SOON_WINDOW] fram
 * idag är kommande, allt annat visas – en tidigare dag har aldrig kommande (`Due.PAST`, HEM-4). Klockslaget
 * är dosens planerade, annars tidpunktens standard (REC-6).
 */
fun doseChecklist(doses: List<Dose>, date: LocalDate, now: Instant, zone: TimeZone): DoseChecklist {
    val ordered = doses.filter { it.date == date }.sortedWith(DOSE_ORDER)
    val (done, open) = ordered.partition { it.isDone }
    val withDue = open.map { dose ->
        OpenDose(dose, if (dose.slot == Slot.AS_NEEDED) null else dueAt(date, dose.plannedTime ?: dose.slot.defaultTime, now, zone))
    }
    val (upcoming, shown) = withDue.partition { it.due == Due.UPCOMING }
    return DoseChecklist(shown = shown, done = done, upcoming = upcoming)
}

/** Tidpunkt (Morgon → Natt → Vid behov), klockslag, namn utan skiftläge och sist id – samma ordning varje gång. */
private val DOSE_ORDER: Comparator<Dose> =
    compareBy<Dose> { it.slot.ordinal }
        .thenBy { it.plannedTime ?: it.slot.defaultTime }
        .thenBy(String.CASE_INSENSITIVE_ORDER) { it.name }
        .thenBy { it.id }

/**
 * Vid behov-kortet på Idag (FAV-2, FAV-11): [favorites] som snabbval, och i "Fler" [others] – de övriga vid
 * behov-medicinerna – och under "Recept" [prescriptions], de aktiva recepten som gäller dagen.
 */
data class AsNeededChoices(
    val favorites: List<PrnMedicine> = emptyList(),
    val others: List<PrnMedicine> = emptyList(),
    val prescriptions: List<Prescription> = emptyList(),
) {
    /** Antalet i "Fler" (FAV-2). */
    val moreCount: Int get() = others.size + prescriptions.size
}

/**
 * FAV-11: samma medicin = samma namn (utan skiftläge, blanktecken normaliserade) och samma styrka (normaliserad)
 * – eller att någon av styrkorna inte är angiven, så att en medicin utan styrka inte visas bredvid samma med styrka.
 */
fun sameMedicine(nameA: String, strengthA: String, nameB: String, strengthB: String): Boolean {
    fun norm(text: String) = text.trim().lowercase().replace(Regex("\\s+"), " ")
    val a = norm(strengthA)
    val b = norm(strengthB)
    return norm(nameA) == norm(nameB) && (a.isEmpty() || b.isEmpty() || a == b)
}

/**
 * FAV-2, FAV-11: delar vid behov-medicinerna på stjärnan, efter namn, och tar med de aktiva recept vars
 * period täcker [today] – utom ett recept som redan finns bland vid behov-medicinerna ([sameMedicine]), så att
 * samma medicin aldrig står två gånger. Lika namn ordnas på id.
 */
fun asNeededChoices(medicines: List<PrnMedicine>, prescriptions: List<Prescription>, today: LocalDate): AsNeededChoices {
    val sorted = medicines.sortedWith(compareBy<PrnMedicine, String>(String.CASE_INSENSITIVE_ORDER) { it.name }.thenBy { it.id })
    val recipes = prescriptions
        .filter { p -> p.active && p.period.covers(today) && medicines.none { sameMedicine(it.name, it.strength, p.name, p.strength) } }
        .sortedWith(compareBy<Prescription, String>(String.CASE_INSENSITIVE_ORDER) { it.name }.thenBy { it.id })
    val (favorites, others) = sorted.partition { it.favorite }
    return AsNeededChoices(favorites, others, recipes)
}

/**
 * HEM-14: dagarna som får en punkt i datumremsan – dagar med en avklarad dos (tagen eller överhoppad,
 * [isDone]) eller en måendelogg. En planerad dos som ingen rört är ingen post.
 */
fun datesWithEntries(doses: List<Dose>, screenings: List<Screening>): Set<LocalDate> =
    (doses.filter { it.isDone }.mapNotNull { it.date } + screenings.mapNotNull { it.date }).toSet()

/**
 * MED-14: tagningstiden när dosen bockas av på Idag vid [now]. Idag (eller en dos utan dag) är det nu; en
 * tidigare dag – bläddrad till i efterhand (HEM-14) – får dosens planerade klockslag på sin dag (annars
 * tidpunktens standard, REC-6), aldrig senare än [now]. Så hamnar en avbockning i efterhand på rätt dag i
 * historiken och i kylperioden (FAV-4) i stället för på dagens datum.
 */
fun Dose.checkOffTime(now: Instant, zone: TimeZone): Instant {
    val day = date ?: return now
    if (day >= now.toLocalDateTime(zone).date) return now
    return minOf(LocalDateTime(day, plannedTime ?: slot.defaultTime).toInstant(zone), now)
}
