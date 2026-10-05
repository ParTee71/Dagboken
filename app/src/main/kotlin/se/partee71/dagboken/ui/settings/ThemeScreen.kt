package se.partee71.dagboken.ui.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalTime
import se.partee71.dagboken.R
import se.partee71.dagboken.core.engine.hasValidHours
import se.partee71.dagboken.core.model.ThemeMode
import se.partee71.dagboken.core.model.ThemeSettings
import se.partee71.dagboken.data.repository.SettingsRepository
import se.partee71.dagboken.ui.common.DetailLoader
import se.partee71.dagboken.ui.common.DetailUiState
import se.partee71.dagboken.ui.common.Failure
import se.partee71.dagboken.ui.common.failureOrNull
import se.partee71.dagboken.ui.components.AppSegmentedChoice
import se.partee71.dagboken.ui.components.EntityDetailScreen
import se.partee71.dagboken.ui.components.LabeledGroup
import se.partee71.dagboken.ui.components.TimeField

/**
 * Det temaskärmen visar: läget och starttimmarna – de lagrade, eller de användaren just valt när
 * ordningen är ogiltig ([hoursInvalid]; de sparas då inte, SET-2).
 */
data class ThemeForm(val theme: ThemeSettings, val hoursInvalid: Boolean = false)

sealed interface ThemeEvent {
    data class ModeChosen(val mode: ThemeMode) : ThemeEvent

    data class LightFromChanged(val hour: Int) : ThemeEvent

    data class DarkFromChanged(val hour: Int) : ThemeEvent

    data object ErrorShown : ThemeEvent

    data object Retry : ThemeEvent
}

/**
 * Tema i inställningsarket (SET-1, SET-2): varje giltigt val sparas direkt i gruppen `theme` och slår
 * igenom i hela appen ([AppThemeViewModel]). Starttimmar där ljust inte börjar före mörkt visas med
 * fältfel och sparas inte.
 */
@HiltViewModel
class ThemeViewModel @Inject constructor(private val settings: SettingsRepository) : ViewModel() {
    /** Timmar som valts men inte sparats – ogiltiga, eller giltiga och på väg till cachen. */
    private val chosen = MutableStateFlow<ThemeSettings?>(null)
    private val loader = DetailLoader(
        combine(
            // Valet släpps först när cachen visar det sparat – annars hoppar fältet tillbaka och nästa
            // ändring bygger på det gamla värdet.
            settings.settings.map { it.theme }.onEach { stored -> chosen.update { picked -> picked?.takeUnless { it.sameHours(stored) } } },
            chosen,
        ) { stored, picked ->
            val hours = picked ?: stored
            ThemeForm(stored.copy(lightStartHour = hours.lightStartHour, darkStartHour = hours.darkStartHour), hoursInvalid = !hours.hasValidHours())
        },
        viewModelScope,
    )
    val state: StateFlow<DetailUiState<ThemeForm>> = loader.state

    private val _failure = MutableStateFlow<Failure?>(null)

    /** Ett sparfel, tills skärmen visat det; samma fel två gånger i rad är två händelser. */
    val failure: StateFlow<Failure?> = _failure.asStateFlow()

    fun onEvent(event: ThemeEvent) {
        when (event) {
            is ThemeEvent.ModeChosen -> write { it.copy(mode = event.mode) }
            is ThemeEvent.LightFromChanged -> chooseHours { it.copy(lightStartHour = event.hour) }
            is ThemeEvent.DarkFromChanged -> chooseHours { it.copy(darkStartHour = event.hour) }
            ThemeEvent.ErrorShown -> _failure.value = null
            ThemeEvent.Retry -> loader.retry()
        }
    }

    private fun chooseHours(change: (ThemeSettings) -> ThemeSettings) {
        val shown = (state.value as? DetailUiState.Content)?.value?.theme ?: return
        val hours = change(shown)
        // Samma timmar som visas: inget att skriva, och ett kvarhängande val skulle dölja senare
        // ändringar från en annan enhet.
        if (hours == shown) return
        chosen.value = hours
        if (!hours.hasValidHours()) return
        // Misslyckas sparningen visas det lagrade igen – om valet inte ändrats under tiden.
        write(onFailure = { chosen.compareAndSet(hours, null) }) { it.copy(lightStartHour = hours.lightStartHour, darkStartHour = hours.darkStartHour) }
    }

    private fun write(onFailure: () -> Unit = {}, change: (ThemeSettings) -> ThemeSettings) {
        viewModelScope.launch {
            settings.update { it.copy(theme = change(it.theme)) }.failureOrNull()?.let {
                _failure.value = it
                onFailure()
            }
        }
    }
}

@Composable
fun ThemeRoute(onBack: () -> Unit, viewModel: ThemeViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val failure by viewModel.failure.collectAsStateWithLifecycle()
    ThemeScreen(state, failure, viewModel::onEvent, onBack)
}

/**
 * Tema som detaljskärm med titeln i toppraden (som formulären): läget Ljust · Mörkt · Auto som
 * `LabeledGroup` med hjälptexten under (som Profilens kön), med direktverkan; vid Auto starttimmarna
 * med `TimeField`.
 */
@Composable
fun ThemeScreen(state: DetailUiState<ThemeForm>, failure: Failure?, onEvent: (ThemeEvent) -> Unit, onBack: () -> Unit) {
    val title = stringResource(R.string.settings_theme)
    val note = stringResource(R.string.theme_note)
    val modes = listOf(stringResource(R.string.theme_light), stringResource(R.string.theme_dark), stringResource(R.string.theme_auto))
    EntityDetailScreen(
        state = state,
        header = null,
        onBack = onBack,
        title = title,
        onRetry = { onEvent(ThemeEvent.Retry) },
        failure = failure,
        onErrorShown = { onEvent(ThemeEvent.ErrorShown) },
    ) { form ->
        val theme = form.theme
        LabeledGroup(stringResource(R.string.theme_mode), helper = note) {
            AppSegmentedChoice(modes, ThemeMode.entries.indexOf(theme.mode), { onEvent(ThemeEvent.ModeChosen(ThemeMode.entries[it])) })
        }
        if (theme.mode == ThemeMode.AUTO) {
            TimeField(stringResource(R.string.theme_light_from), hourTime(theme.lightStartHour), { onEvent(ThemeEvent.LightFromChanged(it.hour)) })
            TimeField(
                stringResource(R.string.theme_dark_from),
                hourTime(theme.darkStartHour),
                { onEvent(ThemeEvent.DarkFromChanged(it.hour)) },
                error = if (form.hoursInvalid) stringResource(R.string.theme_hours_invalid) else null,
            )
        }
    }
}

private fun ThemeSettings.sameHours(other: ThemeSettings) =
    lightStartHour == other.lightStartHour && darkStartHour == other.darkStartHour

/** En starttimme som klockslag ("07:00"); ett lagrat värde utanför dygnet visas som närmaste timme. */
private fun hourTime(hour: Int) = LocalTime(hour.coerceIn(0, LAST_HOUR), 0)

private const val LAST_HOUR = 23
