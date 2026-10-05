package se.partee71.dagboken.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import se.partee71.dagboken.ui.theme.AppColors
import se.partee71.dagboken.ui.theme.AppShapes
import se.partee71.dagboken.ui.theme.AppTypography
import se.partee71.dagboken.ui.theme.Spacing
import se.partee71.dagboken.ui.theme.Tone

/**
 * Etikett eller räknare i en pill, i en av sektionstonerna ([Tone]). [icon] står före texten och
 * [trailingIcon] efter den (t.ex. rullgardinens pil i `CompactDropdownButton`); med [onClick] är pillen
 * en knapp med minst 48 dp tryckyta (t.ex. synkindikatorn i [AppTopBar]), med [role] och [onClickLabel]
 * för TalkBack.
 */
@Composable
fun InfoPill(
    text: String,
    modifier: Modifier = Modifier,
    tone: Tone = Tone.Primary,
    @DrawableRes icon: Int? = null,
    onClick: (() -> Unit)? = null,
    @DrawableRes trailingIcon: Int? = null,
    role: Role = Role.Button,
    onClickLabel: String? = null,
) {
    val colors = AppColors.tone(tone)
    val click = onClick?.let { Modifier.minimumInteractiveComponentSize().clip(AppShapes.pill).clickable(onClickLabel = onClickLabel, role = role, onClick = it) } ?: Modifier
    Row(
        modifier.then(click).background(colors.container, AppShapes.pill).padding(horizontal = Spacing.m, vertical = Spacing.xs),
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        icon?.let { Icon(painterResource(it), contentDescription = null, tint = colors.content, modifier = Modifier.size(ICON)) }
        Text(text, style = AppTypography.pill, color = colors.content, maxLines = 1, overflow = TextOverflow.Ellipsis)
        trailingIcon?.let { Icon(painterResource(it), contentDescription = null, tint = colors.content, modifier = Modifier.size(ICON)) }
    }
}

private val ICON = 16.dp
