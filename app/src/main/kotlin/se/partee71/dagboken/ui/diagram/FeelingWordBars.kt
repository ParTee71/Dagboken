package se.partee71.dagboken.ui.diagram

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import se.partee71.dagboken.R
import se.partee71.dagboken.core.engine.CoreCount
import se.partee71.dagboken.core.engine.FeelingTally
import se.partee71.dagboken.core.model.FeelingStrength
import se.partee71.dagboken.ui.common.feelingName
import se.partee71.dagboken.ui.common.strengthName
import se.partee71.dagboken.ui.theme.AppColors
import se.partee71.dagboken.ui.theme.AppShapes
import se.partee71.dagboken.ui.theme.AppTypography
import se.partee71.ui.theme.Spacing

/**
 * Ordträdet (#320, Trender → Känslor): en liggande stapel per kärnkänsla, vanligast först (lika → hjulets ordning, utan
 * val sist), med kärnans namn framför. Längden är [CoreCount.weight] (den vanligaste fyller raden), stapeln i kärnans
 * ton och nyanserna som ord inne i stapeln, vanligast först – de som får plats. Inga axlar och inga tal. TalkBack läser
 * rad för rad: "Lugn: tacksam, avslappnad", "Stark, inte vald".
 *
 * Med [strengths] (#396, SCR-9) delas stapeln i segment efter styrka – ingen styrka i kärnans ton, sedan svag, tydlig och
 * stark i kärnans styrketoner (`feelingTones.forCore(core, strength)`), längd efter antalet val och minst
 * [MIN_STRENGTH_SEGMENT] så att ett enstaka val syns. Orden står då under stapeln med kärnans namn först, en
 * teckenförklaring "Ingen styrka ▢▢▢▢ Stark" står sist, och TalkBack lägger till den vanligaste styrkan ("…, oftast
 * stark"). Utan [strengths] (default) som förut.
 */
@Composable
fun FeelingWordBars(tally: FeelingTally, modifier: Modifier = Modifier, strengths: Boolean = false) {
    val names = tally.byCount.map { stringResource(feelingName(it.core)) }
    val nameWidth = widestName(names)
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
        tally.byCount.forEachIndexed { index, count ->
            if (strengths) StrengthBar(count, names[index]) else WordBar(count, names[index], nameWidth)
        }
        if (strengths) StrengthLegend(tally.byCount.firstOrNull()?.core.orEmpty())
    }
}

@Composable
private fun WordBar(count: CoreCount, name: String, nameWidth: Dp) {
    val words = nuanceWords(count)
    val spoken = spokenRow(count, name, words, null)
    val tone = AppColors.extended.feelingTones.forCore(count.core)
    Row(Modifier.fillMaxWidth().heightIn(min = WORD_BAR_HEIGHT).clearAndSetSemantics { contentDescription = spoken }, verticalAlignment = Alignment.CenterVertically) {
        Text(name, Modifier.width(nameWidth), style = AppTypography.pill, maxLines = 1)
        if (count.count > 0) {
            Box(
                Modifier.padding(start = Spacing.s)
                    .fillMaxWidth(count.weight)
                    .heightIn(min = WORD_BAR_HEIGHT)
                    .clip(AppShapes.smallTile)
                    .background(tone.container)
                    .padding(horizontal = Spacing.m),
                contentAlignment = Alignment.CenterStart
            ) {
                FittingWords {
                    words.forEach { Text(it, style = AppTypography.itemSubtitle, color = tone.content, maxLines = 1, softWrap = false) }
                }
            }
        }
    }
}

/** En stapel delad i styrkans segment, med kärnans namn och nyanserna under (mockupen #396). */
@Composable
private fun StrengthBar(count: CoreCount, name: String) {
    val words = nuanceWords(count)
    val dominant = count.byStrength.filterValues { it > 0 }.entries.maxWithOrNull(compareBy({ it.value }, { it.key.ordinal }))?.key
    val spoken = spokenRow(count, name, words, dominant)
    val tones = AppColors.extended.feelingTones
    val segments = listOf(count.unrated to tones.forCore(count.core).container) +
        FeelingStrength.entries.map { (count.byStrength[it] ?: 0) to tones.forCore(count.core, it).container }
    Column(Modifier.fillMaxWidth().clearAndSetSemantics { contentDescription = spoken }, verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        if (count.count > 0) {
            Box(
                Modifier.fillMaxWidth(count.weight)
                    .height(WORD_BAR_HEIGHT)
                    .clip(AppShapes.smallTile)
                    .drawBehind { drawSegments(segments.filter { it.first > 0 }, MIN_STRENGTH_SEGMENT.toPx()) }
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s), verticalAlignment = Alignment.CenterVertically) {
            Text(name, style = AppTypography.pill, color = tones.forCore(count.core).content, maxLines = 1)
            if (words.isNotEmpty()) {
                Text(words.joinToString(WORD_SEPARATOR) { it.replaceFirstChar(Char::titlecase) }, style = AppTypography.itemSubtitle, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

/**
 * Segmenten från vänster i given ordning: varje segment minst [minWidth] (ryms inte alla får de lika delar), resten
 * fördelas efter antalet val.
 */
private fun DrawScope.drawSegments(segments: List<Pair<Int, Color>>, minWidth: Float) {
    if (segments.isEmpty()) return
    val total = segments.sumOf { it.first }.toFloat()
    val floor = minWidth.coerceAtMost(size.width / segments.size)
    val spare = size.width - floor * segments.size
    var x = 0f
    segments.forEach { (n, color) ->
        val width = floor + spare * n / total
        drawRect(color, topLeft = Offset(x, 0f), size = Size(width, size.height))
        x += width
    }
}

/** "Ingen styrka ▢▢▢▢ Stark" i [core]s toner; TalkBack läser den som en mening. */
@Composable
private fun StrengthLegend(core: String) {
    val tones = AppColors.extended.feelingTones
    val none = stringResource(R.string.feeling_bars_legend_none)
    val strong = stringResource(R.string.feeling_bars_legend_strong)
    val description = stringResource(R.string.feeling_bars_legend_description)
    val colors = listOf(tones.forCore(core).container) + FeelingStrength.entries.map { tones.forCore(core, it).container }
    Row(
        Modifier.clearAndSetSemantics { contentDescription = description },
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(none, style = AppTypography.caption, color = MaterialTheme.colorScheme.onSurfaceVariant)
        colors.forEach { Box(Modifier.size(LEGEND_STRENGTH_SWATCH, LEGEND_SWATCH).clip(AppShapes.smallTile).background(it)) }
        Text(strong, style = AppTypography.caption, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Nyanserna i gemener, vanligast först. */
@Composable
private fun nuanceWords(count: CoreCount): List<String> = count.nuances.map { stringResource(feelingName(count.core, it.nuance)).lowercase() }

/** Radens TalkBack-text: "Lugn: tacksam, avslappnad", med [dominant] "…, oftast stark"; utan val "Stark, inte vald". */
@Composable
private fun spokenRow(count: CoreCount, name: String, words: List<String>, dominant: FeelingStrength?): String {
    val row = when {
        count.count == 0 -> return stringResource(R.string.feeling_bars_row_none, name)
        words.isEmpty() -> name
        else -> stringResource(R.string.feeling_bars_row_format, name, words.joinToString(", "))
    }
    return dominant?.let { stringResource(R.string.feeling_bars_row_strength, row, stringResource(strengthName(it))) } ?: row
}

private const val WORD_SEPARATOR = " · "

/** Orden i stapeln på en rad med [Spacing.s] emellan – de som inte får plats hela placeras inte. */
@Composable
internal fun FittingWords(content: @Composable () -> Unit) {
    Layout(content) { measurables, constraints ->
        val gap = Spacing.s.roundToPx()
        val placeables = measurables.map { it.measure(constraints.copy(minWidth = 0, maxWidth = Int.MAX_VALUE)) }
        var used = 0
        val fitting = placeables.takeWhile { placeable ->
            val start = if (used == 0) 0 else used + gap
            (start + placeable.width <= constraints.maxWidth).also { if (it) used = start + placeable.width }
        }
        val height = placeables.maxOfOrNull { it.height } ?: 0
        layout(if (constraints.hasBoundedWidth) constraints.maxWidth else used, height.coerceAtLeast(constraints.minHeight)) {
            var x = 0
            fitting.forEach {
                it.placeRelative(x, (height - it.height) / 2)
                x += it.width + gap
            }
        }
    }
}

/** Bredaste kärnnamnet i namnens stil, så att alla staplar börjar på samma ställe även med stor text. */
@Composable
private fun widestName(names: List<String>): Dp {
    val measurer = rememberTextMeasurer()
    val style = AppTypography.pill
    val density = LocalDensity.current
    return remember(names, style, density) {
        with(density) { (names.maxOfOrNull { measurer.measure(it, style).size.width } ?: 0).toDp() }
    }
}
