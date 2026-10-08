package se.partee71.dagboken.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import se.partee71.dagboken.navigation.AppNavigation
import se.partee71.dagboken.navigation.ExportImportKey
import se.partee71.dagboken.reminders.ReminderLaunch
import se.partee71.dagboken.ui.auth.AuthEvent
import se.partee71.dagboken.ui.auth.AuthGate
import se.partee71.dagboken.ui.auth.AuthUiState
import se.partee71.dagboken.ui.auth.AuthViewModel
import se.partee71.dagboken.ui.auth.SignInScreen
import se.partee71.dagboken.ui.auth.UpdateRequiredScreen
import se.partee71.dagboken.ui.components.AppLoading
import se.partee71.dagboken.ui.migration.MigrationGate

/**
 * Appens rot: inloggningen styr vad som visas (AUTH-1, AUTH-6), och efter den startkontrollen för migreringen från
 * 3.x (NAV-6) – migreringsskärmen före flikarna när den erbjuds. [launch] är vad en tryckt påminnelse ska öppna
 * (NOT-9, NOT-11, NOT-12) – det görs när dagboken visas, och [onLaunchHandled] anropas sedan.
 */
@Composable
fun AppRoot(
    modifier: Modifier = Modifier,
    launch: ReminderLaunch? = null,
    onLaunchHandled: () -> Unit = {},
    viewModel: AuthViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    AppRootContent(state, viewModel::onEvent, modifier) {
        MigrationGate(state.account?.uid.orEmpty()) { openImport ->
            AppNavigation(
                state.account,
                onSignOut = { viewModel.onEvent(AuthEvent.SignOut) },
                launch = launch,
                onLaunchHandled = onLaunchHandled,
                openFirst = if (openImport) ExportImportKey else null,
            )
        }
    }
}

/** Appens bakgrund sätts här, en gång; skärmarna ritar ovanpå den. */
@Composable
fun AppRootContent(
    state: AuthUiState,
    onEvent: (AuthEvent) -> Unit,
    modifier: Modifier = Modifier,
    main: @Composable () -> Unit = { AppNavigation(state.account, onSignOut = { onEvent(AuthEvent.SignOut) }) },
) {
    Box(modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        when (state.gate) {
            AuthGate.SignedOut, AuthGate.NeedsUser -> SignInScreen(state, onEvent)
            AuthGate.UpdateRequired -> UpdateRequiredScreen()
            // Syns bara en kort stund vid start; hänger något ser användaren att appen arbetar.
            AuthGate.Loading -> AppLoading()
            AuthGate.Ready -> main()
        }
    }
}
