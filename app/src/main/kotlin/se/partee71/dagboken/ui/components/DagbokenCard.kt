package se.partee71.dagboken.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CardElevation
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * Appens enda kortbyggsten (regel 4). Alla ytor som behöver en upphöjd,
 * rundad kortyta ska använda denna i stället för en egen [ElevatedCard].
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DagbokenCard(
    modifier: Modifier = Modifier,
    title: String? = null,
    onClick: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
    enabled: Boolean = true,
    contentPadding: PaddingValues = PaddingValues(16.dp),
    accentColor: Color? = null,
    containerColor: Color? = null,
    elevation: CardElevation? = null,
    fillMaxWidth: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = MaterialTheme.shapes.large
    val colors = if (containerColor != null) {
        CardDefaults.elevatedCardColors(containerColor = containerColor)
    } else {
        CardDefaults.elevatedCardColors()
    }
    val cardElevation = elevation ?: CardDefaults.elevatedCardElevation()
    val sizedModifier = if (fillMaxWidth) modifier.fillMaxWidth() else modifier

    val titleSlot: @Composable ColumnScope.() -> Unit = {
        if (title != null) {
            Text(
                text       = title,
                style      = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color      = MaterialTheme.colorScheme.onSurface,
                modifier   = Modifier.padding(bottom = 12.dp),
            )
        }
    }

    val body: @Composable () -> Unit = {
        if (accentColor != null) {
            Row(modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
                Box(
                    modifier = Modifier
                        .width(4.dp)
                        .fillMaxHeight()
                        .background(accentColor),
                )
                Column(modifier = Modifier.weight(1f).padding(contentPadding)) {
                    titleSlot()
                    content()
                }
            }
        } else {
            Column(modifier = Modifier.padding(contentPadding)) {
                titleSlot()
                content()
            }
        }
    }

    when {
        onLongClick != null -> ElevatedCard(
            modifier = sizedModifier.combinedClickable(
                enabled     = enabled,
                onClick     = onClick ?: {},
                onLongClick = onLongClick,
            ),
            colors    = colors,
            elevation = cardElevation,
            shape     = shape,
        ) { body() }

        onClick != null -> ElevatedCard(
            onClick   = onClick,
            modifier  = sizedModifier,
            enabled   = enabled,
            shape     = shape,
            colors    = colors,
            elevation = cardElevation,
        ) { body() }

        else -> ElevatedCard(
            modifier  = sizedModifier,
            shape     = shape,
            colors    = colors,
            elevation = cardElevation,
        ) { body() }
    }
}
