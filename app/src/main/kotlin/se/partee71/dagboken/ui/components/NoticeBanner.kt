package se.partee71.dagboken.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
 * indikatorer. [detail] är en förklarande rad under [text]. Utan [onClick] (`null`) är kortet bara ett
 * meddelande – ingen pil, ingen knapp – för ett läge som inget i appen kan ändra ("Health Connect kopplad",
 * TRD-20). Med [action] står åtgärden som en primär [AppButton] under texten i stället för pilen ("Ge åtkomst",
 * "Installera", HLS-4) – då är knappen åtgärden och kortet i sig ingen knapp. [action] och [onClick] hör ihop, som i
 * `StatPill`: en åtgärd kräver [onClick].
 */
@Composable
fun NoticeBanner(
    text: String,
    @DrawableRes icon: Int,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
    tone: Tone = Tone.Warning,
    onClickLabel: String = stringResource(R.string.open),
    detail: String? = null,
    action: String? = null,
) {
    require(action == null || onClick != null) { "NoticeBanner: action kräver onClick" }
    val colors = AppColors.tone(tone)
    // Hela kortet är knappen – utom när åtgärden är en egen knapp under texten.
    val cardClick = onClick.takeIf { action == null }
    val clickable = cardClick?.let { Modifier.clip(AppShapes.card).clickable(onClickLabel = onClickLabel, role = Role.Button, onClick = it) } ?: Modifier
    AppCard(modifier.then(clickable), tone = tone) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.m)) {
            Icon(painterResource(icon), contentDescription = null, tint = colors.content)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                Text(text, style = AppTypography.itemTitle, color = colors.content)
                if (detail != null) Text(detail, style = AppTypography.caption, color = colors.content)
                action?.let { AppButton(it, checkNotNull(onClick)) }
            }
            if (cardClick != null) Icon(painterResource(R.drawable.ic_chevron_right), contentDescription = null, tint = colors.content)
        }
    }
}
