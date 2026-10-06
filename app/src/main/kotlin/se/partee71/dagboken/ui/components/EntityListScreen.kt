@file:OptIn(ExperimentalMaterial3Api::class)

package se.partee71.dagboken.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import se.partee71.dagboken.R
import se.partee71.dagboken.ui.common.ArchiveEvent
import se.partee71.dagboken.ui.common.Failure
import se.partee71.dagboken.ui.common.ListUiState
import se.partee71.dagboken.ui.theme.AppTypography
import se.partee71.dagboken.ui.theme.Spacing
import se.partee71.dagboken.ui.theme.Tone

/**
 * Det tomma tillståndet för en lista: ikon, rubrik och en mening. [examples] är exempel att börja från
 * ("Solkräm") – chips med plus ovanför lägg till-knappen.
 */
data class EmptyContent(
    @param:DrawableRes val icon: Int,
    val title: String,
    val message: String,
    val examples: List<EmptyExample> = emptyList(),
)

/**
 * Listans lägg till: knappens text och åtgärd hör ihop (ingen knapp utan text). [menu] är val i
 * split-knappens pil (t.ex. "Ny vid behov-medicin").
 */
data class AddAction(val label: String, val onClick: () -> Unit, val menu: List<AppMenuItem> = emptyList())

/** Ett exempel i en tom lista: tryck öppnar något nytt att börja från. */
data class EmptyExample(val label: String, val onClick: () -> Unit)

/**
 * En grupp i en grupperad lista: rubrik, valfri ikon och ton. [count] ersätter antalet rader i
 * rubriken ("3 / 6" klara); [columns] = 2 lägger raderna i två kolumner (t.ex. vid behov-medicinerna);
 * [collapsible] gör gruppen hopfälld tills rubriken trycks ("Dolda"). [cards] = true för postkort
 * (`DagbokenEntryCard`, t.ex. recepten): varje rad är ett eget kort och rubriken står på bakgrunden
 * ovanför dem, i stället för att gruppen är ett kort med rader. [showCount] = false tar bort räknaren
 * (Dagbokens dagar, där rubriken är dagen).
 */
data class ListGroup(
    val title: String,
    @param:DrawableRes val icon: Int? = null,
    val tone: Tone = Tone.Primary,
    val count: String? = null,
    val columns: Int = 1,
    val collapsible: Boolean = false,
    val cards: Boolean = false,
    val showCount: Boolean = true,
)

/** En undergrupp inom en grupp: namn i en egen färg (t.ex. ett doseringstillfälle). */
data class ListSubgroup(val title: String, val color: Color)

/**
 * Arkivering i en lista (NFR-3), där den finns: "Visa arkiverade" i menyn ([showing]), Ångra
 * efter svep ([undo]) och ett misslyckat arkivera/ångra som meddelande ([failure]). Händelserna
 * går till `ArchiveActions` i ViewModeln; `collectAsListArchive()` bygger den. [showToggle] =
 * false när dolda rader visas på annat sätt (t.ex. "Avslutade").
 */
data class ListArchive(
    val showing: Boolean = false,
    val undo: UndoRequest? = null,
    val failure: Failure? = null,
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
 * [filter] står fast under rubriken i alla lägen – även tomt och fel – för ett val av vilken lista
 * som visas (t.ex. `AppSegmentedChoice` Aktiviteter · Symptom · Händelser i Listor). [topBarSize] =
 * `TopBarSize.Small` för en underskärm i inställningsarket, med samma lilla topprad som formulären.
 * [add] = `null` för en lista utan lägg till (Dagbok – plusknappen i verktygsraden loggar): ingen knapp,
 * varken i listan eller i det tomma tillståndet. [footer] står sist i listan, efter grupperna, och under
 * budskapet i det tomma tillståndet (t.ex. "Visa äldre").
 *
 * @param group grupp för en rad; grupperna visas i den ordning de först förekommer.
 * @param subgroup undergrupp för en rad inom gruppen (person), i den ordning de förekommer.
 */
@Composable
fun <T> EntityListScreen(
    title: String,
    state: ListUiState<T>,
    empty: EmptyContent,
    add: AddAction?,
    key: (T) -> Any,
    modifier: Modifier = Modifier,
    onRetry: () -> Unit = {},
    group: ((T) -> ListGroup)? = null,
    archive: ListArchive? = null,
    subtitle: String? = null,
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
    header: (@Composable () -> Unit)? = null,
    subgroup: ((T) -> ListSubgroup?)? = null,
    filter: (@Composable () -> Unit)? = null,
    topBarSize: TopBarSize = TopBarSize.Large,
    footer: (@Composable () -> Unit)? = null,
    row: @Composable (T) -> Unit,
) {
    val snackbar = remember { SnackbarHostState() }
    val failure = archive?.failure
    ErrorSnackbar(failure?.error, snackbar, message = failure?.message, key = failure) { archive?.onEvent(ArchiveEvent.ErrorShown) }
    val scroll = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val clearance = LocalBottomClearance.current
    UndoSnackbar(archive?.undo, snackbar, { archive?.onEvent(ArchiveEvent.Undo) }, { archive?.onEvent(ArchiveEvent.UndoDismissed) })
    Scaffold(
        modifier = modifier.fillMaxSize().nestedScroll(scroll.nestedScrollConnection),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            AppTopBar(title, size = topBarSize, subtitle = subtitle, onBack = onBack, scrollBehavior = scroll) {
                actions()
                archive?.takeIf { it.showToggle }?.let {
                    val label = stringResource(if (it.showing) R.string.hide_archived else R.string.show_archived)
                    AppMenu(listOf(AppMenuItem(label, { it.onEvent(ArchiveEvent.ToggleArchived) }, R.drawable.ic_unarchive)))
                }
            }
        },
        snackbarHost = { AppSnackbarHost(snackbar, Modifier.padding(bottom = clearance)) },
        floatingActionButton = {
            if (state is ListUiState.Content && add != null) {
                AddSplitButton(add.label, add.onClick, Modifier.padding(bottom = clearance).testTag(ADD_BUTTON_TAG), menuItems = add.menu)
            }
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            filter?.let { Box(Modifier.padding(start = SCREEN_MARGIN, end = SCREEN_MARGIN, bottom = Spacing.m)) { it() } }
            Box(Modifier.fillMaxWidth().weight(1f)) {
                when (state) {
                    ListUiState.Loading -> AppLoading()
                    ListUiState.Empty -> EmptyState(
                        icon = empty.icon,
                        title = empty.title,
                        message = empty.message,
                        modifier = Modifier.padding(bottom = clearance),
                        action = if (add == null && footer == null) {
                            null
                        } else {
                            {
                                Column(verticalArrangement = Arrangement.spacedBy(Spacing.l)) {
                                    if (add != null) {
                                        if (empty.examples.isNotEmpty()) {
                                            ExampleChips(empty.examples, { it.onClick() }, label = { it.label })
                                        }
                                        AppButton(add.label, add.onClick, Modifier.fillMaxWidth().testTag(ADD_BUTTON_TAG), icon = R.drawable.ic_add)
                                    }
                                    footer?.invoke()
                                }
                            }
                        },
                    )
                    is ListUiState.Error ->
                        LoadErrorState(stringResource(R.string.list_error_title), state.error, onRetry, Modifier.padding(bottom = clearance))
                    is ListUiState.Content -> ListContent(state.items, key, group, subgroup, header, footer, clearance, addButton = add != null, row)
                }
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
    footer: (@Composable () -> Unit)?,
    clearance: Dp,
    addButton: Boolean,
    row: @Composable (T) -> Unit,
) {
    val groups = remember(items, group) { if (group == null) listOf(null to items) else items.groupBy(group).toList() }
    // Utfällda grupper (bara de hopfällbara), efter rubrik; står kvar efter rotation och tillbaka.
    var expanded by rememberSaveable { mutableStateOf(emptyList<String>()) }
    // Varje rad är ett eget listobjekt (lat inläsning, animering per rad); korten byggs av bitar.
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = SCREEN_MARGIN, end = SCREEN_MARGIN, top = Spacing.xs, bottom = clearance + if (addButton) ADD_BUTTON_CLEARANCE else Spacing.l),
    ) {
        header?.let { item(key = "list-header") { Box(Modifier.padding(bottom = Spacing.m)) { it() } } }
        groups.forEachIndexed { index, (head, rows) ->
            if (index > 0) item(key = "gap:$index") { Spacer(Modifier.height(Spacing.m)) }
            val open = head == null || !head.collapsible || head.title in expanded
            head?.let {
                item(key = "header:${it.title}") {
                    Segment(it.cards, top = true, bottom = !open, Modifier.animateItem(), first = true) {
                        GroupHeader(it, rows.size, open) { expanded = if (open) expanded - it.title else expanded + it.title }
                    }
                }
            }
            if (open) groupRows(head, rows, key, subgroup, row)
        }
        footer?.let { item(key = "list-footer") { Box(Modifier.padding(top = Spacing.m)) { it() } } }
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
    val cards = head?.cards == true
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
                Segment(cards, top = top, bottom = bottom, Modifier.animateItem()) { SubgroupLabel(line.subgroup) }
            }
            // Nycklar måste kunna sparas i en Bundle: en rad har sin egen, flera i kolumner en sammansatt sträng.
            is Line.Rows -> item(key = line.members.singleOrNull()?.let(key) ?: line.members.joinToString("|") { key(it).toString() }) {
                Segment(cards, top = top, bottom = bottom, Modifier.animateItem()) {
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

/**
 * En bit av en grupp: i ett kort ([CardSegment]) eller – för en grupp av postkort ([ListGroup.cards]) –
 * direkt på bakgrunden, med kortavstånd ovanför allt utom rubriken ([first]).
 */
@Composable
private fun Segment(cards: Boolean, top: Boolean, bottom: Boolean, modifier: Modifier, first: Boolean = false, content: @Composable ColumnScope.() -> Unit) {
    if (cards) {
        Column(modifier.padding(top = if (first) 0.dp else Spacing.s), content = content)
    } else {
        CardSegment(top = top, bottom = bottom, modifier, content = content)
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
    val count = if (group.showCount) group.count ?: size.toString() else null
    if (!group.collapsible) {
        SectionHeader(group.title, icon = group.icon, count = count, tone = group.tone)
        return
    }
    CollapsibleHeader(open, onToggle) {
        SectionHeader(group.title, Modifier.weight(1f), icon = group.icon, count = count, tone = group.tone)
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

/** Lägg till-knappens tagg (i listan och i det tomma tillståndet) – kontraktstestet prövar att den saknas utan [AddAction]. */
internal const val ADD_BUTTON_TAG = "entity-list-add"
