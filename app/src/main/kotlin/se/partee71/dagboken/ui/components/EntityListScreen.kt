@file:OptIn(ExperimentalMaterial3Api::class)

package se.partee71.dagboken.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import se.partee71.dagboken.R
import se.partee71.dagboken.data.common.DataError
import se.partee71.dagboken.ui.common.ArchiveEvent
import se.partee71.dagboken.ui.common.ListUiState
import se.partee71.dagboken.ui.theme.AppShapes
import se.partee71.dagboken.ui.theme.AppTypography
import se.partee71.dagboken.ui.theme.Spacing
import se.partee71.dagboken.ui.theme.Tone

/**
 * Det tomma tillståndet för en lista: ikon, rubrik, en mening och texten på lägg till-knappen.
 * [examples] är exempel att börja från ("Solkräm") – chips med plus ovanför knappen.
 */
data class EmptyContent(
    @param:DrawableRes val icon: Int,
    val title: String,
    val message: String,
    val actionLabel: String,
    val examples: List<EmptyExample> = emptyList(),
)

/** Ett exempel i en tom lista: tryck öppnar något nytt att börja från. */
data class EmptyExample(val label: String, val onClick: () -> Unit)

/**
 * En grupp i en grupperad lista: rubrik, valfri ikon och ton. [count] ersätter antalet rader i
 * rubriken ("3 / 6" klara); [columns] = 2 lägger raderna i två kolumner (t.ex. vid behov-medicinerna);
 * [collapsible] gör gruppen hopfälld tills rubriken trycks ("Dolda").
 */
data class ListGroup(
    val title: String,
    @param:DrawableRes val icon: Int? = null,
    val tone: Tone = Tone.Primary,
    val count: String? = null,
    val columns: Int = 1,
    val collapsible: Boolean = false,
)

/** En undergrupp inom en grupp: namn i en egen färg (t.ex. ett doseringstillfälle). */
data class ListSubgroup(val title: String, val color: Color)

/**
 * Arkivering i en lista (NFR-3), där den finns: "Visa arkiverade" i menyn ([showing]), Ångra
 * efter svep ([undo]) och ett misslyckat arkivera/ångra som meddelande ([error]). Händelserna
 * går till `ArchiveActions` i ViewModeln; `collectAsListArchive()` bygger den. [showToggle] =
 * false när dolda rader visas på annat sätt (t.ex. "Avslutade").
 */
data class ListArchive(
    val showing: Boolean = false,
    val undo: UndoRequest? = null,
    val error: DataError? = null,
    val showToggle: Boolean = true,
    val onEvent: (ArchiveEvent) -> Unit = {},
) {
    /** Svep på en rad: arkivera den och erbjud Ångra. */
    fun archive(id: String, name: String) = onEvent(ArchiveEvent.Archive(id, name))
}

/**
 * Den enda listskärmen (NFR-1, skill shared-ui-components). Samma fyra lägen överallt:
 * laddning → [AppLoading]; tomt → [EmptyState] med knapp; fel → feltillstånd med
 * "Försök igen"; innehåll → rader i kort, valfritt grupperade med [SectionHeader].
 * "Lägg till" står nere till höger ovanför verktygsraden. Med [archive] finns "Visa arkiverade"
 * i menyn, svepta rader ångras med [UndoSnackbar] och ett fel från arkiveringen visas som
 * meddelande; raderna sveps med `SwipeToHide`.
 *
 * En undersida har [onBack], [subtitle], egna [actions] i rubrikraden och ett
 * [header] överst i listan; `ListArchive.showToggle` = false tar bort "Visa arkiverade".
 *
 * @param group grupp för en rad; grupperna visas i den ordning de först förekommer.
 * @param subgroup undergrupp för en rad inom gruppen (person), i den ordning de förekommer.
 */
@Composable
fun <T> EntityListScreen(
    title: String,
    state: ListUiState<T>,
    empty: EmptyContent,
    onAdd: () -> Unit,
    key: (T) -> Any,
    modifier: Modifier = Modifier,
    onRetry: () -> Unit = {},
    addMenu: List<AppMenuItem> = emptyList(),
    group: ((T) -> ListGroup)? = null,
    archive: ListArchive? = null,
    subtitle: String? = null,
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
    header: (@Composable () -> Unit)? = null,
    subgroup: ((T) -> ListSubgroup?)? = null,
    row: @Composable (T) -> Unit,
) {
    val snackbar = remember { SnackbarHostState() }
    ErrorSnackbar(archive?.error, snackbar) { archive?.onEvent(ArchiveEvent.ErrorShown) }
    val scroll = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val clearance = LocalBottomClearance.current
    UndoSnackbar(archive?.undo, snackbar, { archive?.onEvent(ArchiveEvent.Undo) }, { archive?.onEvent(ArchiveEvent.UndoDismissed) })
    Scaffold(
        modifier = modifier.fillMaxSize().nestedScroll(scroll.nestedScrollConnection),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            AppTopBar(title, subtitle = subtitle, onBack = onBack, scrollBehavior = scroll) {
                actions()
                archive?.takeIf { it.showToggle }?.let {
                    val label = stringResource(if (it.showing) R.string.hide_archived else R.string.show_archived)
                    AppMenu(listOf(AppMenuItem(label, { it.onEvent(ArchiveEvent.ToggleArchived) }, R.drawable.ic_unarchive)))
                }
            }
        },
        snackbarHost = { AppSnackbarHost(snackbar, Modifier.padding(bottom = clearance)) },
        floatingActionButton = {
            if (state is ListUiState.Content) {
                AddSplitButton(empty.actionLabel, onAdd, Modifier.padding(bottom = clearance), menuItems = addMenu)
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (state) {
                ListUiState.Loading -> AppLoading()
                ListUiState.Empty -> EmptyState(
                    icon = empty.icon,
                    title = empty.title,
                    message = empty.message,
                    modifier = Modifier.padding(bottom = clearance),
                    action = {
                        Column(verticalArrangement = Arrangement.spacedBy(Spacing.l)) {
                            if (empty.examples.isNotEmpty()) {
                                ExampleChips(empty.examples, { it.onClick() }, label = { it.label })
                            }
                            AppButton(empty.actionLabel, onAdd, Modifier.fillMaxWidth(), icon = R.drawable.ic_add)
                        }
                    },
                )
                is ListUiState.Error ->
                    LoadErrorState(stringResource(R.string.list_error_title), state.error, onRetry, Modifier.padding(bottom = clearance))
                is ListUiState.Content -> ListContent(state.items, key, group, subgroup, header, clearance, row)
            }
        }
    }
}

@Composable
private fun <T> ListContent(
    items: List<T>,
    key: (T) -> Any,
    group: ((T) -> ListGroup)?,
    subgroup: ((T) -> ListSubgroup?)?,
    header: (@Composable () -> Unit)?,
    clearance: Dp,
    row: @Composable (T) -> Unit,
) {
    val groups = remember(items, group) { if (group == null) listOf(null to items) else items.groupBy(group).toList() }
    // Utfällda grupper (bara de hopfällbara), efter rubrik; står kvar efter rotation och tillbaka.
    var expanded by rememberSaveable { mutableStateOf(emptyList<String>()) }
    // Varje rad är ett eget listobjekt (lat inläsning, animering per rad); korten byggs av bitar.
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = Spacing.l, end = Spacing.l, top = Spacing.xs, bottom = clearance + ADD_BUTTON_CLEARANCE),
    ) {
        header?.let { item(key = "list-header") { Box(Modifier.padding(bottom = Spacing.m)) { it() } } }
        groups.forEachIndexed { index, (head, rows) ->
            if (index > 0) item(key = "gap:$index") { Spacer(Modifier.height(Spacing.m)) }
            val open = head == null || !head.collapsible || head.title in expanded
            head?.let {
                item(key = "header:${it.title}") {
                    CardSegment(top = true, bottom = !open, Modifier.animateItem()) {
                        GroupHeader(it, rows.size, open) { expanded = if (open) expanded - it.title else expanded + it.title }
                    }
                }
            }
            if (open) groupRows(head, rows, key, subgroup, row)
        }
    }
}

/** Raderna i en grupp: undergrupper med rubrik, och i [ListGroup.columns] kolumner. */
private fun <T> LazyListScope.groupRows(
    head: ListGroup?,
    rows: List<T>,
    key: (T) -> Any,
    subgroup: ((T) -> ListSubgroup?)?,
    row: @Composable (T) -> Unit,
) {
    val columns = head?.columns ?: 1
    val runs = if (subgroup == null) listOf(null to rows) else rows.runsBy(subgroup)
    // Undergruppens nyckel är dess rubrik (och ordningsnummer om samma namn finns två gånger) –
    // inte första radens, som byts när en rad prickas av och flyttas sist.
    val seen = mutableMapOf<String, Int>()
    val lines = runs.flatMap { (sub, members) ->
        val label = sub?.let { Line.Label<T>(it, "${it.title}#${seen.merge(it.title, 1, Int::plus)}") }
        listOfNotNull(label) + members.chunked(columns).map { Line.Rows(it) }
    }
    lines.forEachIndexed { position, line ->
        val top = head == null && position == 0
        val bottom = position == lines.lastIndex
        when (line) {
            is Line.Label -> item(key = "sub:${head?.title}:${line.id}") {
                CardSegment(top = top, bottom = bottom, Modifier.animateItem()) { SubgroupLabel(line.subgroup) }
            }
            // Nycklar måste kunna sparas i en Bundle: en rad har sin egen, flera i kolumner en sammansatt sträng.
            is Line.Rows -> item(key = line.members.singleOrNull()?.let(key) ?: line.members.joinToString("|") { key(it).toString() }) {
                CardSegment(top = top, bottom = bottom, Modifier.animateItem()) {
                    if (columns == 1) {
                        row(line.members.single())
                    } else {
                        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                            line.members.forEach { Box(Modifier.weight(1f)) { row(it) } }
                            repeat(columns - line.members.size) { Spacer(Modifier.weight(1f)) }
                        }
                    }
                }
            }
        }
    }
}

/** En rad i listan: en undergruppsrubrik eller en eller flera rader (kolumner). */
private sealed interface Line<T> {
    class Label<T>(val subgroup: ListSubgroup, val id: String) : Line<T>
    class Rows<T>(val members: List<T>) : Line<T>
}

/** Följder av rader med samma undergrupp, i ordning. */
private fun <T> List<T>.runsBy(subgroup: (T) -> ListSubgroup?): List<Pair<ListSubgroup?, List<T>>> {
    val runs = mutableListOf<Pair<ListSubgroup?, MutableList<T>>>()
    forEach { item ->
        val sub = subgroup(item)
        if (runs.isNotEmpty() && runs.last().first == sub) runs.last().second += item else runs += sub to mutableListOf(item)
    }
    return runs
}

@Composable
private fun GroupHeader(group: ListGroup, size: Int, open: Boolean, onToggle: () -> Unit) {
    val count = group.count ?: size.toString()
    if (!group.collapsible) {
        SectionHeader(group.title, icon = group.icon, count = count, tone = group.tone)
        return
    }
    val state = stringResource(if (open) R.string.expanded else R.string.collapsed)
    Row(
        Modifier.fillMaxWidth().clip(AppShapes.row).clickable(onClick = onToggle).semantics { stateDescription = state },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SectionHeader(group.title, Modifier.weight(1f), icon = group.icon, count = count, tone = group.tone)
        Icon(
            painterResource(R.drawable.ic_expand_more),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.rotate(if (open) 180f else 0f),
        )
    }
}

@Composable
private fun SubgroupLabel(subgroup: ListSubgroup) {
    Text(
        subgroup.title,
        style = AppTypography.pill,
        color = subgroup.color,
        modifier = Modifier.padding(start = Spacing.s, top = Spacing.xs).semantics { heading() },
    )
}

/** Plats under sista raden så att lägg till-knappen inte täcker den. */
private val ADD_BUTTON_CLEARANCE = 80.dp
