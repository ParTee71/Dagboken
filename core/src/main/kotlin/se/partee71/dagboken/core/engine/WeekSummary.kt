package se.partee71.dagboken.core.engine

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant
import se.partee71.dagboken.core.model.Dose
import se.partee71.dagboken.core.model.DoseStatus
import se.partee71.dagboken.core.model.Screening

// Veckosammanfattningen (HEM-13) och dagens energi mot igår (HEM-19) – ren beräkning över befintliga
// poster, ingen ny persisterad data.

/** Energitrendens riktning (HEM-13). */
enum class EnergyTrend { UP, DOWN, SAME }

/**
 * HEM-13: veckans sammanfattning. [dosesTakenPercent] är `null` när veckan inte har några schemalagda
 * doser – 4.0 visar då ingen dosandel (3.x visade 0 %).
 */
data class WeekSummary(val energyTrend: EnergyTrend, val dosesTakenPercent: Int?)

/** Skillnaden i snittenergi som räknas som en trend (HEM-13), som i 3.x. */
const val ENERGY_TREND_THRESHOLD = 0.5

/** HEM-13: sammanfattningen visas i början av veckan – söndag och måndag. */
val LocalDate.showsWeekSummary: Boolean get() = dayOfWeek == DayOfWeek.SUNDAY || dayOfWeek == DayOfWeek.MONDAY

/**
 * HEM-13 – port av 3.x `computeWeekSummary` (HomeViewModel). Idag är dagen för [now] i [zone].
 * Energitrenden jämför snittet av alla måendeloggar de senaste sju dagarna (idag inräknad) med de sju
 * dagarna före; mer än [ENERGY_TREND_THRESHOLD] upp eller ner är [EnergyTrend.UP]/[EnergyTrend.DOWN],
 * annars – också när en av veckorna saknar loggar – [EnergyTrend.SAME]. Dosandelen är tagna av veckans
 * schemalagda doser ([isScheduled]) som inte hoppats över **och har förfallit** – en tidigare dag, eller
 * idag med klockslaget nått ([dueAt]); en dos som redan tagits räknas alltid. Kommande doser räknas inte
 * (3.x räknade dem och sänkte andelen på morgonen). I hela procent avrundat nedåt. `null` utan underlag:
 * varken en måendelogg eller en förfallen schemalagd dos den här veckan.
 */
fun weekSummary(now: Instant, zone: TimeZone, screenings: List<Screening>, doses: List<Dose>): WeekSummary? {
    val today = now.toLocalDateTime(zone).date
    val weekStart = today.minus(6, DateTimeUnit.DAY)
    val previousStart = today.minus(13, DateTimeUnit.DAY)
    val previousEnd = today.minus(7, DateTimeUnit.DAY)

    fun averageEnergy(from: LocalDate, to: LocalDate): Double? =
        screenings.filter { it.date?.let { d -> d in from..to } == true }.map { it.energy }.takeIf { it.isNotEmpty() }?.average()

    val thisWeek = averageEnergy(weekStart, today)
    val previousWeek = averageEnergy(previousStart, previousEnd)
    val trend = when {
        thisWeek == null || previousWeek == null -> EnergyTrend.SAME
        thisWeek > previousWeek + ENERGY_TREND_THRESHOLD -> EnergyTrend.UP
        thisWeek < previousWeek - ENERGY_TREND_THRESHOLD -> EnergyTrend.DOWN
        else -> EnergyTrend.SAME
    }

    val weekDoses = doses.filter { dose ->
        val date = dose.date
        date != null && date in weekStart..today && dose.isScheduled && dose.status != DoseStatus.SKIPPED &&
            (dose.status == DoseStatus.TAKEN || dueAt(date, dose.plannedTime ?: dose.slot.defaultTime, now, zone).let { it == Due.LATE || it == Due.PAST })
    }
    val percent = if (weekDoses.isEmpty()) null else weekDoses.count { it.status == DoseStatus.TAKEN } * 100 / weekDoses.size

    return if (thisWeek == null && percent == null) null else WeekSummary(trend, percent)
}

/**
 * HEM-13: "Din vecka" när den visade dagen är [date] – bara när [date] är idag (dagen för [now] i [zone]) och
 * idag är söndag eller måndag, annars `null`. Dagen tas ur samma [now] som sammanfattningen, så att den inte
 * visas på en tisdag strax efter midnatt.
 */
fun weekSummaryOn(date: LocalDate, now: Instant, zone: TimeZone, screenings: List<Screening>, doses: List<Dose>): WeekSummary? {
    val today = now.toLocalDateTime(zone).date
    return if (date == today && today.showsWeekSummary) weekSummary(now, zone, screenings, doses) else null
}

/** HEM-19: dagens snittenergi och skillnaden mot igår (`null` när igår saknar måendelogg). */
data class DayComparison(val average: Float, val changeFromYesterday: Float?)

/**
 * HEM-19: dagens snittenergi ur [computeDailyEnergyStats] – samma dagsvärde som Idag-diagrammet (HEM-7)
 * och Trender (TRD-8) – och skillnaden mot gårdagens. `null` när [today] inte har någon måendelogg.
 */
fun dayComparison(today: LocalDate, screenings: List<Screening>): DayComparison? {
    val byDate = computeDailyEnergyStats(screenings).associateBy { it.date }
    val todayAverage = byDate[today]?.avg ?: return null
    val yesterdayAverage = byDate[today.minus(1, DateTimeUnit.DAY)]?.avg
    return DayComparison(todayAverage, yesterdayAverage?.let { todayAverage - it })
}
