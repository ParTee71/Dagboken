package se.partee71.dagboken.ui.common

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Läser det lagrade värdet in i ett formulär – en gång för alla formulär (skill
 * shared-ui-components): läser direkt, [retry] ("Försök igen") läser om, och [stored] är det lagrade
 * värdet – det som raderas, arkiveras, återställs eller som en sparning jämförs mot, oavsett vad som
 * ändrats i fälten. Utan [read] (något nytt) finns inget att läsa. [project] gör formulärets värde av
 * det lagrade (t.ex. bara namnet ur ett alternativ, eller en grupp ur inställningarna); [prepare] fyller
 * i förval för det som saknas; [showInvalid] visar fel i det lagrade direkt (`EditorState.load`).
 * [afterLoad] körs efter varje lyckad läsning – också efter "Försök igen" – för ett formulär som öppnas
 * med en ändring av det lagrade (t.ex. "Förläng och aktivera"), så att ändringen kan sparas.
 */
class EditorLoader<T, S>(
    private val editor: EditorState<T>,
    private val scope: CoroutineScope,
    private val read: (suspend () -> Result<S?>)?,
    private val project: (S) -> T,
    private val prepare: (T) -> T = { it },
    private val showInvalid: Boolean = false,
    private val afterLoad: (() -> Unit)? = null,
) {
    private val _stored = MutableStateFlow<S?>(null)
    val stored: StateFlow<S?> = _stored.asStateFlow()

    init {
        load()
    }

    fun retry() {
        // Något nytt har inget att läsa om – utan läsning skulle laddningen aldrig ta slut.
        if (read == null) return
        editor.reload()
        load()
    }

    /** Den pågående läsningen; "Försök igen" avbryter den, så att en sen läsning aldrig laddas över en nyare. */
    private var loading: Job? = null

    private fun load() {
        val read = read ?: return
        loading?.cancel()
        loading = scope.launch {
            editor.loadFrom({ read().map { stored -> stored?.also { _stored.value = it }?.let(project) } }, showInvalid, prepare)
            if (editor.state.value.loadError == null) afterLoad?.invoke()
        }
    }
}

/** [EditorLoader] där formulärets värde är det lagrade självt – det vanliga fallet. */
fun <T> EditorLoader(
    editor: EditorState<T>,
    scope: CoroutineScope,
    read: (suspend () -> Result<T?>)?,
    prepare: (T) -> T = { it },
    showInvalid: Boolean = false,
    afterLoad: (() -> Unit)? = null,
): EditorLoader<T, T> = EditorLoader(editor, scope, read, project = { it }, prepare = prepare, showInvalid = showInvalid, afterLoad = afterLoad)
