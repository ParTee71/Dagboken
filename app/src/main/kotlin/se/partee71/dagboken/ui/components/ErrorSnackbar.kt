package se.partee71.dagboken.ui.components

import androidx.annotation.StringRes
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalResources
import se.partee71.dagboken.data.common.DataError
import se.partee71.dagboken.ui.common.toMessage

/**
 * Ett fel från en åtgärd i ramen (t.ex. arkivera) som meddelande i [hostState], med samma
 * texter som överallt (NFR-5). [template] (`%1$s` = felets text) sätter felet i ett sammanhang,
 * t.ex. en nekad synk (NFR-1). [onShown] anropas när det visats, så att ViewModeln släpper det.
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
    val resources = LocalResources.current
    val shown by rememberUpdatedState(onShown)
    // [key]: ett nytt fel (även samma sort igen) visas på nytt.
    LaunchedEffect(key) {
        if (error == null) return@LaunchedEffect
        val text = resources.getString(message ?: error.toMessage())
        hostState.showSnackbar(template?.let { resources.getString(it, text) } ?: text, duration = duration)
        shown()
    }
}
