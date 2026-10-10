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
}
