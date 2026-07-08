package se.partee71.dagboken.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import se.partee71.dagboken.ui.theme.DagbokenAnimSpec

@Composable
fun DurationRow(
    hours: Int,
    minutes: Int,
    onHoursChange: (Int) -> Unit,
    onMinutesChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val hourItems   = remember { (0..23).map { it.toString() } }
    val minuteItems = remember { (0..59 step 10).map { it.toString() } }
    val minuteIndex = (minutes / 10).coerceIn(0, minuteItems.lastIndex)
    val hasValue    = hours > 0 || minutes > 0

    val currentValue = when {
        hours == 0 -> "$minutes min"
        minutes == 0 -> "$hours tim"
        else -> "$hours tim $minutes min"
    }

    var expanded by remember { mutableStateOf(false) }
    val rotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = DagbokenAnimSpec.springNormal,
        label = "duration_chevron",
    )

    DagbokenCard(modifier = modifier, contentPadding = PaddingValues(0.dp)) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text       = "Varaktighet",
                    style      = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Row(
                    verticalAlignment     = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    if (!expanded && hasValue) {
                        Text(
                            text  = currentValue,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                    Icon(
                        imageVector        = Icons.Default.ExpandMore,
                        contentDescription = if (expanded) "Dölj" else "Visa",
                        modifier           = Modifier.rotate(rotation),
                        tint               = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            AnimatedVisibility(
                visible = expanded,
                enter   = expandVertically(animationSpec = DagbokenAnimSpec.springNormalSpec()),
                exit    = shrinkVertically(animationSpec = DagbokenAnimSpec.springNormalSpec()),
            ) {
                Row(
                    modifier              = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment     = Alignment.CenterVertically,
                ) {
                    WheelPicker(
                        items          = hourItems,
                        selectedIndex  = hours.coerceIn(0, 23),
                        onIndexChanged = onHoursChange,
                        itemHeight     = 36.dp,
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text  = "tim",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.width(24.dp))
                    WheelPicker(
                        items          = minuteItems,
                        selectedIndex  = minuteIndex,
                        onIndexChanged = { onMinutesChange(it * 10) },
                        itemHeight     = 36.dp,
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text  = "min",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
