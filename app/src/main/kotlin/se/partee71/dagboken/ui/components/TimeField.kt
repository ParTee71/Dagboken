@file:OptIn(ExperimentalMaterial3Api::class)

package se.partee71.dagboken.ui.components

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TimePickerDialog
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import kotlinx.datetime.LocalTime
import se.partee71.dagboken.R
import se.partee71.dagboken.ui.common.DateFormat
import se.partee71.dagboken.ui.theme.AppColors
import se.partee71.dagboken.ui.theme.AppTypography

/**
 * Tidsfält: visar tiden som "07:00" och öppnar tidsväljaren (24 timmar) vid tryck – samma
 * beteende som [DateField]. TalkBack läser fältet som en knapp med etikett och tid.
 */
@Composable
fun TimeField(
    label: String,
    time: LocalTime,
    onTimeChange: (LocalTime) -> Unit,
    modifier: Modifier = Modifier,
    error: String? = null,
    helper: String? = null,
) {
    var picking by rememberSaveable { mutableStateOf(false) }
    val pickLabel = stringResource(R.string.pick_time)
    PickerField(
        label = label,
        shown = DateFormat.time(time),
        pickLabel = pickLabel,
        icon = R.drawable.ic_clock,
        onPick = { picking = true },
        modifier = modifier,
        error = error,
        helper = helper,
    )
    if (picking) {
        TimePickerPopup(time, pickLabel, onPick = { onTimeChange(it); picking = false }, onDismiss = { picking = false })
    }
}

/** Tidsväljaren (24 timmar) med [title], OK och Avbryt – delas av [TimeField] och [ReminderTimeRow]. */
@Composable
internal fun TimePickerPopup(time: LocalTime, title: String, onPick: (LocalTime) -> Unit, onDismiss: () -> Unit) {
    val state = rememberTimePickerState(initialHour = time.hour, initialMinute = time.minute, is24Hour = true)
    TimePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = { AppButton(stringResource(R.string.ok), onClick = { onPick(LocalTime(state.hour, state.minute)) }, variant = ButtonVariant.Text) },
        title = { Text(title, style = AppTypography.sectionTitle) },
        dismissButton = { AppButton(stringResource(R.string.cancel), onDismiss, variant = ButtonVariant.Text) },
        containerColor = AppColors.extended.card,
    ) {
        TimePicker(state)
    }
}
