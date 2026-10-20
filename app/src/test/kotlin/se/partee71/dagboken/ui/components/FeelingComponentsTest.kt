package se.partee71.dagboken.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import kotlin.math.cos
import kotlin.math.sin
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import se.partee71.dagboken.core.model.Feeling
import se.partee71.dagboken.core.model.FeelingCatalog
import se.partee71.dagboken.testing.captureLightAndDark
import se.partee71.dagboken.testing.clickWithoutRipple
import se.partee71.dagboken.ui.theme.DagbokenTheme
import se.partee71.ui.components.AppCard
import se.partee71.ui.theme.Spacing

/**
 * Känslohjulet och känsloväljaren (SCR-7, SCR-8): hjulet som ingång, nyanserna och de precisare orden, ersättning på
 * samma plats, pills som tar bort och taket på sex – och skärmdumparna av väljarens kärnvy på en 360 dp bred skärm
 * (hjulet i vila står i `CatalogScreenshotTest`).
 */
@RunWith(RobolectricTestRunner::class)
class FeelingComponentsTest {

    @get:Rule
    val rule = createComposeRule()

    private fun show(content: @Composable () -> Unit) = rule.setContent { DagbokenTheme(content = content) }

    private val chosen = SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "vald")
    private val button = SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button)

    private var selected by mutableStateOf(emptyList<Feeling>())

    private fun picker(max: Int = FeelingCatalog.MAX_FEELINGS) = show { FeelingPicker(selected, { selected = it }, maxSelected = max) }

    @Test
    fun `hjulet - tryck på en sektor ger kärnans token, och en kärna med val läses vald`() {
        val clicks = mutableListOf<String>()
        show { FeelingWheel(setOf("calm"), { clicks += it }) }
        rule.onNodeWithContentDescription("Lugn").assert(button).assert(chosen)
        rule.onNodeWithContentDescription("Arg").assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.StateDescription)).performClick()
        rule.onNodeWithContentDescription("Glad").performClick()
        assertEquals(listOf("angry", "glad"), clicks)
    }

    @Test
    fun `hjulets sektorer har minst 48 dp tryckyta`() {
        show { FeelingWheel(emptySet(), {}) }
        FeelingCatalog.cores.forEach { core ->
            val name = mapOf("glad" to "Glad", "strong" to "Stark", "calm" to "Lugn", "sad" to "Ledsen", "angry" to "Arg", "scared" to "Rädd").getValue(core)
            val node = rule.onNodeWithContentDescription(name).fetchSemanticsNode()
            val min = TOUCH_TARGET.value * node.layoutInfo.density.density
            assertTrue(node.touchBoundsInRoot.height >= min && node.touchBoundsInRoot.width >= min, "$name: ${node.touchBoundsInRoot}")
        }
    }

    @Test
    fun `hjulet - sektorn räknas medurs från klockan 12, navet och utsidan är ingen sektor`() {
        val r = 100f

        // En punkt [degrees] medurs från klockan 12, på [distance] av radien från mitten.
        fun at(degrees: Double, distance: Float = 0.8f) = Math.toRadians(degrees).let {
            Offset(r + (sin(it) * r * distance).toFloat(), r - (cos(it) * r * distance).toFloat())
        }
        val sectors = listOf(1.0, 59.0, 61.0, 150.0, 179.0, 181.0, 270.0, 299.0, 301.0, 359.0).map { coreAt(at(it), r, 0.2f) }
        assertEquals(listOf(0, 0, 1, 2, 2, 3, 4, 4, 5, 5), sectors, "Glad, Stark, Lugn, Ledsen, Arg, Rädd medurs")
        assertNull(coreAt(at(30.0, 0.1f), r, 0.2f), "navet")
        assertNull(coreAt(at(30.0, 1.1f), r, 0.2f), "utanför hjulet")
    }

    @Test
    fun `väljaren - kärna, nyans och precisare ord, och ett nytt tryck går tillbaka`() {
        picker()
        rule.onNodeWithText("Börja i mitten. Du kan välja flera, eller inget.").assertIsDisplayed()
        rule.onNodeWithContentDescription("Rädd").performClick()
        assertEquals(listOf(Feeling("scared")), selected, "ett tryck i hjulet väljer kärnan direkt – första nivån räcker")
        rule.onNodeWithContentDescription("Rädd").assert(chosen).assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.Role))
        rule.onNodeWithText("Mer precist:").assertDoesNotExist()
        rule.onNodeWithContentDescription("Orolig").assert(button).performClick()
        assertEquals(listOf(Feeling("scared", "anxious")), selected, "nyansen ersätter kärnan på samma plats")
        rule.onNodeWithContentDescription("Rädd").assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.StateDescription))
        rule.onNodeWithContentDescription("Orolig").assert(chosen)
        rule.onNodeWithText("Mer precist:").assertIsDisplayed()
        rule.onNodeWithText("Spänd").performClick()
        assertEquals(listOf(Feeling("scared", "anxious", "tense")), selected)
        rule.onNodeWithText("Rädd › Orolig › Spänd").assertIsDisplayed()
        rule.onNodeWithText("Spänd").performClick()
        assertEquals(listOf(Feeling("scared", "anxious")), selected, "ett nytt tryck på ordet går tillbaka till nyansen")
        rule.onNodeWithContentDescription("Orolig").performClick()
        assertEquals(listOf(Feeling("scared")), selected, "ett tryck på den aktiva nyansen går tillbaka till kärnan ensam")
        rule.onNodeWithContentDescription("Rädd").assert(chosen)
        rule.onNodeWithText("Mer precist:").assertDoesNotExist()
        rule.onNodeWithContentDescription("Osäker").performClick()
        rule.onNodeWithContentDescription("Orolig").performClick()
        assertEquals(listOf(Feeling("scared", "insecure"), Feeling("scared", "anxious")), selected, "en andra nyans läggs sist")
        rule.onNodeWithContentDescription("Osäker").performClick()
        assertEquals(listOf(Feeling("scared", "insecure"), Feeling("scared", "anxious")), selected, "vald men inte aktiv: blir aktiv, tas inte bort")
        rule.onNodeWithContentDescription("Osäker").performClick()
        assertEquals(listOf(Feeling("scared", "anxious")), selected, "tas bort när kärnan redan står kvar i ett annat val")
        rule.onNodeWithText("Mer precist:").assertDoesNotExist()
    }

    @Test
    fun `väljaren - ett nytt tryck på en vald kärna lägger inte till en till`() {
        selected = listOf(Feeling("calm", "grateful"))
        picker()
        rule.onNodeWithContentDescription("Lugn").performClick()
        assertEquals(listOf(Feeling("calm", "grateful")), selected)
        rule.onNodeWithContentDescription("Lugn").assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.StateDescription))
        rule.onNodeWithText("Tillbaka till hjulet").performClick()
        rule.onNodeWithContentDescription("Glad").performClick()
        rule.onNodeWithText("Tillbaka till hjulet").performClick()
        rule.onNodeWithContentDescription("Glad").assert(chosen).performClick()
        assertEquals(listOf(Feeling("calm", "grateful"), Feeling("glad")), selected)
    }

    @Test
    fun `väljaren - en nyans ersätter kärnan på samma plats, och tillbaka till hjulet behåller valen`() {
        selected = listOf(Feeling("glad"), Feeling("calm"))
        picker()
        rule.onNodeWithContentDescription("Glad").assert(chosen).performClick()
        rule.onNodeWithContentDescription("Upprymd").performClick()
        assertEquals(listOf(Feeling("glad", "excited"), Feeling("calm")), selected)
        rule.onNodeWithText("Pirrig").performClick()
        assertEquals(listOf(Feeling("glad", "excited", "giddy"), Feeling("calm")), selected)
        rule.onNodeWithText("Tillbaka till hjulet").performClick()
        rule.onNodeWithContentDescription("Glad").assert(chosen)
        rule.onNodeWithContentDescription("Lugn").assert(chosen)
        rule.onNodeWithContentDescription("Arg").assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.StateDescription))
        assertEquals(2, selected.size)
    }

    @Test
    fun `väljaren - en redan vald nyans visar sina ord när kärnan öppnas och förfinas på sin plats`() {
        selected = listOf(Feeling("glad"), Feeling("calm", "grateful"), Feeling("scared"))
        picker()
        rule.onNodeWithContentDescription("Lugn").performClick()
        rule.onNodeWithText("Mer precist:").assertIsDisplayed()
        rule.onNodeWithText("Ödmjuk").performClick()
        assertEquals(listOf(Feeling("glad"), Feeling("calm", "grateful", "humble"), Feeling("scared")), selected, "samma plats i listan")
    }

    @Test
    fun `väljaren - två valda nyanser under en kärna, tryck på den inaktiva visar dess ord`() {
        selected = listOf(Feeling("calm", "grateful"), Feeling("glad"), Feeling("calm", "relaxed"))
        picker()
        rule.onNodeWithContentDescription("Lugn").performClick()
        rule.onNodeWithText("Ödmjuk").assertIsDisplayed()
        rule.onNodeWithContentDescription("Avslappnad").assert(chosen).performClick()
        assertEquals(3, selected.size, "inget togs bort")
        rule.onNodeWithText("Ödmjuk").assertDoesNotExist()
        rule.onNodeWithText("Vilsam").performClick()
        assertEquals(listOf(Feeling("calm", "grateful"), Feeling("glad"), Feeling("calm", "relaxed", "restful")), selected)
        rule.onNodeWithText("Tillbaka till hjulet").performClick()
        rule.onNodeWithContentDescription("Lugn").performClick()
        rule.onNodeWithText("Ödmjuk").assertIsDisplayed()
    }

    @Test
    fun `väljaren - tryck på en pill tar bort känslan`() {
        selected = listOf(Feeling("calm", "grateful"), Feeling("scared"))
        picker()
        rule.onNodeWithText("Lugn › Tacksam").assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button)).performClick()
        assertEquals(listOf(Feeling("scared")), selected)
        rule.onNodeWithText("Lugn › Tacksam").assertDoesNotExist()
    }

    @Test
    fun `väljaren - vid taket läggs inget nytt till och knapparna som lägger till är inaktiva`() {
        selected = listOf(Feeling("glad"), Feeling("calm", "grateful"))
        picker(max = 2)
        rule.onNodeWithText("Högst 2 känslor per logg.").assertIsDisplayed()
        rule.onNodeWithContentDescription("Rädd").performClick()
        assertEquals(2, selected.size, "vid taket väljs ingen ny kärna")
        rule.onNodeWithContentDescription("Rädd").assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.StateDescription))
        rule.onNodeWithContentDescription("Orolig").assertIsNotEnabled().performClick()
        assertEquals(2, selected.size)
        rule.onNodeWithText("Tillbaka till hjulet").performClick()
        rule.onNodeWithContentDescription("Glad").performClick()
        rule.onNodeWithContentDescription("Upprymd").assertIsEnabled().performClick()
        assertEquals(listOf(Feeling("glad", "excited"), Feeling("calm", "grateful")), selected, "kärnan ensam förfinas, även vid taket")
        rule.onNodeWithText("Tillbaka till hjulet").performClick()
        rule.onNodeWithContentDescription("Lugn").performClick()
        rule.onNodeWithContentDescription("Tacksam").assertIsEnabled().performClick()
        assertEquals(listOf(Feeling("glad", "excited"), Feeling("calm")), selected, "nyansen går tillbaka till kärnan även vid taket")
        rule.onNodeWithContentDescription("Kärleksfull").assertIsEnabled()
        rule.onNodeWithText("Lugn").performClick()
        assertEquals(listOf(Feeling("glad", "excited")), selected, "ett val går alltid att ta bort via sin pill")
    }

    @Test
    fun `standardtaket är sex känslor`() {
        selected = FeelingCatalog.cores.map { Feeling(it) }
        picker()
        rule.onNodeWithText("Högst 6 känslor per logg.").assertIsDisplayed()
    }

    @Composable
    private fun Sheet(content: @Composable () -> Unit) {
        Column(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.background).padding(Spacing.l)) { AppCard { content() } }
    }

    /** Rädd har det längsta ordet (Underlägsen); kärnvyn med en vald nyans och dess precisare ord, på minsta bredd. */
    @Test
    @Config(qualifiers = "w360dp-h800dp-xxhdpi")
    fun `FeelingPicker - nyanser och precisare ord på 360 dp`() {
        selected = listOf(Feeling("calm", "grateful"), Feeling("glad"))
        var opened = false
        rule.captureLightAndDark("FeelingPicker_nyanser_360dp", settle = {
            waitForIdle()
            if (!opened) {
                onNodeWithContentDescription("Rädd").clickWithoutRipple()
                onNodeWithContentDescription("Orolig").clickWithoutRipple()
                opened = true
                waitForIdle()
            }
        }) { Sheet { FeelingPicker(selected, { selected = it }) } }
    }

    /** Stark har Respekterad; kärnan vald ensam (ringen i navet) direkt ur hjulet. */
    @Test
    @Config(qualifiers = "w360dp-h800dp-xxhdpi")
    fun `FeelingPicker - kärnan vald på 360 dp`() {
        selected = listOf(Feeling("sad", "tired", "exhausted"))
        var opened = false
        rule.captureLightAndDark("FeelingPicker_karnan_vald_360dp", settle = {
            waitForIdle()
            if (!opened) {
                onNodeWithContentDescription("Stark").clickWithoutRipple()
                opened = true
                waitForIdle()
            }
        }) { Sheet { FeelingPicker(selected, { selected = it }) } }
    }
}
