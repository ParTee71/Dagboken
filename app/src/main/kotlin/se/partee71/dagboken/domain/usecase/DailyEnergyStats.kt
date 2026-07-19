package se.partee71.dagboken.domain.usecase

import se.partee71.dagboken.domain.model.Aktivitet

data class DailyEnergyStats(
    val datum: String,
    val avg: Float,
    val min: Float,
    val max: Float,
)

/**
 * Beräknar per dag lägsta, genomsnittliga och högsta loggade screeningenergi.
 * Delad mellan Idag ([avg], HEM-7) och Trender (hela [min]–[max]-spannet, TRD-8) — en
 * enda källa så de aldrig kan visa olika dagsvärden för samma dag.
 */
fun computeDailyEnergyStats(screenings: List<Aktivitet>): List<DailyEnergyStats> =
    screenings
        .filter { it.type == "screening" }
        .groupBy { it.datum }
        .entries
        .sortedBy { it.key }
        .map { (datum, entries) ->
            val energies = entries.map { it.energy.toFloat() }
            DailyEnergyStats(
                datum = datum,
                avg   = energies.average().toFloat(),
                min   = energies.min(),
                max   = energies.max(),
            )
        }
