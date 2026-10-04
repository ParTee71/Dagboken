package se.partee71.dagboken.ui.theme

import androidx.navigation3.runtime.NavEntry
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import se.partee71.dagboken.navigation.AppBackStack
import se.partee71.dagboken.navigation.AppNavHost
import se.partee71.dagboken.navigation.TopLevelKey
import se.partee71.dagboken.testing.captureLightAndDark
import se.partee71.dagboken.ui.AppRootContent
import se.partee71.dagboken.ui.TabPlaceholderScreen
import se.partee71.dagboken.ui.auth.AuthGate
import se.partee71.dagboken.ui.auth.AuthUiState

@RunWith(RobolectricTestRunner::class)
class ThemeScreenshotTest {

    /** Hela startskärmen efter inloggning: Idag med avataren, bottenraden och plusknappen (NAV-8–10). */
    @Test
    fun `startskärmen i ljust och mörkt tema`() {
        captureLightAndDark("AppRoot_start") {
            AppRootContent(AuthUiState(AuthGate.Ready), {}) {
                AppNavHost(AppBackStack(), { key -> NavEntry(key) { TabPlaceholderScreen(key as TopLevelKey, onAccount = {}) } }, onLog = {})
            }
        }
    }
}
