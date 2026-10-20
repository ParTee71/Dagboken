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
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalHapticFeedback
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
import se.partee71.dagboken.core.model.FeelingStrength
import se.partee71.dagboken.core.model.HasFeelings
import se.partee71.dagboken.core.schema.TextLimits
import se.partee71.dagboken.ui.common.feelingName
import se.partee71.dagboken.ui.common.feelingsLabel
import se.partee71.dagboken.ui.common.withStrength
import se.partee71.dagboken.ui.theme.AppColors
import se.partee71.dagboken.ui.theme.AppShapes
import se.partee71.dagboken.ui.theme.AppTypography
import se.partee71.ui.components.AppButton
import se.partee71.ui.components.AppTextField
import se.partee71.ui.components.ButtonVariant
import se.partee71.ui.components.ChipRow
import se.partee71.ui.components.LabeledGroup
import se.partee71.ui.components.inactive
import se.partee71.ui.theme.Spacing
import se.partee71.ui.theme.ToneColors

/**
 * Styrka och kommentar per vald känsla i [FeelingPicker] (SCR-9, SCR-10), lästa och ändrade av anroparen. [onStronger]
 * `null` = ingen styrka (inget långtryck, inga intensiteter); [onNoteChange] `null` = inga kommentarsfält. [None] är
 * väljaren som förut. [of] bygger dem ur en post med känslor (`HasFeelings`).
 */
@Immutable
class FeelingExtras(
    val strengthOf: (Feeling) -> FeelingStrength? = { null },
    val onStronger: ((Feeling) -> Unit)? = null,
    val noteOf: (Feeling) -> String = { "" },
    val onNoteChange: ((Feeling, String) -> Unit)? = null
) {
    companion object {
        val None = FeelingExtras()

        /** Styrka och kommentar ur [post]; varje ändring går till [change] som en ändring av posten ([HasFeelings]). */
        fun <T : HasFeelings<T>> of(post: T, change: ((T) -> T) -> Unit) = FeelingExtras(
            strengthOf = post::strengthOf,
            onStronger = { feeling -> change { it.withNextStrength(feeling) } },
            noteOf = post::noteOf,
            onNoteChange = { feeling, note -> change { it.withNote(feeling, note) } }
        )

        /** Styrka och kommentar ur [post] för visning (postkortet, tillfällesraden) – inget går att ändra. */
        fun shown(post: HasFeelings<*>) = FeelingExtras(strengthOf = post::strengthOf, noteOf = post::noteOf)
    }
}

/**
 * Känslosteget (SCR-7, variant B2 i #318): rubriken "Vad känner du just nu?" med hjälptext, [FeelingWheel] som ingång
 * och de valda känslorna som pills i kärnans ton under ("Lugn › Tacksam", `feelingsLabel`) – tryck på en pill tar bort
 * den. Tryck på en kärna i hjulet väljer den direkt – man får stanna på första nivån ("Lugn") – och visar den som nav
 * överst med dess sex nyanser som liggande knappar i två spalter; den aktiva valda nyansen visar "Mer precist:" med
 * sina två precisare ord som knappar i samma ton – när en kärna öppnas är det den första valda nyansen under den. Ett
 * precisare ord eller en nyans ersätter det grövre valet på samma plats i listan. Tryck på en vald nyans som inte är
 * aktiv gör den aktiv; tryck på den aktiva går tillbaka till kärnan ensam (tas bort om kärnan redan står kvar i ett
 * annat val), och ett nytt tryck på ett valt precisare ord går tillbaka till nyansen. Navet visar kärnan och har en
 * ring när den är vald ensam; ett val tas bort via sin pill. "Tillbaka till hjulet" behåller valen.
 * Högst [maxSelected] val: därefter ignoreras nya val och knapparna som skulle lägga till är inaktiva. Vilken kärna
 * som är öppen och vilken nyans som visar sina ord är komponentens eget tillstånd; valen ägs av anroparen
 * ([selected], [onSelectedChange]). [title] är frågan över hjulet – måendearkets "Vad känner du just nu?" som standard,
 * aktivitetens "Vad kände du?" (AKT-13).
 *
 * [extras] (SCR-9, SCR-10): **långtryck** på det yttersta valda ordet – navet när bara kärnan är vald, annars nyansen
 * eller det precisare ordet – gör känslan ett steg starkare ([longPressAction], TalkBack-åtgärden "Starkare"); ordet,
 * pillen och hjulets sektor fylls med kärnans ton i styrkans intensitet, med 1–3 prickar, och TalkBack läser styrkan
 * som ord. Under pillsen har varje vald känsla ett enradigt kommentarsfält "Varför? (valfritt)".
 */
@Composable
fun FeelingPicker(
    selected: List<Feeling>,
    onSelectedChange: (List<Feeling>) -> Unit,
    modifier: Modifier = Modifier,
    title: String = stringResource(R.string.feelings_title),
    maxSelected: Int = FeelingCatalog.MAX_FEELINGS,
    extras: FeelingExtras = FeelingExtras.None
) {
    var openCore by rememberSaveable { mutableStateOf<String?>(null) }
    var activeNuance by rememberSaveable { mutableStateOf<String?>(null) }
    val full = selected.size >= maxSelected
    val helper = when {
        full -> stringResource(R.string.feelings_max_format, maxSelected)
        extras.onStronger != null && selected.isNotEmpty() -> stringResource(R.string.feelings_strength_help)
        else -> stringResource(R.string.feelings_help)
    }
    LabeledGroup(title, modifier.fillMaxWidth(), helper = helper, helperAbove = true) {
        val core = openCore
        if (core == null) {
            FeelingWheel(selected.mapTo(mutableSetOf()) { it.core }, { opened ->
                openCore = opened
                // En redan vald nyans visar sina ord direkt, så att den kan förfinas på sin plats.
                activeNuance = selected.firstOrNull { it.core == opened && it.nuance != null }?.nuance
                // Kärnan väljs direkt (första nivån räcker); en nyans förfinar den sedan på samma plats.
                FeelingSelection.selectCore(selected, opened, full)?.let(onSelectedChange)
            }, strengths = strongestPerCore(selected, extras.strengthOf))
        } else {
            CoreView(core, selected, full, activeNuance?.takeIf { n -> selected.any { it.core == core && it.nuance == n } }, extras) { change ->
                activeNuance = change.active
                onSelectedChange(change.selected)
            }
        }
        SelectedPills(selected, extras.strengthOf) { index -> onSelectedChange(selected.filterIndexed { i, _ -> i != index }) }
        extras.onNoteChange?.let { onNoteChange -> FeelingNotes(selected, extras.noteOf, onNoteChange) }
        if (core != null) {
            AppButton(
                stringResource(R.string.feelings_back_to_wheel),
                {
                    openCore = null
                    activeNuance = null
                },
                variant = ButtonVariant.Text,
                icon = R.drawable.ic_chevron_left
            )
        }
    }
}

/** Den starkaste kända styrkan bland valen i varje kärna – hjulets sektor visar den (SCR-9). */
private fun strongestPerCore(selected: List<Feeling>, strengthOf: (Feeling) -> FeelingStrength?): Map<String, FeelingStrength> = selected.mapNotNull { f -> strengthOf(f)?.let { f.core to it } }.groupBy({ it.first }, { it.second }).mapValues { (_, s) -> s.max() }

/** Ett val i kärnvyn: den nya listan och vilken nyans som visar sina precisare ord. */
private class Change(val selected: List<Feeling>, val active: String?)

/** Kärnan som nav, nyanserna i två spalter och – för [active] nyans – dess precisare ord. */
@Composable
private fun CoreView(core: String, selected: List<Feeling>, full: Boolean, active: String?, extras: FeelingExtras, onChange: (Change) -> Unit) {
    val tones = AppColors.extended.feelingTones
    val tone = tones.forCore(core)
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Spacing.m)) {
        val hub = Feeling(core).takeIf { it in selected }
        Hub(core, hub?.let(extras.strengthOf)?.let { tones.forCore(core, it) } ?: tone, chosen = hub != null, strength = hub?.let(extras.strengthOf), onStronger = hub?.let { h -> extras.onStronger?.let { { it(h) } } })
        FeelingCatalog.nuances(core).chunked(COLUMNS).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                row.forEach { nuance ->
                    val choice = selected.firstOrNull { it.core == core && it.nuance == nuance }
                    // Bara det yttersta valda ordet bär styrkan: nyansen när inget precisare ord är valt.
                    val outermost = choice?.takeIf { it.detail == null }
                    FeelingWord(
                        feelingName(core, nuance),
                        core,
                        choice != null,
                        enabled = choice != null || FeelingSelection.canAdd(selected, core, full),
                        shape = AppShapes.feelingNuance,
                        outermost = outermost,
                        extras = extras,
                        modifier = Modifier.weight(1f).heightIn(min = NUANCE_HEIGHT)
                    ) {
                        when {
                            // Vald men inte aktiv: visa dess ord, ta inte bort.
                            choice != null && nuance != active -> onChange(Change(selected, nuance))

                            else -> onChange(Change(FeelingSelection.toggleNuance(selected, core, nuance, full), nuance.takeUnless { choice != null }))
                        }
                    }
                }
            }
        }
        if (active != null) Details(core, active, selected, extras) { onChange(Change(it, active)) }
    }
}

/**
 * Kärnan överst som en halv skiva i sin ton ([colors]: kärnans, eller styrkans intensitet när den är vald ensam) – bara
 * visning för vanligt tryck; vald (bara kärnan) har en ring innanför kanten och tar emot långtrycket ([onStronger]).
 */
@Composable
private fun Hub(core: String, colors: ToneColors, chosen: Boolean, strength: FeelingStrength?, onStronger: (() -> Unit)?) {
    val name = stringResource(feelingName(core))
    val description = withStrength(name, strength)
    val selectedState = stringResource(R.string.feeling_selected_state)
    val stronger = stringResource(R.string.feeling_stronger)
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Box(
            Modifier.size(HUB_WIDTH, HUB_HEIGHT)
                .clip(AppShapes.feelingHub)
                .background(colors.content)
                .longPressAction(stronger, LocalHapticFeedback.current, onStronger)
                .semantics {
                    contentDescription = description
                    if (chosen) stateDescription = selectedState
                },
            contentAlignment = Alignment.Center
        ) {
            // Ringen ligger en bit innanför kanten, så att den syns mot fyllnaden i båda temana.
            if (chosen) Box(Modifier.matchParentSize().padding(Spacing.xs).border(RING, colors.container, AppShapes.feelingHub))
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(name, Modifier.clearAndSetSemantics {}, style = AppTypography.itemTitle, color = colors.container)
                StrengthDots(strength, colors.container)
            }
        }
    }
}

/**
 * Ett ord i kärnvyn – en nyans (liggande blad) eller ett precisare ord ([shape]) – i kärnans ton, fyllt med
 * innehållsfärgen när det är valt. [outermost] är valet när ordet är det yttersta valda: det bär då styrkan
 * ([FeelingExtras]) i sin fyllnad, med prickar, och tar emot långtrycket.
 */
@Composable
private fun FeelingWord(
    @StringRes nameRes: Int,
    core: String,
    chosen: Boolean,
    enabled: Boolean,
    shape: Shape,
    outermost: Feeling?,
    extras: FeelingExtras,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val name = stringResource(nameRes)
    val strength = outermost?.let(extras.strengthOf)
    val description = withStrength(name, strength)
    val selectedState = stringResource(R.string.feeling_selected_state)
    val stronger = stringResource(R.string.feeling_stronger)
    val colors = AppColors.extended.feelingTones.chosen(core, chosen, strength)
    Box(
        modifier
            .inactive(!enabled)
            .clip(shape)
            .background(colors.container)
            .longPressAction(stronger, LocalHapticFeedback.current, outermost?.let { o -> extras.onStronger?.let { { it(o) } } })
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .semantics {
                contentDescription = description
                if (chosen) stateDescription = selectedState
            }
            .padding(horizontal = Spacing.m, vertical = Spacing.s),
        contentAlignment = Alignment.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            Text(name, Modifier.clearAndSetSemantics {}, style = AppTypography.itemTitle, color = colors.content, textAlign = TextAlign.Center)
            StrengthDots(strength, colors.content)
        }
    }
}

/** "Mer precist:" och nyansens två precisare ord; ett tryck byter valet på samma plats, ett nytt tryck går tillbaka. */
@Composable
private fun Details(core: String, nuance: String, selected: List<Feeling>, extras: FeelingExtras, onChange: (List<Feeling>) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.s), verticalAlignment = Alignment.CenterVertically) {
        Text(stringResource(R.string.feelings_more_precise), style = AppTypography.itemSubtitle, color = MaterialTheme.colorScheme.onSurfaceVariant)
        ChipRow(Modifier.weight(1f)) {
            FeelingCatalog.details(core, nuance).forEach { detail ->
                val feeling = Feeling(core, nuance, detail).takeIf { it in selected }
                FeelingWord(
                    feelingName(core, nuance, detail),
                    core,
                    feeling != null,
                    enabled = true,
                    shape = AppShapes.pill,
                    outermost = feeling,
                    extras = extras,
                    modifier = Modifier.heightIn(min = TOUCH_TARGET)
                ) { onChange(FeelingSelection.toggleDetail(selected, core, nuance, detail)) }
            }
        }
    }
}

/** De valda känslorna som pills i kärnans ton (i styrkans intensitet); tryck tar bort ("Ta bort Lugn › Tacksam"). */
@Composable
private fun SelectedPills(selected: List<Feeling>, strengthOf: (Feeling) -> FeelingStrength?, onRemove: (Int) -> Unit) {
    if (selected.isEmpty()) return
    ChipRow {
        selected.forEachIndexed { index, feeling ->
            val label = feelingsLabel(feeling)
            FeelingPill(
                feeling,
                strength = strengthOf(feeling),
                onClick = { onRemove(index) },
                trailingIcon = R.drawable.ic_close,
                onClickLabel = stringResource(R.string.feelings_remove_format, label)
            )
        }
    }
}

/** Ett enradigt kommentarsfält per vald känsla (SCR-10): känslans namn som etikett, "Varför? (valfritt)" i fältet. */
@Composable
private fun FeelingNotes(selected: List<Feeling>, noteOf: (Feeling) -> String, onNoteChange: (Feeling, String) -> Unit) {
    selected.forEach { feeling ->
        val label = feelingsLabel(feeling)
        val description = stringResource(R.string.feeling_note_description_format, label)
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            Text(label, Modifier.clearAndSetSemantics {}, style = AppTypography.itemSubtitle, color = MaterialTheme.colorScheme.onSurfaceVariant)
            AppTextField(
                noteOf(feeling),
                { onNoteChange(feeling, it) },
                stringResource(R.string.feeling_note_label),
                Modifier.semantics { contentDescription = description },
                singleLine = true,
                maxLength = TextLimits.SHORT
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

    /** Kärnan ensam sist i listan när inget val har kärnan och det finns plats; annars `null` (ingen ändring). */
    fun selectCore(selected: List<Feeling>, core: String, full: Boolean): List<Feeling>? = if (full || selected.any { it.core == core }) null else selected + Feeling(core)

    /**
     * En nyans: vald (med sitt precisare ord) går tillbaka till kärnan ensam på samma plats – eller bort, om kärnan
     * redan står kvar i ett annat val; annars i stället för kärnan ensam eller sist i listan.
     */
    fun toggleNuance(selected: List<Feeling>, core: String, nuance: String, full: Boolean): List<Feeling> {
        val chosen = selected.indexOfFirst { it.core == core && it.nuance == nuance }
        if (chosen >= 0) {
            val coreElsewhere = selected.withIndex().any { (i, f) -> i != chosen && f.core == core }
            return if (coreElsewhere) selected.filterIndexed { i, _ -> i != chosen } else selected.replace(chosen, Feeling(core))
        }
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
