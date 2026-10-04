package se.partee71.dagboken.ui.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import se.partee71.dagboken.R
import se.partee71.dagboken.ui.components.AppButton
import se.partee71.dagboken.ui.components.AppSnackbarHost
import se.partee71.dagboken.ui.components.EmptyState
import se.partee71.dagboken.ui.components.ErrorSnackbar
import se.partee71.dagboken.ui.theme.Spacing

/** Inloggningen (AUTH-1, AUTH-2, AUTH-4). Fel visas som snackbar ovanför knappen; avbrott visas inte alls. */
@Composable
fun SignInScreen(state: AuthUiState, onEvent: (AuthEvent) -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    ErrorSnackbar(state.error, snackbar) { onEvent(AuthEvent.ErrorShown) }
    EmptyState(
        icon = R.drawable.ic_book,
        title = stringResource(R.string.app_name),
        message = stringResource(R.string.sign_in_welcome),
        modifier = modifier,
        note = stringResource(R.string.sign_in_note),
        fullScreen = true,
        action = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
                AppSnackbarHost(snackbar)
                AppButton(
                    text = stringResource(if (state.busy) R.string.sign_in_busy else R.string.sign_in_google),
                    onClick = { onEvent(AuthEvent.SignIn(context)) },
                    modifier = Modifier.fillMaxWidth(),
                    icon = R.drawable.ic_login,
                    loading = state.busy,
                )
            }
        },
    )
}
