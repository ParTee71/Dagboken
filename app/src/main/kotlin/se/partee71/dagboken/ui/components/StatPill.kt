package se.partee71.dagboken.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import se.partee71.dagboken.ui.theme.AppColors
import se.partee71.dagboken.ui.theme.AppShapes
import se.partee71.dagboken.ui.theme.AppTypography
import se.partee71.dagboken.ui.theme.Spacing
import se.partee71.dagboken.ui.theme.Tone

/**
 * Mätvärde (HLS-6): ikon, värde ("7 842", "—" när datapunkten saknas) och etikett ("Steg") i en tonad
 * yta ([tone]). Läses som en enhet. Med [onClick] är hela ytan en knapp med minst 48 dp och
 * [onClickLabel] beskriver åtgärden ("Begär åtkomst"); utan är den ren avläsning.
 */
@Composable
fun StatPill(
    @DrawableRes icon: Int,
    value: String,
    label: String,
    modifier: Modifier = Modifier,
    tone: Tone = Tone.Neutral,
    onClick: (() -> Unit)? = null,
    onClickLabel: String? = null,
) {
    val colors = AppColors.tone(tone)
    val interaction = if (onClick == null) {
        Modifier.semantics(mergeDescendants = true) {}
    } else {
        Modifier.heightIn(min = TOUCH_TARGET).clickable(onClickLabel = onClickLabel, role = Role.Button, onClick = onClick)
    }
    Row(
        modifier.clip(AppShapes.row).background(colors.container).then(interaction).padding(horizontal = Spacing.l, vertical = Spacing.m),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.m),
    ) {
        Icon(painterResource(icon), contentDescription = null, tint = colors.content, modifier = Modifier.size(ICON))
        Column {
            Text(value, style = AppTypography.quantity, color = colors.content, maxLines = 1)
            Text(label, style = AppTypography.itemSubtitle, color = colors.content, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

private val ICON = 22.dp
