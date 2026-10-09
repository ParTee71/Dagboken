package se.partee71.dagboken.ui.components

import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.test.espresso.Espresso
import kotlin.test.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import se.partee71.dagboken.ui.theme.DagbokenTheme

/**
 * `AppBottomSheet` med ett formulär (NFR-10): med osparade ändringar ([dirty]) stänger varken svep ner, tryck
 * utanför eller bakåt arket – "Släng ändringar?" frågar först och arket står kvar tills svaret; med
 * `canDismiss` = false går det inte att stänga alls. Utan ändringar stängs det som förut.
 */
@RunWith(RobolectricTestRunner::class)
class AppBottomSheetTest {

    @get:Rule
    val rule = createComposeRule()

    private var dismissed = 0

    private fun show(dirty: Boolean = false, canDismiss: () -> Boolean = { true }) {
        rule.setContent {
            DagbokenTheme {
                AppBottomSheet("Promenad", onDismiss = { dismissed++ }, dirty = dirty, canDismiss = canDismiss) { Text("45 minuter") }
            }
        }
    }

    /** Ett svep från rubriken långt nedåt – längre än arket, som annars fjädrar tillbaka. */
    private fun swipeDown() = rule.onNodeWithText("Promenad").performTouchInput { swipeDown(startY = centerY, endY = centerY + SWIPE_PX) }

    private fun tapOutside() = rule.onNodeWithContentDescription(SCRIM).performClick()

    private fun assertAsks() {
        rule.onNodeWithText("Släng ändringar?").assertIsDisplayed()
        rule.onNodeWithText("45 minuter").assertIsDisplayed()
        assertEquals(0, dismissed, "arket står kvar tills svaret")
        rule.onNodeWithText("Fortsätt redigera").performClick()
        rule.onNodeWithText("Släng ändringar?").assertDoesNotExist()
        rule.onNodeWithText("45 minuter").assertIsDisplayed()
        assertEquals(0, dismissed)
    }

    @Test
    fun `med ändringar frågar svep ner, tryck utanför och bakåt först – Släng stänger`() {
        show(dirty = true)
        swipeDown()
        assertAsks()
        tapOutside()
        assertAsks()
        Espresso.pressBack()
        assertAsks()

        swipeDown()
        rule.onNodeWithText("Släng").performClick()
        rule.waitForIdle()
        assertEquals(1, dismissed)
    }

    @Test
    fun `utan ändringar stänger svep ner och tryck utanför direkt`() {
        show()
        tapOutside()
        rule.waitForIdle()
        assertEquals(1, dismissed)
        rule.onNodeWithText("Släng ändringar?").assertDoesNotExist()
    }

    @Test
    fun `canDismiss läses i stunden – false håller arket öppet utan fråga, också utan ny komposition`() {
        var allowed = true
        show(dirty = true, canDismiss = { allowed })
        // Ändras utanför Compose (som ett StateFlow.value i ViewModeln) – ingen omkomposition behövs.
        allowed = false
        swipeDown()
        tapOutside()
        Espresso.pressBack()
        rule.waitForIdle()
        rule.onNodeWithText("Släng ändringar?").assertDoesNotExist()
        rule.onNodeWithText("45 minuter").assertIsDisplayed()
        assertEquals(0, dismissed)
        allowed = true
        swipeDown()
        rule.onNodeWithText("Släng ändringar?").assertIsDisplayed()
    }

    @Test
    fun `Släng döljer arket först och anropar sedan onDismiss en gång`() {
        show(dirty = true)
        swipeDown()
        rule.onNodeWithText("Släng").performClick()
        rule.waitForIdle()
        assertEquals(1, dismissed)
        rule.onNodeWithText("Släng ändringar?").assertDoesNotExist()
    }

    @Test
    fun `efter ett konfigurationsbyte är frågan borta och arket står kvar`() {
        val restoration = StateRestorationTester(rule)
        restoration.setContent {
            DagbokenTheme {
                AppBottomSheet("Promenad", onDismiss = { dismissed++ }, dirty = true) { Text("45 minuter") }
            }
        }
        swipeDown()
        rule.onNodeWithText("Släng ändringar?").assertIsDisplayed()
        restoration.emulateSavedInstanceStateRestore()
        rule.onNodeWithText("Släng ändringar?").assertDoesNotExist()
        rule.onNodeWithText("45 minuter").assertIsDisplayed()
        // Frågan går att få igen, ovanpå arket.
        swipeDown()
        rule.onNodeWithText("Släng").performClick()
        rule.waitForIdle()
        assertEquals(1, dismissed)
    }

    private companion object {
        /** Material 3:s namn på arkets bakgrund (scrim), som stänger arket vid tryck. */
        const val SCRIM = "Close sheet"

        const val SWIPE_PX = 1500f
    }

    /** NFR-21: med ett innehåll högre än skärmen står knappraden kvar i botten – synlig utan att scrolla. */
    @Test
    fun `knappraden syns med långt innehåll`() {
        rule.setContent {
            DagbokenTheme {
                AppBottomSheet("Promenad", onDismiss = {}, footer = { AppButton("Spara", {}) }) {
                    repeat(40) { Text("Rad $it") }
                }
            }
        }
        rule.onNodeWithText("Spara").assertIsDisplayed()
        rule.onNodeWithText("Rad 39").assertIsNotDisplayed()
    }

    /** Måendearket på symptomsteget med alla symptom utfällda: Föregående och Spara syns utan att scrolla. */
    @Test
    fun `stegarkets knappar syns med utfällda symptom`() {
        var saved = 0
        rule.setContent {
            DagbokenTheme {
                val steps = rememberStepwiseScreeningState(GALLERY_SYMPTOMS)
                LaunchedEffect(steps) { steps.pager.scrollToPage(2) }
                AppBottomSheet("Efter frukost", onDismiss = {}, footer = { StepwiseScreeningNavigation(steps, { saved++ }) }) {
                    StepwiseScreeningForm(6, {}, 4, {}, GALLERY_SYMPTOMS, GALLERY_SCORES, {}, onSave = {}, state = steps, navigation = false)
                }
            }
        }
        rule.onNodeWithText("Steg 3 av 3").assertIsDisplayed()
        rule.onNodeWithText("Föregående").assertIsDisplayed()
        rule.onNodeWithText("Spara").assertIsDisplayed().performClick()
        assertEquals(1, saved)
    }
}
