package se.partee71.dagboken.ui.components

import androidx.annotation.StringRes
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.res.stringResource
import se.partee71.dagboken.data.common.DataError
import se.partee71.dagboken.ui.common.toMessage

/**
 * Ett fel från en åtgärd i ramen (t.ex. arkivera) som meddelande i [hostState], med samma
 * texter som överallt. [template] (`%1$s` = felets text) sätter felet i ett sammanhang,
 * t.ex. en nekad synk (NFR-22). [onShown] anropas när det visats, så att ViewModeln släpper det.
 */
@Composable
internal fun ErrorSnackbar(
    error: DataError?,
    hostState: SnackbarHostState,
    @StringRes template: Int? = null,
    duration: SnackbarDuration = SnackbarDuration.Short,
    @StringRes message: Int? = null,
    key: Any? = error,
    onShown: () -> Unit,
) {
    val text = error?.let { stringResource(message ?: it.toMessage()) }?.let { text -> template?.let { stringResource(it, text) } ?: text }
    MessageSnackbar(text, hostState, key = key, duration = duration, onShown = onShown)
}

/**
 * Ett meddelande ([text]) i [hostState] – det enda sättet att visa en text som snackbar (regel 4): fel via
 * [ErrorSnackbar], bekräftelser direkt (t.ex. "Alvedon 500 mg loggad" på Idag). [key] gör samma text två
 * gånger i rad till två meddelanden; [onShown] anropas när det visats, så att ViewModeln släpper det.
 */
@Composable
internal fun MessageSnackbar(
    text: String?,
    hostState: SnackbarHostState,
    key: Any? = text,
    duration: SnackbarDuration = SnackbarDuration.Short,
    onShown: () -> Unit,
) {
    val shown by rememberUpdatedState(onShown)
    val current by rememberUpdatedState(text)
    LaunchedEffect(key) {
        val message = current ?: return@LaunchedEffect
        hostState.showSnackbar(message, duration = duration)
        shown()
    }
}
