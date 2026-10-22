package se.partee71.dagboken.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.navigation3.runtime.NavEntry
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import se.partee71.dagboken.R
import se.partee71.dagboken.navigation.AppBackStack
import se.partee71.dagboken.navigation.AppNavHost
import se.partee71.dagboken.testing.captureLightAndDark
import se.partee71.dagboken.ui.AppRootContent
import se.partee71.dagboken.ui.auth.AuthGate
import se.partee71.dagboken.ui.auth.AuthUiState
import se.partee71.dagboken.ui.components.AccountAvatar
import se.partee71.ui.components.AppTopBar
import se.partee71.ui.components.EmptyState
import se.partee71.ui.components.LocalBottomClearance
import se.partee71.ui.components.TopBarSize

@RunWith(RobolectricTestRunner::class)
class ThemeScreenshotTest {

    /** Hela startskärmen efter inloggning: en flik med stor rubrik, bottenraden och plusknappen (NAV-8–10) – innehållet är temats sak, inte flikens. */
    @Test
    fun `startskärmen i ljust och mörkt tema`() {
        captureLightAndDark("AppRoot_start") {
            AppRootContent(AuthUiState(AuthGate.Ready), {}) {
                AppNavHost(AppBackStack(), { key -> NavEntry(key) { StartScreen() } }, onLog = {})
            }
        }
    }

    /** En flik med stor rubrik och ett tomt tillstånd – bara något att rama in i temat. */
    @Composable
    private fun StartScreen() {
        Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            AppTopBar(stringResource(R.string.tab_today), size = TopBarSize.Large, actions = { AccountAvatar(null, {}) })
            Box(Modifier.fillMaxSize().padding(bottom = LocalBottomClearance.current), contentAlignment = Alignment.Center) {
                EmptyState(R.drawable.ic_sun, "Snart här", "Här kommer klockans mått – steg, puls, sömn och mer – med Health Connect-status.")
            }
        }
    }
}
