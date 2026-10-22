package se.partee71.dagboken.ui.common

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import se.partee71.dagboken.R
import se.partee71.dagboken.ui.components.DateTimeRow
import se.partee71.dagboken.ui.components.SCREEN_MARGIN
import se.partee71.ui.components.DeleteAction
import se.partee71.ui.components.EntityEditScreen

/**
 * Postformulärets ram – `EntityEditScreen` en gång för alla formulär på en [EntryForm] (aktivitet, händelse, dos,
 * episod, incheckning, vid behov-medicin; NFR-10–12): Spara, Försök igen och Radera som [EntryEditEvent], fältens
 * fel som text till [content] (`error(field)`, `null` = inget fel) och ett gemensamt fel överst när ett fel gäller ett
 * fält utanför [shownErrors] (`hasErrorOutside`). [delete] ger raderingens bekräftelse för den lagrade posten (eller
 * formulärets värde innan den lästs) och bekräftelsens åtgärd – bara för en sparad post; `null` = ingen radering.
 */
@Composable
fun <T> EntryEditScreen(
    form: EntryForm<T>,
    title: String,
    onClose: () -> Unit,
    shownErrors: Set<String>,
    delete: (@Composable (shown: T, onConfirm: () -> Unit) -> DeleteAction)? = null,
    content: @Composable ColumnScope.(error: (String) -> String?) -> Unit
) {
    val state = form.state
    val messages = state.errors.mapValues { stringResource(it.value) }
    val error: (String) -> String? = messages::get
    EntityEditScreen(
        title = title,
        state = state,
        events = form.events,
        onSave = { form.onEvent(EntryEditEvent.Save) },
        onClose = onClose,
        delete = if (form.isNew || delete == null) null else delete(form.stored ?: form.value) { form.onEvent(EntryEditEvent.Delete) },
        onRetry = { form.onEvent(EntryEditEvent.Retry) },
        formError = if (state.hasErrorOutside(shownErrors)) stringResource(R.string.form_not_savable) else null,
        horizontalMargin = SCREEN_MARGIN
    ) {
        content(error)
    }
}

/**
 * Postens dag och klockslag (DAT-2) – [DateTimeRow] för [form] med postens [date] och [time] (utan klockslag 00:00);
 * en post utan dag visar ingen rad. [withDate] och [withTime] ger posten med den valda dagen respektive tiden.
 */
@Composable
fun <T> EntryDateTimeRow(form: EntryForm<T>, date: LocalDate?, time: LocalTime?, withDate: T.(LocalDate) -> T, withTime: T.(LocalTime) -> T) {
    val day = date ?: return
    DateTimeRow(day, time ?: LocalTime(0, 0), { picked -> form.change { it.withDate(picked) } }, { picked -> form.change { it.withTime(picked) } })
}
