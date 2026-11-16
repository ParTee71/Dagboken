package se.kodhast.dagboken.ui.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import se.kodhast.dagboken.R
import se.kodhast.dagboken.ui.common.AppErrors
import se.kodhast.ui.components.AppButton
import se.kodhast.ui.components.AppSnackbarHost
import se.kodhast.ui.components.EmptyState
import se.kodhast.ui.components.ErrorSnackbar
import se.kodhast.ui.components.LocalAppBrandStyle
import se.kodhast.ui.theme.Spacing

/**
 * Inloggningen (AUTH-1, AUTH-2, AUTH-4). Fel visas som snackbar ovanför knappen; avbrott visas inte alls.
 * Ikonrutan visar appens bok, inte Kodhäst-hästen (DSN-8): inloggningen ska kännas igen som Dagboken.
 */
@Composable
fun SignInScreen(state: AuthUiState, onEvent: (AuthEvent) -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    ErrorSnackbar(remember(state.error) { state.error?.let(AppErrors::map) }, snackbar) { onEvent(AuthEvent.ErrorShown) }
    CompositionLocalProvider(LocalAppBrandStyle provides LocalAppBrandStyle.current.copy(emptyStateMark = false)) {
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
                        loading = state.busy
                    )
                }
            }
        )
    }
}
