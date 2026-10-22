package se.partee71.dagboken.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotFocused
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import se.partee71.dagboken.core.model.Feeling
import se.partee71.dagboken.core.model.FeelingStrength
import se.partee71.dagboken.core.model.Screening
import se.partee71.dagboken.ui.theme.DagbokenTheme
import se.partee71.ui.components.LongPressSemantics
import se.partee71.ui.components.longPress

/**
 * Styrka och kommentar per känsla i väljaren (SCR-9, SCR-10): långtrycket på det yttersta valda ordet, TalkBack-åtgärden
 * "Starkare", styrkan i ord, att ett släckt ord nollställs, kommentarsfälten – och den delade gesten `longPress` (android-ui) med haptik och anpassad åtgärd.
 * Hög skärm, så att gesterna når orden längst ner i kärnvyn.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h1200dp")
class FeelingStrengthTest {

    @get:Rule
    val rule = createComposeRule()

    private var post by mutableStateOf(Screening("s"))

    private fun show(content: @Composable () -> Unit) = rule.setContent { DagbokenTheme(content = content) }

    private fun picker() = show {
        FeelingPicker(post.feelings, { post = post.withFeelings(it) }, extras = FeelingExtras.of(post) { change -> post = change(post) })
    }

    private fun stronger(description: String) = rule.onNodeWithContentDescription(description).performTouchInput { longClick() }

    private fun described(prefix: String) = SemanticsMatcher(prefix) { node -> node.config.getOrNull(SemanticsProperties.ContentDescription).orEmpty().any { it.startsWith(prefix) } }

    private val noteFields = described("Kommentar till")
    private val addNote = described("Lägg till varför")

    private fun state(text: String) = SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, text)

    @Test
    fun `långtryck på navet när bara kärnan är vald stegar svag, tydlig, stark och tillbaka till ingen`() {
        picker()
        rule.onNodeWithContentDescription("Lugn").performClick()
        val seen = listOf("Lugn", "Lugn, svag", "Lugn, tydlig", "Lugn, stark").map { description ->
            stronger(description)
            rule.waitForIdle()
            post.strengthOf(Feeling("calm"))
        }
        assertEquals(listOf(FeelingStrength.MILD, FeelingStrength.CLEAR, FeelingStrength.STRONG, null), seen)
        rule.onNodeWithContentDescription("Lugn").assert(state("vald"))
    }

    @Test
    fun `bara det yttersta valda ordet tar långtrycket - navet ovanför gör ingenting, vanligt tryck väljer som förut`() {
        post = Screening("s", feelings = listOf(Feeling("calm", "grateful", "humble")))
        picker()
        rule.onNodeWithContentDescription("Lugn").performClick()
        stronger("Lugn")
        rule.waitForIdle()
        assertEquals(emptyMap(), post.feelingStrengths, "navet är inte yttersta")
        rule.onNodeWithContentDescription("Tacksam").assert(SemanticsMatcher.keyNotDefined(SemanticsActions.CustomActions))
        stronger("Ödmjuk")
        rule.waitForIdle()
        assertEquals(mapOf("calm.grateful.humble" to "mild"), post.feelingStrengths)
        rule.onNodeWithContentDescription("Kärleksfull").performClick()
        rule.waitForIdle()
        assertEquals(listOf(Feeling("calm", "grateful", "humble"), Feeling("calm", "loving")), post.feelings, "vanligt tryck lägger till som förut")
    }

    @Test
    fun `förfining tar med styrkan, ett släckt ord nollställer den`() {
        picker()
        rule.onNodeWithContentDescription("Lugn").performClick()
        stronger("Lugn")
        rule.onNodeWithContentDescription("Tacksam").performClick()
        rule.waitForIdle()
        assertEquals(FeelingStrength.MILD, post.strengthOf(Feeling("calm", "grateful")), "följer med till nyansen")
        rule.onNodeWithContentDescription("Tacksam, svag").performClick()
        rule.waitForIdle()
        assertEquals(listOf(Feeling("calm")), post.feelings)
        assertNull(post.strengthOf(Feeling("calm")), "nyansen släcktes – styrkan nollställd")
    }

    @Test
    fun `TalkBack - det yttersta ordet har åtgärden Starkare, och pillen läser styrkan`() {
        post = Screening("s", feelings = listOf(Feeling("glad")), feelingStrengths = mapOf("glad" to "clear"))
        picker()
        rule.onNodeWithContentDescription("Glad, tydlig").performClick()
        val hub = rule.onNodeWithContentDescription("Glad, tydlig")
        val action = hub.fetchSemanticsNode().config[SemanticsActions.CustomActions].single()
        assertEquals("Starkare", action.label)
        rule.runOnIdle { action.action() }
        assertEquals(FeelingStrength.STRONG, post.strengthOf(Feeling("glad")))
        rule.onNodeWithText("Glad").assert(state("stark"))
    }

    @Test
    fun `kommentarsfält per vald känsla - fälls ut, skrivs, läses av TalkBack och försvinner med känslan`() {
        post = Screening("s", feelings = listOf(Feeling("calm", "grateful"), Feeling("glad")))
        picker()
        assertEquals(0, rule.onAllNodes(noteFields).fetchSemanticsNodes().size, "hopfällda som standard")
        rule.onNodeWithContentDescription("Lägg till varför för Lugn › Tacksam").performClick()
        rule.waitForIdle()
        assertEquals(emptyMap(), post.feelingNotes, "att fälla ut ändrar inte posten")
        rule.onNodeWithContentDescription("Kommentar till Lugn › Tacksam").assertIsFocused().performTextInput("Promenad")
        rule.waitForIdle()
        assertEquals(mapOf("calm.grateful" to "Promenad"), post.feelingNotes)
        rule.onNodeWithText("Lugn › Tacksam").performClick()
        rule.waitForIdle()
        assertEquals(emptyMap(), post.feelingNotes)
        rule.onNodeWithContentDescription("Lägg till varför för Glad").assertExists()
        assertEquals(0, rule.onAllNodes(noteFields).fetchSemanticsNodes().size)
    }

    @Test
    fun `en sparad kommentar visas utfälld utan fokus, de andra hopfällda`() {
        post = Screening("s", feelings = listOf(Feeling("calm", "grateful"), Feeling("glad")), feelingNotes = mapOf("calm.grateful" to "Promenad"))
        picker()
        rule.onNodeWithContentDescription("Kommentar till Lugn › Tacksam").assertIsNotFocused().assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.EditableText))
        rule.onNodeWithContentDescription("Lägg till varför för Glad").assertExists()
        rule.onNodeWithContentDescription("Lägg till varför för Lugn › Tacksam").assertDoesNotExist()
    }

    @Test
    fun `Tillbaka till hjulet står överst i kärnvyn, ovanför navet`() {
        post = Screening("s", feelings = listOf(Feeling("calm", "grateful"), Feeling("glad"), Feeling("scared", "anxious", "tense")))
        picker()
        rule.onNodeWithContentDescription("Lugn").performClick()
        val back = rule.onNodeWithText("Tillbaka till hjulet").fetchSemanticsNode().boundsInRoot
        val hub = rule.onNodeWithContentDescription("Lugn").fetchSemanticsNode().boundsInRoot
        assertTrue(back.bottom <= hub.top, "tillbaka ($back) ska ligga ovanför navet ($hub)")
    }

    @Test
    fun `utan extras - inget långtryck och inga kommentarsfält, som förut`() {
        var selected by mutableStateOf(listOf(Feeling("calm")))
        show { FeelingPicker(selected, { selected = it }) }
        rule.onNodeWithContentDescription("Lugn").performClick()
        rule.onNodeWithContentDescription("Lugn").assert(SemanticsMatcher.keyNotDefined(SemanticsActions.CustomActions))
        assertEquals(0, rule.onAllNodes(noteFields).fetchSemanticsNodes().size)
        assertEquals(0, rule.onAllNodes(addNote).fetchSemanticsNodes().size)
    }

    @Test
    fun `longPress - långtryck kör åtgärden en gång med haptik och inte trycket, kort tryck bara trycket`() {
        var clicks = 0
        var presses = 0
        val haptics = mutableListOf<HapticFeedbackType>()
        val haptic = object : HapticFeedback {
            override fun performHapticFeedback(hapticFeedbackType: HapticFeedbackType) {
                haptics += hapticFeedbackType
            }
        }
        show {
            CompositionLocalProvider(LocalHapticFeedback provides haptic) {
                Box(
                    Modifier.size(48.dp).testTag("yta")
                        .longPress("Starkare", { presses++ }, haptic = true, semantics = LongPressSemantics.CustomAction)
                        .clickable { clicks++ }
                )
            }
        }
        rule.onNodeWithTag("yta").performTouchInput { longClick() }
        rule.runOnIdle {
            assertEquals(1, presses)
            assertEquals(0, clicks, "långtrycket tar gesten")
            assertEquals(listOf(HapticFeedbackType.LongPress), haptics)
        }
        rule.onNodeWithTag("yta").performClick()
        rule.runOnIdle {
            assertEquals(1, presses)
            assertEquals(1, clicks)
        }
        val action = rule.onNodeWithTag("yta").fetchSemanticsNode().config[SemanticsActions.CustomActions].single()
        assertEquals("Starkare", action.label)
        rule.runOnIdle { action.action() }
        assertEquals(2, presses)
    }
}
