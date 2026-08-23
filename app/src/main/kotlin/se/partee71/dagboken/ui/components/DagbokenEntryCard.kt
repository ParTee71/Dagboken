package se.partee71.dagboken.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import se.partee71.dagboken.R
import se.partee71.dagboken.ui.theme.DagbokenAnimSpec

/** Nedtoning av ett inaktivt/avslutat postkorts text (t.ex. ett avaktiverat recept). */
private const val DIMMED_ALPHA = 0.5f

/**
 * En åtgärd i ett postkorts kontextmeny. Menyn är densamma oavsett om den öppnas
 * via `⋮` eller långtryck (NFR-15). [destructive] färgar posten i error-färg —
 * "Ta bort" läggs till automatiskt av [DagbokenEntryCard] när `onDelete` finns
 * och ska därför inte skickas in som en egen [EntryAction].
 */
data class EntryAction(
    val label: String,
    val icon: ImageVector,
    val destructive: Boolean = false,
    val onClick: () -> Unit,
)

/**
 * Kontextmenyn för en post — samma innehåll och ordning oavsett om den öppnas via `⋮`,
 * långtryck eller (för chip-kort som vid behov-favoriterna) enbart långtryck.
 * Anroparen placerar den i en [Box] tillsammans med sitt eget ankare.
 */
@Composable
internal fun EntryActionMenu(
    expanded: Boolean,
    actions: List<EntryAction>,
    onDismiss: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        actions.forEach { action ->
            DropdownMenuItem(
                text        = {
                    Text(
                        text  = action.label,
                        color = if (action.destructive) cs.error else Color.Unspecified,
                    )
                },
                leadingIcon = {
                    Icon(
                        imageVector        = action.icon,
                        contentDescription = null,
                        tint               = if (action.destructive) cs.error else LocalContentColor.current,
                    )
                },
                onClick     = { onDismiss(); action.onClick() },
            )
        }
    }
}

/**
 * Appens **postkort** (NFR-15/NFR-16) — kortet som representerar en sparad post
 * (historikpost, aktivitet, dos, incheckning, episod, recept). Komponenten äger
 * hela interaktionsmönstret så att det blir lika överallt:
 *
 * - **Tryck** = öppna posten ([onClick]) — detaljskärm om posten har en, annars redigering.
 *   Tryck expanderar aldrig.
 * - **Långtryck** = kontextmenyn, identisk med den som `⋮` öppnar.
 * - **Svep ←** = [onDelete]; kortet fjädrar tillbaka och anroparens bekräftelsedialog avgör.
 * - **Svep →** = oanvänd, reserverad riktning.
 * - **Chevron-knappen** = expandera [expandedContent].
 *
 * Trailing-innehållet renderas alltid i ordningen
 * `[trailingChip] [anteckningsikon] [chevron] [⋮]`, där varje del hoppas över när den
 * saknas. Bygg inte en egen kortvariant med samma innehåll — utöka den här (regel 4).
 *
 * [supportingContent] är en valfri rad-slot under undertiteln för status som behöver
 * egen färg (t.ex. ett recepts periodetikett i error-färg). [dimmed] tonar ned titel och
 * undertitel för en post som är avaktiverad eller avslutad.
 *
 * Sektions- och navigationskort är *inte* postkort och ska fortsätta använda
 * [DagbokenCard] direkt.
 */
@Composable
fun DagbokenEntryCard(
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    supportingContent: (@Composable ColumnScope.() -> Unit)? = null,
    dimmed: Boolean = false,
    leadingIcon: ImageVector? = null,
    accentColor: Color? = null,
    trailingChip: (@Composable () -> Unit)? = null,
    noteText: String = "",
    expandedContent: (@Composable ColumnScope.() -> Unit)? = null,
    actions: List<EntryAction> = emptyList(),
    onDelete: (() -> Unit)? = null,
) {
    val cs = MaterialTheme.colorScheme
    var expanded by remember { mutableStateOf(false) }
    var menuExpanded by remember { mutableStateOf(false) }

    // "Ta bort" ligger alltid sist i menyn (NFR-16) och läggs till här i stället för
    // hos varje anropare, så ordningen inte kan glida isär mellan ytorna.
    val deleteLabel = stringResource(R.string.delete)
    val menuActions = remember(actions, onDelete, deleteLabel) {
        if (onDelete == null) {
            actions
        } else {
            actions + EntryAction(
                label       = deleteLabel,
                icon        = Icons.Default.Delete,
                destructive = true,
                onClick     = onDelete,
            )
        }
    }

    val card: @Composable (Modifier) -> Unit = { cardModifier ->
        DagbokenCard(
            modifier         = cardModifier,
            onClick          = onClick,
            onLongClick      = if (menuActions.isNotEmpty()) { { menuExpanded = true } } else null,
            onClickLabel     = stringResource(R.string.entry_card_open),
            onLongClickLabel = stringResource(R.string.alternatives),
            contentPadding   = PaddingValues(12.dp),
            accentColor      = accentColor,
        ) {
            Row(
                modifier          = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Titel + undertitel läses som en enhet av TalkBack; ikonknapparna
                // till höger förblir egna, separat nåbara åtgärder.
                Row(
                    modifier          = Modifier
                        .weight(1f)
                        .semantics(mergeDescendants = true) {},
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (leadingIcon != null) {
                        Icon(
                            imageVector        = leadingIcon,
                            contentDescription = null,
                            tint               = cs.onSurfaceVariant,
                            modifier           = Modifier.size(24.dp),
                        )
                        Spacer(Modifier.width(12.dp))
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            text     = title,
                            style    = MaterialTheme.typography.titleSmall,
                            color    = if (dimmed) cs.onSurface.copy(alpha = DIMMED_ALPHA) else cs.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (subtitle != null) {
                            Text(
                                text     = subtitle,
                                style    = MaterialTheme.typography.bodySmall,
                                color    = cs.onSurfaceVariant.copy(
                                    alpha = if (dimmed) DIMMED_ALPHA else 1f,
                                ),
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        supportingContent?.invoke(this)
                    }
                }

                trailingChip?.invoke()

                NoteIndicatorIcon(noteText = noteText, dialogTitle = title)

                if (expandedContent != null) {
                    val chevronAngle by animateFloatAsState(
                        targetValue   = if (expanded) 180f else 0f,
                        animationSpec = DagbokenAnimSpec.springNormal,
                        label         = "entry_card_chevron",
                    )
                    IconButton(
                        onClick  = { expanded = !expanded },
                        modifier = Modifier.size(MIN_TOUCH_TARGET),
                    ) {
                        Icon(
                            imageVector        = Icons.Default.ExpandMore,
                            contentDescription = stringResource(
                                if (expanded) R.string.collapse else R.string.expand,
                            ),
                            modifier           = Modifier.size(20.dp).rotate(chevronAngle),
                            tint               = cs.onSurfaceVariant,
                        )
                    }
                }

                if (menuActions.isNotEmpty()) {
                    Box {
                        IconButton(
                            onClick  = { menuExpanded = true },
                            modifier = Modifier.size(MIN_TOUCH_TARGET),
                        ) {
                            Icon(
                                imageVector        = Icons.Default.MoreVert,
                                contentDescription = stringResource(R.string.alternatives),
                                modifier           = Modifier.size(20.dp),
                            )
                        }
                        EntryActionMenu(
                            expanded  = menuExpanded,
                            actions   = menuActions,
                            onDismiss = { menuExpanded = false },
                        )
                    }
                }
            }

            AnimatedVisibility(
                visible = expanded && expandedContent != null,
                enter   = expandVertically(animationSpec = DagbokenAnimSpec.springNormalSpec()),
                exit    = shrinkVertically(animationSpec = DagbokenAnimSpec.springNormalSpec()),
            ) {
                Column {
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                    expandedContent?.invoke(this)
                }
            }
        }
    }

    if (onDelete != null) {
        SwipeToDeleteBox(onDelete = onDelete, modifier = modifier) { card(Modifier) }
    } else {
        card(modifier)
    }
}

/**
 * Svep-för-att-radera enligt NFR-15. Svepet *begär* raderingen — anroparens
 * bekräftelsedialog avgör om den blir av, och därför returnerar
 * `confirmValueChange` alltid `false` så att kortet fjädrar tillbaka i stället
 * för att animeras bort innan svaret finns. (Tidigare returnerade ytorna `true`,
 * vilket lämnade posten kvar i listan men i dismissat state — ett osynligt
 * "spökkort" när dialogen avbröts.)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SwipeToDeleteBox(
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            if (value == SwipeToDismissBoxValue.EndToStart) onDelete()
            false
        },
    )
    SwipeToDismissBox(
        state                       = dismissState,
        modifier                    = modifier,
        enableDismissFromStartToEnd = false,
        backgroundContent           = {
            Box(
                modifier         = Modifier
                    .fillMaxSize()
                    .padding(end = 16.dp),
                contentAlignment = Alignment.CenterEnd,
            ) {
                Icon(
                    imageVector        = Icons.Default.Delete,
                    contentDescription = null,
                    tint               = MaterialTheme.colorScheme.error,
                )
            }
        },
    ) {
        content()
    }
}
