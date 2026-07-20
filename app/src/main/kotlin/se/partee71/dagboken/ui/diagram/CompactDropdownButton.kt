package se.partee71.dagboken.ui.diagram

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Kompakt dropdown-triggerknapp (regel 4, #149) — delas av Trenders period- och
 * serieväljare (sex periodväljare + tre serieväljare) i stället för att varje väljare
 * bygger sin egen [OutlinedButton]-stil. Mindre `contentPadding`/ikon/typografi än
 * standardknappen, så flera väljare får plats utan att tränga ut övrigt korttext.
 */
@Composable
fun CompactDropdownButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    OutlinedButton(
        onClick        = onClick,
        modifier       = modifier,
        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
    ) {
        Text(label, maxLines = 1, style = MaterialTheme.typography.labelSmall)
        Spacer(Modifier.width(2.dp))
        Icon(Icons.Default.ArrowDropDown, contentDescription = null, modifier = Modifier.size(14.dp))
    }
}
