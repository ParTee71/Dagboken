package se.partee71.dagboken.ui.components

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import se.partee71.dagboken.ui.theme.Spacing

/** Avdelare mellan rader i ett kort – temats linjefärg och tjocklek. */
@Composable
fun AppDivider(modifier: Modifier = Modifier) {
    HorizontalDivider(
        modifier.padding(horizontal = Spacing.m),
        thickness = 1.dp,
        color = MaterialTheme.colorScheme.outlineVariant,
    )
}
