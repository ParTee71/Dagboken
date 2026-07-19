package se.partee71.dagboken.ui.diagram

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import se.partee71.dagboken.R
import java.util.Locale

/** "5" för heltal, annars en decimal — undviker "5.0" i en kompakt etikett. */
internal fun formatChartValue(value: Float): String =
    if (value == value.toInt().toFloat()) value.toInt().toString() else String.format(Locale.ROOT, "%.1f", value)

/**
 * Min/max-etikett som visas under **varje** diagram i appen (TRD-9) — oavsett hur
 * y-axelns rutnät råkar skala sig, ska det faktiska datats lägsta och högsta värde
 * alltid synas som text. Delad av Trenders diagram och Idag-diagrammen (regel 4).
 */
@Composable
fun MinMaxCaption(min: Float, max: Float, modifier: Modifier = Modifier) {
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(
            "${stringResource(R.string.diagram_stat_min)}: ${formatChartValue(min)}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            "${stringResource(R.string.diagram_stat_max)}: ${formatChartValue(max)}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
