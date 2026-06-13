package se.partee71.dagboken

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import se.partee71.dagboken.data.datastore.PreferencesRepository
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    prefs: PreferencesRepository,
) : ViewModel() {

    val isDarkTheme = prefs.isDarkTheme
        .stateIn(viewModelScope, SharingStarted.Eagerly, true)

    val dynamicColor = prefs.dynamicColor
        .stateIn(viewModelScope, SharingStarted.Eagerly, true)

    // null = DataStore not yet read; false = not done; true = done
    val migrationDone = prefs.migrationDone
        .map { it as Boolean? }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)
}
