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
) {
    val toggle = Modifier.toggleable(value = checked, role = Role.Switch, onValueChange = onCheckedChange)
    ItemRowLayout(
        title = title,
        modifier = modifier,
        interaction = if (onClick == null) toggle else Modifier.clickable(onClickLabel = onClickLabel, onClick = onClick),
        subtitle = subtitle,
        trailing = {
            if (onClick == null) {
                Switch(checked = checked, onCheckedChange = null)
            } else {
                Switch(checked = checked, onCheckedChange = onCheckedChange, modifier = Modifier.semantics { contentDescription = title })
            }
        },
    )
}
