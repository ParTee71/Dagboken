package se.partee71.dagboken.core.engine

import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime

// När påminnelserna ska utlösas (NOT-2, NOT-5, NOT-12, NOT-14) – port av 3.x `screeningAlarmTriggerMs`
// och `medAlarmTriggerMs`. Ren beräkning med "nu" och tidszonen som parametrar; schemaläggaren i appen
// lägger bara ögonblicket i AlarmManager.

/** NOT-2: medicinpåminnelsen kommer så här många minuter före tidpunktens klockslag. */
const val MED_LEAD_MINUTES: Int = 15

/**
 * NOT-5, NOT-14: nästa gång klockan är [time] i [zone] efter [now] – idag om den inte nåtts, annars i
 * morgon (lika med [now] räknas som nådd). Räknas i lokala datum, så att ett dagligt larm står kvar på
 * samma klockslag över ett sommartidsbyte; ett klockslag som inte finns den dagen (vårens hopp, 02:30)
 * blir det första giltiga efter hoppet (03:30).
 */
fun nextDailyAt(time: LocalTime, now: Instant, zone: TimeZone): Instant {
    val today = now.toLocalDateTime(zone).date
    val candidate = LocalDateTime(today, time).toInstant(zone)
    return if (candidate > now) candidate else LocalDateTime(today.plus(1, DateTimeUnit.DAY), time).toInstant(zone)
}

/**
 * NOT-2: klockslaget medicinpåminnelsen för en tidpunkt kl. [slotTime] utlöses – [leadMinutes] före, runt
 * midnatt (00:00 − 15 min = 23:45, då kvällen före).
 */
fun medAlarmTime(slotTime: LocalTime, leadMinutes: Int = MED_LEAD_MINUTES): LocalTime {
    val second = Math.floorMod(slotTime.toSecondOfDay() - leadMinutes * SECONDS_PER_MINUTE, SECONDS_PER_DAY)
    return LocalTime.fromSecondOfDay(second)
}

/** NOT-2, NOT-14: nästa medicinpåminnelse för en tidpunkt kl. [slotTime] ([medAlarmTime], [nextDailyAt]). */
fun nextMedAlarm(slotTime: LocalTime, now: Instant, zone: TimeZone, leadMinutes: Int = MED_LEAD_MINUTES): Instant =
    nextDailyAt(medAlarmTime(slotTime, leadMinutes), now, zone)

/**
 * NOT-3, NOT-17: dagen vars doser en medicinpåminnelse som utlöstes [firedAt] gäller – dagen då tidpunkten
 * nås, [leadMinutes] senare. Påminnelsen 23:45 för en tidpunkt 00:00 gäller alltså nästa dags doser.
 */
fun medReminderDate(firedAt: Instant, zone: TimeZone, leadMinutes: Int = MED_LEAD_MINUTES): LocalDate =
    (firedAt + leadMinutes.minutes).toLocalDateTime(zone).date

private const val SECONDS_PER_MINUTE = 60
private const val SECONDS_PER_DAY = 24 * 60 * 60
