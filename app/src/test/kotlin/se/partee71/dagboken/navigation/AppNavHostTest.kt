package se.partee71.dagboken.navigation

import androidx.compose.foundation.clickable
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.navigation3.runtime.NavEntry
import kotlin.test.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import se.partee71.dagboken.data.common.DataError
import se.partee71.dagboken.ui.components.AppTopBar
import se.partee71.dagboken.ui.sync.SyncEvent
import se.partee71.dagboken.ui.sync.SyncUiState
import se.partee71.dagboken.ui.theme.DagbokenTheme

/** Navigationsramen: bottenraden med flikar och plusknapp i flikarnas rot, dold på undersidor (NAV-3, NAV-8, NAV-10). */
@RunWith(RobolectricTestRunner::class)
class AppNavHostTest {

    @get:Rule
    val rule = createComposeRule()

    private val backStack = AppBackStack()

    private fun show() = rule.setContent {
        DagbokenTheme {
            AppNavHost(backStack, { key -> NavEntry(key) { Text("Skärm: ${key::class.simpleName}") } })
        }
    }

    @Test
    fun `bottenraden byter flik och visar flikens skärm`() {
        show()
        rule.onNodeWithText("Skärm: TodayKey").assertIsDisplayed()
        rule.onNodeWithContentDescription("Dagbok").performClick()
        rule.onNodeWithText("Skärm: DiaryKey").assertIsDisplayed()
        assertEquals(DiaryKey, backStack.currentTab)
        listOf("Idag", "Trender", "Mediciner").forEach { rule.onNodeWithContentDescription(it).assertIsDisplayed() }
    }

    @Test
    fun `plusknappen syns bara när den har en åtgärd och öppnar loggmenyn (NAV-10)`() {
        var opened = 0
        rule.setContent {
            DagbokenTheme {
                AppNavHost(backStack, { key -> NavEntry(key) { Text("Skärm: ${key::class.simpleName}") } }, onLog = { opened++ })
            }
        }
        rule.onNodeWithContentDescription("Logga").performClick()
        assertEquals(1, opened)
        rule.runOnIdle { backStack.push(ComponentGalleryKey) }
        rule.onNodeWithContentDescription("Logga").assertDoesNotExist()
    }

    @Test
    fun `utan åtgärd finns ingen plusknapp`() {
        show()
        rule.onNodeWithContentDescription("Logga").assertDoesNotExist()
    }

    @Test
    fun `en flik behåller sitt sparade tillstånd när man byter flik och tillbaka`() {
        rule.setContent {
            DagbokenTheme {
                AppNavHost(backStack, { key ->
                    NavEntry(key) {
                        var taps by rememberSaveable { mutableIntStateOf(0) }
                        Text("${key::class.simpleName}: $taps", Modifier.clickable { taps++ })
                    }
                })
            }
        }
        rule.onNodeWithText("DiaryKey", substring = true).assertDoesNotExist()
        rule.onNodeWithContentDescription("Dagbok").performClick()
        rule.onNodeWithText("DiaryKey: 0").performClick()
        rule.onNodeWithText("DiaryKey: 1").assertIsDisplayed()
        rule.onNodeWithContentDescription("Trender").performClick()
        rule.onNodeWithContentDescription("Dagbok").performClick()
        rule.onNodeWithText("DiaryKey: 1").assertIsDisplayed()
    }

    @Test
    fun `en stängd undersida börjar om när den öppnas igen`() {
        rule.setContent {
            DagbokenTheme {
                AppNavHost(backStack, { key ->
                    NavEntry(key) {
                        var taps by rememberSaveable { mutableIntStateOf(0) }
                        Text("${key::class.simpleName}: $taps", Modifier.clickable { taps++ })
                    }
                })
            }
        }
        rule.runOnIdle { backStack.push(ComponentGalleryKey) }
        rule.onNodeWithText("ComponentGalleryKey: 0").performClick()
        rule.runOnIdle { backStack.pop() }
        rule.runOnIdle { backStack.push(ComponentGalleryKey) }
        rule.onNodeWithText("ComponentGalleryKey: 0").assertIsDisplayed()
    }

    @Test
    fun `undersidor döljer bottenraden`() {
        show()
        rule.runOnIdle { backStack.push(ComponentGalleryKey) }
        rule.onNodeWithText("Skärm: ComponentGalleryKey").assertIsDisplayed()
        rule.onNodeWithContentDescription("Dagbok").assertDoesNotExist()
        rule.runOnIdle { backStack.pop() }
        rule.onNodeWithContentDescription("Dagbok").assertIsDisplayed()
    }

    @Test
    fun `synkindikatorn syns i skärmens toppbar och förklarar sig vid tryck (NFR-22)`() {
        var sync by mutableStateOf(SyncUiState())
        rule.setContent {
            DagbokenTheme {
                AppNavHost(backStack, { key -> NavEntry(key) { AppTopBar("Idag") } }, sync = sync)
            }
        }
        rule.onNodeWithContentDescription("Ändringar väntar på att synkas").assertDoesNotExist()
        sync = SyncUiState(pending = true)
        rule.onNodeWithContentDescription("Ändringar väntar på att synkas").performClick()
        rule.onNodeWithText("Ändringarna är sparade på telefonen och skickas när det finns nät.").assertIsDisplayed()
    }

    @Test
    fun `en nekad skrivning visas med orsaken och släpps sedan (NFR-22)`() {
        val events = mutableListOf<SyncEvent>()
        rule.mainClock.autoAdvance = false
        rule.setContent {
            DagbokenTheme {
                AppNavHost(
                    backStack,
                    { key -> NavEntry(key) { Text("Skärm: ${key::class.simpleName}") } },
                    sync = SyncUiState(writeError = DataError.PermissionDenied),
                    onSyncEvent = { events += it },
                )
            }
        }
        rule.mainClock.advanceTimeBy(SHOWN_MILLIS)
        rule.onNodeWithText("En ändring kunde inte sparas och har ångrats. Du har inte behörighet till de här uppgifterna.").assertIsDisplayed()
        assertEquals(emptyList(), events)
        rule.mainClock.advanceTimeBy(LONG_SNACKBAR_MILLIS)
        rule.waitForIdle()
        assertEquals(listOf<SyncEvent>(SyncEvent.WriteErrorShown), events)
    }

    @Test
    fun `tryck på synkindikatorn stänger inte ett felmeddelande som visas`() {
        rule.mainClock.autoAdvance = false
        rule.setContent {
            DagbokenTheme {
                AppNavHost(
                    backStack,
                    { key -> NavEntry(key) { AppTopBar("Idag") } },
                    sync = SyncUiState(pending = true, writeError = DataError.PermissionDenied),
                )
            }
        }
        rule.mainClock.advanceTimeBy(SHOWN_MILLIS)
        rule.onNodeWithContentDescription("Ändringar väntar på att synkas").performClick()
        rule.mainClock.advanceTimeBy(SHOWN_MILLIS)
        rule.onNodeWithText("En ändring kunde inte sparas och har ångrats. Du har inte behörighet till de här uppgifterna.").assertIsDisplayed()
        rule.onNodeWithText("Ändringarna är sparade på telefonen och skickas när det finns nät.").assertDoesNotExist()
    }

    private companion object {
        const val SHOWN_MILLIS = 500L
        const val LONG_SNACKBAR_MILLIS = 11_000L
    }
}
