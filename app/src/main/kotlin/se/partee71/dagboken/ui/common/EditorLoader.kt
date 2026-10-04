package se.partee71.dagboken.ui.common

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Läser det lagrade värdet in i ett formulär – en gång för alla formulär (skill
 * shared-ui-components): läser direkt, [retry] ("Försök igen") läser om, och [stored] är det lagrade
 * värdet – det som raderas, arkiveras eller återställs, oavsett vad som ändrats i fälten. Utan
 * [read] (något nytt) finns inget att läsa. [prepare] fyller i förval för det som saknas.
 */
class EditorLoader<T>(
    private val editor: EditorState<T>,
    private val scope: CoroutineScope,
    private val read: (suspend () -> Result<T?>)?,
    private val prepare: (T) -> T = { it },
) {
    private val _stored = MutableStateFlow<T?>(null)
    val stored: StateFlow<T?> = _stored.asStateFlow()

    init {
        load()
    }

    fun retry() {
        // Något nytt har inget att läsa om – utan läsning skulle laddningen aldrig ta slut.
        if (read == null) return
        editor.reload()
        load()
    }

    private fun load() {
        val read = read ?: return
        scope.launch { editor.loadFrom(read) { stored -> _stored.value = stored; prepare(stored) } }
    }
}
