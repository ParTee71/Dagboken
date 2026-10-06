package se.partee71.dagboken.core.engine

import kotlinx.datetime.LocalDate
import se.partee71.dagboken.core.model.Prescription
import se.partee71.dagboken.core.model.PrnMedicine

// Fliken Mediciners indelning (MEDF-1, MEDF-3, MEDF-5) – en gång, här, så att appen bara formaterar.

/** Fliken Mediciners tre delar, var och en i visningsordning. */
data class MedicineOverview(
    /** Recepten vars period inte passerats [today] – aktiva och pausade, efter namn (MEDF-1). */
    val current: List<Prescription>,
    /** Vid behov-medicinerna efter namn (MEDF-3). */
    val asNeeded: List<PrnMedicine>,
    /** Recepten vars period passerats, senast avslutade först (MEDF-5). */
    val ended: List<Prescription>,
)

/**
 * MEDF-1, MEDF-3, MEDF-5: delar in recepten och vid behov-medicinerna sett från [today]. Ett recept vars
 * period passerats ([hasExpiredOn]) är avslutat även om det fortfarande är aktivt – dosgenereringen
 * avslutar det (REC-8). Namn jämförs utan hänsyn till skiftläge; lika namn ordnas på id, så att
 * ordningen alltid är densamma.
 */
fun medicineOverview(prescriptions: List<Prescription>, medicines: List<PrnMedicine>, today: LocalDate): MedicineOverview {
    val (ended, current) = prescriptions.partition { it.hasExpiredOn(today) }
    val byName = compareBy<Prescription, String>(String.CASE_INSENSITIVE_ORDER) { it.name }.thenBy { it.id }
    return MedicineOverview(
        current = current.sortedWith(byName),
        asNeeded = medicines.sortedWith(compareBy<PrnMedicine, String>(String.CASE_INSENSITIVE_ORDER) { it.name }.thenBy { it.id }),
        ended = ended.sortedWith(compareByDescending<Prescription> { it.period.end }.then(byName)),
    )
}
