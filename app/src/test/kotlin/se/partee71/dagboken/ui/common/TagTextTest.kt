package se.partee71.dagboken.ui.common

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlin.test.assertEquals
import kotlin.test.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import se.partee71.dagboken.core.model.BodySensationCatalog
import se.partee71.dagboken.core.model.ContextTagCatalog
import se.partee71.dagboken.core.model.TagCatalog

/** Kroppens och sammanhangets svenska namn (SCR-9): ett namn per token i katalogens ordning, inget för det okända. */
@RunWith(RobolectricTestRunner::class)
class TagTextTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    private fun names(catalog: TagCatalog) = catalog.tokens.map { token -> context.getString(checkNotNull(tagName(catalog, token)) { "saknar namn: $token" }) }

    @Test
    fun `kroppens ord heter som i mockupen, i katalogens ordning`() {
        assertEquals(
            listOf(
                "Spänd käke", "Hjärtklappning", "Klump i magen", "Tung i bröstet", "Rastlösa ben", "Varm i ansiktet",
                "Huvudvärk", "Trött i kroppen", "Lätt i kroppen", "Varm och mjuk", "Ingenting särskilt"
            ),
            names(BodySensationCatalog)
        )
    }

    @Test
    fun `sammanhangets ord heter som i mockupen, i katalogens ordning`() {
        assertEquals(
            listOf("Sömn", "Familj", "Arbete", "Hälsa", "Mediciner", "Ensamtid", "Väder", "Ekonomi", "Vänner", "Motion", "Mat"),
            names(ContextTagCatalog)
        )
    }

    @Test
    fun `ett okänt token eller en annan katalog har inget namn`() {
        assertNull(tagName(BodySensationCatalog, "sleep"))
        assertNull(tagName(ContextTagCatalog, "nothing"))
        assertNull(tagName(TagCatalog(listOf("a")), "a"))
    }
}
