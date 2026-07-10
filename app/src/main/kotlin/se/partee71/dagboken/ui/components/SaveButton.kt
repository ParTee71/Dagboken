package se.partee71.dagboken.ui.components

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import se.partee71.dagboken.R

/**
 * Appens enda spara-knapp (regel 4). enabled ska spegla dirty-state (osparade,
 * giltiga ändringar) — inte bara fältvalidering — så knappen är avstängd tills
 * det faktiskt finns något nytt att spara.
 */
@Composable
fun SaveButton(
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    label: String = stringResource(R.string.save),
    fillMaxWidth: Boolean = true,
) {
    FilledTonalButton(
        onClick  = onClick,
        enabled  = enabled,
        modifier = if (fillMaxWidth) modifier.fillMaxWidth() else modifier,
    ) {
        Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.size(8.dp))
        Text(label)
    }
}
