package se.partee71.dagboken.ui

import android.content.Context
import androidx.activity.OnBackPressedDispatcher
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import kotlin.test.assertEquals
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import se.partee71.dagboken.R
import se.partee71.dagboken.data.common.DataError
import se.partee71.dagboken.ui.common.EditorEffect
import se.partee71.dagboken.ui.common.EditorState
import se.partee71.dagboken.ui.common.EditorUiState
import se.partee71.dagboken.ui.common.ListUiState
import se.partee71.dagboken.ui.components.ADD_BUTTON_TAG
import se.partee71.dagboken.ui.theme.DagbokenTheme

private val context: Context get() = ApplicationProvider.getApplicationContext()

private fun text(id: Int, vararg args: Any) = context.getString(id, *args)

/**
 * Kontraktet för en skärm på `EntityListScreen` (NFR-1): laddning, tomt med knapp som lägger
 * till, fel med "Försök igen" och innehåll. Feature-tester anropar det här och testar sedan
 * bara det som är unikt för skärmen.
 *
 * @param screen skärmens innehåll för ett tillstånd, med callbacks för lägg till och försök igen.
 * @param item en rad och [itemText] som ska synas för den.
 * @param addLabel lägg till-knappens text; `null` för en lista utan lägg till (Dagbok) – då prövas att
 *   ingen lägg till-knapp finns, varken i det tomma tillståndet eller ovanför listan.
 */
fun <T> ComposeContentTestRule.runListScreenContract(
    item: T,
    itemText: String,
    emptyTitle: String,
    addLabel: String?,
    screen: @Composable (state: ListUiState<T>, onAdd: () -> Unit, onRetry: () -> Unit) -> Unit,
) {
    var state by mutableStateOf<ListUiState<T>>(ListUiState.Loading)
    var adds = 0
    var retries = 0
    setContent { DagbokenTheme { screen(state, { adds++ }, { retries++ }) } }

    onNodeWithContentDescription(text(R.string.loading)).assertExists()

    state = ListUiState.Empty
    onNodeWithText(emptyTitle).assertIsDisplayed()
    if (addLabel != null) {
        onNodeWithText(addLabel).performClick()
        assertEquals(1, adds, "tomt tillstånd: knappen ska lägga till")
    } else {
        onNodeWithTag(ADD_BUTTON_TAG).assertDoesNotExist()
    }

    state = ListUiState.Error(DataError.Offline)
    onNodeWithText(text(R.string.list_error_title)).assertIsDisplayed()
    onNodeWithText(text(R.string.error_offline)).assertIsDisplayed()
    onNodeWithText(text(R.string.retry)).performClick()
    assertEquals(1, retries, "fel: Försök igen ska läsa om listan")

    state = ListUiState.Content(listOf(item))
    onNodeWithText(itemText).assertIsDisplayed()
    if (addLabel != null) {
        onNodeWithText(addLabel).performClick()
        assertEquals(2, adds, "innehåll: lägg till ska finnas ovanför listan")
    } else {
        onNodeWithTag(ADD_BUTTON_TAG).assertDoesNotExist()
    }
}

/**
 * Kontraktet för en skärm på `EntityEditScreen` + `EditorState` (NFR-2): "Spara" inaktiv tills
 * formuläret är giltigt och ändrat, fältfel efter ändring, "Släng ändringar?" vid bakåt,
 * sparfel som snackbar och tillbaka efter lyckad sparning.
 *
 * @param editor formulärets tillstånd; [makeInvalid] och [makeValid] ändrar det som användaren skulle.
 *   Ett formulär där varje värde är giltigt (t.ex. Påminnelser) har ingen [makeInvalid]; då prövas bara
 *   att "Spara" följer ändrat-läget.
 * @param invalidMessage felet som ska synas efter [makeInvalid].
 * @param screen skärmen; `onSave` sparar via [editor] med ett resultat som kontraktet styr.
 */
fun <T> ComposeContentTestRule.runEditScreenContract(
    editor: EditorState<T>,
    makeInvalid: (EditorState<T>.() -> Unit)?,
    makeValid: EditorState<T>.() -> Unit,
    invalidMessage: String?,
    screen: @Composable (state: EditorUiState<T>, effects: Flow<EditorEffect>, onSave: () -> Unit, onClose: () -> Unit) -> Unit,
) {
    var saveResult: Result<Unit> = Result.failure(DataError.Offline)
    var closes = 0
    var systemBack: OnBackPressedDispatcher? = null
    setContent {
        systemBack = LocalOnBackPressedDispatcherOwner.current?.onBackPressedDispatcher
        DagbokenTheme {
            val state by editor.state.collectAsState()
            val scope = rememberCoroutineScope()
            screen(state, editor.effects, { scope.launch { editor.save { saveResult } } }, { closes++ })
        }
    }
    val saveButton = onNodeWithText(text(R.string.save))
    saveButton.assertIsNotEnabled()

    if (makeInvalid != null) {
        runOnIdle { editor.makeInvalid() }
        onNodeWithText(checkNotNull(invalidMessage) { "ett ogiltigt värde behöver sitt felmeddelande" }).assertIsDisplayed()
        saveButton.assertIsNotEnabled()
    }

    runOnIdle { editor.makeValid() }
    saveButton.assertIsEnabled()

    onNodeWithContentDescription(text(R.string.back)).performClick()
    onNodeWithText(text(R.string.discard_title)).assertIsDisplayed()
    onNodeWithText(text(R.string.keep_editing)).performClick()
    assertEquals(0, closes, "Fortsätt redigera ska stanna kvar")

    // Systemets bakåt (gest eller knapp) frågar på samma sätt som pilen.
    runOnIdle { checkNotNull(systemBack).onBackPressed() }
    onNodeWithText(text(R.string.discard_title)).assertIsDisplayed()
    onNodeWithText(text(R.string.keep_editing)).performClick()
    assertEquals(0, closes, "systemets bakåt med ändringar ska fråga först")

    saveButton.performClick()
    onNodeWithText(text(R.string.error_offline)).assertIsDisplayed()
    assertEquals(0, closes, "sparfel ska inte lämna skärmen")

    saveResult = Result.success(Unit)
    saveButton.performClick()
    waitForIdle()
    assertEquals(1, closes, "lyckad sparning ska gå tillbaka")

    onNodeWithContentDescription(text(R.string.back)).performClick()
    assertEquals(2, closes, "bakåt utan ändringar ska lämna skärmen direkt")
}
