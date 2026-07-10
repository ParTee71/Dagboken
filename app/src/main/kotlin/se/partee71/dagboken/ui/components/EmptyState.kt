package se.partee71.dagboken.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/**
 * Appens enda tomlägeskomponent (regel 4). Alla listor/skärmar utan data ska
 * använda denna i stället för en egen ikon+text-kolumn.
 */
@Composable
fun EmptyState(
    icon: ImageVector,
    title: String,
    body: String? = null,
    modifier: Modifier = Modifier.fillMaxSize(),
    action: (@Composable () -> Unit)? = null,
) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector        = icon,
                contentDescription = null,
                modifier           = Modifier.size(56.dp),
                tint               = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text      = title,
                style     = MaterialTheme.typography.titleMedium,
                color     = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            if (body != null) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text      = body,
                    style     = MaterialTheme.typography.bodyMedium,
                    color     = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    textAlign = TextAlign.Center,
                )
            }
            if (action != null) {
                Spacer(Modifier.height(16.dp))
                action()
            }
        }
    }
}
