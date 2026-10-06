package se.partee71.dagboken.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Switch
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics

/**
 * Inställningsrad med växel: hela raden är tryckytan, TalkBack läser den som en växel. Med
 * [onClick] är växeln en egen kontroll (läses med titeln) och resten av raden öppnar detaljer –
 * som en påminnelse i Inställningar ([onClickLabel] läses upp), samma mönster som [CheckRow].
 * [inactive] tonar ned titel och undertext men inte växeln – den ska gå att se och slå på igen.
 */
@Composable
fun SwitchRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    onClick: (() -> Unit)? = null,
    onClickLabel: String? = null,
    inactive: Boolean = false,
) {
    val toggle = Modifier.toggleable(value = checked, role = Role.Switch, onValueChange = onCheckedChange)
    ItemRowLayout(
        title = title,
        modifier = modifier,
        interaction = if (onClick == null) toggle else Modifier.clickable(onClickLabel = onClickLabel, onClick = onClick),
        subtitle = subtitle,
        textInactive = inactive,
        trailing = {
            if (onClick == null) {
                SwitchControl(checked, onCheckedChange = null)
            } else {
                SwitchControl(checked, onCheckedChange, label = title)
            }
        },
    )
}

/**
 * Själva växeln – en gång för [SwitchRow] och postkortets reglage (`DagbokenEntryCard(toggle)`). Utan
 * [onCheckedChange] är den en ren indikator (raden växlar och läser upp den); med är den en egen
 * kontroll som TalkBack läser som [label].
 */
@Composable
internal fun SwitchControl(checked: Boolean, onCheckedChange: ((Boolean) -> Unit)?, label: String? = null) {
    Switch(checked = checked, onCheckedChange = onCheckedChange, modifier = if (label == null) Modifier else Modifier.semantics { contentDescription = label })
}
