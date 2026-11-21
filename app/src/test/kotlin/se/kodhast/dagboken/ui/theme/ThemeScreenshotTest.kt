package se.kodhast.dagboken.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.navigation3.runtime.NavEntry
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import se.kodhast.dagboken.R
import se.kodhast.dagboken.navigation.AppBackStack
import se.kodhast.dagboken.navigation.AppNavHost
import se.kodhast.dagboken.testing.captureLightAndDark
import se.kodhast.dagboken.ui.AppRootContent
import se.kodhast.dagboken.ui.auth.AuthUiState
import se.kodhast.dagboken.ui.components.UserMenu
import se.kodhast.ui.common.AuthGate
import se.kodhast.ui.components.AppHeader
import se.kodhast.ui.components.EmptyState
import se.kodhast.ui.components.LocalBottomClearance

@RunWith(RobolectricTestRunner::class)
class ThemeScreenshotTest {

    /** Hela startskärmen efter inloggning: en flik med stor rubrik, bottenraden och plusknappen (NAV-8–10) – innehållet är temats sak, inte flikens. */
    @Test
    fun `startskärmen i ljust och mörkt tema`() {
        captureLightAndDark("AppRoot_start") {
            AppRootContent(AuthUiState(AuthGate.Ready), {}) {
                AppNavHost(AppBackStack(), { key -> NavEntry(key) { StartScreen() } }, onLog = {}, account = { UserMenu(null, {}, {}, {}) })
            }
        }
    }

    /** En flik med Kodhäst-sidhuvudet (NAV-12) och ett tomt tillstånd – bara något att rama in i temat. */
    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    private fun StartScreen() {
        Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            AppHeader(stringResource(R.string.tab_today), TopAppBarDefaults.exitUntilCollapsedScrollBehavior())
            Box(Modifier.fillMaxSize().padding(bottom = LocalBottomClearance.current), contentAlignment = Alignment.Center) {
                EmptyState(R.drawable.ic_sun, "Snart här", "Här kommer klockans mått – steg, puls, sömn och mer – med Health Connect-status.")
            }
        }
    }
}
