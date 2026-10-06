package se.partee71.dagboken.ui.common

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import se.partee71.dagboken.R
import se.partee71.dagboken.core.schema.DocCodec
import se.partee71.dagboken.core.schema.DocumentRules
import se.partee71.dagboken.data.common.DataError
import se.partee71.dagboken.data.common.suspendRunCatching
import se.partee71.dagboken.data.repository.EntryStore

/** Det användaren gör i formuläret för en post – samma för alla poster (aktivitet, händelse, dos, episod, incheckning). */
sealed interface EntryEditEvent<out T> {
    /** En ändring av [field] (`null` = ett fält utan egen validering). */
    data class Changed<T>(val field: String?, val change: (T) -> T) : EntryEditEvent<T>

    data object Save : EntryEditEvent<Nothing>

    /** Bekräftad radering (efter `ConfirmDialog`, HIST-5). */
    data object Delete : EntryEditEvent<Nothing>

    data object Retry : EntryEditEvent<Nothing>
}

/**
 * Formuläret för en post i dagboken på `EntityEditScreen` – en gång för alla poster (AKT-9, HAN-1, MED-15, SJ-1,
 * SJ-11, NFR-10–12): [editor] ([EditorState]), läsningen av den sparade posten [id] ([EditorLoader], med "Försök
 * igen"), eller för en ny posten som [create] ger (förval, id och skapandetid satta en gång; ett fel – t.ex. en
 * episod som inte finns – visas som läsfel med "Försök igen", som skapar på nytt). [clean] gör det som skrivs av
 * formulärets värde (trimmad text m.m.) med den lästa posten som jämförelse; samma värde valideras ([validator]),
 * så att "Spara" följer det som faktiskt sparas. Sparat via [EntryStore.save] – ny som den är, ändrad fältvis mot
 * det lästa ([stored]). [confirmSave] får stoppa ett sparförsök för en fråga först (vid behov i efterhand: "För
 * tidigt", FAV-4) – frågan sparar sedan med ett nytt [EntryEditEvent.Save]. [errorMessage] ger en egen text för ett
 * sparfel som inte är ett `DataError` ([EditorState]); [saveUnchanged] låter en ny post sparas med förvalen (ett
 * riktigt svar, t.ex. vid behov i efterhand kl. nu). Radering stänger formuläret. [scope] är ViewModelns; skärmen
 * får allt som en [EntryForm].
 */
class EntryEditor<T>(
    private val scope: CoroutineScope,
    private val repository: EntryStore<T>,
    private val id: String?,
    validator: Validator<T>,
    placeholder: T,
    private val create: suspend () -> T,
    private val clean: (loaded: T?, value: T) -> T = { _, value -> value },
    private val confirmSave: (T) -> Boolean = { true },
    errorMessage: (Throwable) -> Int? = { null },
    saveUnchanged: Boolean = false,
) {
    /** Det lästa, för valideringen – satt när läsningen finns (formuläret valideras redan när det skapas). */
    private var loaded: () -> T? = { null }

    /** Laddar tills den sparade posten lästs eller den nya skapats. */
    val editor: EditorState<T> = EditorState(placeholder, { value -> validator.validate(clean(loaded(), value)) }, loading = true, errorMessage = errorMessage, saveUnchanged = saveUnchanged)

    // Ett lagrat värde som rules nekar (t.ex. en text från verktygen över taket) visar sitt fel direkt.
    private val loader = EditorLoader(editor, scope, read = id?.let { id -> { repository.get(id) } }, showInvalid = true)

    /** Posten som den är lagrad; `null` för en ny eller innan den lästs. */
    val stored: StateFlow<T?> = loader.stored

    val isNew: Boolean get() = id == null

    /**
     * Skapandet av en ny post; "Försök igen" efter ett fel skapar på nytt. Står före `init`, som startar det – annars
     * skulle initieringen här efteråt glömma jobbet.
     */
    private var creating: Job? = null

    init {
        loaded = { stored.value }
        // Förvalen är utgångsläget: "Spara" blir aktiv först när något ändrats (NFR-10, som 3.x).
        if (id == null) createNew()
    }

    private fun createNew() {
        creating?.cancel()
        creating = scope.launch {
            suspendRunCatching({ it as? DataError ?: DataError.Unknown }) { create() }
                .onSuccess { editor.load(it) }
                .onFailure { editor.loadFailed(it as DataError) }
        }
    }

    fun onEvent(event: EntryEditEvent<T>) {
        when (event) {
            is EntryEditEvent.Changed -> if (event.field == null) editor.update(transform = event.change) else editor.update(event.field, transform = event.change)
            EntryEditEvent.Save -> save()
            EntryEditEvent.Delete -> delete()
            EntryEditEvent.Retry -> if (id == null) retryNew() else loader.retry()
        }
    }

    private fun retryNew() {
        if (editor.state.value.loadError == null) return
        editor.reload()
        createNew()
    }

    private fun save() {
        if (!confirmSave(editor.value)) return
        scope.launch {
            editor.save { value ->
                // Aldrig en ny post i stället för en som inte gick att läsa.
                val loaded = if (id == null) null else stored.value ?: return@save Result.failure(DataError.NotFound)
                repository.save(loaded, clean(loaded, value))
            }
        }
    }

    private fun delete() {
        val id = id ?: return
        scope.launch { editor.run { repository.delete(id) } }
    }
}

/** Det formulärskärmen för en post visar och gör: läget, den lagrade posten (det som raderas), ramens händelser och [onEvent]. */
class EntryForm<T>(
    val isNew: Boolean,
    val state: EditorUiState<T>,
    val effects: Flow<EditorEffect>,
    val onEvent: (EntryEditEvent<T>) -> Unit,
    val stored: T? = null,
) {
    val value: T get() = state.value

    /** Ändrar formuläret ([EntryEditEvent.Changed]). */
    fun change(field: String? = null, transform: (T) -> T) = onEvent(EntryEditEvent.Changed(field, transform))
}

/** [EntryForm] ur [editor] – en gång för alla postformulärs `Route`. */
@Composable
fun <T> rememberEntryForm(editor: EntryEditor<T>): EntryForm<T> {
    val state by editor.editor.state.collectAsStateWithLifecycle()
    val stored by editor.stored.collectAsStateWithLifecycle()
    return EntryForm(editor.isNew, state, editor.editor.effects, editor::onEvent, stored)
}

/**
 * Validering som speglar `firestore.rules` för [collection] (TextLimits och intervallen i [DocumentRules]): varje
 * fält i det kodade värdet som rules skulle neka får [message] – t.ex. en text från verktygen som är längre än taket.
 * [form] lägger till formulärets egna krav (t.ex. att en typ är vald); de går före.
 */
fun <T> rulesValidator(collection: String, codec: DocCodec<T>, @StringRes message: Int = R.string.field_not_savable, form: (T) -> Map<String, Int> = { emptyMap() }): Validator<T> =
    Validator { value ->
        val rules = DocumentRules.validate(collection, codec.encode(value)).associate { it.field.substringBefore('[').substringBefore('.') to message }
        rules + form(value)
    }

/** Om ett fel gäller ett fält utanför [shown] – fält som formuläret inte visar ett fel vid, så att ramen visar ett gemensamt (`EntityEditScreen(formError)`). */
fun EditorUiState<*>.hasErrorOutside(shown: Set<String>): Boolean = errors.keys.any { it !in shown }
