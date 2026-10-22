package se.partee71.dagboken.ui.common

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import kotlin.test.assertEquals
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import se.partee71.dagboken.R
import se.partee71.dagboken.ui.theme.DagbokenTheme
import se.partee71.ui.common.EditorEvents
import se.partee71.ui.common.EditorUiState
import se.partee71.ui.components.DeleteAction

/** Postformulärets ram (`EntryEditScreen`) och dag + klockslag (`EntryDateTimeRow`) – det som är gemensamt för alla postformulär. */
@RunWith(RobolectricTestRunner::class)
class EntryEditScreenTest {

    @get:Rule
    val rule = createComposeRule()

    private data class Post(val title: String, val date: LocalDate? = null, val time: LocalTime? = null)

    private val shown = setOf("title")

    private fun form(state: EditorUiState<Post>, isNew: Boolean = true, stored: Post? = null, onEvent: (EntryEditEvent<Post>) -> Unit = {}) = EntryForm(isNew, state, EditorEvents.None, onEvent, stored)

    private fun show(form: EntryForm<Post>, content: @Composable (error: (String) -> String?) -> Unit = {}) = rule.setContent {
        DagbokenTheme {
            EntryEditScreen(form, "Ny post", {}, shown, delete = { post, onConfirm -> DeleteAction("Radera posten?", "${post.title} raderas.", onConfirm) }) { error -> content(error) }
        }
    }

    @Test
    fun `Spara skickar Save, och en ny post har ingen radering`() {
        val events = mutableListOf<EntryEditEvent<Post>>()
        show(form(EditorUiState(Post("Promenad"), isDirty = true), onEvent = { events += it }))
        rule.onNodeWithContentDescription("Fler val").assertDoesNotExist()
        rule.onNodeWithText("Spara").performClick()
        assertEquals(listOf<EntryEditEvent<Post>>(EntryEditEvent.Save), events)
    }

    @Test
    fun `en sparad post raderas efter bekräftelsen som namnger den lagrade`() {
        val events = mutableListOf<EntryEditEvent<Post>>()
        show(form(EditorUiState(Post("Ändrad")), isNew = false, stored = Post("Promenad"), onEvent = { events += it }))
        rule.onNodeWithContentDescription("Fler val").performClick()
        rule.onNodeWithText("Radera").performClick()
        rule.onNodeWithText("Promenad raderas.").assertIsDisplayed()
        assertEquals(emptyList(), events, "inget raderas utan bekräftelse")
        rule.onNodeWithText("Radera").performClick()
        assertEquals(listOf<EntryEditEvent<Post>>(EntryEditEvent.Delete), events)
    }

    @Test
    fun `fältets fel kommer som text, och ett fel utanför de visade står överst`() {
        val errors = persistentMapOf("title" to R.string.field_not_savable)
        show(form(EditorUiState(Post("x"), errors = errors, isValid = false))) { error -> Text(error("title") ?: "inget fel") }
        rule.onNodeWithText("Värdet går inte att spara – korta eller ändra det").assertIsDisplayed()
        rule.onNodeWithText("Ett värde går inte att spara – ändra det för att kunna spara.").assertDoesNotExist()
    }

    @Test
    fun `ett fel på ett fält som formuläret inte visar ger det gemensamma felet`() {
        val errors = persistentMapOf("minutes" to R.string.field_not_savable)
        show(form(EditorUiState(Post("x"), errors = errors, isValid = false))) { error -> Text(error("title") ?: "inget fel") }
        rule.onNodeWithText("inget fel").assertIsDisplayed()
        rule.onNodeWithText("Ett värde går inte att spara – ändra det för att kunna spara.").assertIsDisplayed()
    }

    @Test
    fun `dag och klockslag visas bara med en dag, och utan klockslag som 00-00`() {
        val withDay = form(EditorUiState(Post("Promenad", LocalDate(2026, 10, 6))))
        val withoutDay = form(EditorUiState(Post("Promenad")))
        rule.setContent {
            DagbokenTheme {
                Column {
                    EntryDateTimeRow(withoutDay, withoutDay.value.date, withoutDay.value.time, { copy(date = it) }, { copy(time = it) })
                    EntryDateTimeRow(withDay, withDay.value.date, withDay.value.time, { copy(date = it) }, { copy(time = it) })
                }
            }
        }
        // TalkBack läser fältet som "Tid, 00:00"; bara posten med en dag har en rad.
        rule.onAllNodesWithContentDescription("Tid, ", substring = true).assertCountEquals(1)
        rule.onNodeWithContentDescription("Tid, 00:00", substring = true).assertExists()
    }
}
