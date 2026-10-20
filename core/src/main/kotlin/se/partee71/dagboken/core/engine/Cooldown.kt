package se.partee71.dagboken.core.engine

import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.toLocalDateTime
import se.partee71.dagboken.core.model.Dose
import se.partee71.dagboken.core.model.DoseStatus
import se.partee71.dagboken.core.model.Prescription
import se.partee71.dagboken.core.model.PrnMedicine
import se.partee71.dagboken.core.model.Slot

// Vid behov-doser (FAV-4, FAV-5, FAV-11, FAV-13, MED-11, MED-16) – port av 3.x `CheckCooldownUseCase`,
// `CheckDailyLimitUseCase` och beräkningsdelen av `LogVidBehovDosUseCase`. Samma regler för
// snabbvalet på Idag och för loggning i efterhand: allt utvärderas mot ögonblicket [at] dosen gäller.

/** Utfallet av en kontroll innan en vid behov-dos loggas. */
sealed interface PrnCheck {
    data object Allowed : PrnCheck

    /** FAV-4: kylperioden pågår; [remaining] kvar till nästa dos. Användaren kan välja att ta dosen ändå. */
    data class Cooldown(val remaining: Duration) : PrnCheck

    /** FAV-5: dagens gräns är nådd – blockerar alltid. */
    data object DailyLimitReached : PrnCheck
}

/**
 * Om [dose] är en tagen dos av den här medicinen: loggad från den ([Dose.prnId]) eller med samma namn
 * oavsett skiftläge, som 3.x (som bara hade namnet – även en tagen receptdos med samma namn räknas).
 * Ett tomt namn matchar aldrig på namn, bara på [Dose.prnId].
 */
fun PrnMedicine.isTakenDoseOf(dose: Dose): Boolean = dose.status == DoseStatus.TAKEN &&
    (dose.prnId == id || (name.isNotBlank() && dose.name.equals(name, ignoreCase = true)))

/** När en tagen dos togs: [Dose.takenAt] (MED-14), annars [Dose.createdAt] (3.x `timestamp`). */
fun Dose.takenMoment(): Instant? = takenAt ?: createdAt

/**
 * FAV-4: kvarvarande kylperiod vid [at], räknat från den senaste tagna dosen vid eller före [at]
 * (MED-16: en dos loggad senare spärrar inte en tidigare). `null` när ingen kylperiod pågår –
 * [PrnMedicine.minHoursBetween] 0 eller mindre, ingen tidigare dos, eller perioden har gått ut.
 */
fun PrnMedicine.cooldownRemaining(doses: Iterable<Dose>, at: Instant): Duration? {
    if (minHoursBetween <= 0) return null
    val last = doses.filter(::isTakenDoseOf).mapNotNull { it.takenMoment() }.filter { it <= at }.maxOrNull() ?: return null
    val remaining = minHoursBetween.hours - (at - last)
    return remaining.takeIf { it.isPositive() }
}

/** FAV-5: antalet tagna doser av den här medicinen ([isTakenDoseOf]) på [date]. */
fun PrnMedicine.takenOn(doses: Iterable<Dose>, date: LocalDate): Int = doses.count { it.date == date && isTakenDoseOf(it) }

/** FAV-5: om dagens gräns är nådd på [date] ([PrnMedicine.maxPerDay] 0 = obegränsat). */
fun PrnMedicine.dailyLimitReached(doses: Iterable<Dose>, date: LocalDate): Boolean = maxPerDay > 0 && takenOn(doses, date) >= maxPerDay

/**
 * Dagarna vars doser [checkDose] behöver se vid [at] (FAV-4, FAV-5, MED-16): från dagen max([PrnMedicine.minHoursBetween],
 * 24) timmar före [at] – dagsgränsen gäller hela dagen – och en vecka till (kylperioden mäts på tagningstiden, och
 * en äldre dos kan ha bockats av nyss), till och med dagen för [at]. Samma urval för loggningen och formulärets
 * kontroll i efterhand.
 */
fun PrnMedicine.checkDays(at: Instant, zone: TimeZone): ClosedRange<LocalDate> {
    val window = maxOf(minHoursBetween, MIN_LOOKBACK_HOURS).hours
    val from = (at - window).toLocalDateTime(zone).date.minus(EXTRA_LOOKBACK_DAYS, DateTimeUnit.DAY)
    return from..at.toLocalDateTime(zone).date
}

/** Dagsgränsen (FAV-5) gäller dagen för dosen, så minst ett dygn bakåt läses. */
private const val MIN_LOOKBACK_HOURS = 24

/** Dagar extra bakåt för doser vars dag ligger före tagningstiden (avbockade i efterhand). */
private const val EXTRA_LOOKBACK_DAYS = 7

/**
 * FAV-4, FAV-5, MED-16: får en dos loggas vid [at]? Dagsgränsen (dagen för [at] i [zone]) kontrolleras
 * alltid; kylperioden hoppas över med [force], efter att användaren bekräftat.
 */
fun PrnMedicine.checkDose(doses: Iterable<Dose>, at: Instant, zone: TimeZone, force: Boolean = false): PrnCheck {
    if (dailyLimitReached(doses, at.toLocalDateTime(zone).date)) return PrnCheck.DailyLimitReached
    if (force) return PrnCheck.Allowed
    return cooldownRemaining(doses, at)?.let(PrnCheck::Cooldown) ?: PrnCheck.Allowed
}

/**
 * FAV-13: läget för en vid behov-medicin innan man trycker – samma regler som trycket ([checkDose]: dagsgränsen före
 * kylperioden), så att markeringen i chipet och i "Fler" aldrig säger något annat än trycket.
 */
sealed interface PrnAvailability {
    /** Dosen kan tas nu. */
    data object Ok : PrnAvailability

    /** FAV-4: kylperioden pågår; en dos går bra från [nextAllowed] (den senaste tagna dosen + kylperioden, i verkliga timmar). */
    data class Cooldown(val nextAllowed: Instant) : PrnAvailability

    /** FAV-5: dagens gräns är nådd – [taken] tagna av [limit] på dagen. */
    data class DailyLimit(val taken: Int, val limit: Int) : PrnAvailability
}

/**
 * FAV-13: läget vid [now] ur [doses] (dagarna i [checkDays], som trycket läser), med dagen för [now] i [zone] för
 * dagsgränsen. Räknas om när [now] går (minutklockan på Idag), så att markeringen försvinner av sig själv.
 */
fun PrnMedicine.availabilityAt(doses: Iterable<Dose>, now: Instant, zone: TimeZone): PrnAvailability = when (val check = checkDose(doses, now, zone)) {
    PrnCheck.Allowed -> PrnAvailability.Ok
    is PrnCheck.Cooldown -> PrnAvailability.Cooldown(now + check.remaining)
    PrnCheck.DailyLimitReached -> PrnAvailability.DailyLimit(takenOn(doses, now.toLocalDateTime(zone).date), maxPerDay)
}

/**
 * Dosen som loggas från vid behov-medicinen vid [at]: tagen, med medicinens tidpunkt, dos och
 * anteckning som förval (MED-11) och [Dose.prnId] satt. [id] är ett nytt dokument-id.
 */
fun PrnMedicine.takenDose(id: String, at: Instant, zone: TimeZone): Dose = asNeededDose(id, at, zone, slot, name, strength, dose, unit, note).copy(prnId = this.id)

/**
 * FAV-11: en extrados av receptets medicin vid [at] – en vid behov-dos utan receptkoppling (den ska
 * kunna raderas, inte hoppas över, MED-3), med dagens totala dos (REC-12) och receptets anteckning som
 * förval. Kylperiod och dagsgräns gäller inte (de hör till vid behov-medicinen).
 */
fun Prescription.extraDose(id: String, at: Instant, zone: TimeZone): Dose {
    val date = at.toLocalDateTime(zone).date
    return asNeededDose(id, at, zone, Slot.AS_NEEDED, name, strength, doseFor(date), unit, note)
}

private fun asNeededDose(
    id: String,
    at: Instant,
    zone: TimeZone,
    slot: Slot,
    name: String,
    strength: String,
    dose: String,
    unit: String,
    note: String?
): Dose {
    val local = at.toLocalDateTime(zone)
    return Dose(
        id = id,
        date = local.date,
        slot = slot,
        name = name,
        strength = strength,
        dose = dose,
        unit = unit,
        status = DoseStatus.TAKEN,
        // 3.x `tid` = `tagenTid` i hela minuter.
        plannedTime = LocalTime(local.hour, local.minute),
        takenAt = at,
        createdAt = at,
        note = note?.takeIf { it.isNotBlank() }
    )
}
