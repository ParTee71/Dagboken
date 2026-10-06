package se.partee71.dagboken.ui.theme

import androidx.compose.ui.res.stringResource
import androidx.navigation3.runtime.NavEntry
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import se.partee71.dagboken.navigation.AppBackStack
import se.partee71.dagboken.navigation.AppNavHost
import se.partee71.dagboken.testing.captureLightAndDark
import se.partee71.dagboken.R
import se.partee71.dagboken.ui.AppRootContent
import se.partee71.dagboken.ui.auth.AuthGate
import se.partee71.dagboken.ui.auth.AuthUiState
import se.partee71.dagboken.ui.components.AccountAvatar
import se.partee71.dagboken.ui.components.UpcomingScreen

@RunWith(RobolectricTestRunner::class)
class ThemeScreenshotTest {

    /** Hela startskärmen efter inloggning: en flik med stor rubrik, bottenraden och plusknappen (NAV-8–10) – innehållet är temats sak, inte flikens. */
    @Test
    fun `startskärmen i ljust och mörkt tema`() {
        captureLightAndDark("AppRoot_start") {
            AppRootContent(AuthUiState(AuthGate.Ready), {}) {
                AppNavHost(AppBackStack(), { key -> NavEntry(key) { UpcomingScreen(stringResource(R.string.tab_today), R.drawable.ic_sun, stringResource(R.string.trends_watch_upcoming)) { AccountAvatar(null, {}) } } }, onLog = {})
            }
        }
    }
}
