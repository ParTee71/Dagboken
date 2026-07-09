package se.partee71.dagboken

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import se.partee71.dagboken.data.datastore.PreferencesRepository
import java.time.LocalTime
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    prefs: PreferencesRepository,
) : ViewModel() {

    private val minuteTicker = flow {
        while (true) {
            emit(Unit)
            delay(60_000L)
        }
    }

    val isDarkTheme = combine(
        prefs.themeMode,
        prefs.themeLightStart,
        prefs.themeDarkStart,
        minuteTicker,
    ) { mode, lightStart, darkStart, _ ->
        when (mode) {
            "light" -> false
            "dark"  -> true
            else    -> {
                val h = LocalTime.now().hour
                h < lightStart || h >= darkStart
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000L), true)

    val dynamicColor = prefs.dynamicColor
        .stateIn(viewModelScope, SharingStarted.Eagerly, true)

    // null = DataStore not yet read; false = not done; true = done
    val migrationDone = prefs.migrationDone
        .map { it as Boolean? }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val _pendingNavRoute = MutableStateFlow<String?>(null)
    val pendingNavRoute: StateFlow<String?> = _pendingNavRoute.asStateFlow()

    fun setPendingNavRoute(route: String) { _pendingNavRoute.value = route }
    fun clearPendingNavRoute() { _pendingNavRoute.value = null }

    // Set from the screening "Logga nu"-notisåtgärd: Idag pre-expands this event's
    // inline-screeningformulär so the user lands straight on the form to fill in.
    private val _pendingScreeningLabel = MutableStateFlow<String?>(null)
    val pendingScreeningLabel: StateFlow<String?> = _pendingScreeningLabel.asStateFlow()

    fun setPendingScreeningLabel(label: String) { _pendingScreeningLabel.value = label }
    fun clearPendingScreeningLabel() { _pendingScreeningLabel.value = null }
}
