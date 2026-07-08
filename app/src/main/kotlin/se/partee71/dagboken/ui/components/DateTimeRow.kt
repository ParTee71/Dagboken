package se.partee71.dagboken.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import se.partee71.dagboken.R
import se.partee71.dagboken.ui.formatDisplayDate
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateTimeRow(
    datum: String,
    tid: String,
    onDatumChange: (String) -> Unit,
    onTidChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }

    ElevatedCard(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier              = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment     = Alignment.CenterVertically,
        ) {
            OutlinedButton(
                onClick  = { showDatePicker = true },
                modifier = Modifier.weight(1f),
            ) {
                Icon(Icons.Outlined.CalendarMonth, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.size(6.dp))
                Text(
                    text     = formatDisplayDate(datum),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style    = MaterialTheme.typography.labelLarge,
                )
            }
            OutlinedButton(
                onClick  = { showTimePicker = true },
                modifier = Modifier.width(108.dp),
            ) {
                Icon(Icons.Outlined.Schedule, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.size(6.dp))
                Text(tid, style = MaterialTheme.typography.labelLarge)
            }
        }
    }

    if (showDatePicker) {
        DatePickerModal(
            initialDatum = datum,
            onConfirm    = { onDatumChange(it); showDatePicker = false },
            onDismiss    = { showDatePicker = false },
        )
    }

    if (showTimePicker) {
        TimePickerModal(
            initialTid = tid,
            onConfirm  = { onTidChange(it); showTimePicker = false },
            onDismiss  = { showTimePicker = false },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DatePickerModal(
    initialDatum: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val state = rememberDatePickerState(
        initialSelectedDateMillis = runCatching {
            LocalDate.parse(initialDatum).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        }.getOrNull(),
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                state.selectedDateMillis?.let { millis ->
                    val date = Instant.ofEpochMilli(millis)
                        .atZone(ZoneOffset.UTC)
                        .toLocalDate()
                    onConfirm(date.format(DateTimeFormatter.ISO_LOCAL_DATE))
                }
            }) { Text(stringResource(R.string.ok)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    ) {
        DatePicker(state = state)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimePickerModal(
    initialTid: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val (h, m) = remember {
        runCatching {
            val p = initialTid.split(":")
            p[0].toInt() to p[1].toInt()
        }.getOrElse { 0 to 0 }
    }
    val state = rememberTimePickerState(initialHour = h, initialMinute = m, is24Hour = true)
    AlertDialog(
        onDismissRequest = onDismiss,
        title            = { Text(stringResource(R.string.settings_pick_time)) },
        text             = { TimePicker(state = state) },
        confirmButton    = {
            TextButton(onClick = { onConfirm("%02d:%02d".format(state.hour, state.minute)) }) {
                Text(stringResource(R.string.ok))
            }
        },
        dismissButton    = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}
