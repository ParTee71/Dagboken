package se.partee71.dagboken.ui.settings

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import se.partee71.dagboken.R
import se.partee71.dagboken.core.engine.hasActiveName
import se.partee71.dagboken.core.model.Option
import se.partee71.dagboken.core.model.OptionKind
import se.partee71.dagboken.data.common.DataError
import se.partee71.dagboken.data.repository.DuplicateOptionName
import se.partee71.dagboken.data.repository.OptionsRepository
import se.partee71.dagboken.ui.common.EditorEffect
import se.partee71.dagboken.ui.common.EditorLoader
import se.partee71.dagboken.ui.common.EditorState
import se.partee71.dagboken.ui.common.EditorUiState
import se.partee71.dagboken.ui.common.Validator
import se.partee71.dagboken.ui.components.AppTextField
import se.partee71.dagboken.ui.components.EntityEditScreen

/** Fältet i formuläret – namnet är det enda. */
const val OPTION_NAME = "name"

/** Fältlöst fel medan listan läses: "Spara" är inaktiv tills dubbletter går att se (visas aldrig som fältfel). */
const val OPTIONS_LOADING = "optionsLoading"

/**
 * Namnet måste finnas och får inte redan stå i listan (SET-5, SET-6, SET-9); [others] är listans
 * alternativ, `null` medan de läses.
 */
fun optionNameValidator(others: () -> List<Option>?, exceptId: String?) = Validator<String> { name ->
    val options = others()
    when {
        name.isBlank() -> mapOf(OPTION_NAME to R.string.option_name_missing)
        options == null -> mapOf(OPTIONS_LOADING to R.string.loading)
        options.hasActiveName(name, exceptId) -> mapOf(OPTION_NAME to R.string.option_name_duplicate)
        else -> emptyMap()
    }
}

/**
 * Datalagrets nej till en dubblett ([DuplicateOptionName] från nytt, namnbyte, återställ och Ångra) som
 * text; andra fel visas med `DataError.toMessage()`.
 */
@StringRes
fun optionErrorText(error: Throwable): Int? = if (error is DuplicateOptionName) R.string.option_name_duplicate else null

sealed interface OptionEditEvent {
    data class NameChanged(val name: String) : OptionEditEvent

    data object Save : OptionEditEvent

    data object Archive : OptionEditEvent

    data object Restore : OptionEditEvent

    data object Retry : OptionEditEvent
}

/**
 * Nytt alternativ eller namnbyte ([id] = `null` för nytt) i listan [kind] (SET-5, SET-6, SET-9, SET-11):
 * ett nytt får id `OptionIds.of(kind, name)` (DAT-13), ett namnbyte behåller id:t så att redan loggade
 * poster visar det nya namnet. Ett befintligt kan arkiveras och återställas i menyn (DAT-9).
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel(assistedFactory = OptionEditViewModel.Factory::class)
class OptionEditViewModel @AssistedInject constructor(
    private val options: OptionsRepository,
    @Assisted private val kind: OptionKind,
    @Assisted private val id: String?,
) : ViewModel() {
    /** Listans alternativ som valideringen läser – före formuläret, som validerar redan när det skapas. */
    private val latest = MutableStateFlow<List<Option>?>(null)

    val editor: EditorState<String> = EditorState("", optionNameValidator({ latest.value }, id), loading = id != null, errorMessage = ::optionErrorText)

    private val listRetries = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    /** Felet från listan, tills den gått att läsa – visas som formulärets läsfel och rensas inte av en lyckad läsning av alternativet. */
    private val listError = MutableStateFlow<DataError?>(null)

    /**
     * Listans alternativ ur cachen – för att hitta dubbletter; `null` tills listan lästs. Går listan
     * inte att läsa visas felet som formulärets läsfel med "Försök igen" (i stället för ett låst Spara).
     */
    private val others: StateFlow<List<Option>?> = listRetries.onStart { emit(Unit) }
        .flatMapLatest {
            options.observe(kind)
                .map<List<Option>, List<Option>?> { it }
                .catch { error ->
                    listError.value = error as? DataError ?: DataError.Unknown
                    emit(null)
                }
        }
        .onEach { latest.value = it }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    // showInvalid: ett lagrat namn som visar sig vara en dubblett när listan kommer visar sitt fel direkt.
    private val loader = EditorLoader(editor, viewModelScope, read = id?.let { id -> { options.get(id) } }, project = Option::name, showInvalid = true)

    /** Alternativet som det är lagrat (namnbyte, arkivera, återställ); `null` för något nytt eller innan det lästs. */
    val stored: StateFlow<Option?> = loader.stored

    val isNew: Boolean get() = id == null

    init {
        // Listan kommer efter formuläret: validera om när den kommer eller ändras (en dubblett kan
        // ha lagts till på en annan enhet). Kommer den efter ett listfel är felet åtgärdat; i ett nytt
        // formulär står det användaren skrivit kvar.
        others.onEach { list ->
            if (list != null && listError.value != null) {
                listError.value = null
                if (id == null) editor.clearLoadError()
            }
            editor.revalidate()
        }.launchIn(viewModelScope)
        // Ett listfel står kvar som läsfel även om alternativet självt läses in under tiden.
        combine(listError, editor.state) { error, state -> error?.takeIf { state.loadError == null && !state.loading } }
            .onEach { error -> error?.let(editor::loadFailed) }
            .launchIn(viewModelScope)
    }

    fun onEvent(event: OptionEditEvent) {
        when (event) {
            is OptionEditEvent.NameChanged -> editor.update(OPTION_NAME) { event.name }
            OptionEditEvent.Save -> viewModelScope.launch {
                editor.save { name -> save(name) }
            }
            OptionEditEvent.Archive -> setArchived(true)
            OptionEditEvent.Restore -> setArchived(false)
            OptionEditEvent.Retry -> {
                // Listan först: ett gammalt listfel får inte läggas tillbaka över det som läses om.
                listError.value = null
                listRetries.tryEmit(Unit)
                if (id != null) loader.retry() else if (listError.value == null) editor.clearLoadError()
            }
        }
    }

    /** Nytt läggs till; ett befintligt byter namn – aldrig ett nytt i stället för ett som inte gick att läsa. */
    private suspend fun save(name: String): Result<Unit> {
        if (id == null) return options.add(kind, name)
        val option = stored.value ?: return Result.failure(DataError.NotFound)
        return options.rename(option, name)
    }

    private fun setArchived(archived: Boolean) {
        val id = id ?: return
        viewModelScope.launch { editor.run { options.setArchived(id, archived) } }
    }

    @AssistedFactory
    interface Factory {
        fun create(kind: OptionKind, id: String?): OptionEditViewModel
    }
}

@Composable
fun OptionEditRoute(kind: OptionKind, id: String?, onClose: () -> Unit) {
    val viewModel = hiltViewModel<OptionEditViewModel, OptionEditViewModel.Factory> { it.create(kind, id) }
    val state by viewModel.editor.state.collectAsStateWithLifecycle()
    val stored by viewModel.stored.collectAsStateWithLifecycle()
    OptionEditScreen(kind, isNew = viewModel.isNew, archived = stored?.archived, state, viewModel.editor.effects, viewModel::onEvent, onClose)
}

/**
 * Nytt alternativ eller namnbyte på `EntityEditScreen` (NFR-10): namnet, med "Finns redan i listan"
 * för en dubblett; ett befintligt kan arkiveras eller återställas i menyn ([archived], `null` = nytt
 * eller inte laddat).
 */
@Composable
fun OptionEditScreen(
    kind: OptionKind,
    isNew: Boolean,
    archived: Boolean?,
    state: EditorUiState<String>,
    effects: Flow<EditorEffect>,
    onEvent: (OptionEditEvent) -> Unit,
    onClose: () -> Unit,
) {
    EntityEditScreen(
        title = stringResource(if (isNew) kind.newLabel() else R.string.option_rename),
        state = state,
        effects = effects,
        onSave = { onEvent(OptionEditEvent.Save) },
        onClose = onClose,
        onArchive = if (archived == false) ({ onEvent(OptionEditEvent.Archive) }) else null,
        onRestore = if (archived == true) ({ onEvent(OptionEditEvent.Restore) }) else null,
        onRetry = { onEvent(OptionEditEvent.Retry) },
    ) {
        AppTextField(
            state.value,
            { onEvent(OptionEditEvent.NameChanged(it)) },
            stringResource(R.string.option_name),
            error = state.errorFor(OPTION_NAME)?.let { stringResource(it) },
            helper = if (isNew) null else stringResource(R.string.option_rename_help),
        )
    }
}
