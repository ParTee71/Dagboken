package se.partee71.dagboken.ui.samsung

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FitnessCenter
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun SamsungHealthPlaceholderScreen() {
    val cs = MaterialTheme.colorScheme

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier = Modifier
                .size(100.dp)
                .background(
                    Brush.radialGradient(
                        colors = listOf(cs.primaryContainer, cs.secondaryContainer),
                    ),
                    CircleShape,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector        = Icons.Outlined.FitnessCenter,
                contentDescription = null,
                modifier           = Modifier.size(52.dp),
                tint               = cs.primary,
            )
        }

        Spacer(Modifier.height(24.dp))

        Surface(
            color = cs.tertiaryContainer,
            shape = MaterialTheme.shapes.small,
        ) {
            Text(
                text     = "Kommer snart",
                style    = MaterialTheme.typography.labelMedium,
                color    = cs.onTertiaryContainer,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
            )
        }

        Spacer(Modifier.height(16.dp))

        Text(
            text       = "Samsung Health",
            style      = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
        )

        Spacer(Modifier.height(8.dp))

        Text(
            text      = "Synkronisera din hälsodata från Samsung Health för en komplett bild av ditt välmående.",
            style     = MaterialTheme.typography.bodyMedium,
            color     = cs.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )

        Spacer(Modifier.height(32.dp))

        Button(
            onClick  = {},
            enabled  = false,
            modifier = Modifier.alpha(0.4f),
        ) {
            Text("Anslut Samsung Health")
        }
    }
}
