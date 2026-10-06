package se.partee71.dagboken.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import javax.inject.Provider
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.datetime.TimeZone
import se.partee71.dagboken.core.engine.isDarkAt
import se.partee71.dagboken.core.model.ThemeSettings
import se.partee71.dagboken.data.auth.AuthRepository
import se.partee71.dagboken.data.common.withFallback
import se.partee71.dagboken.data.repository.SettingsRepository
import se.partee71.dagboken.ui.common.STOP_TIMEOUT_MILLIS
import se.partee71.dagboken.ui.common.hours

/** Appens tema för `MainActivity` (SET-1, DSN-5). */
sealed interface AppTheme {
    /** Inloggning och inställningar läses fortfarande – startskärmen står kvar så att temat inte blinkar. */
    data object Loading : AppTheme

    /** Utloggad, inställningarna går inte att läsa eller tog för lång tid: systemets tema. */
    data object System : AppTheme

    /** Valt i inställningarna: ljust, mörkt eller auto just nu. */
    data class Chosen(val dark: Boolean) : AppTheme
}

/** Det tema som visas för [theme] när systemet är [systemDark]. */
fun AppTheme.isDark(systemDark: Boolean): Boolean = (this as? AppTheme.Chosen)?.dark ?: systemDark

/**
 * Appens tema ur inställningarna. `Loading` (och tidsgränsen) gäller bara den allra första laddningen;
 * kommer appen tillbaka från bakgrunden står det senaste temat kvar medan det läses om. Auto byter på starttimmarna och räknas om varje hel timme med
 * enhetens aktuella tidszon ([timeZone] läses vid varje omräkning), så att temat byter medan appen är
 * öppen. Dröjer inställningarna längre än [LOAD_TIMEOUT] (t.ex. offline utan cache) gäller systemet –
 * startskärmen fastnar aldrig.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class AppThemeViewModel @Inject constructor(
    auth: AuthRepository,
    settings: SettingsRepository,
    private val clock: Clock,
    private val timeZone: Provider<TimeZone>,
) : ViewModel() {
    private val chosen: Flow<AppTheme> = auth.authState
        .map { it?.uid }
        .distinctUntilChanged()
        .flatMapLatest { uid ->
            if (uid == null) {
                flowOf(AppTheme.System)
            } else {
                combine(settings.settings.map<_, ThemeSettings?> { it.theme }.withFallback(null), clock.hours { timeZone.get() }) { theme, hour ->
                    theme?.let { AppTheme.Chosen(it.isDarkAt(hour)) } ?: AppTheme.System
                }
            }
        }

    private val timedOut: Flow<Boolean> = flow {
        emit(false)
        delay(LOAD_TIMEOUT)
        emit(true)
    }

    /** Har temat varit känt en gång? Då står det kvar när skärmen kommer tillbaka – inget nytt `Loading`. */
    private var known = false

    val theme: StateFlow<AppTheme> = flow {
        if (known) {
            emitAll(chosen)
        } else {
            emitAll(combine(chosen.onStart { emit(AppTheme.Loading) }, timedOut) { theme, late -> if (theme == AppTheme.Loading && late) AppTheme.System else theme })
        }
    }
        .onEach { if (it != AppTheme.Loading) known = true }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), AppTheme.Loading)

    companion object {
        /** Längsta tid startskärmen väntar på temat. */
        val LOAD_TIMEOUT = 1.seconds
    }
}
