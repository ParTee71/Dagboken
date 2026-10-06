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
import se.partee71.dagboken.core.engine.asNeededChoices
import se.partee71.dagboken.core.engine.illnessDay
import se.partee71.dagboken.core.engine.occasionChoices
import se.partee71.dagboken.core.engine.ongoingEpisode
import se.partee71.dagboken.core.model.IllnessEpisode
import se.partee71.dagboken.core.model.PrnMedicine
import se.partee71.dagboken.core.model.Occasion
import se.partee71.dagboken.core.model.Option
import se.partee71.dagboken.core.model.Screening
import se.partee71.dagboken.core.model.Settings
import se.partee71.dagboken.core.model.SymptomScore
import se.partee71.dagboken.data.common.withFallback
import se.partee71.dagboken.data.repository.IllnessRepository
import se.partee71.dagboken.data.repository.OptionsRepository
import se.partee71.dagboken.data.repository.PrnMedicineRepository
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

/**
 * Plusknappens dosval och länken "Logga en dos i efterhand" i Mediciner (NAV-10, MEDF-6): vid behov-medicinerna i
 * [medicines] – stjärnmärkta först, sedan övriga, var för sig efter namn (som Idags Vid behov-kort, `asNeededChoices`)
 * – och en engångsdos, mot dagen [date] (`null` = idag).
 */
data class DosePicker(val date: LocalDate?, val medicines: List<PrnMedicine>)

/**
 * Plusknappens sjukdomsval (NAV-10, SJ-1, SJ-2): checka in på den pågående episoden [ongoing] – dag [day] i
 * sjukdomen den dag som loggas; bara när dagen ligger inom episoden – eller en ny episod, mot dagen [date] (`null` = idag).
 */
data class IllnessPicker(val date: LocalDate?, val ongoing: IllnessEpisode?, val day: Int?)

/** Om [day] ligger inom episoden: från starten (utan start: alltid) och till slutet, eller pågående. */
private fun IllnessEpisode.covers(day: LocalDate): Boolean = (start?.let { it <= day } ?: true) && (end?.let { it >= day } ?: true)

/** Formuläret ett val i plusknappens ark öppnar ([LogSheets]). */
sealed interface LogTarget {
    /** Vid behov-medicinen [prnId] i efterhand (MED-16). */
    data class AsNeeded(val prnId: String, val date: LocalDate?) : LogTarget

    /** En engångsdos (MED-11). */
    data class OneOffDose(val date: LocalDate?) : LogTarget

    /** En ny incheckning under [episodeId] (SJ-2). */
    data class Checkin(val episodeId: String, val date: LocalDate?) : LogTarget

    /** En ny sjukdomsepisod (SJ-1). */
    data class NewEpisode(val date: LocalDate?) : LogTarget
}

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

    /** "Dos" i plusknappens meny eller "Logga en dos i efterhand" i Mediciner: dosvalet mot [date] (`null` = idag). */
    data class PickDose(val date: LocalDate?) : LogEvent

    /** "Sjukdom" i plusknappens meny: sjukdomsvalet mot [date] (`null` = idag). */
    data class PickIllness(val date: LocalDate?) : LogEvent

    /** Dos- eller sjukdomsvalet stängs – med ett val eller utan. */
    data object ClosePick : LogEvent
}

/**
 * Det plusknappen och Dagbok öppnar ovanpå flikarna (NAV-10, HIST-3): tillfällesväljaren och måendearket – samma
 * ark som på Idag ([ScreeningSheet]) – och dos- och sjukdomsvalen ([dosePicker], [illnessPicker]). [selectedDay] är
 * dagen Idag visar; formulären för aktivitet, händelse, dos, episod och incheckning är egna skärmar. Sparat →
 * "Mående sparat" ([notice], SCR-3).
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class LogViewModel @Inject constructor(
    screenings: ScreeningRepository,
    private val settings: SettingsRepository,
    options: OptionsRepository,
    medicines: PrnMedicineRepository,
    illnesses: IllnessRepository,
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

    private sealed interface PickFor {
        val date: LocalDate?

        data class Dose(override val date: LocalDate?) : PickFor

        data class Illness(override val date: LocalDate?) : PickFor
    }

    /** Dos- eller sjukdomsvalet när det är öppet. */
    private val pickFor = MutableStateFlow<PickFor?>(null)

    /** Dosvalet när det är öppet – vid behov-medicinerna följs medan det är öppet; ett läsfel visar bara engångsdosen. */
    val dosePicker: StateFlow<DosePicker?> = pickFor.flatMapLatest { target ->
        if (target !is PickFor.Dose) {
            flowOf(null)
        } else {
            medicines.observe().withFallback(emptyList()).map { list ->
                val choices = asNeededChoices(list, emptyList(), today())
                DosePicker(target.date, choices.favorites + choices.others)
            }
        }
    }.stateIn(viewModelScope, sharing, null)

    /** Sjukdomsvalet när det är öppet: den pågående episoden (`ongoingEpisode`, som Idag) och dag N den dag som loggas. */
    val illnessPicker: StateFlow<IllnessPicker?> = pickFor.flatMapLatest { target ->
        if (target !is PickFor.Illness) {
            flowOf(null)
        } else {
            illnesses.observeEpisodes().withFallback(emptyList()).map { episodes ->
                val day = target.date ?: today()
                // Checka in bara när dagen ligger inom episoden – annars hör incheckningen inte dit.
                val ongoing = ongoingEpisode(episodes)?.takeIf { it.covers(day) }
                IllnessPicker(target.date, ongoing, illnessDay(ongoing?.start, day))
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
            is LogEvent.PickDose -> pickFor.value = PickFor.Dose(event.date?.takeUnless { it == today() })
            is LogEvent.PickIllness -> pickFor.value = PickFor.Illness(event.date?.takeUnless { it == today() })
            LogEvent.ClosePick -> pickFor.value = null
        }
    }
}
