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
import se.partee71.dagboken.ui.auth.AuthEvent
import se.partee71.dagboken.ui.auth.AuthGate
import se.partee71.dagboken.ui.auth.AuthUiState
import se.partee71.dagboken.ui.auth.AuthViewModel
import se.partee71.dagboken.ui.auth.SignInScreen
import se.partee71.dagboken.ui.auth.UpdateRequiredScreen
import se.partee71.dagboken.ui.components.AppLoading

/** Appens rot: inloggningen styr vad som visas (AUTH-1, AUTH-6). */
@Composable
fun AppRoot(modifier: Modifier = Modifier, viewModel: AuthViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    AppRootContent(state, viewModel::onEvent, modifier) { AppNavigation(onSignOut = { viewModel.onEvent(AuthEvent.SignOut) }) }
}

/** Appens bakgrund sätts här, en gång; skärmarna ritar ovanpå den. */
@Composable
fun AppRootContent(
    state: AuthUiState,
    onEvent: (AuthEvent) -> Unit,
    modifier: Modifier = Modifier,
    main: @Composable () -> Unit = { AppNavigation(onSignOut = { onEvent(AuthEvent.SignOut) }) },
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
