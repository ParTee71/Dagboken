package se.partee71.dagboken.ui.components

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import se.partee71.dagboken.R
import se.partee71.dagboken.core.model.Feeling
import se.partee71.dagboken.core.model.FeelingCatalog
import se.partee71.dagboken.ui.common.feelingName
import se.partee71.dagboken.ui.common.feelingsLabel
import se.partee71.dagboken.ui.theme.AppColors
import se.partee71.dagboken.ui.theme.AppShapes
import se.partee71.dagboken.ui.theme.AppTypography
import se.partee71.dagboken.ui.theme.Spacing
import se.partee71.dagboken.ui.theme.ToneColors

/**
 * Känslosteget (SCR-7, variant B2 i #318): rubriken "Vad känner du just nu?" med hjälptext, [FeelingWheel] som ingång
 * och de valda känslorna som pills i kärnans ton under ("Lugn › Tacksam", `feelingsLabel`) – tryck på en pill tar bort
 * den. Tryck på en kärna visar den som nav överst (tryck väljer bara kärnan) och dess sex nyanser som liggande knappar
 * i två spalter; den aktiva valda nyansen visar "Mer precist:" med sina två precisare ord som [AppFilterChip] – när en
 * kärna öppnas är det den första valda nyansen under den. Ett precisare ord eller en nyans ersätter det grövre valet
 * på samma plats i listan. Tryck på en vald nyans som inte är aktiv gör den aktiv; tryck på den aktiva tar bort den,
 * och ett nytt tryck på ett valt precisare ord går tillbaka till nyansen. "Tillbaka till hjulet" behåller valen.
 * Högst [maxSelected] val: därefter ignoreras nya val och knapparna som skulle lägga till är inaktiva. Vilken kärna
 * som är öppen och vilken nyans som visar sina ord är komponentens eget tillstånd; valen ägs av anroparen
 * ([selected], [onSelectedChange]).
 */
@Composable
fun FeelingPicker(
    selected: List<Feeling>,
    onSelectedChange: (List<Feeling>) -> Unit,
    modifier: Modifier = Modifier,
    maxSelected: Int = FeelingCatalog.MAX_FEELINGS,
) {
    var openCore by rememberSaveable { mutableStateOf<String?>(null) }
    var activeNuance by rememberSaveable { mutableStateOf<String?>(null) }
    val full = selected.size >= maxSelected
    val helper = if (full) stringResource(R.string.feelings_max_format, maxSelected) else stringResource(R.string.feelings_help)
    LabeledGroup(stringResource(R.string.feelings_title), modifier.fillMaxWidth(), helper = helper, helperAbove = true) {
        val core = openCore
        if (core == null) {
            FeelingWheel(selected.mapTo(mutableSetOf()) { it.core }, { opened ->
                openCore = opened
                // En redan vald nyans visar sina ord direkt, så att den kan förfinas på sin plats.
                activeNuance = selected.firstOrNull { it.core == opened && it.nuance != null }?.nuance
            })
        } else {
            CoreView(core, selected, full, activeNuance?.takeIf { n -> selected.any { it.core == core && it.nuance == n } }) { change ->
                activeNuance = change.active
                onSelectedChange(change.selected)
            }
        }
        SelectedPills(selected) { index -> onSelectedChange(selected.filterIndexed { i, _ -> i != index }) }
        if (core != null) {
            AppButton(
                stringResource(R.string.feelings_back_to_wheel),
                { openCore = null; activeNuance = null },
                variant = ButtonVariant.Text,
                icon = R.drawable.ic_chevron_left,
            )
        }
    }
}

/** Ett val i kärnvyn: den nya listan och vilken nyans som visar sina precisare ord. */
private class Change(val selected: List<Feeling>, val active: String?)

/** Kärnan som nav, nyanserna i två spalter och – för [active] nyans – dess precisare ord. */
@Composable
private fun CoreView(core: String, selected: List<Feeling>, full: Boolean, active: String?, onChange: (Change) -> Unit) {
    val tone = AppColors.extended.feelingTones.forCore(core)
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Spacing.m)) {
        val coreOnly = Feeling(core) in selected
        Hub(core, tone, coreOnly, enabled = coreOnly || !full) {
            onChange(Change(FeelingSelection.toggleCore(selected, core, full), null))
        }
        FeelingCatalog.nuances(core).chunked(COLUMNS).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                row.forEach { nuance ->
                    val chosen = selected.any { it.core == core && it.nuance == nuance }
                    NuanceButton(feelingName(core, nuance), tone, chosen, enabled = chosen || FeelingSelection.canAdd(selected, core, full), Modifier.weight(1f)) {
                        when {
                            // Vald men inte aktiv: visa dess ord, ta inte bort.
                            chosen && nuance != active -> onChange(Change(selected, nuance))
                            else -> onChange(Change(FeelingSelection.toggleNuance(selected, core, nuance, full), nuance.takeUnless { chosen }))
                        }
                    }
                }
            }
        }
        if (active != null) Details(core, active, selected) { onChange(Change(it, active)) }
    }
}

/** Kärnan överst som en halv skiva i sin ton; vald (bara kärnan) har en ring innanför kanten. */
@Composable
private fun Hub(core: String, tone: ToneColors, chosen: Boolean, enabled: Boolean, onClick: () -> Unit) {
    val name = stringResource(feelingName(core))
    val selectedState = stringResource(R.string.feeling_selected_state)
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Box(
            Modifier.size(HUB_WIDTH, HUB_HEIGHT)
                .inactive(!enabled)
                .clip(AppShapes.feelingHub)
                .background(tone.content)
                .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
                .semantics {
                    contentDescription = name
                    if (chosen) stateDescription = selectedState
                },
            contentAlignment = Alignment.Center,
        ) {
            // Ringen ligger en bit innanför kanten, så att den syns mot fyllnaden i båda temana.
            if (chosen) Box(Modifier.matchParentSize().padding(Spacing.xs).border(RING, tone.container, AppShapes.feelingHub))
            Text(name, Modifier.clearAndSetSemantics {}, style = AppTypography.itemTitle, color = tone.container)
        }
    }
}

/** En nyans: ett liggande blad i kärnans ton, fyllt med innehållsfärgen när den är vald. */
@Composable
private fun NuanceButton(@StringRes nameRes: Int, tone: ToneColors, chosen: Boolean, enabled: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val name = stringResource(nameRes)
    val selectedState = stringResource(R.string.feeling_selected_state)
    Box(
        modifier.heightIn(min = NUANCE_HEIGHT)
            .inactive(!enabled)
            .clip(AppShapes.feelingNuance)
            .background(if (chosen) tone.content else tone.container)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .semantics {
                contentDescription = name
                if (chosen) stateDescription = selectedState
            }
            .padding(horizontal = Spacing.m, vertical = Spacing.s),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            name,
            Modifier.clearAndSetSemantics {},
            style = AppTypography.itemTitle,
            color = if (chosen) tone.container else tone.content,
            textAlign = TextAlign.Center,
        )
    }
}

/** "Mer precist:" och nyansens två precisare ord; ett tryck byter valet på samma plats, ett nytt tryck går tillbaka. */
@Composable
private fun Details(core: String, nuance: String, selected: List<Feeling>, onChange: (List<Feeling>) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.s), verticalAlignment = Alignment.CenterVertically) {
        Text(stringResource(R.string.feelings_more_precise), style = AppTypography.itemSubtitle, color = MaterialTheme.colorScheme.onSurfaceVariant)
        ChipRow(Modifier.weight(1f)) {
            FeelingCatalog.details(core, nuance).forEach { detail ->
                val chosen = Feeling(core, nuance, detail) in selected
                AppFilterChip(stringResource(feelingName(core, nuance, detail)), chosen, { onChange(FeelingSelection.toggleDetail(selected, core, nuance, detail)) })
            }
        }
    }
}

/** De valda känslorna som pills i kärnans ton; tryck tar bort ("Ta bort Lugn › Tacksam"). */
@Composable
private fun SelectedPills(selected: List<Feeling>, onRemove: (Int) -> Unit) {
    if (selected.isEmpty()) return
    val tones = AppColors.extended.feelingTones
    ChipRow {
        selected.forEachIndexed { index, feeling ->
            val label = feelingsLabel(feeling)
            InfoPill(
                label,
                colors = tones.forCore(feeling.core),
                onClick = { onRemove(index) },
                trailingIcon = R.drawable.ic_close,
                onClickLabel = stringResource(R.string.feelings_remove_format, label),
            )
        }
    }
}

/**
 * Valreglerna i [FeelingPicker] (SCR-7), utan UI: ett val läggs till sist, ett förfinat val ersätter det grövre på
 * samma plats, och när listan är full ([full]) läggs inget nytt till.
 */
internal object FeelingSelection {
    /** Om en nyans under [core] kan väljas: det finns plats, eller kärnan står ensam och kan förfinas. */
    fun canAdd(selected: List<Feeling>, core: String, full: Boolean) = !full || Feeling(core) in selected

    /** Bara kärnan: av om den är vald, annars sist i listan. */
    fun toggleCore(selected: List<Feeling>, core: String, full: Boolean): List<Feeling> = when {
        Feeling(core) in selected -> selected - Feeling(core)
        full -> selected
        else -> selected + Feeling(core)
    }

    /** En nyans: av (med sitt precisare ord) om den är vald, annars i stället för kärnan ensam eller sist i listan. */
    fun toggleNuance(selected: List<Feeling>, core: String, nuance: String, full: Boolean): List<Feeling> {
        if (selected.any { it.core == core && it.nuance == nuance }) return selected.filterNot { it.core == core && it.nuance == nuance }
        val coreOnly = selected.indexOf(Feeling(core))
        return when {
            coreOnly >= 0 -> selected.replace(coreOnly, Feeling(core, nuance))
            full -> selected
            else -> selected + Feeling(core, nuance)
        }
    }

    /** Ett precisare ord: ersätter nyansens val på samma plats; ett nytt tryck på det valda går tillbaka till nyansen. */
    fun toggleDetail(selected: List<Feeling>, core: String, nuance: String, detail: String): List<Feeling> {
        val index = selected.indexOfFirst { it.core == core && it.nuance == nuance }
        if (index < 0) return selected
        val next = if (selected[index].detail == detail) null else detail
        return selected.replace(index, Feeling(core, nuance, next))
    }

    private fun List<Feeling>.replace(index: Int, feeling: Feeling) = toMutableList().also { it[index] = feeling }
}

/** Nyanserna står i två spalter. */
private const val COLUMNS = 2

/** Navet är en halv skiva: bredden dubbla höjden ([AppShapes.feelingHub]). */
private val HUB_HEIGHT = 72.dp
private val HUB_WIDTH = HUB_HEIGHT * 2
private val NUANCE_HEIGHT = 52.dp
private val RING = 2.dp
