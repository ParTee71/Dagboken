package se.partee71.dagboken.ui.log

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import se.partee71.dagboken.R
import se.partee71.dagboken.core.engine.OccasionState
import se.partee71.dagboken.core.engine.latest
import se.partee71.dagboken.ui.common.DateFormat
import se.partee71.dagboken.ui.common.label
import se.partee71.dagboken.ui.components.AppBottomSheet
import se.partee71.dagboken.ui.common.medicineTitle
import se.partee71.dagboken.ui.common.prnLimits
import se.partee71.dagboken.ui.common.title
import se.partee71.dagboken.ui.components.AppDivider
import se.partee71.dagboken.ui.components.ItemRow
import se.partee71.dagboken.ui.components.OccasionRow
import se.partee71.dagboken.ui.components.OccasionValue

/**
 * Ett måendetillfälle som [OccasionRow] – en gång för Idags Mående-kort (HEM-4, HEM-5) och plusknappens
 * tillfällesväljare (HEM-8b): namnet, status, loggens tid (eller tillfällets klockslag) och den senaste loggens
 * energi och stress som chips. [onLog] är "Logga nu" och [onClick] hela raden.
 */
@Composable
fun OccasionStateRow(state: OccasionState, onLog: () -> Unit, onClick: () -> Unit) {
    val latest = state.latest
    OccasionRow(
        title = stringResource(state.occasion.label()),
        status = state.status,
        onLog = onLog,
        time = (if (latest != null) latest.time else state.time)?.let(DateFormat::time),
        values = latest?.let {
            listOf(OccasionValue(stringResource(R.string.energy), it.energy), OccasionValue(stringResource(R.string.stress), it.stress, higherIsBetter = false))
        }.orEmpty(),
        onClick = onClick,
    )
}

/**
 * Plusknappens tillfällesväljare (HEM-8b): ett ark med en rad per tillfälle och dess status, som på Idag – och dagen
 * i rubriken när det inte är idag. Hela raden (och "Logga nu") öppnar måendearket med en ny logg för tillfället,
 * också när det redan är loggat (ett extra tillfälle går att logga).
 */
@Composable
fun OccasionPickerSheet(picker: OccasionPicker, onEvent: (LogEvent) -> Unit) {
    val name = stringResource(R.string.log_mood_pick_title)
    val title = if (picker.isToday) name else stringResource(R.string.today_screening_title_day_format, name, DateFormat.display(picker.date))
    AppBottomSheet(title, onDismiss = { onEvent(LogEvent.ClosePicker) }) {
        picker.occasions.forEach { state ->
            key(state.occasion) {
                val log = { onEvent(LogEvent.LogOccasion(state)) }
                OccasionStateRow(state, onLog = log, onClick = log)
            }
        }
    }
}

/**
 * Plusknappens dosval och länken "Logga en dos i efterhand" i Mediciner (NAV-10, MEDF-6, MED-16): en rad per vid
 * behov-medicin – namn och dos, kylperiod och dagsgräns som i Mediciner – som öppnar den i efterhand, och sist
 * "Engångsdos" (MED-11). Raderna är arkets `ItemRow`, som i plusknappens meny.
 */
@Composable
fun DosePickerSheet(picker: DosePicker, onClose: () -> Unit, onOpen: (LogTarget) -> Unit) {
    AppBottomSheet(stringResource(R.string.dose_pick_title), onDismiss = onClose) {
        picker.medicines.forEach { medicine ->
            key(medicine.id) {
                ItemRow(
                    medicineTitle(medicine.displayName, medicine.dose, medicine.unit),
                    subtitle = prnLimits(medicine),
                    icon = R.drawable.ic_pill,
                    onClick = { onOpen(LogTarget.AsNeeded(medicine.id, picker.date)) },
                    navigates = true,
                )
            }
        }
        if (picker.medicines.isNotEmpty()) AppDivider()
        ItemRow(
            stringResource(R.string.dose_one_off),
            subtitle = stringResource(R.string.dose_one_off_help),
            icon = R.drawable.ic_add,
            onClick = { onOpen(LogTarget.OneOffDose(picker.date)) },
            navigates = true,
        )
    }
}

/**
 * Plusknappens sjukdomsval (NAV-10, SJ-1, SJ-2): "Checka in på Förkylning" med dag N när en episod pågår, och "Ny
 * sjukdomsepisod".
 */
@Composable
fun IllnessPickerSheet(picker: IllnessPicker, onClose: () -> Unit, onOpen: (LogTarget) -> Unit) {
    AppBottomSheet(stringResource(R.string.log_illness), onDismiss = onClose) {
        picker.ongoing?.let { episode ->
            ItemRow(
                stringResource(R.string.illness_checkin_on_format, episode.title()),
                subtitle = picker.day?.let { stringResource(R.string.today_illness_day_format, it) },
                icon = R.drawable.ic_thermometer,
                onClick = { onOpen(LogTarget.Checkin(episode.id, picker.date)) },
                navigates = true,
            )
        }
        ItemRow(
            stringResource(R.string.illness_new),
            icon = R.drawable.ic_add,
            onClick = { onOpen(LogTarget.NewEpisode(picker.date)) },
            navigates = true,
        )
    }
}

/**
 * Det plusknappen (och Dagbok och Mediciner) öppnar ovanpå flikarna (NAV-10, HIST-3, MEDF-6): tillfällesväljaren
 * och måendearket – samma ark som på Idag ([ScreeningSheetView]) – och dos- och sjukdomsvalen, vars val stänger
 * arket och öppnar formuläret med [onOpen].
 */
@Composable
fun LogSheets(viewModel: LogViewModel, onOpen: (LogTarget) -> Unit = {}) {
    val picker by viewModel.picker.collectAsStateWithLifecycle()
    val screening by viewModel.screening.collectAsStateWithLifecycle()
    val dosePicker by viewModel.dosePicker.collectAsStateWithLifecycle()
    val illnessPicker by viewModel.illnessPicker.collectAsStateWithLifecycle()
    picker?.let { OccasionPickerSheet(it, viewModel::onEvent) }
    screening?.let { sheet ->
        // Symptomlistan följs bara medan arket är öppet.
        val symptomOptions by viewModel.symptomOptions.collectAsStateWithLifecycle()
        // Ett nytt ark får ett eget ark-tillstånd (steg, svep).
        key(sheet) { ScreeningSheetView(sheet, symptomOptions, viewModel::onEvent) }
    }
    val close = { viewModel.onEvent(LogEvent.ClosePick) }
    val open = { target: LogTarget ->
        close()
        onOpen(target)
    }
    dosePicker?.let { DosePickerSheet(it, close, open) }
    illnessPicker?.let { IllnessPickerSheet(it, close, open) }
}

