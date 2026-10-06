package se.partee71.dagboken.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import se.partee71.dagboken.R
import se.partee71.dagboken.ui.theme.AppColors
import se.partee71.dagboken.ui.theme.AppShapes
import se.partee71.dagboken.ui.theme.AppTypography
import se.partee71.dagboken.ui.theme.Spacing
import se.partee71.dagboken.ui.theme.Tone

/**
 * Ett meddelande överst på en skärm som leder vidare – t.ex. periodsluten i Mediciner (MEDF-2): ett
 * [AppCard] i [tone] med [icon], [text] och en pil. Hela kortet är en knapp som läses med
 * [onClickLabel] ("Öppna") och öppnar det meddelandet gäller ([onClick]); ikonen och pilen är rena
 * indikatorer.
 */
@Composable
fun NoticeBanner(
    text: String,
    @DrawableRes icon: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tone: Tone = Tone.Warning,
    onClickLabel: String = stringResource(R.string.open),
) {
    val colors = AppColors.tone(tone)
    AppCard(modifier.clip(AppShapes.card).clickable(onClickLabel = onClickLabel, role = Role.Button, onClick = onClick), tone = tone) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.m)) {
            Icon(painterResource(icon), contentDescription = null, tint = colors.content)
            Text(text, Modifier.weight(1f), style = AppTypography.itemTitle, color = colors.content)
            Icon(painterResource(R.drawable.ic_chevron_right), contentDescription = null, tint = colors.content)
        }
    }
}
