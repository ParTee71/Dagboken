package se.partee71.dagboken.ui.log

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
import javax.inject.Provider
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import se.partee71.dagboken.R
import se.partee71.dagboken.core.engine.PrnCheck
import se.partee71.dagboken.core.engine.atTime
import se.partee71.dagboken.core.engine.checkDays
import se.partee71.dagboken.core.engine.checkDose
import se.partee71.dagboken.core.engine.isPrescribed
import se.partee71.dagboken.core.engine.momentOf
import se.partee71.dagboken.core.engine.onDay
import se.partee71.dagboken.core.engine.shownTime
import se.partee71.dagboken.core.engine.takenDose
import se.partee71.dagboken.core.engine.withTaken
import se.partee71.dagboken.core.model.Dose
import se.partee71.dagboken.core.model.DoseStatus
import se.partee71.dagboken.core.model.PrnMedicine
import se.partee71.dagboken.core.model.Slot
import se.partee71.dagboken.core.schema.CollectionNames
import se.partee71.dagboken.core.schema.DoseCodec
import se.partee71.dagboken.data.common.DataError
import se.partee71.dagboken.data.common.withFallback
import se.partee71.dagboken.data.repository.DoseRepository
import se.partee71.dagboken.data.repository.DoseSlotTaken
import se.partee71.dagboken.data.repository.PrnMedicineRepository
import se.partee71.dagboken.data.repository.creatingStore
import se.partee71.dagboken.ui.common.nowInMinutes
import se.partee71.dagboken.ui.common.EditorState
import se.partee71.dagboken.ui.common.EntryEditEvent
import se.partee71.dagboken.ui.common.EntryEditor
import se.partee71.dagboken.ui.common.EntryForm
import se.partee71.dagboken.ui.common.Validator
import se.partee71.dagboken.ui.common.entryDeleteAction
import se.partee71.dagboken.ui.common.hasErrorOutside
import se.partee71.dagboken.ui.common.label
import se.partee71.dagboken.ui.common.medicineTitle
import se.partee71.dagboken.ui.common.nonBlank
import se.partee71.dagboken.ui.common.prnLimits
import se.partee71.dagboken.ui.common.rememberEntryForm
import se.partee71.dagboken.ui.common.rulesValidator
import se.partee71.dagboken.ui.components.AppCard
import se.partee71.dagboken.ui.components.AppTextField
import se.partee71.dagboken.ui.components.ChoiceChips
import se.partee71.dagboken.ui.components.DateField
import se.partee71.dagboken.ui.components.DateTimeRow
import se.partee71.dagboken.ui.components.EntityEditScreen
import se.partee71.dagboken.ui.components.FieldError
import se.partee71.dagboken.ui.components.ItemRow
import se.partee71.dagboken.ui.components.LabeledGroup
import se.partee71.dagboken.ui.components.NoteField
import se.partee71.dagboken.ui.components.NoticeBanner
import se.partee71.dagboken.ui.components.SwitchRow
import se.partee71.dagboken.ui.components.UnitChoice

/** Fälten i formuläret – codecens namn, så att rules-felen ([rulesValidator]) hamnar på rätt fält. */
object DoseField {
    const val NAME = "name"
    const val DOSE = "dose"
    const val TAKEN_AT = "takenAt"
    const val NOTE = "note"

    /** Dagens gräns för vid behov-medicinen är nådd vid den valda tiden (FAV-5) – visas som meddelande, inte vid ett fält. */
    const val LIMIT = "dailyLimit"
}

/** Vad dosformuläret gör: en ny engångsdos (NAV-10), en vid behov-medicin i efterhand (MED-16) eller en loggad dos (MED-15). */
enum class DoseMode { NEW, AS_NEEDED, EDIT }

/**
 * MED-11, MED-15, MED-16: ett namn krävs (en receptdos har receptets, som inte ändras här), en tagen dos tas aldrig i
 * framtiden ([now]) och – i efterhand – inte när dagens gräns är nådd ([limitReached]); allt annat som rules.
 */
fun doseValidator(now: () -> Instant, limitReached: () -> Boolean = { false }): Validator<Dose> = rulesValidator(CollectionNames.DOSES, DoseCodec) { d ->
    buildMap {
        if (d.name.isBlank() && !d.isPrescribed) put(DoseField.NAME, R.string.option_name_missing)
        if (d.status == DoseStatus.TAKEN && d.takenAt?.let { it > now() } == true) put(DoseField.TAKEN_AT, R.string.dose_in_future)
        if (limitReached()) put(DoseField.LIMIT, R.string.dose_limit_reached)
    }
}

/** Det som sparas: trimmad text, och en tom anteckning som ingen. */
internal fun Dose.cleaned(): Dose = copy(name = name.trim(), dose = dose.trim(), note = note?.trim().nonBlank())

/** Fälten som formuläret visar ett fel vid; ett fel på något annat visas överst (`EntityEditScreen(formError)`). */
private val SHOWN_ERRORS = setOf(DoseField.NAME, DoseField.DOSE, DoseField.TAKEN_AT, DoseField.NOTE, DoseField.LIMIT)

/** Ett sparförsök i efterhand som kylperioden eller dagsgränsen stoppade (FAV-4, FAV-5) – inget sparades. */
private class AsNeededBlocked : Exception()

/**
 * Texten för ett sparfel som inte är ett `DataError`: i efterhand stoppat av kylperioden eller dagsgränsen, och en
 * receptdos som flyttas till en dag där tidpunkten redan har en dos (MED-15, [DoseSlotTaken]).
 */
fun doseErrorText(error: Throwable): Int? = when (error) {
    is AsNeededBlocked -> R.string.dose_not_saved
    is DoseSlotTaken -> R.string.dose_slot_taken
    else -> null
}

/**
 * Dosformuläret (MED-11, MED-15, MED-16, FAV-10, MEDF-6) på det delade postformuläret ([form]):
 * - en loggad dos [id] (Dagbok, HIST-3): dag, klockslag och anteckningen; en receptdos också "Tagen" (av =
 *   överhoppad), en dos utan recept namn, dos, enhet och tidpunkt (den raderas i stället för att hoppas över). En receptdos som flyttas till en annan dag får ett nytt id med
 *   receptkopplingen kvar (`DoseRepository.save`), och Radera hoppar över den (`DoseRepository.delete`).
 * - vid behov-medicinen [prnId] i efterhand mot [date] (`null` = idag) kl. nu: medicinen som information, dag,
 *   klockslag och anteckningen (förval medicinens, MED-11). Kylperioden och dagsgränsen prövas mot den valda tiden
 *   – samma `checkDose` som snabbvalen på Idag, ur samma doser – och visas direkt ([check]); dagsgränsen gör "Spara"
 *   inaktiv, kylperioden frågar "För tidigt" ([cooldown]) när man sparar. Sparat via `DoseRepository.logAsNeeded`,
 *   som prövar igen.
 * - utan båda en ny engångsdos mot [date] kl. nu (`DoseRepository.newOneOff`).
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel(assistedFactory = DoseEditViewModel.Factory::class)
class DoseEditViewModel @AssistedInject constructor(
    private val doses: DoseRepository,
    private val medicines: PrnMedicineRepository,
    private val clock: Clock,
    private val zones: Provider<TimeZone>,
    @Assisted("id") id: String?,
    @Assisted("prnId") prnId: String?,
    @Assisted date: LocalDate?,
) : ViewModel() {
    val mode: DoseMode = when {
        id != null -> DoseMode.EDIT
        prnId != null -> DoseMode.AS_NEEDED
        else -> DoseMode.NEW
    }

    /** Tidszonen formuläret räknar dag och klockslag i (enhetens). */
    val zone: TimeZone get() = zones.get()

    private val _medicine = MutableStateFlow<PrnMedicine?>(null)

    /** Vid behov-medicinen i efterhand, när den lästs. */
    val medicine: StateFlow<PrnMedicine?> = _medicine.asStateFlow()

    private val _check = MutableStateFlow<PrnCheck?>(null)

    /** Kylperiod och dagsgräns vid den valda tiden (FAV-4, FAV-5, MED-16); `null` utan medicin eller innan doserna lästs. */
    val check: StateFlow<PrnCheck?> = _check.asStateFlow()

    private val _cooldown = MutableStateFlow<CooldownPrompt?>(null)

    /** "För tidigt" när den visas (FAV-4). */
    val cooldown: StateFlow<CooldownPrompt?> = _cooldown.asStateFlow()

    /**
     * Tiden kylperioden bekräftats bort för ("Ta ändå"); dagsgränsen gäller ändå (FAV-5). Gäller bara den tiden: en
     * ny dag eller ett nytt klockslag frågar igen, och ett misslyckat sparande glömmer bekräftelsen.
     */
    private var confirmedFor: Instant? = null

    val form = EntryEditor(
        viewModelScope,
        if (mode == DoseMode.AS_NEEDED) creatingStore(::logLater) else doses,
        id,
        doseValidator({ clock.now() }) { _check.value == PrnCheck.DailyLimitReached },
        placeholder = Dose(""),
        create = { create(prnId, date) },
        clean = { _, value -> value.cleaned() },
        confirmSave = ::mayLog,
        errorMessage = ::doseErrorText,
        // Medicinen kl. nu den visade dagen är ett riktigt svar; en engångsdos kräver ändå ett namn.
        saveUnchanged = mode == DoseMode.AS_NEEDED,
    )

    val editor: EditorState<Dose> = form.editor

    init {
        if (mode == DoseMode.AS_NEEDED) {
            viewModelScope.launch {
                checks().collect {
                    _check.value = it
                    editor.revalidate()
                }
            }
        }
    }

    private suspend fun create(prnId: String?, date: LocalDate?): Dose {
        val zone = zone
        val now = clock.nowInMinutes(zone)
        val day = date ?: now.date
        val time = now.time
        if (prnId == null) return doses.newOneOff(day, time)
        val medicine = medicines.get(prnId).getOrThrow() ?: throw DataError.NotFound
        _medicine.value = medicine
        // Bara formulärets värde: id:t och skapandetiden sätter `logAsNeeded` när dosen skrivs.
        return medicine.takenDose("", momentOf(day, time, zone), zone)
    }

    /** Kontrollen vid den valda tiden, om igen när tiden eller doserna ändras – ett läsfel visar ingen kontroll (sparandet prövar ändå). */
    private fun checks(): Flow<PrnCheck?> =
        combine(_medicine, editor.state.map { it.value.takenAt }.distinctUntilChanged()) { medicine, at -> medicine to at }
            .flatMapLatest { (medicine, at) ->
                if (medicine == null || at == null) {
                    flowOf(null)
                } else {
                    val zone = zone
                    val days = medicine.checkDays(at, zone)
                    doses.observeDays(days.start, days.endInclusive).withFallback(emptyList()).map { medicine.checkDose(it, at, zone) }
                }
            }
            .distinctUntilChanged()

    /** FAV-4: med en pågående kylperiod frågar "För tidigt" först – "Ta ändå" sparar ([confirmCooldown]). */
    private fun mayLog(value: Dose): Boolean {
        val check = _check.value
        if (mode != DoseMode.AS_NEEDED || isConfirmed(value.takenAt) || check !is PrnCheck.Cooldown || !editor.state.value.isValid) return true
        val medicine = _medicine.value ?: return true
        _cooldown.value = CooldownPrompt(medicine, check.remaining)
        return false
    }

    /** Loggar i efterhand – samma väg och kontroll som snabbvalen på Idag (`DoseRepository.logAsNeeded`). */
    private suspend fun logLater(dose: Dose): Result<Unit> {
        val medicine = _medicine.value ?: return Result.failure(DataError.NotFound)
        val at = dose.takenAt ?: return Result.failure(IllegalArgumentException("En dos i efterhand har en tid"))
        val result = doses.logAsNeeded(medicine, at, force = isConfirmed(at), note = dose.note).mapCatching { log ->
            when (val check = log.check) {
                PrnCheck.Allowed -> Unit
                is PrnCheck.Cooldown -> {
                    // Kylperioden hann börja efter kontrollen i formuläret: fråga nu.
                    _cooldown.value = CooldownPrompt(medicine, check.remaining)
                    throw AsNeededBlocked()
                }
                PrnCheck.DailyLimitReached -> throw AsNeededBlocked()
            }
        }
        if (result.isFailure) confirmedFor = null
        return result
    }

    private fun isConfirmed(at: Instant?): Boolean = at != null && at == confirmedFor

    /** "Ta ändå" (FAV-4): sparar utan kylperioden. */
    fun confirmCooldown() {
        confirmedFor = editor.value.takenAt
        _cooldown.value = null
        form.onEvent(EntryEditEvent.Save)
    }

    fun dismissCooldown() {
        _cooldown.value = null
    }

    @AssistedFactory
    interface Factory {
        fun create(@Assisted("id") id: String?, @Assisted("prnId") prnId: String?, date: LocalDate?): DoseEditViewModel
    }
}

/** Det vid behov i efterhand visar utöver formuläret: medicinen, kontrollen vid den valda tiden och "För tidigt". */
class AsNeededState(
    val medicine: PrnMedicine? = null,
    val check: PrnCheck? = null,
    val cooldown: CooldownPrompt? = null,
    val onConfirmCooldown: () -> Unit = {},
    val onDismissCooldown: () -> Unit = {},
)

@Composable
fun DoseEditRoute(id: String?, prnId: String?, date: LocalDate?, onClose: () -> Unit) {
    val viewModel = hiltViewModel<DoseEditViewModel, DoseEditViewModel.Factory> { it.create(id, prnId, date) }
    val medicine by viewModel.medicine.collectAsStateWithLifecycle()
    val check by viewModel.check.collectAsStateWithLifecycle()
    val cooldown by viewModel.cooldown.collectAsStateWithLifecycle()
    DoseEditScreen(
        rememberEntryForm(viewModel.form),
        onClose,
        viewModel.mode,
        viewModel.zone,
        AsNeededState(medicine, check, cooldown, viewModel::confirmCooldown, viewModel::dismissCooldown),
    )
}

/**
 * Dosformuläret på `EntityEditScreen` (MED-11, MED-15, MED-16, NFR-10–12) i läget [mode] (se [DoseEditViewModel]),
 * med dag och klockslag i [zone]. En loggad dos kan raderas i menyn efter en bekräftelse som namnger den lagrade dosen
 * – en receptdos markeras som överhoppad (HIST-5, MED-3).
 */
@Composable
fun DoseEditScreen(form: EntryForm<Dose>, onClose: () -> Unit, mode: DoseMode, zone: TimeZone, asNeeded: AsNeededState = AsNeededState()) {
    val state = form.state
    val d = form.value
    val stored = form.stored ?: d
    val prescribed = mode == DoseMode.EDIT && stored.isPrescribed
    val messages = state.errors.mapValues { stringResource(it.value) }
    val error: (String) -> String? = messages::get
    EntityEditScreen(
        title = stringResource(
            when (mode) {
                DoseMode.NEW -> R.string.dose_new
                DoseMode.AS_NEEDED -> R.string.dose_log_later
                DoseMode.EDIT -> R.string.dose_edit
            },
        ),
        state = state,
        effects = form.effects,
        onSave = { form.onEvent(EntryEditEvent.Save) },
        onClose = onClose,
        delete = if (mode != DoseMode.EDIT) {
            null
        } else {
            val title = medicineTitle(stored.displayName, stored.dose, stored.unit)
            entryDeleteAction(R.string.diary_subject_dose, title, stored.date, stored.shownTime(zone), skips = stored.isPrescribed) { form.onEvent(EntryEditEvent.Delete) }
        },
        onRetry = { form.onEvent(EntryEditEvent.Retry) },
        formError = if (state.hasErrorOutside(SHOWN_ERRORS)) stringResource(R.string.form_not_savable) else null,
    ) {
        when {
            mode == DoseMode.AS_NEEDED -> asNeeded.medicine?.let { MedicineInfo(medicineTitle(it.displayName, it.dose, it.unit), prnLimits(it), help = null) }
            prescribed -> MedicineInfo(medicineTitle(d.displayName, d.dose, d.unit), stringResource(d.slot.label()), stringResource(R.string.dose_prescribed_help))
        }
        DayAndTime(form, zone, timeShown = !prescribed || d.status == DoseStatus.TAKEN, error = error(DoseField.TAKEN_AT))
        if (mode == DoseMode.AS_NEEDED) AsNeededNotice(asNeeded)
        if (mode != DoseMode.AS_NEEDED && !prescribed) DoseFields(form, error)
        // "Tagen" bara för en receptdos: av = överhoppad (MED-3); en dos utan recept raderas i stället (menyn).
        if (prescribed) {
            SwitchRow(
                stringResource(R.string.dose_taken),
                d.status == DoseStatus.TAKEN,
                { taken -> form.change(DoseField.TAKEN_AT) { it.withTaken(taken, zone) } },
                subtitle = stringResource(R.string.dose_taken_help),
            )
        }
        AppCard {
            NoteField(d.note.orEmpty(), { note -> form.change(DoseField.NOTE) { it.copy(note = note.ifEmpty { null }) } }, error = error(DoseField.NOTE))
        }
    }
    asNeeded.cooldown?.let { CooldownDialog(it, asNeeded.onConfirmCooldown, asNeeded.onDismissCooldown) }
}

/** Medicinen som information – vid behov-medicinen i efterhand, receptdosens namn, dos och tidpunkt (MED-15). */
@Composable
private fun MedicineInfo(title: String, subtitle: String, help: String?) {
    LabeledGroup(stringResource(R.string.dose_medicine), helper = help) {
        AppCard { ItemRow(title, subtitle = subtitle) }
    }
}

/**
 * Dag och klockslag (DAT-2): en tagen dos tagningstid (MED-14), annars det planerade klockslaget. En överhoppad
 * receptdos har inget klockslag att ändra ([timeShown] = `false`) – schemats tid styrs av receptet.
 */
@Composable
private fun DayAndTime(form: EntryForm<Dose>, zone: TimeZone, timeShown: Boolean, error: String?) {
    val date = form.value.date ?: return
    val onDate: (LocalDate) -> Unit = { day -> form.change(DoseField.TAKEN_AT) { it.onDay(day, zone) } }
    if (timeShown) {
        DateTimeRow(date, form.value.shownTime(zone), onDate, { time -> form.change(DoseField.TAKEN_AT) { it.atTime(time, zone) } })
    } else {
        DateField(stringResource(R.string.date), date, onDate)
    }
    error?.let { FieldError(it) }
}

/** Namn, styrka, dos, enhet och tidpunkt – en dos utan recept (MED-11, MED-15). */
@Composable
private fun DoseFields(form: EntryForm<Dose>, error: (String) -> String?) {
    val d = form.value
    AppTextField(d.name, { name -> form.change(DoseField.NAME) { it.copy(name = name) } }, stringResource(R.string.option_name), error = error(DoseField.NAME))
    AppTextField(d.strength, { strength -> form.change { it.copy(strength = strength) } }, stringResource(R.string.medicine_strength))
    AppTextField(d.dose, { dose -> form.change(DoseField.DOSE) { it.copy(dose = dose) } }, stringResource(R.string.dose_label), error = error(DoseField.DOSE))
    UnitChoice(d.unit, { unit -> form.change { it.copy(unit = unit) } })
    LabeledGroup(stringResource(R.string.dose_slot)) {
        ChoiceChips(Slot.entries, d.slot, { slot -> form.change { it.copy(slot = slot) } }, label = { stringResource(it.label()) })
    }
}

/** Kylperioden eller dagsgränsen vid den valda tiden (FAV-4, FAV-5, MED-16) – direkt i formuläret. */
@Composable
private fun AsNeededNotice(asNeeded: AsNeededState) {
    val medicine = asNeeded.medicine ?: return
    when (val check = asNeeded.check) {
        is PrnCheck.Cooldown -> NoticeBanner(cooldownText(R.string.dose_cooldown_notice, CooldownPrompt(medicine, check.remaining)), R.drawable.ic_clock, onClick = null)
        PrnCheck.DailyLimitReached -> NoticeBanner(stringResource(R.string.today_limit_reached_format, medicine.maxPerDay, medicine.displayName), R.drawable.ic_info, onClick = null)
        PrnCheck.Allowed, null -> Unit
    }
}
