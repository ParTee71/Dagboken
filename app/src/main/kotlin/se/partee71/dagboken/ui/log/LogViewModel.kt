package se.partee71.dagboken.ui.log

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import javax.inject.Provider
import kotlin.time.Clock
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import se.partee71.dagboken.core.engine.OccasionState
import se.partee71.dagboken.core.engine.occasionChoices
import se.partee71.dagboken.core.model.Occasion
import se.partee71.dagboken.core.model.Option
import se.partee71.dagboken.core.model.Screening
import se.partee71.dagboken.core.model.Settings
import se.partee71.dagboken.core.model.SymptomScore
import se.partee71.dagboken.data.common.withFallback
import se.partee71.dagboken.data.repository.OptionsRepository
import se.partee71.dagboken.data.repository.ScreeningRepository
import se.partee71.dagboken.data.repository.SettingsRepository
import se.partee71.dagboken.ui.common.EditorSheetState
import se.partee71.dagboken.ui.common.STOP_TIMEOUT_MILLIS
import se.partee71.dagboken.ui.common.SelectedDay
import se.partee71.dagboken.ui.common.minutes

/**
 * Plusknappens tillfällesväljare (HEM-8b, NAV-10): [date] är dagen som loggas, [isToday] om det är idag, och
 * [occasions] alla fyra tillfällena med status – de aktiverade som Idags Mående-kort (`occasionChoices`).
 */
data class OccasionPicker(val date: LocalDate, val isToday: Boolean, val occasions: List<OccasionState>)

sealed interface LogEvent {
    /** "Mående" i plusknappens meny: tillfällesväljaren för den dag som loggas (`SelectedDay.logDay`, `null` = idag). */
    data class PickOccasion(val date: LocalDate?) : LogEvent

    /** Ett tillfälle i väljaren: en ny logg för det, också när det redan är loggat (HEM-8b). */
    data class LogOccasion(val state: OccasionState) : LogEvent

    /**
     * "Logga nu" eller ett ologgat tillfälle på Idag (HEM-5, SCR-6): en ny logg för [occasion] på [date] (`null` = idag,
     * då klockslaget nu), en tidigare dag på påminnelsens tid [reminder].
     */
    data class LogScreening(val occasion: Occasion, val date: LocalDate?, val reminder: LocalTime?) : LogEvent

    data object ClosePicker : LogEvent

    /** En loggad måendepost på Idag eller i Dagbok: arket med loggen för ändring (HEM-5, HIST-3, SCR-1). */
    data class EditScreening(val screening: Screening) : LogEvent

    data class ChangeEnergy(val energy: Int) : LogEvent

    data class ChangeStress(val stress: Int) : LogEvent

    data class ChangeSymptoms(val symptoms: List<SymptomScore>) : LogEvent

    data object SaveScreening : LogEvent

    data object CloseScreening : LogEvent

    data object NoticeShown : LogEvent
}

/**
 * Det plusknappen och Dagbok öppnar ovanpå flikarna (NAV-10, HIST-3): tillfällesväljaren och måendearket – samma
 * ark som på Idag ([ScreeningSheet]). [selectedDay] är dagen Idag visar; formulären för aktivitet och händelse är
 * egna skärmar. Sparat → "Mående sparat" ([notice], SCR-3).
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class LogViewModel @Inject constructor(
    screenings: ScreeningRepository,
    private val settings: SettingsRepository,
    options: OptionsRepository,
    private val selectedDay: SelectedDay,
    private val clock: Clock,
    private val zone: Provider<TimeZone>,
) : ViewModel() {
    private val sharing = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS, replayExpirationMillis = 0)

    private val _notice = MutableStateFlow(false)

    /** "Mående sparat" ska visas (SCR-3). */
    val notice: StateFlow<Boolean> = _notice.asStateFlow()

    private val sheet = ScreeningSheet(viewModelScope, screenings, options, clock, zone, sharing) { _notice.value = true }

    /** Måendearket när det är öppet. */
    val screening: StateFlow<EditorSheetState<Screening, ScreeningSheetInfo>?> = sheet.current

    val symptomOptions: StateFlow<List<Option>> = sheet.symptomOptions

    /** Väljaren när den är öppen: för en tidigare dag, eller för idag ([PickerFor.date] `null`, som följer midnatt). */
    private val pickerFor = MutableStateFlow<PickerFor?>(null)

    private data class PickerFor(val date: LocalDate?)

    /** Klockan i hela minuter – delad, så att dagen och status räknas på samma läsning. */
    private val minutes = clock.minutes { zone.get() }.shareIn(viewModelScope, sharing, replay = 1)

    /**
     * Tillfällesväljaren när den är öppen: dagens loggar och påminnelser följs, och status räknas om varje minut
     * ("Snart"/"Försenat", som Idag). Öppnad för idag följer den med över midnatt. Ett läsfel visar tillfällena utan
     * loggar hellre än att fälla väljaren.
     */
    val picker: StateFlow<OccasionPicker?> = pickerFor.flatMapLatest { target ->
        if (target == null) {
            flowOf(null)
        } else {
            val day = minutes.map { target.date ?: it.date }.distinctUntilChanged()
            val logs = day.flatMapLatest { date -> screenings.observeDay(date).withFallback(emptyList()).map { date to it } }
            combine(logs, settings.settings.withFallback(Settings()), minutes) { (date, logs), stored, now ->
                val z = zone.get()
                OccasionPicker(date, date == now.date, occasionChoices(stored.reminders, logs, date, now.toInstant(z), z))
            }
        }
    }.stateIn(viewModelScope, sharing, null)

    /** Dagen plusknappen loggar mot (NAV-10, HEM-14): den Idag visar när Idag är vald, annars idag (`null`). */
    fun logDay(onToday: Boolean): LocalDate? = selectedDay.logDay(onToday)

    private fun today(): LocalDate = clock.now().toLocalDateTime(zone.get()).date

    fun onEvent(event: LogEvent) {
        when (event) {
            is LogEvent.PickOccasion -> pickerFor.value = PickerFor(event.date?.takeUnless { it == today() })
            is LogEvent.LogOccasion -> {
                // Dagen när raden trycks: öppnad för idag är det idag enligt klockan nu, också efter midnatt (som Idag).
                val target = pickerFor.value ?: return
                pickerFor.value = null
                sheet.log(event.state.occasion, target.date, event.state.time)
            }
            is LogEvent.LogScreening -> sheet.log(event.occasion, event.date, event.reminder)
            LogEvent.ClosePicker -> pickerFor.value = null
            is LogEvent.EditScreening -> sheet.edit(event.screening)
            is LogEvent.ChangeEnergy -> sheet.changeEnergy(event.energy)
            is LogEvent.ChangeStress -> sheet.changeStress(event.stress)
            is LogEvent.ChangeSymptoms -> sheet.changeSymptoms(event.symptoms)
            LogEvent.SaveScreening -> sheet.save()
            LogEvent.CloseScreening -> sheet.close()
            LogEvent.NoticeShown -> _notice.value = false
        }
    }
}
