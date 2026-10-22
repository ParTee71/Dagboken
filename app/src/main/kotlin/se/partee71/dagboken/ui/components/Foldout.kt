package se.partee71.dagboken.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import se.partee71.dagboken.ui.theme.AppTypography
import se.partee71.ui.components.CollapsibleHeader
import se.partee71.ui.components.SectionHeader
import se.partee71.ui.theme.Spacing
import se.partee71.ui.theme.Tone

/**
 * Ihopfällbar sektion (NFR-18) – i ett [AppCard] eller som hela innehållet i ett ihopfällbart
 * sektionskort. **Hela titelraden** växlar läget (till skillnad från postkortet, där tryck öppnar
 * posten): raden är minst 48 dp hög, läses som knapp med "Fäll ut"/"Fäll ihop" och med läget
 * (utfälld/hopfälld) som `stateDescription`; chevronen är en ren indikator som fjädrar runt.
 * [trailing] står före chevronen – en kontroll som hör till innehållet (t.ex. en periodväljare);
 * innehåll som saknar mening i stängt läge utelämnar anroparen då. [summary] står under titeln i
 * stängt läge – det valda värdet eller början av en text (högst två rader).
 */
@Composable
fun Foldout(
    title: String,
    expanded: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
    trailing: (@Composable () -> Unit)? = null,
    summary: String? = null,
    header: (@Composable RowScope.() -> Unit)? = null,
    contentSpacing: Dp = Spacing.m,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(modifier) {
        CollapsibleHeader(expanded, onToggle) {
            if (header != null) {
                header()
            } else {
                Column(Modifier.weight(1f).padding(vertical = Spacing.s)) {
                    Text(title, style = AppTypography.itemTitle, color = MaterialTheme.colorScheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (!expanded && !summary.isNullOrBlank()) {
                        Text(summary, style = AppTypography.itemSubtitle, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
            trailing?.invoke()
        }
        ExpandableContent(expanded) {
            Column(Modifier.padding(bottom = Spacing.s), verticalArrangement = Arrangement.spacedBy(contentSpacing), content = content)
        }
    }
}

/**
 * Rubrikvarianten av [Foldout] för ett sektionskort med bibliotekets `SectionHeader` (NFR-18): ikonrutan, titeln och
 * räknaren i [countTone] står i titelraden, som också då växlar kortet, med chevronen sist. Stängt visas bara raden –
 * räknaren talar om läget utan att man fäller ut. Titeln läses som rubrik; resten som i [Foldout].
 */
@Composable
fun SectionFoldout(
    title: String,
    expanded: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
    @DrawableRes icon: Int? = null,
    count: String? = null,
    tone: Tone = Tone.Primary,
    countTone: Tone = tone,
    content: @Composable ColumnScope.() -> Unit
) {
    Foldout(
        title,
        expanded,
        onToggle,
        modifier,
        header = { SectionHeader(title, Modifier.weight(1f), icon = icon, count = count, tone = tone, countTone = countTone) },
        contentSpacing = Spacing.xs,
        content = content
    )
}

/** Det som fälls ut: fjädrar fram och fälls snabbt ihop – [Foldout] och postkortets detaljer. */
@Composable
internal fun ExpandableContent(visible: Boolean, content: @Composable () -> Unit) {
    AnimatedVisibility(
        visible = visible,
        enter = expandVertically(MaterialTheme.motionScheme.defaultSpatialSpec()),
        exit = shrinkVertically(MaterialTheme.motionScheme.fastSpatialSpec())
    ) { content() }
}
