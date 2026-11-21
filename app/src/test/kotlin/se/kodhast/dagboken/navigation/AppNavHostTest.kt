package se.kodhast.dagboken.navigation

import androidx.compose.foundation.clickable
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.navigation3.runtime.NavEntry
import androidx.test.espresso.Espresso
import kotlin.test.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import se.kodhast.dagboken.data.auth.AuthUser
import se.kodhast.dagboken.data.common.DataError
import se.kodhast.dagboken.testing.captureScreenLightAndDark
import se.kodhast.dagboken.testing.clickWithoutRipple
import se.kodhast.dagboken.ui.common.AppErrors
import se.kodhast.dagboken.ui.components.LogChoice
import se.kodhast.dagboken.ui.components.UserMenu
import se.kodhast.dagboken.ui.sync.SyncEvent
import se.kodhast.dagboken.ui.theme.DagbokenTheme
import se.kodhast.ui.common.DetailUiState
import se.kodhast.ui.common.SyncUiState
import se.kodhast.ui.components.APP_HEADER_TAG
import se.kodhast.ui.components.EntityDetailScreen

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
    fun `plusknappen syns bara när den har en åtgärd och fäller ut loggmenyns fem val (NAV-10)`() {
        val picked = mutableListOf<LogChoice>()
        rule.setContent {
            DagbokenTheme {
                AppNavHost(backStack, { key -> NavEntry(key) { Text("Skärm: ${key::class.simpleName}") } }, onLog = { picked += it })
            }
        }
        rule.onNodeWithContentDescription("Logga").performClick()
        // Valen i ordning, ovanför plusknappen (FAB-menyn); ett val stänger menyn och loggar valet.
        val labels = listOf("Mående", "Aktivitet", "Dos", "Händelse", "Sjukdom")
        val tops = labels.map { rule.onNodeWithText(it).assertIsDisplayed().fetchSemanticsNode().boundsInRoot.top }
        assertEquals(tops.sorted(), tops, "valen uppifrån och ned i NAV-10:s ordning")
        rule.onNodeWithText("Aktivitet").performClick()
        assertEquals(listOf(LogChoice.Activity), picked)
        rule.onNodeWithText("Mående").assertDoesNotExist()
        rule.runOnIdle { backStack.push(ComponentGalleryKey) }
        rule.onNodeWithContentDescription("Logga").assertDoesNotExist()
    }

    /** Plusknappens loggmeny öppen i Dagbokens tema: FAB-menyn med fem val över den tonade bottenraden (NAV-10). */
    @Test
    fun `loggmenyn öppen i ljust och mörkt tema`() = rule.captureScreenLightAndDark("AppNavHost_loggmeny", open = { onNodeWithContentDescription("Logga").clickWithoutRipple() }) {
        AppNavHost(AppBackStack(), { key -> NavEntry(key) { Text("Skärm: ${key::class.simpleName}") } }, onLog = {})
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
    fun `systemets tillbaka berättar vilken skärm som stängdes (NAV-9)`() {
        val popped = mutableListOf<AppKey>()
        rule.setContent {
            DagbokenTheme {
                AppNavHost(backStack, { key -> NavEntry(key) { Text("Skärm: ${key::class.simpleName}") } }, onPopped = { popped += it })
            }
        }
        rule.runOnIdle { backStack.push(ThemeKey) }
        rule.onNodeWithText("Skärm: ThemeKey").assertIsDisplayed()
        Espresso.pressBack()
        rule.onNodeWithText("Skärm: TodayKey").assertIsDisplayed()
        assertEquals(listOf<AppKey>(ThemeKey), popped)
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
    fun `flikens sidhuvud har kontot från navigationen och prickarna läser synkläget (NFR-22, NAV-9, NAV-12)`() {
        var sync by mutableStateOf(SyncUiState())
        var opened = 0
        rule.setContent {
            DagbokenTheme {
                AppNavHost(
                    backStack,
                    { key -> NavEntry(key) { EntityDetailScreen(DetailUiState.Content(Unit), header = null, onBack = null, title = "Idag") {} } },
                    sync = sync,
                    account = { UserMenu(AuthUser("uid-anna", "Anna Berg"), { opened++ }, {}, {}, online = sync.online) }
                )
            }
        }
        rule.onNodeWithTag(APP_HEADER_TAG).assertIsDisplayed()
        rule.onNodeWithContentDescription(PENDING).assertDoesNotExist()
        rule.onNodeWithContentDescription(ACCOUNT).assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Utan anslutning"))

        sync = SyncUiState(pending = true, online = true)
        rule.onNodeWithContentDescription(PENDING).assertExists()
        rule.onNodeWithContentDescription(ACCOUNT)
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Ansluten"))
            .performClick()
        rule.onNodeWithText("Inställningar").performClick()
        assertEquals(1, opened)

        sync = SyncUiState(online = true)
        rule.onNodeWithContentDescription(PENDING).assertDoesNotExist()
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
                    sync = SyncUiState(writeError = AppErrors.map(DataError.PermissionDenied)),
                    onSyncEvent = { events += it }
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

    private companion object {
        /** Prickarnas text för TalkBack i android-ui:s sidhuvud. */
        const val PENDING = "Ändringar väntar på servern"
        const val ACCOUNT = "Konto och inställningar"
        const val SHOWN_MILLIS = 500L
        const val LONG_SNACKBAR_MILLIS = 11_000L
    }
}
