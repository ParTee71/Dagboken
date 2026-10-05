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

    /** [message] är texten som visas – som standard felets (`DataError.toMessage()`). */
    data class Failed(val error: DataError, @param:StringRes val message: Int = error.toMessage()) : EditorEffect
}

/**
 * Den enda formulärlogiken (skill shared-ui-components): värde, ändrat-läge, validering och
 * sparning. Varje `*EditViewModel` exponerar en [EditorState]; `EntityEditScreen` visar den.
 *
 * - Ett fältfel visas när fältet har ändrats eller efter ett sparförsök – aldrig innan
 *   användaren hunnit skriva.
 * - [loading] = true tills [load] anropats (redigering av något som finns).
 * - [errorMessage] ger en egen text för ett fel som inte är ett `DataError` (t.ex. en dubblett som
 *   datalagret nekar); annars visas `DataError.toMessage()`.
 */
class EditorState<T>(
    initial: T,
    private val validator: Validator<T>,
    loading: Boolean = false,
    private val errorMessage: (Throwable) -> Int? = { null },
) {
    private var original: T = initial
    private val touched = mutableSetOf<String>()
    private var attempted = false

    /** Om fält som är ogiltiga i det lagrade värdet ska visa sitt fel ([load]). */
    private var showStoredInvalid = false
    private val _state = MutableStateFlow(compute(initial, loading = loading))
    private val _effects = Channel<EditorEffect>(Channel.BUFFERED)

    val state: StateFlow<EditorUiState<T>> = _state.asStateFlow()
    val effects: Flow<EditorEffect> = _effects.receiveAsFlow()

    val value: T get() = _state.value.value

    /**
     * Sätter det lagrade värdet – formuläret blir oändrat och fel nollställs. Med [showInvalid] räknas
     * ett fält som är ogiltigt i det lagrade (t.ex. ett födelseår utanför spannet från 3.x, eller ett
     * namn som visar sig vara en dubblett när listan kommer, se [revalidate]) som rört, så att felet
     * syns direkt och kan rättas. Standard är som tidigare: inga fel förrän fältet ändrats.
     */
    fun load(value: T, showInvalid: Boolean = false) {
        original = value
        touched.clear()
        showStoredInvalid = showInvalid
        if (showInvalid) touched += validator.validate(value).keys
        attempted = false
        _state.value = compute(value, loading = false)
    }

    /**
     * Läser det lagrade värdet med [read] – en gång för alla formulär. Finns det inte, eller går
     * det inte att läsa, visas läsfelet; [prepare] fyller i förval för det som saknas. [showInvalid]
     * som i [load].
     */
    suspend fun loadFrom(read: suspend () -> Result<T?>, showInvalid: Boolean = false, prepare: (T) -> T = { it }) {
        read()
            .onSuccess { stored -> if (stored == null) loadFailed(DataError.NotFound) else load(prepare(stored), showInvalid) }
            .onFailure { loadFailed(it as? DataError ?: DataError.Unknown) }
    }

    /** Det lagrade värdet gick inte att läsa – formuläret visar felet i stället för en evig laddning. */
    fun loadFailed(error: DataError) {
        _state.update { it.copy(loading = false, loadError = error) }
    }

    /**
     * Läsfelet är åtgärdat utan att något lagrat läses om – t.ex. en lista som valideringen läser och
     * som gick att läsa vid "Försök igen". Det användaren skrivit står kvar.
     */
    fun clearLoadError() {
        _state.update { it.copy(loadError = null) }
    }

    /** Inför ett nytt läsförsök ("Försök igen"). */
    fun reload() {
        _state.update { it.copy(loading = true, loadError = null) }
    }

    /** Ändrar värdet; [fields] markeras som rörda så att deras fel börjar synas. */
    fun update(vararg fields: String, transform: (T) -> T) {
        touched += fields
        // Ett läsfel står kvar: formuläret har inget lagrat värde att spara över.
        _state.update { compute(transform(it.value), loading = it.loading, saving = it.saving).copy(loadError = it.loadError) }
    }

    /**
     * Validerar om utan att något ändrats – när det valideringen läser har kommit eller ändrats (t.ex.
     * listan som dubbletter söks i). Laddning och läsfel står kvar. Efter en [load] med `showInvalid`
     * visas ett fel som först nu uppstår i det oändrade lagrade värdet.
     */
    fun revalidate() {
        if (showStoredInvalid && !_state.value.isDirty) touched += validator.validate(value).keys
        update { it }
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
        val failure = action().failureOrNull(errorMessage)
        _state.update { it.copy(saving = false) }
        if (failure == null) {
            original = written
            val after = compute(value, loading = false)
            _state.value = after
            if (closeIfChanged || !after.isDirty) _effects.send(EditorEffect.Done)
        } else {
            _effects.send(EditorEffect.Failed(failure.error, failure.message))
        }
    }

    private fun compute(value: T, loading: Boolean, saving: Boolean = false): EditorUiState<T> {
        val all = validator.validate(value)
        val visible = if (attempted) all else all.filterKeys { it in touched }
        return EditorUiState(value, visible, all.isEmpty(), value != original, loading, saving)
    }
}
