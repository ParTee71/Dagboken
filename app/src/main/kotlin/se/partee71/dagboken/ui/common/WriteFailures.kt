package se.partee71.dagboken.ui.common

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import se.partee71.ui.common.UiError
import se.partee71.ui.common.uiErrorOrNull

/**
 * Skrivfelen för en skärm utan redigeringsram (lista, detalj, Idag): en skrivning som misslyckas blir ett [UiError]
 * (via [AppErrors]) i [failure] tills skärmen visat det ([clear], `ErrorShown`); samma fel två gånger i rad är två
 * händelser. [write] kör en skrivning i [scope] (ViewModelns) och rapporterar dess fel – en gång för alla skärmar.
 */
class WriteFailures(private val scope: CoroutineScope) {
    private val _failure = MutableStateFlow<UiError?>(null)
    val failure: StateFlow<UiError?> = _failure.asStateFlow()

    /** Visar [error] (om det finns ett). */
    fun report(error: UiError?) {
        if (error != null) _failure.value = error
    }

    /** Visar [result]s fel; `true` när det misslyckades. */
    fun report(result: Result<*>): Boolean {
        val error = result.uiErrorOrNull(AppErrors)
        report(error)
        return error != null
    }

    fun clear() {
        _failure.value = null
    }

    fun write(action: suspend () -> Result<Unit>) {
        scope.launch { report(action()) }
    }
}
