package se.partee71.dagboken.data.medicines

import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import se.partee71.dagboken.core.model.MedicineForm

/**
 * Den incheckade läkemedelslistan (REC-14): en trasig eller saknad fil skulle annars bara ge en tom
 * lista – och en funktion som tyst slutat fungera.
 */
@RunWith(RobolectricTestRunner::class)
class AssetMedicineRepositoryTest {

    private val repository = AssetMedicineRepository(RuntimeEnvironment.getApplication())

    @Test
    fun `den inbyggda listan läses med datum, tusentals läkemedel och sökbara namn`() = runTest {
        val catalog = repository.catalog()
        assertNotNull(catalog.updated)
        assertTrue(catalog.entries.size > 1000, "bara ${catalog.entries.size} läkemedel")
        val alvedon = catalog.search("alvedon 500").map { it.entry }
        assertTrue(alvedon.isNotEmpty())
        assertTrue(alvedon.all { it.name.startsWith("Alvedon") })
        assertTrue(alvedon.any { it.form == MedicineForm.TABLET })
        // Varje rad har namn och form; listan läses bara en gång.
        assertEquals(emptyList(), catalog.entries.filter { it.name.isBlank() || it.formText.isBlank() })
        assertSame(catalog, repository.catalog())
    }

    @Test
    fun `datumet läses ur filens första rader, och ett misslyckande cachas inte`() = runTest {
        // Datumet i filens rubrik – listan uppdateras vid varje release (skill release), så det hårdkodas inte.
        val header = RuntimeEnvironment.getApplication().assets.open("medicines.tsv").bufferedReader().useLines { lines ->
            lines.first { it.startsWith("# updated ") }.removePrefix("# updated ").trim()
        }
        assertEquals(kotlinx.datetime.LocalDate.parse(header), repository.updated().also { assertNotNull(it) })
        var calls = 0
        val flaky = AssetMedicineRepository {
            if (calls++ == 0) throw java.io.IOException("trasig") else "# updated 2026-10-04\nAlvedon\t500 mg\tTablett\n".byteInputStream()
        }
        assertTrue(flaky.catalog().entries.isEmpty(), "första försöket ger en tom lista")
        assertEquals(1, flaky.catalog().entries.size, "nästa anrop försöker igen")
        assertSame(flaky.catalog(), flaky.catalog(), "en lyckad läsning cachas")
        assertEquals(2, calls)

        var reads = 0
        val counted = AssetMedicineRepository { reads++; "Alvedon\t500 mg\tTablett\n".byteInputStream() }
        assertEquals(null, counted.updated())
        assertEquals(null, counted.updated())
        assertEquals(1, reads, "ett läst datum (även null) cachas")
    }
}
