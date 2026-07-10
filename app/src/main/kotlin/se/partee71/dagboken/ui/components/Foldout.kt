package se.partee71.dagboken.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.unit.dp
import se.partee71.dagboken.ui.theme.DagbokenAnimSpec

/** Collapsible section. Mirrors Foldout from src/components/UI.jsx. */
@Composable
fun Foldout(
    title: String,
    expanded: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val rotation by animateFloatAsState(
        targetValue  = if (expanded) 180f else 0f,
        animationSpec = DagbokenAnimSpec.springNormal,
        label         = "foldout_chevron",
    )

    Column(modifier = modifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onToggle)
                .padding(vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            Icon(
                imageVector = Icons.Default.ExpandMore,
                contentDescription = if (expanded) "Dölj" else "Visa",
                modifier = Modifier.rotate(rotation),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically(animationSpec = DagbokenAnimSpec.springNormalSpec()),
            exit = shrinkVertically(animationSpec = DagbokenAnimSpec.springNormalSpec()),
        ) {
            content()
        }
    }
}
