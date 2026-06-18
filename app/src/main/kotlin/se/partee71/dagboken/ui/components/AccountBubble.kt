package se.partee71.dagboken.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import se.partee71.dagboken.ui.theme.Emerald400

@Composable
fun AccountBubble(
    email: String?,
    photoUrl: String?,
    displayName: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val cs = MaterialTheme.colorScheme
    Box(
        modifier = modifier
            .padding(start = 8.dp)
            .size(40.dp),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .border(1.5.dp, cs.outlineVariant, CircleShape)
                .background(cs.surfaceVariant)
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            if (email != null && photoUrl != null) {
                AsyncImage(
                    model              = photoUrl,
                    contentDescription = displayName ?: "Profil",
                    modifier           = Modifier
                        .size(36.dp)
                        .clip(CircleShape),
                    contentScale = ContentScale.Crop,
                )
            } else {
                Icon(
                    imageVector        = Icons.Default.AccountCircle,
                    contentDescription = "Logga in",
                    modifier           = Modifier.size(20.dp),
                    tint               = cs.onSurfaceVariant.copy(alpha = 0.6f),
                )
            }
        }

        if (email != null) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .size(10.dp)
                    .background(cs.surface, CircleShape)
                    .padding(2.dp)
                    .background(Emerald400, CircleShape),
            )
        }
    }
}
