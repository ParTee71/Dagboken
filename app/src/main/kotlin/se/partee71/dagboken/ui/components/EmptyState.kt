package se.partee71.dagboken.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import se.partee71.dagboken.ui.theme.AppShapes
import se.partee71.dagboken.ui.theme.AppTypography
import se.partee71.dagboken.ui.theme.IconSize
import se.partee71.dagboken.ui.theme.Spacing

/**
 * Tomt tillstånd (skill shared-ui-components): ikon, rubrik, en mening och en knapp som löser
 * det. [fullScreen] fyller skärmen med budskapet i mitten och knappen längst ned – för skärmar
 * som bara består av budskapet (inloggning, "Uppdatera appen"); annars står knappen under texten.
 * [isError] tonar ikonrutan som ett fel (listans feltillstånd i `EntityListScreen`). [compact] är
 * samma budskap i mindre format för en yta inuti ett kort – diagrammens tomma läge ("För lite data än").
 */
@Composable
fun EmptyState(
    @DrawableRes icon: Int,
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    note: String? = null,
    fullScreen: Boolean = false,
    isError: Boolean = false,
    compact: Boolean = false,
    action: (@Composable () -> Unit)? = null,
) {
    val footer: @Composable ColumnScope.() -> Unit = {
        action?.let { Box(Modifier.fillMaxWidth()) { it() } }
        note?.let {
            Text(
                it,
                style = AppTypography.itemSubtitle,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
    if (fullScreen) {
        Column(modifier.fillMaxSize().padding(horizontal = Spacing.xl, vertical = Spacing.xxl)) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) { Message(icon, title, message, isError, compact) }
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.m), content = footer)
        }
    } else {
        val gap = if (compact) Spacing.l else Spacing.xl
        Column(
            modifier.fillMaxWidth().padding(gap),
            verticalArrangement = Arrangement.spacedBy(gap),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Message(icon, title, message, isError, compact)
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.m), content = footer)
        }
    }
}

@Composable
private fun Message(@DrawableRes icon: Int, title: String, message: String, isError: Boolean, compact: Boolean) {
    val tile = if (isError) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.primaryContainer
    val onTile = if (isError) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onPrimaryContainer
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(if (compact) Spacing.m else Spacing.xl),
    ) {
        Box(
            Modifier.size(if (compact) COMPACT_ICON_TILE else ICON_TILE).background(tile, if (compact) AppShapes.smallTile else AppShapes.iconTile),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painterResource(icon),
                contentDescription = null,
                tint = onTile,
                modifier = Modifier.size(if (compact) IconSize.control else IconSize.hero),
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
            Text(
                title,
                style = if (compact) AppTypography.sectionTitle else AppTypography.headline,
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center,
                modifier = Modifier.semantics { heading() },
            )
            Text(
                message,
                style = AppTypography.body,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

private val ICON_TILE = 120.dp
private val COMPACT_ICON_TILE = 48.dp
