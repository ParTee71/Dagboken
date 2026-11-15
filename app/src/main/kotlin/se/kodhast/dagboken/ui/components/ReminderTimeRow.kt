package se.kodhast.dagboken.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import kotlinx.datetime.LocalTime
import se.kodhast.dagboken.R
import se.kodhast.dagboken.ui.common.DateFormat
import se.kodhast.ui.components.ItemRow
import se.kodhast.ui.components.SwitchRow
import se.kodhast.ui.components.TimePickerPopup
import se.kodhast.ui.components.inactive

/**
 * En påminnelse (NOT-18): namn och klockslag. Tryck på raden öppnar tidsväljaren (radens primära
 * åtgärd, NFR-17); med [onEnabledChange] har raden ett på/av-reglage som egen kontroll – samma
 * mönster som [SwitchRow] med `onClick`. Utan reglage är det en [ItemRow]. En avslagen påminnelse – eller
 * en vars hela grupp är avslagen ([inactive], t.ex. medicintiderna när medicinpåminnelserna är av) –
 * tonas ned som en pausad rad – texten, inte reglaget, som ska synas fullt för att slås på igen; den
 * går ändå att ändra.
 */
@Composable
fun ReminderTimeRow(
    label: String,
    time: LocalTime,
    onTimeChange: (LocalTime) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onEnabledChange: ((Boolean) -> Unit)? = null,
    inactive: Boolean = false
) {
    var picking by rememberSaveable { mutableStateOf(false) }
    val pickLabel = stringResource(R.string.pick_time)
    val shown = DateFormat.time(time)
    if (onEnabledChange == null) {
        ItemRow(label, modifier, subtitle = shown, onClick = { picking = true }, inactive = inactive)
    } else {
        SwitchRow(label, enabled, onEnabledChange, modifier, subtitle = shown, onClick = { picking = true }, onClickLabel = pickLabel, inactive = inactive || !enabled)
    }
    if (picking) {
        TimePickerPopup(time, label, onPick = {
            onTimeChange(it)
            picking = false
        }, onDismiss = { picking = false })
    }
}
