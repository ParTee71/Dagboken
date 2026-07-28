package se.partee71.dagboken.widget

import se.partee71.dagboken.domain.model.Medicin
import se.partee71.dagboken.domain.model.tidpunktSortIndex

/**
 * Dagens medicinchecklista för widgeten: vid behov-doser (ingen fast tid) visas inte —
 * de loggas i appen, inte via widgetens avbockning (samma avgränsning som
 * [se.partee71.dagboken.data.repository.MedicinerRepository.markTodayDosesTaken]).
 * Överhoppade doser (MED-15) döljs också. Sorterad i samma tidpunktsordning som Idag-vyn.
 */
fun widgetChecklistItems(mediciner: List<Medicin>): List<Medicin> =
    mediciner
        .filter { !it.skipped && it.tidpunkt != "Vid behov" }
        .sortedBy { tidpunktSortIndex(it.tidpunkt) }
