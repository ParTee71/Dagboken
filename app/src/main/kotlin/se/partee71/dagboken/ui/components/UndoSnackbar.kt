package se.partee71.dagboken.ui.components

import androidx.annotation.StringRes
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalAccessibilityManager
import androidx.compose.ui.res.stringResource
import kotlinx.coroutines.withTimeoutOrNull
import se.partee71.dagboken.R

/**
 * Något som just dolts och kan ångras; [id] skiljer två döljningar av samma namn åt. [format] är
 * meddelandet ("%s arkiverad", eller ett eget som "%s dold").
 */
data class UndoRequest(val id: String, val name: String, @param:StringRes val format: Int = R.string.archived_format)

/**
 * Den enda ångra-mekanismen: "%s arkiverad · Ångra" (eller [UndoRequest.format], t.ex. "%s dold") i [hostState] i 5 s – längre om
 * användaren valt längre tid för åtgärder i Androids tillgänglighetsinställningar. [onUndo]
 * vid tryck på Ångra, annars [onDismissed]. En ny [request] ersätter den som visas; den förra
 * förblir då dold. Lämnar skärmen kompositionen (flikbyte, ny skärm, rotation) medan ångra
 * erbjuds räknas det som [onDismissed] – annars visades samma meddelande igen när man kom
 * tillbaka (NFR-3). Visar ingenting själv – meddelandet syns i `AppSnackbarHost`.
 */
@Composable
fun UndoSnackbar(request: UndoRequest?, hostState: SnackbarHostState, onUndo: () -> Unit, onDismissed: () -> Unit) {
    val message = request?.let { stringResource(it.format, it.name) }
    val action = stringResource(R.string.undo)
    val undo by rememberUpdatedState(onUndo)
    val dismissed by rememberUpdatedState(onDismissed)
    val accessibility = LocalAccessibilityManager.current
    val current by rememberUpdatedState(request)
    var resolved by remember { mutableStateOf<UndoRequest?>(null) }
    LaunchedEffect(request) {
        resolved = null
        if (request == null || message == null) return@LaunchedEffect
        val timeout = accessibility?.calculateRecommendedTimeoutMillis(UNDO_MILLIS, containsText = true, containsControls = true) ?: UNDO_MILLIS
        val result = withTimeoutOrNull(timeout) {
            hostState.showSnackbar(message, actionLabel = action, withDismissAction = false, duration = SnackbarDuration.Indefinite)
        }
        resolved = request
        if (result == SnackbarResult.ActionPerformed) undo() else dismissed()
    }
    DisposableEffect(Unit) {
        onDispose { current?.let { if (it != resolved) dismissed() } }
    }
}

/** Hur länge ångra erbjuds. */
const val UNDO_MILLIS = 5_000L
