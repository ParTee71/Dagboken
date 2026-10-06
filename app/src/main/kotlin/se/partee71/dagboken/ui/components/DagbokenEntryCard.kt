package se.partee71.dagboken.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import kotlinx.coroutines.launch
import se.partee71.dagboken.R
import se.partee71.dagboken.ui.theme.AppShapes
import se.partee71.dagboken.ui.theme.AppTypography
import se.partee71.dagboken.ui.theme.Spacing

/**
 * Postkortet (NFR-15, NFR-16) – kortet för en sparad post: dos, aktivitet, mående, händelse,
 * incheckning, episod, recept. Komponenten äger hela gestmönstret, så att det är lika överallt:
 *
 * - **tryck** öppnar posten ([onClick]) och expanderar aldrig;
 * - **långtryck** öppnar samma meny som `⋮`: Redigera ([onEdit]), [actions], Radera sist i felfärg;
 * - **svep från höger till vänster** begär radering: kortet fjädrar tillbaka och [delete] bekräftas
 *   alltid med [ConfirmDialog] – samma dialog som menyns Radera, så ingen åtgärd finns bara via svep;
 * - svep från vänster till höger är reserverat och gör ingenting.
 *
 * Trailing-delen har fast ordning: [toggle] (postkortets enda direktkontroll, t.ex. receptets
 * aktiv-reglage – dubbleras som menyval av anroparen), [status] (värdechip), anteckningsikonen när
 * [note] har text, chevronen när [expandedContent] finns (fäller ut detaljerna), `⋮`. [accent] är
 * en statusfärg i vänsterkanten (energi, aktiv/inaktiv, pågående) – aldrig dekoration – och
 * [inactive] tonar ner en avslutad eller pausad post (inte reglaget, som ska gå att slå på igen).
 * [below] står under titel och undertext, t.ex. pills för dagens dos och period. Med [toggle] står
 * titeln på hela bredden överst och undertexten bredvid kontrollerna, så att titeln inte trycks ihop.
 */
@Composable
fun DagbokenEntryCard(
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    leading: (@Composable () -> Unit)? = null,
    accent: Color? = null,
    status: (@Composable () -> Unit)? = null,
    note: String = "",
    expandedContent: (@Composable ColumnScope.() -> Unit)? = null,
    onEdit: (() -> Unit)? = null,
    actions: List<AppMenuItem> = emptyList(),
    delete: DeleteAction? = null,
    inactive: Boolean = false,
    toggle: EntryToggle? = null,
    below: (@Composable () -> Unit)? = null,
) {
    var menuOpen by remember { mutableStateOf(false) }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    val menu = entryMenu(onEdit, actions, delete) { confirmDelete = true }
    val card: @Composable (Modifier) -> Unit = { outer ->
        EntryCardBody(
            title = title,
            onClick = onClick,
            modifier = outer,
            subtitle = subtitle,
            leading = leading,
            accent = accent,
            status = status,
            note = note,
            expandedContent = expandedContent,
            menu = menu,
            menuOpen = menuOpen,
            onMenuOpenChange = { menuOpen = it },
            inactive = inactive,
            toggle = toggle,
            below = below,
        )
    }
    if (delete == null) card(modifier) else SwipeToDelete({ confirmDelete = true }, modifier) { card(Modifier) }
    if (delete != null && confirmDelete) {
        ConfirmDialog(
            delete.title,
            delete.message,
            stringResource(R.string.delete),
            onConfirm = {
                confirmDelete = false
                delete.onConfirm()
            },
            onDismiss = { confirmDelete = false },
            destructive = true,
        )
    }
}

/** Postkortets direktkontroll (NFR-16): ett reglage, läst av TalkBack som [label]. */
data class EntryToggle(val checked: Boolean, val onCheckedChange: (Boolean) -> Unit, val label: String)

/** Menyn i ordningen NFR-16: Redigera, det kontextspecifika, Radera sist. */
@Composable
private fun entryMenu(onEdit: (() -> Unit)?, actions: List<AppMenuItem>, delete: DeleteAction?, onDelete: () -> Unit): List<AppMenuItem> {
    val edit = onEdit?.let { AppMenuItem(stringResource(R.string.edit), it, R.drawable.ic_edit) }
    val remove = delete?.let { AppMenuItem(stringResource(R.string.delete), onDelete, R.drawable.ic_delete, destructive = true) }
    return listOfNotNull(edit) + actions + listOfNotNull(remove)
}

@Composable
private fun EntryCardBody(
    title: String,
    onClick: () -> Unit,
    modifier: Modifier,
    subtitle: String?,
    leading: (@Composable () -> Unit)?,
    accent: Color?,
    status: (@Composable () -> Unit)?,
    note: String,
    expandedContent: (@Composable ColumnScope.() -> Unit)?,
    menu: List<AppMenuItem>,
    menuOpen: Boolean,
    onMenuOpenChange: (Boolean) -> Unit,
    inactive: Boolean,
    toggle: EntryToggle?,
    below: (@Composable () -> Unit)?,
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    AppCard(
        modifier
            .clip(AppShapes.card)
            .combinedClickable(
                onClickLabel = stringResource(R.string.open),
                onLongClickLabel = stringResource(R.string.more_options).takeIf { menu.isNotEmpty() },
                onLongClick = if (menu.isEmpty()) null else ({ onMenuOpenChange(true) }),
                onClick = onClick,
            )
            .accentBar(accent),
    ) {
        // Med reglage blir trailing-delen bred (reglage, anteckning, chevron, ⋮): titeln får då hela
        // bredden ovanför, och undertexten står bredvid kontrollerna – titeln trycks aldrig ihop.
        if (toggle != null) RowText(title, null, Modifier.inactive(inactive).padding(start = Spacing.xs))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
            Row(
                Modifier.weight(1f).inactive(inactive).padding(start = Spacing.xs),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.m),
            ) {
                leading?.invoke()
                RowText(title.takeIf { toggle == null }, subtitle)
            }
            toggle?.let { SwitchControl(it.checked, it.onCheckedChange, label = it.label) }
            status?.invoke()
            NoteIndicator(note, title)
            if (expandedContent != null) ExpandButton(expanded) { expanded = !expanded }
            if (menu.isNotEmpty()) {
                Box {
                    AppIconButton(R.drawable.ic_more_vert, stringResource(R.string.more_options), { onMenuOpenChange(true) })
                    AppMenuPopup(menu, menuOpen, onDismiss = { onMenuOpenChange(false) })
                }
            }
        }
        below?.let { Box(Modifier.inactive(inactive).padding(start = Spacing.xs)) { it() } }
        if (expandedContent != null) {
            ExpandableContent(expanded) {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
                    AppDivider()
                    expandedContent()
                }
            }
        }
    }
}

/** Postkortets chevron-knapp (NFR-16): samma etikett som [Foldout]s titelrad, läget som `stateDescription`. */
@Composable
private fun ExpandButton(expanded: Boolean, onToggle: () -> Unit) {
    val labels = expandLabels(expanded)
    AppIconButton(
        R.drawable.ic_expand_more,
        labels.action,
        onToggle,
        Modifier.rotate(chevronRotation(expanded)).semantics { stateDescription = labels.state },
    )
}

/**
 * Svep från höger till vänster begär radering ([onRequest]); kortet fjädrar genast tillbaka och
 * ligger kvar tills bekräftelsen svarat (NFR-15). Svep åt höger är avstängt.
 */
@Composable
private fun SwipeToDelete(onRequest: () -> Unit, modifier: Modifier, content: @Composable () -> Unit) {
    val state = rememberSwipeToDismissBoxState()
    val scope = rememberCoroutineScope()
    SwipeToDismissBox(
        state = state,
        backgroundContent = {
            // Bara en bild av svepet; åtgärden finns för TalkBack som menyns Radera.
            Row(
                Modifier.fillMaxSize().clip(AppShapes.card).background(MaterialTheme.colorScheme.errorContainer).padding(horizontal = Spacing.l).clearAndSetSemantics {},
                horizontalArrangement = Arrangement.spacedBy(Spacing.s, Alignment.End),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val color = MaterialTheme.colorScheme.onErrorContainer
                Icon(painterResource(R.drawable.ic_delete), contentDescription = null, tint = color)
                Text(stringResource(R.string.delete), style = AppTypography.button, color = color)
            }
        },
        modifier = modifier,
        enableDismissFromStartToEnd = false,
        onDismiss = { value ->
            if (value == SwipeToDismissBoxValue.EndToStart) {
                onRequest()
                scope.launch { state.reset() }
            }
        },
        content = { content() },
    )
}

