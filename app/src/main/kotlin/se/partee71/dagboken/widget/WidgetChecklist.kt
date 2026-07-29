package se.partee71.dagboken.widget

import se.partee71.dagboken.domain.model.Medicin
import se.partee71.dagboken.domain.model.tidpunktSortIndex
import se.partee71.dagboken.domain.model.tidpunktToHour

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

/** Kompakt sammanfattning för widgetens framsida (#159) — hela checklistan nås ett klick bort. */
data class WidgetMedsSummary(val taken: Int, val total: Int, val overdue: Int)

/**
 * [items] förväntas redan vara filtrerad via [widgetChecklistItems]. En dos räknas som
 * försenad när dess schemalagda timme har passerat och den fortfarande inte är tagen.
 */
fun widgetMedsSummary(items: List<Medicin>, nowHour: Int): WidgetMedsSummary {
    val taken = items.count { it.tagen }
    val overdue = items.count { medicin ->
        !medicin.tagen && (tidpunktToHour(medicin.tidpunkt)?.let { it < nowHour } ?: false)
    }
    return WidgetMedsSummary(taken = taken, total = items.size, overdue = overdue)
}

/**
 * Doser som fortfarande är aktuella att bocka av (#164) — en avbockad dos försvinner ur
 * widgeten direkt i stället för att ligga kvar avbockad i listan.
 */
fun widgetActionableItems(items: List<Medicin>): List<Medicin> = items.filterNot { it.tagen }
