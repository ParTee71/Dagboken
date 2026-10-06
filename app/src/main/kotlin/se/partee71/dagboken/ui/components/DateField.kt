@file:OptIn(ExperimentalMaterial3Api::class)

package se.partee71.dagboken.ui.components

import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
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
import se.partee71.dagboken.ui.theme.Spacing

/**
 * Datumfält: visar datumet på svenska ("lör 19 dec 2026") och öppnar datumväljaren vid tryck.
 * TalkBack läser fältet som en knapp med etikett, datum och eventuellt fel. Utan datum visas
 * [emptyLabel] nedtonat i fältet – vad ett tomt datum betyder, t.ex. "Periodens slut" för en
 * doshöjning utan eget slut; utan [emptyLabel] är fältet tomt. Med [onClear] (och [emptyLabel]) får
 * ett satt datum en rensa-knapp efter fältet som tömmer det tillbaka till [emptyLabel] – TalkBack läser
 * "Till periodens slut"; utan datum hålls knappens plats tom, så att fältet inte byter bredd. [context]
 * läggs till fältets och knappens namn för TalkBack när samma fält finns flera gånger på skärmen
 * ("Startdatum, doshöjning 2", "Till periodens slut, doshöjning 2").
 */
@Composable
fun DateField(
    label: String,
    date: LocalDate?,
    onDateChange: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
    error: String? = null,
    emptyLabel: String? = null,
    onClear: (() -> Unit)? = null,
    context: String? = null,
) {
    val inContext: @Composable (String) -> String = { text -> context?.let { stringResource(R.string.field_in_context_format, text, it) } ?: text }
    var picking by rememberSaveable { mutableStateOf(false) }
    val field: @Composable (Modifier) -> Unit = { fieldModifier ->
        PickerField(
            label = label,
            shown = date?.let(DateFormat::display).orEmpty(),
            pickLabel = stringResource(R.string.pick_date),
            icon = R.drawable.ic_calendar,
            onPick = { picking = true },
            modifier = fieldModifier,
            error = error,
            emptyLabel = emptyLabel,
            contentLabel = context?.let { inContext(label) },
        )
    }
    if (onClear != null && emptyLabel != null) {
        Row(modifier, verticalAlignment = Alignment.Top) {
            field(Modifier.weight(1f))
            val clearModifier = Modifier.padding(top = Spacing.s)
            if (date != null) {
                AppIconButton(R.drawable.ic_close, inContext(stringResource(R.string.date_clear_format, emptyLabel.replaceFirstChar { it.lowercase() })), onClear, clearModifier)
            } else {
                // Platsen hålls utan datum, så att fältet inte byter bredd när ett datum väljs eller rensas.
                Spacer(clearModifier.size(TOUCH_TARGET))
            }
        }
    } else {
        field(modifier)
    }
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
