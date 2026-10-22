package se.partee71.dagboken.core.engine

import kotlin.test.assertEquals
import org.junit.Test
import se.partee71.dagboken.core.model.Prescription
import se.partee71.dagboken.core.model.PrnMedicine

/** Namn utan skiftläge, sedan id – samma ordning för alla medicinlistor. */
class OrderingTest {

    @Test
    fun `namn utan hänsyn till skiftläge, lika namn på id`() {
        val medicines = listOf(
            PrnMedicine("c", "alvedon"),
            PrnMedicine("b", "Alvedon"),
            PrnMedicine("a", "Zyrtec"),
            PrnMedicine("d", "Ibuprofen")
        )
        assertEquals(listOf("b", "c", "d", "a"), medicines.sortedWith(nameThenId<PrnMedicine> { it.name }).map { it.id })
    }

    @Test
    fun `fungerar för recept också`() {
        val prescriptions = listOf(Prescription("2", "Levaxin"), Prescription("1", "levaxin"), Prescription("0", "Atenolol"))
        assertEquals(listOf("0", "1", "2"), prescriptions.sortedWith(nameThenId<Prescription> { it.name }).map { it.id })
    }
}
