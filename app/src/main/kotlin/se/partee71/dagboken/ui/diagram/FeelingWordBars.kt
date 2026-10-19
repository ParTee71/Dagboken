package se.partee71.dagboken.ui.diagram

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import se.partee71.dagboken.R
import se.partee71.dagboken.core.engine.CoreCount
import se.partee71.dagboken.core.engine.FeelingTally
import se.partee71.dagboken.ui.common.feelingName
import se.partee71.dagboken.ui.theme.AppColors
import se.partee71.dagboken.ui.theme.AppShapes
import se.partee71.dagboken.ui.theme.AppTypography
import se.partee71.dagboken.ui.theme.Spacing

/**
 * Ordträdet (#320, Trender → Känslor): en liggande stapel per kärnkänsla, vanligast först (lika → hjulets ordning, utan
 * val sist), med kärnans namn framför. Längden är [CoreCount.weight] (den vanligaste fyller raden), stapeln i kärnans
 * ton och nyanserna som ord inne i stapeln, vanligast först – de som får plats. Inga axlar och inga tal. TalkBack läser
 * rad för rad: "Lugn: tacksam, avslappnad", "Stark, inte vald".
 */
@Composable
fun FeelingWordBars(tally: FeelingTally, modifier: Modifier = Modifier) {
    val names = tally.byCount.map { stringResource(feelingName(it.core)) }
    val nameWidth = widestName(names)
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
        tally.byCount.forEachIndexed { index, count -> WordBar(count, names[index], nameWidth) }
    }
}

@Composable
private fun WordBar(count: CoreCount, name: String, nameWidth: Dp) {
    val words = count.nuances.map { stringResource(feelingName(count.core, it.nuance)).lowercase() }
    val spoken = when {
        count.count == 0 -> stringResource(R.string.feeling_bars_row_none, name)
        words.isEmpty() -> name
        else -> stringResource(R.string.feeling_bars_row_format, name, words.joinToString(", "))
    }
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
