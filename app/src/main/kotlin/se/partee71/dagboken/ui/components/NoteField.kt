package se.partee71.dagboken.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import se.partee71.dagboken.R
import se.partee71.dagboken.ui.theme.DagbokenAnimSpec

@Composable
fun NoteField(
    text: String,
    onTextChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = stringResource(R.string.note_placeholder),
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val hasText = text.isNotBlank()

    LaunchedEffect(text) { if (text.isBlank()) expanded = false }
    val chevronRotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = DagbokenAnimSpec.springNormal,
        label = "note_chevron",
    )

    DagbokenCard(modifier = modifier, contentPadding = PaddingValues(0.dp)) {
        Column(modifier = Modifier.padding(horizontal = 16.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded }
                    .padding(vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = if (hasText || expanded) stringResource(R.string.label_note) else placeholder,
                    style = MaterialTheme.typography.labelLarge,
                    color = if (hasText || expanded)
                        MaterialTheme.colorScheme.onSurfaceVariant
                    else
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
                    modifier = Modifier.weight(1f),
                )
                Icon(
                    imageVector = Icons.Default.ExpandMore,
                    contentDescription = null,
                    modifier = Modifier.rotate(chevronRotation),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            // Collapsed text preview — max 2 lines
            AnimatedVisibility(
                visible = !expanded && hasText,
                enter = expandVertically(animationSpec = DagbokenAnimSpec.springNormalSpec()),
                exit = shrinkVertically(animationSpec = DagbokenAnimSpec.springNormalSpec()),
            ) {
                Column {
                    HorizontalDivider()
                    Text(
                        text = text,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(vertical = 10.dp),
                    )
                }
            }

            // Expanded: full editable field
            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically(animationSpec = DagbokenAnimSpec.springNormalSpec()),
                exit = shrinkVertically(animationSpec = DagbokenAnimSpec.springNormalSpec()),
            ) {
                OutlinedTextField(
                    value = text,
                    onValueChange = onTextChange,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp),
                    minLines = 3,
                    placeholder = { Text(placeholder) },
                )
            }
        }
    }
}
