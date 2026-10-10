package se.partee71.dagboken.testing

import se.partee71.dagboken.core.medicine.MedicineCatalog
import se.partee71.dagboken.data.medicines.MedicineRepository

/** En liten läkemedelslista i samma format som `assets/medicines.tsv` (REC-14). */
class FakeMedicines(text: String = SAMPLE) : MedicineRepository {
    private val catalog = MedicineCatalog.parse(text)

    override suspend fun catalog(): MedicineCatalog = catalog

    companion object {
        val SAMPLE = listOf(
            "# updated 2026-10-04",
            "Alvedon\t60 mg\tSuppositorium",
            "Alvedon\t500 mg\tFilmdragerad tablett",
            "Alvedon\t500 mg\tMunsönderfallande tablett",
            "Alvedon forte\t1 g\tFilmdragerad tablett",
            "Bricanyl Turbuhaler\t0,5 mg/dos\tInhalationspulver",
            "Levaxin\t50 mikrogram\tTablett",
        ).joinToString("\n")
    }
}
