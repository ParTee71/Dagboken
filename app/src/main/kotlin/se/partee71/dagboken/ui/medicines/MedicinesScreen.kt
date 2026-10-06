package se.partee71.dagboken.ui.medicines

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import javax.inject.Provider
import kotlin.time.Clock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn
import se.partee71.dagboken.R
import se.partee71.dagboken.core.engine.PeriodEnding
import se.partee71.dagboken.core.engine.boostEnd
import se.partee71.dagboken.core.engine.boostFor
import se.partee71.dagboken.core.engine.doseFor
import se.partee71.dagboken.core.engine.endingSoon
import se.partee71.dagboken.core.engine.endingSoonDate
import se.partee71.dagboken.core.engine.medicineOverview
import se.partee71.dagboken.core.engine.totalWith
import se.partee71.dagboken.core.model.Prescription
import se.partee71.dagboken.core.model.PrnMedicine
import se.partee71.dagboken.data.auth.AuthUser
import se.partee71.dagboken.data.repository.PrescriptionRepository
import se.partee71.dagboken.data.repository.PrnMedicineRepository
import se.partee71.dagboken.ui.common.ArchiveEvent
import se.partee71.dagboken.ui.common.DateFormat
import se.partee71.dagboken.ui.common.Failure
import se.partee71.dagboken.ui.common.days
import se.partee71.dagboken.ui.common.ListEvent
import se.partee71.dagboken.ui.common.ListLoader
import se.partee71.dagboken.ui.common.ListUiState
import se.partee71.dagboken.ui.common.STOP_TIMEOUT_MILLIS
import se.partee71.dagboken.ui.common.doseText
import se.partee71.dagboken.ui.common.failureOrNull
import se.partee71.dagboken.ui.common.medicineTitle
import se.partee71.dagboken.ui.common.periodText
import se.partee71.dagboken.ui.common.tidyingUpEachDay
import se.partee71.dagboken.ui.common.prescriptionSubtitle
import se.partee71.dagboken.ui.common.prnLimits
import se.partee71.dagboken.ui.components.AccountAvatar
import se.partee71.dagboken.ui.components.AddAction
import se.partee71.dagboken.ui.components.AppMenuItem
import se.partee71.dagboken.ui.components.DagbokenEntryCard
import se.partee71.dagboken.ui.components.DeleteAction
import se.partee71.dagboken.ui.components.EmptyContent
import se.partee71.dagboken.ui.components.EmptyExample
import se.partee71.dagboken.ui.components.EntityListScreen
import se.partee71.dagboken.ui.components.EntryToggle
import se.partee71.dagboken.ui.components.FavoriteStar
import se.partee71.dagboken.ui.components.GroupLabel
import se.partee71.dagboken.ui.components.InfoPill
import se.partee71.dagboken.ui.components.ItemRow
import se.partee71.dagboken.ui.components.ListArchive
import se.partee71.dagboken.ui.components.ListGroup
import se.partee71.dagboken.ui.components.NoticeBanner
import se.partee71.dagboken.ui.theme.AppTypography
import se.partee71.dagboken.ui.theme.Spacing
import se.partee71.dagboken.ui.theme.Tone

/** En rad i fliken Mediciner: ett recept (aktivt, pausat eller avslutat) eller en vid behov-medicin. */
sealed interface MedicineItem {
    val key: String

    /** [ended] = perioden har passerats (REC-8) – står under "Avslutade recept". */
    data class Recipe(val prescription: Prescription, val ended: Boolean = false) : MedicineItem {
        override val key: String get() = "recipe:${prescription.id}"
    }

    data class AsNeeded(val medicine: PrnMedicine) : MedicineItem {
        override val key: String get() = "prn:${medicine.id}"
    }
}

/** Flikens rader i visningsordning – indelningen och ordningen räknas i `:core` ([medicineOverview]). */
fun medicineItems(prescriptions: List<Prescription>, medicines: List<PrnMedicine>, today: LocalDate): List<MedicineItem> {
    val overview = medicineOverview(prescriptions, medicines, today)
    return overview.current.map { MedicineItem.Recipe(it) } +
        overview.asNeeded.map { MedicineItem.AsNeeded(it) } +
        overview.ended.map { MedicineItem.Recipe(it, ended = true) }
}

sealed interface MedicinesEvent {
    /** Aktiv-reglaget eller menyns Aktivera/Avaktivera (REC-5). */
    data class ActiveChanged(val prescription: Prescription, val active: Boolean) : MedicinesEvent

    /** Bekräftad radering av ett recept (NFR-15). */
    data class DeletePrescription(val prescription: Prescription) : MedicinesEvent

    /** Stjärnan på en vid behov-medicin (FAV-2, SET-10). */
    data class FavoriteToggled(val medicine: PrnMedicine) : MedicinesEvent

    data object Retry : MedicinesEvent

    data object ErrorShown : MedicinesEvent
}

/**
 * Fliken Mediciner (MEDF-1…MEDF-5): recept och scheman, vid behov-medicinerna och de avslutade
 * recepten, med periodsluten idag och i morgon som banner (MEDF-2). All medicinlogik kommer från
 * `:core/engine`; ett misslyckat reglage, stjärna eller radering visas som meddelande ([failure]).
 */
@HiltViewModel
class MedicinesViewModel @Inject constructor(
    private val prescriptions: PrescriptionRepository,
    private val medicines: PrnMedicineRepository,
    clock: Clock,
    zone: Provider<TimeZone>,
) : ViewModel() {
    /**
     * Dagen fliken räknar med – dagens dos, periodetiketter, periodslut och avslutade – byts vid
     * midnatt (tidszonen läses vid varje omräkning). **En** källa: listan, bannern och pillsen läser
     * alla härifrån, så att de aldrig visar olika dagar.
     */
    val today: StateFlow<LocalDate> =
        clock.days { zone.get() }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), clock.todayIn(zone.get()))

    /** REC-8, REC-5, REC-10: städningen när fliken visas och vid varje ny dag (`tidyingUpEachDay`). */
    private val tidiedDays = today.tidyingUpEachDay(prescriptions, viewModelScope)

    private val loader = ListLoader(combine(prescriptions.observe(), medicines.observe(), tidiedDays) { p, m, day -> medicineItems(p, m, day) }, viewModelScope)
    val state: StateFlow<ListUiState<MedicineItem>> = loader.state

    /** MEDF-2: periodslut idag och i morgon (NOT-12), bland recepten i listan. */
    val endings: StateFlow<List<PeriodEnding>> = combine(state, today) { state, day ->
        val items = (state as? ListUiState.Content)?.items.orEmpty()
        items.filterIsInstance<MedicineItem.Recipe>().map { it.prescription }.endingSoon(day)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), emptyList())

    private val _failure = MutableStateFlow<Failure?>(null)
    val failure: StateFlow<Failure?> = _failure.asStateFlow()

    fun onEvent(event: MedicinesEvent) {
        when (event) {
            is MedicinesEvent.ActiveChanged -> write { prescriptions.setActive(event.prescription, event.active) }
            is MedicinesEvent.DeletePrescription -> write { prescriptions.delete(event.prescription.id) }
            is MedicinesEvent.FavoriteToggled -> write { medicines.setFavorite(event.medicine, !event.medicine.favorite) }
            MedicinesEvent.Retry -> loader.onEvent(ListEvent.Retry)
            MedicinesEvent.ErrorShown -> _failure.value = null
        }
    }

    private fun write(action: suspend () -> Result<Unit>) {
        viewModelScope.launch { action().failureOrNull()?.let { _failure.value = it } }
    }
}

@Composable
fun MedicinesRoute(
    account: AuthUser?,
    onAccount: () -> Unit,
    onOpenPrescription: (String?) -> Unit,
    onOpenPrn: (String?) -> Unit,
    onExtendPrescription: (String) -> Unit,
    viewModel: MedicinesViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val endings by viewModel.endings.collectAsStateWithLifecycle()
    val failure by viewModel.failure.collectAsStateWithLifecycle()
    val today by viewModel.today.collectAsStateWithLifecycle()
    MedicinesScreen(state, endings, today, failure, viewModel::onEvent, onOpenPrescription, onOpenPrn, onExtendPrescription) {
        AccountAvatar(account?.name ?: account?.email, onAccount, photoUrl = account?.photoUrl)
    }
}

/**
 * Fliken på `EntityListScreen` med stor topprad och avataren (NAV-9), som övriga flikar: periodsluten
 * som banner överst, recepten som postkort (NFR-15/16) med aktiv-reglaget som enda direktkontroll,
 * vid behov-medicinerna som rader med stjärna och pil (NFR-17, som Listor) och de avslutade recepten
 * hopfällda sist med antal (NFR-18). Lägg till = "Nytt recept" med "Ny vid behov-medicin" i pilens meny
 * (MEDF-4). [onOpenPrescription]/[onOpenPrn] öppnar formuläret (`null` = nytt); [onExtendPrescription]
 * öppnar ett avslutat recept förlängt och aktivt (MEDF-5).
 */
@Composable
fun MedicinesScreen(
    state: ListUiState<MedicineItem>,
    endings: List<PeriodEnding>,
    today: LocalDate,
    failure: Failure?,
    onEvent: (MedicinesEvent) -> Unit,
    onOpenPrescription: (String?) -> Unit,
    onOpenPrn: (String?) -> Unit,
    onExtendPrescription: (String) -> Unit,
    avatar: @Composable () -> Unit = {},
) {
    val newPrn = stringResource(R.string.medicines_new_prn)
    val items = (state as? ListUiState.Content)?.items.orEmpty()
    val activeCount = items.count { it is MedicineItem.Recipe && !it.ended && it.prescription.active }
    val prescriptionsGroup = ListGroup(stringResource(R.string.medicines_prescriptions), tone = Tone.Neutral, count = pluralStringResource(R.plurals.medicines_active_count, activeCount, activeCount), cards = true)
    val prnGroup = ListGroup(stringResource(R.string.medicines_as_needed), tone = Tone.Neutral)
    val endedGroup = ListGroup(stringResource(R.string.medicines_ended), tone = Tone.Neutral, collapsible = true, cards = true)
    EntityListScreen(
        title = stringResource(R.string.tab_medicines),
        state = state,
        empty = EmptyContent(
            R.drawable.ic_pill,
            stringResource(R.string.medicines_empty_title),
            stringResource(R.string.medicines_empty_message),
            examples = listOf(EmptyExample(newPrn) { onOpenPrn(null) }),
        ),
        add = AddAction(
            stringResource(R.string.medicines_new_prescription),
            { onOpenPrescription(null) },
            menu = listOf(AppMenuItem(newPrn, { onOpenPrn(null) }, R.drawable.ic_add)),
        ),
        key = { it.key },
        onRetry = { onEvent(MedicinesEvent.Retry) },
        group = { item ->
            when {
                item is MedicineItem.AsNeeded -> prnGroup
                item is MedicineItem.Recipe && item.ended -> endedGroup
                else -> prescriptionsGroup
            }
        },
        archive = ListArchive(failure = failure, showToggle = false) { if (it == ArchiveEvent.ErrorShown) onEvent(MedicinesEvent.ErrorShown) },
        actions = { avatar() },
        header = if (endings.isEmpty()) null else ({ EndingsBanner(endings, today) { onOpenPrescription(endings.first().prescriptionId) } }),
    ) { item ->
        when (item) {
            is MedicineItem.Recipe -> PrescriptionCard(item, today, onEvent, { onExtendPrescription(item.prescription.id) }) { onOpenPrescription(item.prescription.id) }
            is MedicineItem.AsNeeded -> ItemRow(
                medicineTitle(item.medicine.name, item.medicine.dose, item.medicine.unit),
                subtitle = prnLimits(item.medicine),
                onClick = { onOpenPrn(item.medicine.id) },
                navigates = true,
                trailing = { FavoriteStar(item.medicine.name, item.medicine.favorite, { onEvent(MedicinesEvent.FavoriteToggled(item.medicine)) }) },
            )
        }
    }
}

/** MEDF-2: periodsluten idag och i morgon – tryck öppnar det första receptet. */
@Composable
private fun EndingsBanner(endings: List<PeriodEnding>, today: LocalDate, onOpen: () -> Unit) {
    NoticeBanner(endings.map { endingText(it, today) }.joinToString(" "), R.drawable.ic_bell, onOpen)
}

@Composable
private fun endingText(ending: PeriodEnding, today: LocalDate): String {
    val isToday = ending.date == today
    return when (ending) {
        is PeriodEnding.PrescriptionEnds ->
            stringResource(if (isToday) R.string.medicines_ends_today else R.string.medicines_ends_tomorrow, ending.name)
        is PeriodEnding.BoostEnds ->
            stringResource(
                if (isToday) R.string.medicines_boost_ends_today else R.string.medicines_boost_ends_tomorrow,
                ending.name,
                doseText(ending.newDose, ending.unit),
            )
    }
}

/**
 * Receptets postkort (MEDF-1, REC-5, REC-12, REC-13): namn och dos, tidpunkter · upprepning · period
 * som undertext, aktiv-reglaget som enda direktkontroll (dubblerat i menyn), pills för dagens dos med
 * höjningen inom parentes, höjningens och periodens dagar och "Slutar idag/i morgon"; anteckningen med
 * ikonen och doshöjningarna under chevronen. Ett avslutat recept är nedtonat med "Avslutat {datum}"
 * och har inget reglage – menyns "Förläng och aktivera" ([onExtend]) öppnar det i receptformuläret (MEDF-5).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PrescriptionCard(item: MedicineItem.Recipe, today: LocalDate, onEvent: (MedicinesEvent) -> Unit, onExtend: () -> Unit, onOpen: () -> Unit) {
    val p = item.prescription
    val title = medicineTitle(p.name, p.dose, p.unit)
    val active = p.active && !item.ended
    val toggleLabel = stringResource(if (p.active) R.string.medicines_deactivate else R.string.medicines_activate)
    val pills = prescriptionPills(item, today)
    DagbokenEntryCard(
        title = title,
        onClick = onOpen,
        subtitle = prescriptionSubtitle(p),
        accent = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
        note = p.note.orEmpty(),
        expandedContent = if (p.boosts.any { it.start != null }) ({ BoostLines(p) }) else null,
        onEdit = onOpen,
        actions = if (item.ended) {
            listOf(AppMenuItem(stringResource(R.string.prescription_extend), onExtend, R.drawable.ic_refresh))
        } else {
            listOf(AppMenuItem(toggleLabel, { onEvent(MedicinesEvent.ActiveChanged(p, !p.active)) }, if (p.active) R.drawable.ic_toggle_off else R.drawable.ic_toggle_on))
        },
        delete = DeleteAction(
            stringResource(R.string.delete_named_title, p.name),
            stringResource(R.string.medicines_delete_prescription_message),
        ) { onEvent(MedicinesEvent.DeletePrescription(p)) },
        inactive = !active,
        toggle = if (item.ended) null else EntryToggle(p.active, { onEvent(MedicinesEvent.ActiveChanged(p, it)) }, stringResource(R.string.medicines_active_format, p.name)),
        below = if (pills.isEmpty()) {
            null
        } else {
            {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.s), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                    pills.forEach { (text, tone) -> InfoPill(text, tone = tone) }
                }
            }
        },
    )
}

/** Receptkortets pills i ordning: dagens dos med höjning, höjningens dagar, perioden och periodslutet – eller "Avslutat". */
@Composable
private fun prescriptionPills(item: MedicineItem.Recipe, today: LocalDate): List<Pair<String, Tone>> {
    val p = item.prescription
    val end = p.period.end
    if (item.ended) return listOfNotNull(end?.let { stringResource(R.string.medicines_pill_ended, DateFormat.short(it)) to Tone.Neutral })
    val pills = mutableListOf<Pair<String, Tone>>()
    p.boostFor(today)?.let { boost ->
        pills += stringResource(R.string.medicines_pill_today_boost, doseText(p.doseFor(today), p.unit), boost.dose.trim()) to Tone.Primary
        pills += stringResource(R.string.medicines_pill_boost, periodText(boost.start, p.boostEnd(boost))) to Tone.Neutral
    }
    if (end != null) pills += stringResource(R.string.medicines_pill_period, periodText(p.period.start, end)) to Tone.Neutral
    p.endingSoonDate(today)?.let { ends ->
        pills += stringResource(if (ends == today) R.string.medicines_pill_ends_today else R.string.medicines_pill_ends_tomorrow) to Tone.Warning
    }
    return pills
}

/** Doshöjningarna under chevronen (REC-9, REC-12): "29 sep – 12 okt: +25 mg (totalt 75 mg)". */
@Composable
private fun BoostLines(p: Prescription) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        GroupLabel(stringResource(R.string.medicines_boosts))
        p.boosts.filter { it.start != null }.sortedBy { it.start }.forEach { boost ->
            Text(
                // Totalen som i receptformuläret (totalWith) – ingen när dosen inte går att räkna.
                p.totalWith(boost)?.let { total ->
                    stringResource(R.string.medicines_boost_line, periodText(boost.start, p.boostEnd(boost)), doseText(boost.dose, p.unit), doseText(total, p.unit))
                } ?: stringResource(R.string.medicines_boost_line_no_total, periodText(boost.start, p.boostEnd(boost)), doseText(boost.dose, p.unit)),
                style = AppTypography.itemSubtitle,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
