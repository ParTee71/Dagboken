package se.partee71.dagboken.ui.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import se.partee71.dagboken.R

/**
 * En påminnelserad: namn, klockslag som öppnar en tidväljare och — när [onToggle] anges —
 * ett på/av-reglage. Delad av alla påminnelser under Hantera → Notifikationer
 * (medicintidpunkter, screeningtillfällen och periodpåminnelsen) så de ser likadana ut
 * och beter sig likadant.
 *
 * @param time klockslag på formen `HH:mm`.
 * @param enabled false gråar ut tidväljaren (raden är avstängd), reglaget går ändå att slå på.
 * @param defaultHour används när [time] inte går att tolka.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReminderTimeRow(
    label: String,
    time: String,
    onTimeSelected: (hour: Int, minute: Int) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onToggle: (() -> Unit)? = null,
    defaultHour: Int = 8,
) {
    val parts  = time.split(":")
    val hour   = parts.getOrNull(0)?.toIntOrNull() ?: defaultHour
    val minute = parts.getOrNull(1)?.toIntOrNull() ?: 0
    var showPicker by remember { mutableStateOf(false) }

    Row(
        modifier          = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        TextButton(
            onClick = { showPicker = true },
            enabled = enabled,
        ) {
            Text(
                text       = time,
                style      = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.width(4.dp))
            Icon(
                Icons.Default.Alarm,
                contentDescription = stringResource(R.string.settings_pick_time),
                modifier = Modifier.size(18.dp),
            )
        }
        if (onToggle != null) {
            Switch(checked = enabled, onCheckedChange = { onToggle() })
        }
    }

    if (showPicker) {
        val state = rememberTimePickerState(initialHour = hour, initialMinute = minute, is24Hour = true)
        AlertDialog(
            onDismissRequest = { showPicker = false },
            title            = { Text(label) },
            text             = { TimePicker(state = state) },
            confirmButton    = {
                TextButton(onClick = {
                    onTimeSelected(state.hour, state.minute)
                    showPicker = false
                }) { Text(stringResource(R.string.ok)) }
            },
            dismissButton = {
                TextButton(onClick = { showPicker = false }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }
}
