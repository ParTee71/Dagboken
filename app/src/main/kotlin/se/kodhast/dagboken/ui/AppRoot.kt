package se.kodhast.dagboken.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import se.kodhast.dagboken.R
import se.kodhast.dagboken.navigation.AppNavigation
import se.kodhast.dagboken.reminders.ReminderLaunch
import se.kodhast.dagboken.ui.auth.AuthEvent
import se.kodhast.dagboken.ui.auth.AuthUiState
import se.kodhast.dagboken.ui.auth.AuthViewModel
import se.kodhast.dagboken.ui.common.AppErrors
import se.kodhast.ui.components.AuthGateHost
import se.kodhast.ui.components.SignInScreen
import se.kodhast.ui.components.UpdateRequiredScreen

/**
 * Appens rot: inloggningen styr vad som visas (AUTH-1, AUTH-6), sedan flikarna. [launch] är vad en tryckt påminnelse
 * ska öppna (NOT-9, NOT-11, NOT-12) – det görs när dagboken visas, och [onLaunchHandled] anropas sedan.
 */
@Composable
fun AppRoot(
    modifier: Modifier = Modifier,
    launch: ReminderLaunch? = null,
    onLaunchHandled: () -> Unit = {},
    viewModel: AuthViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    AppRootContent(state, viewModel::onEvent, modifier) {
        AppNavigation(
            state.account,
            onSignOut = { viewModel.onEvent(AuthEvent.SignOut) },
            launch = launch,
            onLaunchHandled = onLaunchHandled
        )
    }
}

/**
 * Android-ui:s [AuthGateHost] med Dagbokens texter: inloggningen visar appens bok, inte Kodhäst-hästen (DSN-8),
 * och "Uppdatera appen" länkar till Dagbokens releaser (BCK-15). Fel visas en gång; avbrott visas inte alls (AUTH-4).
 */
@Composable
fun AppRootContent(
    state: AuthUiState,
    onEvent: (AuthEvent) -> Unit,
    modifier: Modifier = Modifier,
    main: @Composable () -> Unit = { AppNavigation(state.account, onSignOut = { onEvent(AuthEvent.SignOut) }) }
) {
    AuthGateHost(
        gate = state.gate,
        signIn = {
            val context = LocalContext.current
            SignInScreen(
                appIcon = R.drawable.ic_book,
                appName = stringResource(R.string.app_name),
                welcome = stringResource(R.string.sign_in_welcome),
                note = stringResource(R.string.sign_in_note),
                busy = state.busy,
                error = remember(state.error) { state.error?.let(AppErrors::map) },
                onErrorShown = { onEvent(AuthEvent.ErrorShown) },
                onSignIn = { onEvent(AuthEvent.SignIn(context)) }
            )
        },
        updateRequired = {
            UpdateRequiredScreen(
                message = stringResource(R.string.update_required_message),
                note = stringResource(R.string.update_required_note),
                releasesUrl = stringResource(R.string.releases_url)
            )
        },
        modifier = modifier,
        content = main
    )
}
