package se.partee71.dagboken.ui.today

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import javax.inject.Provider
import kotlin.time.Clock
import kotlin.time.Duration
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import se.partee71.dagboken.R
import se.partee71.dagboken.core.engine.AsNeededChoices
import se.partee71.dagboken.core.engine.DayComparison
import se.partee71.dagboken.core.engine.DayPart
import se.partee71.dagboken.core.engine.DayProgress
import se.partee71.dagboken.core.engine.DoseChecklist
import se.partee71.dagboken.core.engine.OccasionState
import se.partee71.dagboken.core.engine.OngoingIllness
import se.partee71.dagboken.core.engine.PrnCheck
import se.partee71.dagboken.core.engine.WeekSummary
import se.partee71.dagboken.core.engine.asNeededChoices
import se.partee71.dagboken.core.engine.checkOffTime
import se.partee71.dagboken.core.engine.isScheduled
import se.partee71.dagboken.core.engine.datesWithEntries
import se.partee71.dagboken.core.engine.dayComparison
import se.partee71.dagboken.core.engine.dayPartAt
import se.partee71.dagboken.core.engine.dayProgress
import se.partee71.dagboken.core.engine.dailyEnergyAverages
import se.partee71.dagboken.core.engine.dayStreak
import se.partee71.dagboken.core.engine.daysEnding
import se.partee71.dagboken.core.engine.doseChecklist
import se.partee71.dagboken.core.engine.doseFor
import se.partee71.dagboken.core.engine.enabledOccasions
import se.partee71.dagboken.core.engine.occasionStates
import se.partee71.dagboken.core.engine.ongoingEpisode
import se.partee71.dagboken.core.engine.ongoingIllness
import se.partee71.dagboken.core.engine.weekSummaryOn
import se.partee71.dagboken.core.model.Checkin
import se.partee71.dagboken.core.model.Dose
import se.partee71.dagboken.core.model.DoseStatus
import se.partee71.dagboken.core.model.IllnessEpisode
import se.partee71.dagboken.core.model.Occasion
import se.partee71.dagboken.core.model.Option
import se.partee71.dagboken.core.model.OptionKind
import se.partee71.dagboken.core.model.Prescription
import se.partee71.dagboken.core.model.PrnMedicine
import se.partee71.dagboken.core.model.ReminderSettings
import se.partee71.dagboken.core.model.Screening
import se.partee71.dagboken.core.model.Settings
import se.partee71.dagboken.core.model.SymptomScore
import se.partee71.dagboken.data.common.DataError
import se.partee71.dagboken.data.common.combineByKey
import se.partee71.dagboken.data.common.suspendRunCatching
import se.partee71.dagboken.data.common.withFallback
import se.partee71.dagboken.data.repository.DoseRepository
import se.partee71.dagboken.data.repository.IllnessRepository
import se.partee71.dagboken.data.repository.OptionsRepository
import se.partee71.dagboken.data.repository.PrescriptionRepository
import se.partee71.dagboken.data.repository.PrnMedicineRepository
import se.partee71.dagboken.data.repository.ScreeningRepository
import se.partee71.dagboken.data.repository.SettingsRepository
import se.partee71.dagboken.ui.common.DetailLoader
import se.partee71.dagboken.ui.common.DetailUiState
import se.partee71.dagboken.ui.common.EditorSheet
import se.partee71.dagboken.ui.common.EditorSheetState
import se.partee71.dagboken.ui.common.Failure
import se.partee71.dagboken.ui.common.STOP_TIMEOUT_MILLIS
import se.partee71.dagboken.ui.common.failureOrNull
import se.partee71.dagboken.ui.common.medicineTitle
import se.partee71.dagboken.ui.common.minutes
import se.partee71.dagboken.ui.common.tidyingUpEachDay
import se.partee71.dagboken.ui.components.UndoRequest
import se.partee71.dagboken.ui.components.weekMonday

sealed interface TodayEvent {
    /** En dag i datumremsan (HEM-14); framtida dagar går inte att välja. */
    data class SelectDate(val date: LocalDate) : TodayEvent

    /** Svep eller TalkBack-åtgärd i datumremsan: veckan som börjar [monday] (HEM-14). */
    data class ShowWeek(val monday: LocalDate) : TodayEvent

    /** Hela doseraden: tagen ↔ planerad (MED-2, MED-14). */
    data class SetTaken(val dose: Dose, val taken: Boolean) : TodayEvent

    /** Bekräftat "Hoppa över" (MED-3). */
    data class Skip(val dose: Dose) : TodayEvent

    data object Undo : TodayEvent

    /** Ångra för [id] gick ut – ett nyare ångra står kvar. */
    data class UndoDismissed(val id: String) : TodayEvent

    /** "Visa tagna"/"Dölj tagna" (MED-5). */
    data object ToggleDone : TodayEvent

    /** "Visa kommande"/"Dölj kommande" (MED-13). */
    data object ToggleUpcoming : TodayEvent

    /** Ett vid behov-snabbval eller en vid behov-medicin i Fler (FAV-2, FAV-4, FAV-5). */
    data class LogAsNeeded(val medicine: PrnMedicine) : TodayEvent

    /** "Ta ändå" i "För tidigt" (FAV-4). */
    data object ConfirmCooldown : TodayEvent

    data object DismissCooldown : TodayEvent

    /** En receptmedicin i Fler: extrados (FAV-11). */
    data class LogExtra(val prescription: Prescription) : TodayEvent

    /** Favorit i långtrycksmenyn (FAV-8). */
    data class ToggleFavorite(val medicine: PrnMedicine) : TodayEvent

    /** Bekräftat "Radera" i långtrycksmenyn (FAV-3). */
    data class DeleteMedicine(val medicine: PrnMedicine) : TodayEvent

    /** "Logga nu" eller tryck på ett ologgat måendetillfälle: arket med en ny logg för den visade dagen (HEM-5, SCR-6). */
    data class LogScreening(val occasion: Occasion) : TodayEvent

    /** Tryck på ett loggat måendetillfälle: arket med loggen för ändring (HEM-5, SCR-1). */
    data class EditScreening(val screening: Screening) : TodayEvent

    /** Arkets energi (SCR-1). */
    data class ChangeEnergy(val energy: Int) : TodayEvent

    /** Arkets stress (SCR-1). */
    data class ChangeStress(val stress: Int) : TodayEvent

    /** Arkets symptom (SCR-2). */
    data class ChangeSymptoms(val symptoms: List<SymptomScore>) : TodayEvent

    data object SaveScreening : TodayEvent

    data object CloseScreening : TodayEvent

    /** Konfettin har fallit – den faller inte igen samma dag (HEM-19). */
    data object ConfettiShown : TodayEvent

    data object ErrorShown : TodayEvent

    data object NoticeShown : TodayEvent

    data object Retry : TodayEvent
}

/** Belöningsläget (HEM-19): dagens jämförelse med igår, dagar i rad (HEM-20) och om konfettin ska falla nu. */
data class DayDone(val comparison: DayComparison?, val streak: Int, val playConfetti: Boolean)

/**
 * Idag för den visade dagen [date]. [today] är dagens datum och [week] måndagen i veckan som datumremsan
 * visar. [progress] är framstegsraden (HEM-18) och [doseProgress] samma räkning för bara doserna
 * (Mediciner-kortets räknare); [dayDone] är satt i belöningsläget – bara idag och när allt är klart.
 */
data class TodayContent(
    val today: LocalDate,
    val date: LocalDate,
    val week: LocalDate,
    val dayPart: DayPart,
    val datesWithEntries: Set<LocalDate>,
    val progress: DayProgress,
    val todayComplete: Boolean,
    val doseProgress: DayProgress,
    val checklist: DoseChecklist,
    val showDone: Boolean,
    val showUpcoming: Boolean,
    val prescriptions: Map<String, Prescription>,
    val choices: AsNeededChoices,
    val dayDone: DayDone?,
    /** Tidszonen dagen räknades i – tagningstiden visas i den (MED-14). */
    val zone: TimeZone,
    /** Mående-kortet: de aktiverade tillfällena med status (HEM-4, HEM-5, NOT-4). */
    val occasions: List<OccasionState> = emptyList(),
    /** "Din vecka" (HEM-13): bara idag, en söndag eller måndag, och med underlag. */
    val weekSummary: WeekSummary? = null,
    /** 7-dagarstrenden (HEM-7): dagarna till och med idag och dagsvärdet för var och en (`null` = lucka). */
    val energyDays: List<LocalDate> = emptyList(),
    val energy: List<Float?> = emptyList(),
    /** Pågående sjukdom (HEM-12), eller `null` – också medan den läses. */
    val illness: OngoingIllness? = null,
) {
    val isToday: Boolean get() = date == today
}

/**
 * Måendearkets rubrik (HEM-5), tagen när arket öppnas: tillfället och – en annan dag än idag – dagen.
 * Symptomvalen följer symptomlistan medan arket är öppet (`TodayViewModel.symptomOptions`).
 */
data class ScreeningSheetInfo(val occasion: Occasion?, val date: LocalDate?)

/** "För tidigt" (FAV-4): [remaining] kvar av kylperioden för [medicine]. */
data class CooldownPrompt(val medicine: PrnMedicine, val remaining: Duration)

/** Ett meddelande efter ett snabbval: [text] med [args] (FAV-2, FAV-6). */
class TodayNotice(@param:StringRes val text: Int, vararg val args: Any)

/**
 * Fliken Idag (HEM-1, HEM-2, HEM-14, HEM-18, HEM-19, MED-1–3, MED-5, MED-13, MED-14, FAV-2–6, FAV-8, FAV-11).
 * Allt räknas i `:core` – checklistan, framsteg, vid behov-valen, dagar i rad – och alla dosskrivningar går
 * via [DoseRepository]. Tiden kommer ur **en** minutklocka ([minutes]): "Snart" och "Försenat",
 * hälsningen och dagbytet vid midnatt läses alla därifrån, med tidszonen vid varje tick.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class TodayViewModel @Inject constructor(
    private val doses: DoseRepository,
    private val prescriptions: PrescriptionRepository,
    private val medicines: PrnMedicineRepository,
    private val screenings: ScreeningRepository,
    private val settings: SettingsRepository,
    private val options: OptionsRepository,
    private val illnesses: IllnessRepository,
    private val clock: Clock,
    private val zone: Provider<TimeZone>,
) : ViewModel() {
    private val now: Flow<LocalDateTime> =
        clock.minutes { zone.get() }.shareIn(viewModelScope, sharing, replay = 1)

    /** Dagens datum, igen vid midnatt – med städningen vid start och varje ny dag, som i Mediciner. */
    private val today: Flow<LocalDate> = now.map { it.date }.distinctUntilChanged().tidyingUpEachDay(prescriptions, viewModelScope)

    /** Vald dag (`null` = idag, även efter midnatt) och visad vecka (`null` = den valda dagens). */
    private val selected = MutableStateFlow<LocalDate?>(null)
    private val week = MutableStateFlow<LocalDate?>(null)
    private val showDone = MutableStateFlow(false)
    private val showUpcoming = MutableStateFlow(false)

    /** Dagen konfettin redan fallit (HEM-19) – en gång per dag, så länge ViewModeln lever. */
    private val celebrated = MutableStateFlow<LocalDate?>(null)

    /**
     * Den visade dagen och idag. Varje ny dag – ett val i remsan eller midnatt – säkerställer dagens receptdoser
     * (`ensureDay`, HEM-10), också en tidigare dag som aldrig varit "idag". Ingen egen dosgenerering här.
     */
    private val days: Flow<Pair<LocalDate, LocalDate>> = combine(selected, today) { chosen, today ->
        (chosen?.takeIf { it <= today } ?: today) to today
    }.distinctUntilChanged().onEach { (date, today) ->
        viewModelScope.launch { suspendRunCatching({ DataError.Unknown }) { prescriptions.ensureDay(date, today) } }
    }

    private data class Span(val date: LocalDate, val today: LocalDate, val week: LocalDate) {
        /** Det som läses: den visade dagen, remsans vecka, dagarna i rad bakåt från idag (HEM-20) och "Din vecka"s två veckor (HEM-13). */
        val from: LocalDate get() = minOf(week, date.weekMonday(), today.minus(STREAK_DAYS, DateTimeUnit.DAY))
    }

    private data class Entries(val doses: List<Dose>, val screenings: List<Screening>) {
        /** Posterna från [from] och framåt. */
        fun since(from: LocalDate) = Entries(doses.filter { it.date.isOnOrAfter(from) }, screenings.filter { it.date.isOnOrAfter(from) })
    }

    /** Delad, så att städningen och `ensureDay` körs en gång per ny dag fast både läsningen och vyn följer den. */
    private val span: Flow<Span> = combine(days, week) { (date, today), shown ->
        Span(date, today, (shown?.takeIf { it <= today } ?: date).weekMonday())
    }.distinctUntilChanged().shareIn(viewModelScope, sharing, replay = 1)

    private val entries: Flow<Entries> = span.map { it.from to it.today }.distinctUntilChanged().flatMapLatest { (from, to) ->
        combine(doses.observeDays(from, to), screenings.observeDays(from, to), ::Entries)
    }

    private data class Library(
        val prescriptions: List<Prescription>,
        val medicines: List<PrnMedicine>,
        val settings: Settings,
    )

    /**
     * HEM-12: den pågående episoden och dess incheckningar. Ett tillägg – det kombineras in sist (`content`), med
     * "inget" tills det lästs, så att det varken håller tillbaka resten av Idag eller räknar om dagen; ett läsfel
     * visar inget kort i stället för att fälla fliken (`withFallback`).
     */
    private val illness: Flow<Pair<IllnessEpisode, List<Checkin>>?> =
        illnesses.observeEpisodes().withFallback(emptyList()).map(::ongoingEpisode).distinctUntilChanged()
            // Incheckningarna lyssnas om bara när episoden byts, och en ny episod visas först med sina egna.
            .combineByKey({ it?.id }, { id -> if (id == null) flowOf(emptyList()) else illnesses.observeCheckins(id).withFallback(emptyList()) }) { episode, checkins ->
                episode?.let { it to checkins }
            }

    private val library: Flow<Library> = combine(prescriptions.observe(), medicines.observe(), settings.settings, ::Library)

    /**
     * Symptomlistan (Listor) för måendearkets val (SCR-2) – följs medan arket är öppet, så att en sen lista ger
     * symptomsteget; tom tills den lästs och vid läsfel. Inte en del av Idags innehåll.
     */
    val symptomOptions: StateFlow<List<Option>> = options.observe(OptionKind.SYMPTOM).distinctUntilChanged()
        .withFallback(emptyList()).stateIn(viewModelScope, sharing, emptyList())

    private data class Toggles(val done: Boolean, val upcoming: Boolean, val celebrated: LocalDate?)

    private val toggles = combine(showDone, showUpcoming, celebrated, ::Toggles)

    /** Allt som inte beror på klockslaget räknas en gång per ändring – inte varje minut. */
    private val day: Flow<DayView> = combine(span, entries, library, toggles, ::day)

    /** Per minut räknas bara det som beror på klockslaget: hälsningen och "Snart"/"Försenat" (MED-13). */
    private val content: Flow<TodayContent> =
        combine(day, now, illness.onStart { emit(null) }) { day, now, illness -> day.at(clock(now), illness) }

    /** Det som beror på klockslaget [now] i tidszonen [zone]. */
    private class Moment(val now: LocalDateTime, val zone: TimeZone)

    private fun clock(now: LocalDateTime) = Moment(now, zone.get())

    private val loader = DetailLoader(content, viewModelScope)
    val state: StateFlow<DetailUiState<TodayContent>> = loader.state

    private val _failure = MutableStateFlow<Failure?>(null)
    val failure: StateFlow<Failure?> = _failure.asStateFlow()

    private val _cooldown = MutableStateFlow<CooldownPrompt?>(null)
    val cooldown: StateFlow<CooldownPrompt?> = _cooldown.asStateFlow()

    private val _notice = MutableStateFlow<TodayNotice?>(null)
    val notice: StateFlow<TodayNotice?> = _notice.asStateFlow()

    /** Måendearket (HEM-5, SCR-1): sparat → arket stängs och "Mående sparat" visas (SCR-3). */
    private val sheet = EditorSheet<Screening, ScreeningSheetInfo>(viewModelScope) { _notice.value = TodayNotice(R.string.today_screening_saved) }

    /** Måendearket när det är öppet (HEM-5). */
    val screening: StateFlow<EditorSheetState<Screening, ScreeningSheetInfo>?> = sheet.current

    /** Ångra efter en avbockning eller "Hoppa över": dosen som den var före. */
    private var undoDose: Dose? = null
    private var undoCount = 0
    private val _undo = MutableStateFlow<UndoRequest?>(null)
    val undo: StateFlow<UndoRequest?> = _undo.asStateFlow()

    /** Den visade dagen utan det som beror på klockslaget ([Moment]) – dagens doser och allt som räknats ur dem. */
    private class DayView(
        val today: LocalDate,
        val date: LocalDate,
        val week: LocalDate,
        val datesWithEntries: Set<LocalDate>,
        val progress: DayProgress,
        val todayComplete: Boolean,
        val doseProgress: DayProgress,
        val doses: List<Dose>,
        val toggles: Toggles,
        val prescriptions: Map<String, Prescription>,
        val choices: AsNeededChoices,
        val dayDone: DayDone?,
        val reminders: ReminderSettings,
        /** Måendeloggarna – `occasionStates` väljer den visade dagens (en plats för urvalet). */
        val screenings: List<Screening>,
        val weekEntries: Entries?,
        val energyDays: List<LocalDate>,
        val energy: List<Float?>,
    ) {
        fun at(moment: Moment, illness: Pair<IllnessEpisode, List<Checkin>>?): TodayContent = TodayContent(
            today = today,
            date = date,
            week = week,
            dayPart = dayPartAt(moment.now.hour),
            datesWithEntries = datesWithEntries,
            progress = progress,
            todayComplete = todayComplete,
            doseProgress = doseProgress,
            checklist = doseChecklist(doses, date, moment.now.toInstant(moment.zone), moment.zone),
            showDone = toggles.done,
            showUpcoming = toggles.upcoming,
            prescriptions = prescriptions,
            choices = choices,
            dayDone = dayDone,
            zone = moment.zone,
            occasions = occasionStates(reminders, screenings, date, moment.now.toInstant(moment.zone), moment.zone),
            weekSummary = weekEntries?.let { weekSummaryOn(date, moment.now.toInstant(moment.zone), moment.zone, it.screenings, it.doses) },
            energyDays = energyDays,
            energy = energy,
            illness = illness?.let { (episode, checkins) -> ongoingIllness(episode, checkins, today) },
        )
    }

    private fun day(span: Span, entries: Entries, library: Library, toggles: Toggles): DayView {
        val (date, today) = span.date to span.today
        val occasions = library.settings.reminders.enabledOccasions
        val dayDoses = entries.doses.filter { it.date == date }
        // HEM-7: de sju senaste dagarna till och med idag, oavsett vilken dag som visas.
        val energyDays = daysEnding(today)
        val progress = dayProgress(date, dayDoses, entries.screenings, occasions)
        val todayProgress = if (date == today) progress else dayProgress(today, entries.doses, entries.screenings, occasions)
        val dayDone = if (date == today && progress.isComplete) {
            DayDone(
                comparison = dayComparison(today, entries.screenings),
                streak = dayStreak(today, entries.doses, entries.screenings, occasions),
                playConfetti = toggles.celebrated != today,
            )
        } else {
            null
        }
        return DayView(
            today = today,
            date = date,
            week = span.week,
            datesWithEntries = datesWithEntries(entries.doses, entries.screenings),
            progress = progress,
            todayComplete = todayProgress.isComplete,
            doseProgress = dayProgress(date, dayDoses, emptyList(), emptyList()),
            doses = dayDoses,
            toggles = toggles,
            prescriptions = library.prescriptions.associateBy { it.id },
            choices = asNeededChoices(library.medicines, library.prescriptions, today),
            dayDone = dayDone,
            reminders = library.settings.reminders,
            screenings = entries.screenings,
            // HEM-13: bara när idag visas, och bara de två veckorna som jämförs – omräkningen varje minut (doserna
            // förfaller med klockan) går inte igenom hela läsningen. Om den visas avgörs av minutens klocka (`at`).
            weekEntries = if (date == today) entries.since(today.minus(WEEK_SUMMARY_DAYS - 1, DateTimeUnit.DAY)) else null,
            energyDays = energyDays,
            energy = dailyEnergyAverages(entries.screenings, energyDays),
        )
    }

    /** FAV-2, FAV-11: snabbvalen loggar nu – bara när idag visas (en tidigare dag loggas i efterhand, #239). */
    private val viewingToday: Boolean get() = currentContent?.isToday == true

    /** Det som visas just nu, eller `null` under laddning och vid fel. */
    private val currentContent: TodayContent? get() = (state.value as? DetailUiState.Content)?.value

    fun onEvent(event: TodayEvent) {
        when (event) {
            is TodayEvent.SelectDate -> selectDate(event.date)
            is TodayEvent.ShowWeek -> week.value = event.monday
            // En loggad vid behov- eller extrados bockas inte av – den tas bort (dosformuläret, #239).
            is TodayEvent.SetTaken -> if (event.dose.isScheduled) setStatus(event.dose, if (event.taken) DoseStatus.TAKEN else DoseStatus.PLANNED)
            is TodayEvent.Skip -> setStatus(event.dose, DoseStatus.SKIPPED)
            TodayEvent.Undo -> undo()
            is TodayEvent.UndoDismissed -> if (_undo.value?.id == event.id) clearUndo()
            TodayEvent.ToggleDone -> showDone.value = !showDone.value
            TodayEvent.ToggleUpcoming -> showUpcoming.value = !showUpcoming.value
            is TodayEvent.LogAsNeeded -> if (viewingToday) logAsNeeded(event.medicine, force = false)
            TodayEvent.ConfirmCooldown -> _cooldown.value?.let {
                _cooldown.value = null
                if (viewingToday) logAsNeeded(it.medicine, force = true)
            }
            TodayEvent.DismissCooldown -> _cooldown.value = null
            is TodayEvent.LogExtra -> if (viewingToday) logExtra(event.prescription)
            is TodayEvent.ToggleFavorite -> write { medicines.setFavorite(event.medicine, !event.medicine.favorite) }
            is TodayEvent.DeleteMedicine -> write { medicines.delete(event.medicine.id) }
            is TodayEvent.LogScreening -> logScreening(event.occasion)
            is TodayEvent.EditScreening -> openSheet(event.screening, event.screening, clock.now().toLocalDateTime(zone.get()).date)
            // Id:t och skapandetiden hör till loggen, inte formuläret.
            is TodayEvent.ChangeEnergy -> sheet.update(ENERGY) { it.copy(energy = event.energy) }
            is TodayEvent.ChangeStress -> sheet.update(STRESS) { it.copy(stress = event.stress) }
            is TodayEvent.ChangeSymptoms -> sheet.update(SYMPTOMS) { it.copy(symptoms = event.symptoms) }
            // SCR-1: ny logg som den är, ändrad fältvis (`ScreeningRepository.save`).
            TodayEvent.SaveScreening -> sheet.save(screenings::save)
            TodayEvent.CloseScreening -> sheet.close()
            TodayEvent.ConfettiShown -> celebrated.value = currentContent?.today
            TodayEvent.ErrorShown -> _failure.value = null
            TodayEvent.NoticeShown -> _notice.value = null
            TodayEvent.Retry -> loader.retry()
        }
    }

    private fun selectDate(date: LocalDate) {
        val today = currentContent?.today ?: return
        if (date > today) return
        // Idag följer med över midnatt; en tidigare dag står kvar.
        selected.value = date.takeIf { it != today }
        week.value = null
    }

    /**
     * MED-2, MED-3, MED-14: en ny status för dosen – bara status och tagningstid skrivs. Tagen och överhoppad
     * kan ångras ([undo]); att bocka av en tagen dos är sitt eget ångra (tryck igen).
     */
    private fun setStatus(dose: Dose, status: DoseStatus) {
        viewModelScope.launch {
            // MED-14: en tidigare dag bockas av på dosens planerade tid, inte nu (`checkOffTime`).
            val takenAt = if (status == DoseStatus.TAKEN) dose.checkOffTime(clock.now(), zone.get()) else null
            val failure = doses.setStatus(dose, status, takenAt).failureOrNull()
            if (failure != null) {
                _failure.value = failure
                return@launch
            }
            val format = when (status) {
                DoseStatus.TAKEN -> R.string.today_undo_taken_format
                DoseStatus.SKIPPED -> R.string.today_undo_skipped_format
                DoseStatus.PLANNED -> null
            }
            if (format == null) {
                // Bara ångra för just den här dosen blir inaktuellt.
                if (undoDose?.id == dose.id) clearUndo()
            } else {
                undoDose = dose
                _undo.value = UndoRequest("${dose.id}#${++undoCount}", medicineTitle(dose.name, dose.dose, dose.unit), format)
            }
        }
    }

    private fun undo() {
        val dose = undoDose ?: return
        clearUndo()
        write { doses.setStatus(dose, dose.status, dose.takenAt) }
    }

    private fun clearUndo() {
        undoDose = null
        _undo.value = null
    }

    /**
     * HEM-5, SCR-6: en ny logg för [occasion] på den visade dagen – också en tidigare dag. Dag och klockslag ur
     * **en** läsning av klockan: visas idag är dagen klockans (också om midnatt passerat innan vyn hunnit
     * följa med), och klockslaget är nu; en tidigare dag får tillfällets påminnelsetid (3.x tog alltid "nu").
     */
    private fun logScreening(occasion: Occasion) {
        val content = currentContent ?: return
        val now = clock.now().toLocalDateTime(zone.get())
        val date = if (content.isToday) now.date else content.date
        val time = if (date == now.date) {
            LocalTime(now.hour, now.minute)
        } else {
            content.occasions.firstOrNull { it.occasion == occasion }?.time ?: occasion.defaultTime
        }
        openSheet(null, screenings.new(date, occasion).copy(time = time), now.date)
    }

    /**
     * Öppnar arket med [value] ([loaded] = den sparade loggen). Rubriken tas nu, så att arket står sig medan skärmen
     * bakom laddar om: dagen står i den när den inte är [today] (samma klockläsning som gav loggens dag).
     */
    private fun openSheet(loaded: Screening?, value: Screening, today: LocalDate) {
        sheet.open(loaded, value, ScreeningSheetInfo(value.occasion, value.date?.takeIf { it != today }))
    }

    /** FAV-4, FAV-5, FAV-6: nu (bara när idag visas); kylperioden frågar ("För tidigt"), dagsgränsen stoppar med ett meddelande. */
    private fun logAsNeeded(medicine: PrnMedicine, force: Boolean) {
        viewModelScope.launch {
            val result = doses.logAsNeeded(medicine, clock.now(), force)
            result.failureOrNull()?.let { _failure.value = it; return@launch }
            val title = medicineTitle(medicine.name, medicine.dose, medicine.unit)
            when (val check = result.getOrThrow()) {
                PrnCheck.Allowed -> _notice.value = TodayNotice(R.string.today_logged_format, title)
                is PrnCheck.Cooldown -> _cooldown.value = CooldownPrompt(medicine, check.remaining)
                PrnCheck.DailyLimitReached -> _notice.value = TodayNotice(R.string.today_limit_reached_format, medicine.maxPerDay, medicine.name)
            }
        }
    }

    /** FAV-11: receptets medicin som extrados nu, med dagens dos (REC-12). */
    private fun logExtra(prescription: Prescription) {
        viewModelScope.launch {
            val now = clock.now()
            val failure = doses.logExtraDose(prescription, now).failureOrNull()
            if (failure != null) {
                _failure.value = failure
            } else {
                val today = currentContent?.today
                val dose = today?.let { prescription.doseFor(it) } ?: prescription.dose
                _notice.value = TodayNotice(R.string.today_logged_format, medicineTitle(prescription.name, dose, prescription.unit))
            }
        }
    }

    private fun write(action: suspend () -> Result<Unit>) {
        viewModelScope.launch { action().failureOrNull()?.let { _failure.value = it } }
    }

    private companion object {
        /** Delade flöden följer skärmen och glömmer sitt senaste värde när de stoppats (en ny dag kan ha börjat). */
        val sharing = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS, replayExpirationMillis = 0)

        /** Hur långt bakåt dagar i rad räknas (HEM-20) – en längre svit visas som så här många dagar. */
        const val STREAK_DAYS = 90

        /** Arkets fält (rörda när de ändrats, `EditorState`). */
        const val ENERGY = "energy"
        const val STRESS = "stress"
        const val SYMPTOMS = "symptoms"

        /** "Din vecka" jämför två veckor (HEM-13): så många dagar till och med idag behövs. */
        const val WEEK_SUMMARY_DAYS = 14

        init {
            // Läsningen bakåt ([Span.from]) täcker sviten och därmed också "Din vecka"s två veckor.
            require(STREAK_DAYS >= WEEK_SUMMARY_DAYS - 1)
        }
    }
}

/** Om dagen finns och är [from] eller senare – urvalet av poster för en period. */
private fun LocalDate?.isOnOrAfter(from: LocalDate): Boolean = this != null && this >= from
