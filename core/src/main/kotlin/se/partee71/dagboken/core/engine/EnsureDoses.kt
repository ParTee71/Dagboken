package se.partee71.dagboken.core.engine

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import se.partee71.dagboken.core.time.HOME_ZONE
import se.partee71.dagboken.core.model.Dose
import se.partee71.dagboken.core.model.DoseIds
import se.partee71.dagboken.core.model.DoseStatus
import se.partee71.dagboken.core.model.Prescription
import se.partee71.dagboken.core.model.Schedule
import se.partee71.dagboken.core.model.Slot

// Receptens doser (MED-4, REC-8, REC-10) – port av 3.x `EnsureTodayEntriesUseCase.compute`,
// `MedicinerRepository.ensureEntriesForDate` och `syncPendingDoses`. Ren beräkning: repositoryt
// läser doserna, anropar härifrån och skriver resultatet.

/**
 * Tidpunkterna receptet ger doser på: de schemalagda i vald ordning, utan dubbletter; inga alls →
 * Morgon (som 3.x). "Vid behov" ger aldrig en receptdos ([DoseIds.prescribed]), och ett recept med
 * bara "Vid behov" ger inga doser alls (ingen Morgon). Avvikelse från 3.x, som genererade
 * `recept_{id}_{datum}_Vid behov` (12:00) om data hade tidpunkten – formuläret erbjöd den aldrig, men
 * äldre eller importerad data kan ha den. Sådana migrerade doser behålls orörda (de har tidpunkten
 * "Vid behov" och [syncDoses] rör dem inte), men nya skapas inte.
 */
fun Prescription.doseSlots(): List<Slot> =
    if (slots.isEmpty()) listOf(Slot.MORNING) else slots.filter { it != Slot.AS_NEEDED }.distinct()

/**
 * MED-4: receptets planerade doser för [date] – en per tidpunkt med det stabila id:t
 * [DoseIds.prescribed] (samma som 3.x), dagens totala dos (REC-12), tidpunktens standardklockslag
 * (REC-6) och receptets anteckning som förval (REC-1). Tom när receptet är inaktivt eller inte
 * gäller dagen. [Dose.createdAt] är dag och klockslag i [zone] (3.x `Timestamps.of`), så att två
 * körningar ger exakt samma dokument.
 */
fun Prescription.plannedDoses(date: LocalDate, zone: TimeZone): List<Dose> {
    if (!active || !appliesOn(date)) return emptyList()
    val total = doseFor(date)
    return doseSlots().map { slot ->
        Dose(
            id = DoseIds.prescribed(id, date, slot),
            date = date,
            slot = slot,
            name = name,
            strength = strength,
            dose = total,
            unit = unit,
            status = DoseStatus.PLANNED,
            plannedTime = slot.defaultTime,
            prescriptionId = id,
            createdAt = LocalDateTime(date, slot.defaultTime).toInstant(zone),
            note = note?.takeIf { it.isNotBlank() },
        )
    }
}

/** Vad dosgenereringen för en dag ska skriva (MED-4, REC-8). */
data class EnsurePlan(
    /**
     * Id:n för aktiva recept vars period passerats (REC-8). Repositoryt skriver **bara** fältet
     * `active = false` på dem (merge), så att resten av dokumentet – också fält från en nyare app –
     * lämnas orört. Recepten raderas aldrig.
     */
    val deactivate: List<String>,
    /** Doser som saknas för dagen. */
    val create: List<Dose>,
)

/**
 * HEM-10: första dagen receptet kan ha en dos – det senare av skapandedagen (`createdAt` i [HOME_ZONE],
 * samma zon som konverteraren) och periodens start. `null` = varken skapandetid eller start (ingen gräns).
 */
fun Prescription.firstDoseDay(): LocalDate? = listOfNotNull(createdAt?.toLocalDateTime(HOME_ZONE)?.date, period.start).maxOrNull()

/**
 * MED-4, REC-8: dosgenereringen för [date] (idag eller en tidigare dag, HEM-14). [existing] är dagens
 * befintliga doser; en dos vars id redan finns skapas aldrig igen, vilken status den än har – därför
 * är genereringen idempotent. Aktiva recept vars period passerats sett från [today] (inte [date], så
 * att bakåtbläddring varken väcker eller avslutar något) avslutas och ger inga doser, som i 3.x. Ingen
 * dos genereras före receptets [firstDoseDay] (HEM-10) – 3.x seedade recept utan start bakåt utan gräns.
 * En tidigare dag följer receptets **nuvarande** aktiv-läge och schema; pauser har ingen historik.
 */
fun ensureDoses(
    prescriptions: List<Prescription>,
    existing: Collection<Dose>,
    date: LocalDate,
    today: LocalDate,
    zone: TimeZone,
): EnsurePlan {
    val (expired, current) = prescriptions.activeByExpiryOn(today)
    val existingIds = existing.mapTo(HashSet()) { it.id }
    return EnsurePlan(
        deactivate = expired.map { it.id },
        create = current.filter { p -> p.firstDoseDay()?.let { date >= it } ?: true }
            .flatMap { it.plannedDoses(date, zone) }.filter { it.id !in existingIds },
    )
}

/** REC-10: hur receptets doser ska ändras när receptet sparats. */
data class DoseSync(
    val create: List<Dose> = emptyList(),
    val update: List<Dose> = emptyList(),
    val delete: List<Dose> = emptyList(),
) {
    val isEmpty: Boolean get() = create.isEmpty() && update.isEmpty() && delete.isEmpty()
}

/**
 * REC-10, REC-5: diffen mellan det sparade receptet och dess befintliga doser. Bara **planerade** doser
 * från och med [today] berörs – tagna och överhoppade ändras aldrig, och gårdagens historik lämnas orörd.
 * - **delete:** receptet är inaktivt (REC-5), dagen ligger utanför perioden eller upprepningen, eller
 *   tidpunkten finns inte längre i receptet (anteckningen följer med dokumentet);
 * - **update:** namn, styrka, dos eller enhet följer receptet och dagens höjning (REC-12);
 * - **create:** saknade doser för dagarna [today]…[through] (standard bara idag) om receptet är
 *   aktivt – t.ex. en ny tidpunkt syns direkt i dagens checklista. Senare dagar fylls på av
 *   [ensureDoses] när de visas.
 *
 * Data från en nyare app rörs aldrig: med okänd upprepning ([Schedule.Unknown]) blir diffen tom, och
 * en dos med "Vid behov" som tidpunkt (så avkodas en okänd tidpunkt; ett recept genererar aldrig en
 * sådan) varken uppdateras eller raderas.
 *
 * Receptets doser är de med [Dose.prescriptionId] = receptets id, och migrerade doser utan
 * receptkoppling vars id har receptets form `recept_{id}_{datum}_{tidpunkt}` ([DoseIds.prescribed]).
 *
 * **Krav på [existing]:** alla receptets doser för [today]…[through], oavsett status (andra doser får
 * finnas med; bara receptets egna räknas för update/delete). En dos skapas aldrig om dess id finns
 * bland [existing], vilken status eller vilket recept den än har – men en tagen dos som *saknas* i
 * listan kan diffen inte se, så repositoryt ska dessutom skriva `create` som "skapa om den inte finns".
 */
fun Prescription.syncDoses(
    existing: Collection<Dose>,
    today: LocalDate,
    zone: TimeZone,
    through: LocalDate = today,
): DoseSync {
    if (schedule is Schedule.Unknown) return DoseSync()
    val pending = existing.filter { dose ->
        owns(dose) && dose.status == DoseStatus.PLANNED && dose.slot != Slot.AS_NEEDED &&
            dose.date?.let { it >= today } == true
    }
    if (!active) return DoseSync(delete = pending)
    val slotsNow = doseSlots().toSet()
    val (obsolete, kept) = pending.partition { !appliesOn(it.date!!) || it.slot !in slotsNow }
    val update = kept.mapNotNull { dose ->
        val total = doseFor(dose.date!!)
        dose.takeIf { it.name != name || it.strength != strength || it.dose != total || it.unit != unit }
            ?.copy(name = name, strength = strength, dose = total, unit = unit)
    }
    val existingIds = existing.mapTo(HashSet()) { it.id }
    val create = generateSequence(today) { it.plus(1, DateTimeUnit.DAY) }
        .takeWhile { it <= through }
        .flatMap { plannedDoses(it, zone) }
        .filter { it.id !in existingIds }
        .toList()
    return DoseSync(create = create, update = update, delete = obsolete)
}

/** Om [dose] hör till receptet: kopplad via [Dose.prescriptionId], eller okopplad med receptets dos-id-form. */
private fun Prescription.owns(dose: Dose): Boolean = dose.prescriptionRef == id
