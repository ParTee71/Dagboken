package se.partee71.dagboken.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import se.partee71.dagboken.R
import se.partee71.dagboken.ui.theme.AppColors
import se.partee71.dagboken.ui.theme.AppShapes
import se.partee71.dagboken.ui.theme.AppTypography
import se.partee71.dagboken.ui.theme.Spacing

/**
 * Den enda listraden (skill shared-ui-components): valfri [leading] (avatar, kryss), titel,
 * undertext och [trailing] (pill, pil, växel). [done] ger den dämpade "klar"-stilen,
 * [tinted] den tonade bakgrunden och [accent] en färgad vänsterkant (status, NFR-16).
 * [navigates] visar en pil längst till höger (efter [trailing]) – raden öppnar en annan skärm.
 * [inactive] tonar ner hela raden, för det som finns men inte räknas med (ett pausat recept).
 * [below] står under titel och undertext över hela radens bredd (t.ex. en episods incheckningar).
 * [titleHighlight] färgmarkerar en del av titeln – det man sökt på i ett [SuggestionField].
 */
@Composable
fun ItemRow(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
    onClick: (() -> Unit)? = null,
    tinted: Boolean = false,
    done: Boolean = false,
    accent: Color? = null,
    navigates: Boolean = false,
    inactive: Boolean = false,
    below: (@Composable () -> Unit)? = null,
    titleHighlight: IntRange? = null,
) {
    val end: (@Composable () -> Unit)? = if (navigates) {
        {
            trailing?.invoke()
            Icon(painterResource(R.drawable.ic_chevron_right), contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    } else {
        trailing
    }
    ItemRowLayout(
        title = title,
        modifier = if (inactive) modifier.alpha(INACTIVE_ALPHA) else modifier,
        interaction = if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier,
        subtitle = subtitle,
        leading = leading,
        trailing = end,
        tinted = tinted,
        done = done,
        accent = accent,
        below = below,
        titleHighlight = titleHighlight,
    )
}

/** Gemensam layout för [ItemRow], `CheckRow` och `SwitchRow` – de skiljer sig bara i [interaction]. */
@Composable
internal fun ItemRowLayout(
    title: String,
    modifier: Modifier,
    interaction: Modifier,
    subtitle: String? = null,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
    tinted: Boolean = false,
    done: Boolean = false,
    accent: Color? = null,
    below: (@Composable () -> Unit)? = null,
    titleHighlight: IntRange? = null,
) {
    val tint = AppColors.extended.rowTint
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    Column(
        modifier
            .fillMaxWidth()
            .heightIn(min = MIN_HEIGHT)
            .clip(AppShapes.row)
            .then(if (tinted) Modifier.background(tint) else Modifier)
            .then(interaction)
            .accentBar(accent)
            .padding(horizontal = Spacing.m, vertical = Spacing.s),
        verticalArrangement = Arrangement.spacedBy(Spacing.s, Alignment.CenterVertically),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.m)) {
            leading?.invoke()
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                val decoration = if (done) TextDecoration.LineThrough else null
                Text(
                    highlighted(title, titleHighlight),
                    style = AppTypography.itemTitle,
                    color = if (done) muted else MaterialTheme.colorScheme.onSurface,
                    textDecoration = decoration,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                subtitle?.let { Text(it, style = AppTypography.itemSubtitle, color = muted, textDecoration = decoration) }
            }
            trailing?.invoke()
        }
        below?.invoke()
    }
}

/** Statusfärgen i vänsterkanten (NFR-16) – för listraden och postkortet; `null` ritar inget. */
internal fun Modifier.accentBar(color: Color?): Modifier = if (color == null) this else drawWithContent {
    drawContent()
    val width = ACCENT_WIDTH.toPx()
    drawRoundRect(color, size = Size(width, size.height), cornerRadius = CornerRadius(width / 2))
}

private val MIN_HEIGHT = 56.dp
private val ACCENT_WIDTH = 4.dp

/** Nedtoning för det som finns men inte räknas med (pausat recept, avslutad post). */
internal const val INACTIVE_ALPHA = 0.55f

/** [text] med [range] i primärfärgen; utan (eller med ett ogiltigt) intervall bara texten. */
@Composable
private fun highlighted(text: String, range: IntRange?): AnnotatedString {
    val clipped = range?.let { it.first.coerceAtLeast(0)..it.last.coerceAtMost(text.lastIndex) }?.takeUnless { it.isEmpty() }
        ?: return AnnotatedString(text)
    return buildAnnotatedString {
        append(text)
        addStyle(SpanStyle(color = MaterialTheme.colorScheme.primary), clipped.first, clipped.last + 1)
    }
}
