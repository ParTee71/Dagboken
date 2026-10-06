package se.partee71.dagboken.core.engine

import kotlin.time.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import se.partee71.dagboken.core.model.Dose
import se.partee71.dagboken.core.model.DoseIds
import se.partee71.dagboken.core.model.DoseStatus
import se.partee71.dagboken.core.model.Slot

// Dosformuläret (MED-11, MED-15, MED-16) – hur dag, klockslag och "Tagen" ändrar en dos, en gång, här.

/**
 * Receptet dosen hör till: [Dose.prescriptionId], eller – för en migrerad dos utan koppling – receptets id ur
 * dos-id:t `recept_{id}_{datum}_{tidpunkt}` (`DoseIds.prescribed`, MED-4). `null` för en dos utan recept (vid
 * behov, extrados, engångsdos).
 */
val Dose.prescriptionRef: String?
    get() = prescriptionId ?: PRESCRIBED_ID.matchEntire(id)?.groupValues?.get(1)

/** Om dosen kommer ur ett recept ([prescriptionRef]) – hoppas över i stället för att raderas (MED-3, MED-15). */
val Dose.isPrescribed: Boolean get() = prescriptionRef != null

/** `recept_{id}_{yyyy-MM-dd}_{tidpunkt}` – receptets id får innehålla `_`, tidpunktens namn inte. */
private val PRESCRIBED_ID = Regex("""recept_(.+)_\d{4}-\d{2}-\d{2}_[^_]+""")

/**
 * Klockslaget formuläret visar: tagningstiden (MED-14) för en tagen dos, annars det planerade klockslaget och
 * till sist tidpunktens standard (REC-6) – i hela minuter.
 */
fun Dose.shownTime(zone: TimeZone): LocalTime {
    val taken = takenAt?.takeIf { status == DoseStatus.TAKEN }?.toLocalDateTime(zone)?.time
    val time = taken ?: plannedTime ?: slot.defaultTime
    return LocalTime(time.hour, time.minute)
}

/** Ögonblicket [date] kl. [time] i [zone]. */
fun momentOf(date: LocalDate, time: LocalTime, zone: TimeZone): Instant = LocalDateTime(date, time).toInstant(zone)

/** Dosen flyttad till [date] med samma klockslag ([shownTime]); en tagen dos tagningstid följer med (MED-15). */
fun Dose.onDay(date: LocalDate, zone: TimeZone): Dose {
    val time = shownTime(zone)
    return copy(date = date, takenAt = if (status == DoseStatus.TAKEN) momentOf(date, time, zone) else takenAt)
}

/**
 * Dosen kl. [time] på sin dag: en tagen dos tagningstid (MED-14). En dos utan recept får också klockslaget som
 * sitt planerade (3.x `tid`); en receptdos behåller schemats klockslag – det styrs av receptet (MED-15).
 */
fun Dose.atTime(time: LocalTime, zone: TimeZone): Dose {
    val day = date ?: return this
    val takenAt = if (status == DoseStatus.TAKEN) momentOf(day, time, zone) else takenAt
    return copy(takenAt = takenAt, plannedTime = if (isPrescribed) plannedTime else time)
}

/**
 * "Tagen" i formuläret (MED-15): på → tagen vid det visade klockslaget på dosens dag; av → överhoppad utan
 * tagningstid – samma som "Hoppa över" (MED-3), så att dosgenereringen aldrig skapar den igen som planerad.
 */
fun Dose.withTaken(taken: Boolean, zone: TimeZone): Dose {
    if (!taken) return copy(status = DoseStatus.SKIPPED, takenAt = null)
    val day = date ?: return copy(status = DoseStatus.TAKEN)
    return copy(status = DoseStatus.TAKEN, takenAt = momentOf(day, shownTime(zone), zone))
}

/**
 * En engångsdos från plusknappen (MED-11, NAV-10): tagen på [date] kl. [time] i [zone] med tidpunkten "Vid behov"
 * och [unit] som förval, utan recept eller vid behov-medicin. [id] och [createdAt] sätts en gång när formuläret
 * öppnas.
 */
fun oneOffDose(id: String, date: LocalDate, time: LocalTime, zone: TimeZone, createdAt: Instant, unit: String): Dose = Dose(
    id = id,
    date = date,
    slot = Slot.AS_NEEDED,
    unit = unit,
    status = DoseStatus.TAKEN,
    plannedTime = time,
    takenAt = momentOf(date, time, zone),
    createdAt = createdAt,
)

/**
 * Om dosens id är receptets datumbundna `recept_{id}_{datum}_{tidpunkt}` ([DoseIds.prescribed]) – det id
 * dosgenereringen (MED-4) känner igen för en dag.
 */
val Dose.hasDayBoundId: Boolean get() = PRESCRIBED_ID.matches(id)

/**
 * MED-15: om en ändring flyttar en receptdos med datumbundet id ([hasDayBoundId]) till en annan dag – då kan den
 * inte behålla id:t: dagens egen dos skulle aldrig skapas och den gamla dagens skapas inte igen. En dos med annat
 * id (t.ex. flyttad i 3.x, med slumpat id) är inte bunden till dagen och ändras som vilken dos som helst.
 */
fun Dose.movesPrescribedDose(edited: Dose): Boolean = hasDayBoundId && edited.date != date

/**
 * MED-15: [edited] som flyttad receptdos – med måldagens datumbundna id `recept_{id}_{datum}_{tidpunkt}`, så att
 * dosgenereringen ser den och inte skapar en till planerad dos den dagen, och med receptkopplingen kvar
 * ([prescriptionRef], också för en migrerad dos som bara hade den i id:t). En dos med tidpunkten "Vid behov" (som
 * ett recept aldrig genererar) eller utan datum får [randomId].
 */
fun Dose.moveTarget(edited: Dose, randomId: () -> String): Dose {
    val ref = prescriptionRef
    val day = edited.date
    val id = if (ref != null && day != null && edited.slot != Slot.AS_NEEDED) DoseIds.prescribed(ref, day, edited.slot) else randomId()
    return edited.copy(id = id, prescriptionId = edited.prescriptionId ?: ref)
}
