package se.partee71.dagboken.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import se.partee71.dagboken.R
import se.partee71.dagboken.ui.theme.AppColors
import se.partee71.dagboken.ui.theme.AppShapes
import se.partee71.dagboken.ui.theme.AppTypography
import se.partee71.dagboken.ui.theme.Spacing

/**
 * Antal ± inom [range]. [label] beskriver vad som räknas ("doser per dag") och läses av
 * TalkBack på knapparna ("Minska doser per dag") och på värdet ("2 doser per dag"). [step] är
 * hur mycket ett tryck ändrar (t.ex. 5 för minuter i [DurationRow]).
 */
@Composable
fun QuantityStepper(
    value: Int,
    onValueChange: (Int) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    range: IntRange = 0..999,
    enabled: Boolean = true,
    step: Int = 1,
) {
    val colors = IconButtonDefaults.filledIconButtonColors(
        containerColor = AppColors.extended.card,
        contentColor = MaterialTheme.colorScheme.onSurface,
    )
    Row(
        modifier.background(AppColors.extended.rowTint, AppShapes.pill).padding(Spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        FilledIconButton(
            onClick = { onValueChange((value - step).coerceIn(range)) },
            modifier = Modifier.size(BUTTON),
            enabled = enabled && value > range.first,
            colors = colors,
        ) { Icon(painterResource(R.drawable.ic_remove), stringResource(R.string.decrease_format, label)) }
        Text(
            value.toString(),
            style = AppTypography.quantity,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(min = VALUE_WIDTH).semantics {
                contentDescription = "$value $label"
                liveRegion = LiveRegionMode.Polite
            },
        )
        FilledIconButton(
            onClick = { onValueChange((value + step).coerceIn(range)) },
            modifier = Modifier.size(BUTTON),
            enabled = enabled && value < range.last,
            colors = colors,
        ) { Icon(painterResource(R.drawable.ic_add), stringResource(R.string.increase_format, label)) }
    }
}

private val BUTTON = 44.dp
private val VALUE_WIDTH = 48.dp
