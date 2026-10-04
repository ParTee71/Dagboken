@file:OptIn(ExperimentalMaterial3Api::class)

package se.partee71.dagboken.ui.components

import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import kotlinx.datetime.LocalDate
import se.partee71.dagboken.R
import se.partee71.dagboken.ui.common.DateFormat
import se.partee71.dagboken.ui.theme.AppColors

/**
 * Datumfält: visar datumet på svenska ("lör 19 dec 2026") och öppnar datumväljaren vid tryck.
 * TalkBack läser fältet som en knapp med etikett, datum och eventuellt fel.
 */
@Composable
fun DateField(
    label: String,
    date: LocalDate?,
    onDateChange: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
    error: String? = null,
) {
    var picking by rememberSaveable { mutableStateOf(false) }
    PickerField(
        label = label,
        shown = date?.let(DateFormat::display).orEmpty(),
        pickLabel = stringResource(R.string.pick_date),
        icon = R.drawable.ic_calendar,
        onPick = { picking = true },
        modifier = modifier,
        error = error,
    )
    if (picking) {
        val state = rememberDatePickerState(initialSelectedDateMillis = date?.let(DateFormat::toEpochMillis))
        DatePickerDialog(
            onDismissRequest = { picking = false },
            confirmButton = {
                AppButton(stringResource(R.string.ok), onClick = {
                    state.selectedDateMillis?.let { onDateChange(DateFormat.fromEpochMillis(it)) }
                    picking = false
                }, variant = ButtonVariant.Text)
            },
            dismissButton = { AppButton(stringResource(R.string.cancel), { picking = false }, variant = ButtonVariant.Text) },
            colors = DatePickerDefaults.colors(containerColor = AppColors.extended.card),
        ) {
            DatePicker(state)
        }
    }
}
