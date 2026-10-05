package se.partee71.dagboken.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SelectableChipColors
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import se.partee71.dagboken.R
import se.partee71.dagboken.ui.theme.AppShapes
import se.partee71.dagboken.ui.theme.AppTypography
import se.partee71.dagboken.ui.theme.IconSize

/** Valchip: bock och korallton när det är valt. Används ensamt eller i en [ChipRow]. */
@Composable
fun AppFilterChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    @DrawableRes icon: Int? = null,
) {
    val leading = if (selected) R.drawable.ic_check else icon
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label, style = AppTypography.pill) },
        modifier = modifier.heightIn(min = CHIP_HEIGHT),
        enabled = enabled,
        leadingIcon = leading?.let { { Icon(painterResource(it), contentDescription = null, modifier = Modifier.size(IconSize.tile)) } },
        shape = AppShapes.pill,
        colors = chipColors(),
    )
}

/** Samma färger för alla chips – även [PersonChip]. */
@Composable
internal fun chipColors(): SelectableChipColors = FilterChipDefaults.filterChipColors(
    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
    selectedLeadingIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
    labelColor = MaterialTheme.colorScheme.onSurface,
)

private val CHIP_HEIGHT = 36.dp
