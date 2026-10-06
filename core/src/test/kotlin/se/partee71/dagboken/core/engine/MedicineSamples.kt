package se.partee71.dagboken.core.engine

import kotlin.time.Instant
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import se.partee71.dagboken.core.model.Boost
import se.partee71.dagboken.core.model.Dose
import se.partee71.dagboken.core.model.DoseStatus
import se.partee71.dagboken.core.model.Period
import se.partee71.dagboken.core.model.Prescription
import se.partee71.dagboken.core.model.PrnMedicine
import se.partee71.dagboken.core.model.Repeat
import se.partee71.dagboken.core.model.Schedule
import se.partee71.dagboken.core.model.Slot

// Delade, syntetiska testdata för medicinmotorerna – fasta datum, aldrig "nu".

val STOCKHOLM: TimeZone = TimeZone.of("Europe/Stockholm")

/** `"2026-05-01"` → dag. */
fun day(iso: String): LocalDate = LocalDate.parse(iso)

/** Dag och klockslag (`"2026-05-01T08:00"`) i Europe/Stockholm. */
fun at(isoLocal: String): Instant = LocalDateTime.parse(isoLocal).toInstant(STOCKHOLM)

fun schedule(repeat: Repeat = Repeat.DAILY, days: Set<DayOfWeek> = emptySet(), intervalDays: Int = 2) =
    Schedule.Repeating(repeat, days, intervalDays)

fun boost(start: String?, end: String?, dose: String, id: String = "b-$start") =
    Boost(id = id, start = start?.let(::day), end = end?.let(::day), dose = dose, unit = "mg")

fun prescription(
    id: String = "r1",
    name: String = "Metformin",
    dose: String = "500",
    slots: List<Slot> = listOf(Slot.MORNING),
    schedule: Schedule = schedule(),
    start: String? = "2026-01-01",
    end: String? = null,
    boosts: List<Boost> = emptyList(),
    active: Boolean = true,
    createdAt: Instant? = null,
    note: String? = null,
) = Prescription(
    id = id, name = name, dose = dose, unit = "mg", slots = slots, schedule = schedule,
    period = Period(start?.let(::day), end?.let(::day)), boosts = boosts, active = active,
    createdAt = createdAt, note = note,
)

fun prn(
    minHoursBetween: Int = 4,
    maxPerDay: Int = 0,
    note: String? = null,
) = PrnMedicine(
    id = "p1", name = "Paracetamol", dose = "500", unit = "mg",
    minHoursBetween = minHoursBetween, maxPerDay = maxPerDay, favorite = true, note = note,
)

/** En tagen dos av [name] vid [time] (Europe/Stockholm). */
fun taken(time: String, name: String = "Paracetamol", id: String = "d-$time", prnId: String? = null, status: DoseStatus = DoseStatus.TAKEN) =
    at(time).let { moment ->
        Dose(id = id, date = LocalDateTime.parse(time).date, slot = Slot.AS_NEEDED, name = name, dose = "500", unit = "mg", status = status, takenAt = moment, createdAt = moment, prnId = prnId)
    }
