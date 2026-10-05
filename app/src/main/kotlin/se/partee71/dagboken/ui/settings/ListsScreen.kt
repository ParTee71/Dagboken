package se.partee71.dagboken.ui.settings

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.launch
import se.partee71.dagboken.R
import se.partee71.dagboken.core.model.Option
import se.partee71.dagboken.core.model.OptionKind
import se.partee71.dagboken.data.common.dataError
import se.partee71.dagboken.data.repository.OptionsRepository
import se.partee71.dagboken.ui.common.ArchiveActions
import se.partee71.dagboken.ui.common.ListEvent
import se.partee71.dagboken.ui.common.ListLoader
import se.partee71.dagboken.ui.common.ListUiState
import se.partee71.dagboken.ui.common.collectAsListArchive
import se.partee71.dagboken.ui.common.label
import se.partee71.dagboken.ui.components.AppIconButton
import se.partee71.dagboken.ui.components.AppSegmentedChoice
import se.partee71.dagboken.ui.components.EmptyContent
import se.partee71.dagboken.ui.components.EntityListScreen
import se.partee71.dagboken.ui.components.InfoPill
import se.partee71.dagboken.ui.components.ItemRow
import se.partee71.dagboken.ui.components.ListArchive
import se.partee71.dagboken.ui.components.SwipeToHide
import se.partee71.dagboken.ui.components.TopBarSize
import se.partee71.dagboken.ui.theme.Tone

sealed interface ListsEvent {
    /** Vilken lista som visas: Aktiviteter · Symptom · Händelser. */
    data class KindChosen(val kind: OptionKind) : ListsEvent

    /** Stjärnan – radens enda direktkontroll (NFR-17, SET-5, SET-9). */
    data class FavoriteToggled(val option: Option) : ListsEvent

    data object Retry : ListsEvent
}

/**
 * Listor i inställningsarket (SET-5, SET-6, SET-9, DAT-9): alternativen i den valda listan, svep
 * arkiverar med Ångra (`ArchiveActions`), stjärnan markerar favorit. Nytt och namnbyte öppnar
 * `OptionEditScreen`.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ListsViewModel @Inject constructor(private val options: OptionsRepository) : ViewModel() {
    private val _kind = MutableStateFlow(OptionKind.ACTIVITY)
    val kind: StateFlow<OptionKind> = _kind.asStateFlow()

    val archive = ArchiveActions(viewModelScope, options::setArchived, errorMessage = ::optionErrorText)
    private val loader = ListLoader(archive.visible(_kind.flatMapLatest { options.observe(it) }), viewModelScope)
    val state: StateFlow<ListUiState<Option>> = loader.state

    fun onEvent(event: ListsEvent) {
        when (event) {
            is ListsEvent.KindChosen -> _kind.value = event.kind
            is ListsEvent.FavoriteToggled -> viewModelScope.launch {
                options.setFavorite(event.option, !event.option.favorite).dataError()?.let(archive::report)
            }
            ListsEvent.Retry -> loader.onEvent(ListEvent.Retry)
        }
    }
}

@Composable
fun ListsRoute(onBack: () -> Unit, onAdd: (OptionKind) -> Unit, onOpen: (Option) -> Unit, viewModel: ListsViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val kind by viewModel.kind.collectAsStateWithLifecycle()
    ListsScreen(state, kind, viewModel.archive.collectAsListArchive(), viewModel::onEvent, onBack, { onAdd(kind) }, onOpen)
}

/** Texterna och ikonen för en lista: tomt läge och lägg till-knappen. */
private enum class ListTexts(
    val kind: OptionKind,
    @param:DrawableRes val icon: Int,
    @param:StringRes val emptyTitle: Int,
    @param:StringRes val emptyMessage: Int,
    @param:StringRes val add: Int,
) {
    Activities(OptionKind.ACTIVITY, R.drawable.ic_activity, R.string.lists_empty_activity, R.string.lists_empty_activity_message, R.string.option_new_activity),
    Symptoms(OptionKind.SYMPTOM, R.drawable.ic_thermometer, R.string.lists_empty_symptom, R.string.lists_empty_symptom_message, R.string.option_new_symptom),
    Events(OptionKind.EVENT, R.drawable.ic_event, R.string.lists_empty_event, R.string.lists_empty_event_message, R.string.option_new_event),
    ;

    companion object {
        fun of(kind: OptionKind) = entries.first { it.kind == kind }
    }
}

/** Den nya alternativets rubrik i formuläret ("Ny aktivitet", "Nytt symptom", "Ny händelsetyp"). */
@StringRes
internal fun OptionKind.newLabel(): Int = ListTexts.of(this).add

/**
 * Listor på `EntityListScreen` (liten topprad som de andra underskärmarna) med valet av lista
 * (`AppSegmentedChoice`) fast under rubriken, svep som arkiverar (`SwipeToHide` + `UndoSnackbar`),
 * stjärnan som direktkontroll och pil till formuläret (NFR-17); en arkiverad rad är nedtonad med
 * "Arkiverad" som pill, som en pausad rad.
 */
@Composable
fun ListsScreen(
    state: ListUiState<Option>,
    kind: OptionKind,
    archive: ListArchive,
    onEvent: (ListsEvent) -> Unit,
    onBack: () -> Unit,
    onAdd: () -> Unit,
    onOpen: (Option) -> Unit,
) {
    val texts = ListTexts.of(kind)
    val kinds = OptionKind.entries
    val labels = kinds.map { stringResource(it.label()) }
    EntityListScreen(
        title = stringResource(R.string.settings_lists),
        state = state,
        empty = EmptyContent(texts.icon, stringResource(texts.emptyTitle), stringResource(texts.emptyMessage), stringResource(texts.add)),
        onAdd = onAdd,
        key = { it.id },
        onRetry = { onEvent(ListsEvent.Retry) },
        archive = archive,
        onBack = onBack,
        filter = { AppSegmentedChoice(labels, kinds.indexOf(kind), { onEvent(ListsEvent.KindChosen(kinds[it])) }) },
        topBarSize = TopBarSize.Small,
    ) { option ->
        SwipeToHide(onHide = { archive.archive(option.id, option.name) }, enabled = !option.archived) { swipe ->
            ItemRow(
                option.name,
                swipe,
                onClick = { onOpen(option) },
                navigates = true,
                inactive = option.archived,
                trailing = {
                    // Som "Pausat" i PausableRow: läget som pill, raden nedtonad.
                    if (option.archived) InfoPill(stringResource(R.string.option_archived), tone = Tone.Neutral)
                    FavoriteStar(option) { onEvent(ListsEvent.FavoriteToggled(option)) }
                },
            )
        }
    }
}

@Composable
private fun FavoriteStar(option: Option, onToggle: () -> Unit) {
    val description = stringResource(if (option.favorite) R.string.option_favorite_remove else R.string.option_favorite_add, option.name)
    AppIconButton(if (option.favorite) R.drawable.ic_star_filled else R.drawable.ic_star, description, onToggle)
}
