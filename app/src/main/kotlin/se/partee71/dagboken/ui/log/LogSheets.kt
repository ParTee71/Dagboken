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
import se.partee71.dagboken.ui.components.LogChoice
import se.partee71.dagboken.ui.components.OccasionRow
import se.partee71.dagboken.ui.components.OccasionValue
import se.partee71.dagboken.ui.components.UpcomingScreen

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
 * Det plusknappen (och Dagbok) öppnar ovanpå flikarna (NAV-10, HIST-3): tillfällesväljaren och måendearket – samma
 * ark som på Idag ([ScreeningSheetView]).
 */
@Composable
fun LogSheets(viewModel: LogViewModel) {
    val picker by viewModel.picker.collectAsStateWithLifecycle()
    val screening by viewModel.screening.collectAsStateWithLifecycle()
    picker?.let { OccasionPickerSheet(it, viewModel::onEvent) }
    screening?.let { sheet ->
        // Symptomlistan följs bara medan arket är öppet.
        val symptomOptions by viewModel.symptomOptions.collectAsStateWithLifecycle()
        // Ett nytt ark får ett eget ark-tillstånd (steg, svep).
        key(sheet) { ScreeningSheetView(sheet, symptomOptions, viewModel::onEvent) }
    }
}

/** Dos och Sjukdom i plusknappens meny tills deras formulär finns (#271): en underskärm med "Snart här". */
@Composable
fun LogUpcomingScreen(choice: LogChoice, onBack: () -> Unit) {
    UpcomingScreen(stringResource(choice.label), choice.icon, stringResource(R.string.log_upcoming), onBack = onBack)
}
