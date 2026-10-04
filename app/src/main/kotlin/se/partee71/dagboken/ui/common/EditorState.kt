package se.partee71.dagboken.ui.common

import androidx.annotation.StringRes
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import se.partee71.dagboken.data.common.DataError
import se.partee71.dagboken.data.common.dataError

/** Validering av ett formulärvärde: fältnamn → felmeddelande. Tom karta = giltigt. */
fun interface Validator<T> {
    fun validate(value: T): Map<String, Int>
}

/** Det som `EntityEditScreen` visar. [errors] innehåller bara de fel som ska synas. */
data class EditorUiState<T>(
    val value: T,
    val errors: Map<String, Int> = emptyMap(),
    val isValid: Boolean = true,
    val isDirty: Boolean = false,
    val loading: Boolean = false,
    val saving: Boolean = false,
    /** Det lagrade värdet gick inte att läsa; ramen visar felet och "Försök igen". */
    val loadError: DataError? = null,
) {
    /** "Spara" är aktiv först när formuläret är giltigt och ändrat (NFR-2). */
    val canSave: Boolean get() = isValid && isDirty && !saving && !loading && loadError == null

    /** Felet för [field], om det ska visas. */
    @StringRes
    fun errorFor(field: String): Int? = errors[field]
}

/** Engångshändelser till ramen: klart (tillbaka) eller fel (snackbar). */
sealed interface EditorEffect {
    data object Done : EditorEffect

    data class Failed(val error: DataError) : EditorEffect
}

/**
 * Den enda formulärlogiken (skill shared-ui-components): värde, ändrat-läge, validering och
 * sparning. Varje `*EditViewModel` exponerar en [EditorState]; `EntityEditScreen` visar den.
 *
 * - Ett fältfel visas när fältet har ändrats eller efter ett sparförsök – aldrig innan
 *   användaren hunnit skriva.
 * - [loading] = true tills [load] anropats (redigering av något som finns).
 */
class EditorState<T>(initial: T, private val validator: Validator<T>, loading: Boolean = false) {
    private var original: T = initial
    private val touched = mutableSetOf<String>()
    private var attempted = false
    private val _state = MutableStateFlow(compute(initial, loading = loading))
    private val _effects = Channel<EditorEffect>(Channel.BUFFERED)

    val state: StateFlow<EditorUiState<T>> = _state.asStateFlow()
    val effects: Flow<EditorEffect> = _effects.receiveAsFlow()

    val value: T get() = _state.value.value

    /** Sätter det lagrade värdet – formuläret blir oändrat och fel nollställs. */
    fun load(value: T) {
        original = value
        touched.clear()
        attempted = false
        _state.value = compute(value, loading = false)
    }

    /**
     * Läser det lagrade värdet med [read] – en gång för alla formulär. Finns det inte, eller går
     * det inte att läsa, visas läsfelet; [prepare] fyller i förval för det som saknas.
     */
    suspend fun loadFrom(read: suspend () -> Result<T?>, prepare: (T) -> T = { it }) {
        read()
            .onSuccess { stored -> if (stored == null) loadFailed(DataError.NotFound) else load(prepare(stored)) }
            .onFailure { loadFailed(it as? DataError ?: DataError.Unknown) }
    }

    /** Det lagrade värdet gick inte att läsa – formuläret visar felet i stället för en evig laddning. */
    fun loadFailed(error: DataError) {
        _state.update { it.copy(loading = false, loadError = error) }
    }

    /** Inför ett nytt läsförsök ("Försök igen"). */
    fun reload() {
        _state.update { it.copy(loading = true, loadError = null) }
    }

    /** Ändrar värdet; [fields] markeras som rörda så att deras fel börjar synas. */
    fun update(vararg fields: String, transform: (T) -> T) {
        touched += fields
        _state.update { compute(transform(it.value), loading = it.loading, saving = it.saving) }
    }

    /**
     * Validerar och sparar med [write]. Ogiltigt → alla fel visas och inget sparas.
     * Lyckat → [EditorEffect.Done]; fel → [EditorEffect.Failed] och formuläret står kvar.
     */
    suspend fun save(write: suspend (T) -> Result<Unit>) {
        // Ett dubbeltryck sparar inte två gånger, och medan det lagrade värdet laddas (eller inte
        // gick att läsa) finns inget att spara – platshållaren får aldrig skriva över det.
        val now = _state.value
        if (now.saving || now.loading || now.loadError != null) return
        attempted = true
        val current = compute(value, loading = false)
        _state.value = current
        if (!current.isValid) return
        // Ändrades formuläret medan det sparades är ändringen inte sparad: formuläret står kvar
        // som ändrat i stället för att stängas och tappa den (NFR-2).
        perform(written = current.value, closeIfChanged = false) { write(current.value) }
    }

    /**
     * Kör en åtgärd som stänger formuläret (arkivera, radera) med samma fel- och klarhantering.
     * Den stänger alltid: en ändring under tiden får inte kunna sparas tillbaka över det som
     * arkiverats eller raderats.
     */
    suspend fun run(action: suspend () -> Result<Unit>) = perform(written = value, closeIfChanged = true, action)

    private suspend fun perform(written: T, closeIfChanged: Boolean, action: suspend () -> Result<Unit>) {
        if (_state.value.saving) return
        _state.update { it.copy(saving = true) }
        val error = action().dataError()
        _state.update { it.copy(saving = false) }
        if (error == null) {
            original = written
            val after = compute(value, loading = false)
            _state.value = after
            if (closeIfChanged || !after.isDirty) _effects.send(EditorEffect.Done)
        } else {
            _effects.send(EditorEffect.Failed(error))
        }
    }

    private fun compute(value: T, loading: Boolean, saving: Boolean = false): EditorUiState<T> {
        val all = validator.validate(value)
        val visible = if (attempted) all else all.filterKeys { it in touched }
        return EditorUiState(value, visible, all.isEmpty(), value != original, loading, saving)
    }
}
