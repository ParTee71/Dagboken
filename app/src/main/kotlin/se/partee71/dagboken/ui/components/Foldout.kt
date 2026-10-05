package se.partee71.dagboken.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import se.partee71.dagboken.R
import se.partee71.dagboken.ui.theme.AppShapes
import se.partee71.dagboken.ui.theme.AppTypography
import se.partee71.dagboken.ui.theme.Spacing

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
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier) {
        CollapsibleHeader(expanded, onToggle) {
            Column(Modifier.weight(1f).padding(vertical = Spacing.s)) {
                Text(title, style = AppTypography.itemTitle, color = MaterialTheme.colorScheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (!expanded && !summary.isNullOrBlank()) {
                    Text(summary, style = AppTypography.itemSubtitle, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
            trailing?.invoke()
        }
        ExpandableContent(expanded) {
            Column(Modifier.padding(bottom = Spacing.s), verticalArrangement = Arrangement.spacedBy(Spacing.m), content = content)
        }
    }
}

/**
 * Titelraden i allt som fälls ihop – [Foldout] och hopfällbara grupper i `EntityListScreen`:
 * hela raden växlar, minst 48 dp, `Role.Button`, åtgärdsetikett och läge för TalkBack och en
 * roterande chevron sist.
 */
@Composable
internal fun CollapsibleHeader(
    expanded: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit,
) {
    val labels = expandLabels(expanded)
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = TOUCH_TARGET)
            .clip(AppShapes.row)
            .clickable(onClickLabel = labels.action, role = Role.Button, onClick = onToggle)
            .semantics { stateDescription = labels.state },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.s),
    ) {
        content()
        Icon(
            painterResource(R.drawable.ic_expand_more),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.rotate(chevronRotation(expanded)),
        )
    }
}

/** Åtgärd ("Fäll ut"/"Fäll ihop") och läge ("Hopfälld"/"Utfälld") för TalkBack – samma i allt som fälls ut. */
internal class ExpandLabels(val action: String, val state: String)

@Composable
internal fun expandLabels(expanded: Boolean) = ExpandLabels(
    action = stringResource(if (expanded) R.string.collapse else R.string.expand),
    state = stringResource(if (expanded) R.string.expanded else R.string.collapsed),
)

/** Chevronens fjädrande vridning – ett halvt varv i utfällt läge (DSN-4). */
@Composable
internal fun chevronRotation(expanded: Boolean): Float {
    val rotation by animateFloatAsState(
        targetValue = if (expanded) HALF_TURN else 0f,
        animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec(),
        label = "chevron",
    )
    return rotation
}

/** Det som fälls ut: fjädrar fram och fälls snabbt ihop – [Foldout] och postkortets detaljer. */
@Composable
internal fun ExpandableContent(visible: Boolean, content: @Composable () -> Unit) {
    AnimatedVisibility(
        visible = visible,
        enter = expandVertically(MaterialTheme.motionScheme.defaultSpatialSpec()),
        exit = shrinkVertically(MaterialTheme.motionScheme.fastSpatialSpec()),
    ) { content() }
}

private const val HALF_TURN = 180f
