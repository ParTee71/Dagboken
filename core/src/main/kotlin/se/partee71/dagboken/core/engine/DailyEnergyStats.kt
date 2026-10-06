package se.partee71.dagboken.core.engine

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import se.partee71.dagboken.core.model.Screening
import se.partee71.dagboken.core.time.datesBetween

/** En dags lägsta, genomsnittliga och högsta loggade screeningenergi. */
data class DailyEnergyStats(
    val date: LocalDate,
    val avg: Float,
    val min: Float,
    val max: Float,
)

/**
 * Beräknar per dag lägsta, genomsnittliga och högsta loggade screeningenergi.
 * Delad mellan Idag ([DailyEnergyStats.avg], HEM-7) och Trender (hela min–max-spannet, TRD-8) — en
 * enda källa så de aldrig kan visa olika dagsvärden för samma dag. Portad från 3.x
 * `domain/usecase/DailyEnergyStats.kt`: i 4.0 är screeningar en egen samling, så filtret på
 * `type == "screening"` behövs inte. En screening utan datum hör inte till någon dag och räknas
 * inte. Dagarna kommer i stigande datumordning.
 */
fun computeDailyEnergyStats(screenings: List<Screening>): List<DailyEnergyStats> =
    screenings
        .mapNotNull { screening -> screening.date?.let { it to screening.energy.toFloat() } }
        .groupBy({ it.first }, { it.second })
        .entries
        .sortedBy { it.key }
        .map { (date, energies) ->
            DailyEnergyStats(
                date = date,
                avg = energies.average().toFloat(),
                min = energies.min(),
                max = energies.max(),
            )
        }

/**
 * Dagsstatistiken utlagd på [days] (t.ex. de senaste sju dagarna, HEM-7): en plats per dag, `null`
 * för en dag utan screening – en lucka i diagrammet, aldrig en nolla.
 */
fun List<DailyEnergyStats>.alignTo(days: List<LocalDate>): List<DailyEnergyStats?> {
    val byDate = associateBy { it.date }
    return days.map { byDate[it] }
}

/** Idags 7-dagarstrend (HEM-7): så många dagar visas. */
const val ENERGY_TREND_DAYS = 7

/** De [count] dagarna till och med [last], äldst först – x-axeln i Idags 7-dagarstrend (HEM-7). */
fun daysEnding(last: LocalDate, count: Int = ENERGY_TREND_DAYS): List<LocalDate> =
    datesBetween(last.minus(count - 1, DateTimeUnit.DAY), last)

/**
 * HEM-7: dagsvärdet ([DailyEnergyStats.avg] ur [computeDailyEnergyStats], samma som Trender, TRD-8) för
 * var och en av [days]; `null` för en dag utan screening – en lucka, aldrig en nolla.
 */
fun dailyEnergyAverages(screenings: List<Screening>, days: List<LocalDate>): List<Float?> =
    computeDailyEnergyStats(screenings).alignTo(days).map { it?.avg }
