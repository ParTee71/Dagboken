package se.partee71.dagboken.ui.diagram

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import se.partee71.dagboken.R
import se.partee71.dagboken.ui.components.AppMenuItem
import se.partee71.dagboken.ui.components.AppMenuPopup
import se.partee71.dagboken.ui.components.InfoPill
import se.partee71.dagboken.ui.theme.Tone

/**
 * Kompakt rullgardin i ett diagramkorts titelrad (TRD-3, TRD-12): en klickbar `InfoPill` med valt värde
 * och en pil, som öppnar samma meny som `AppMenu` med [items]. Platssnål – mindre än `AppButton` – men
 * tryckytan är minst 48 dp (NFR-14). Läses som en rullgardin med sitt värde; markera valt alternativ i
 * [items] med en ikon (t.ex. `R.drawable.ic_check`).
 */
@Composable
fun CompactDropdownButton(label: String, items: List<AppMenuItem>, modifier: Modifier = Modifier) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier) {
        InfoPill(
            label,
            tone = Tone.Neutral,
            trailingIcon = R.drawable.ic_expand_more,
            role = Role.DropdownList,
            onClickLabel = stringResource(R.string.chart_choose),
            onClick = { expanded = true },
        )
        AppMenuPopup(items, expanded, onDismiss = { expanded = false })
    }
}
