package se.partee71.dagboken.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import se.partee71.dagboken.core.engine.CoreCount
import se.partee71.dagboken.core.engine.FeelingTally
import se.partee71.dagboken.ui.common.feelingName
import se.partee71.dagboken.ui.theme.AppColors
import se.partee71.dagboken.ui.theme.AppShapes
import se.partee71.dagboken.ui.theme.AppTypography
import se.partee71.ui.theme.Spacing
import se.partee71.ui.theme.ToneColors

/**
 * Minihjulets storlek: [DAY] ett hjul per dag i Trenders vecka, [CARD] i Idags kort Dagens känslor, [HEATMAP] värmekartan
 * i Trenders kort Känslor – hela bredden upp till hjulets största diameter, med plats för [FeelingMiniWheel]s `labels`.
 */
enum class FeelingWheelSize(internal val diameter: Dp, internal val gap: Dp) {
    DAY(40.dp, 1.dp),
    CARD(88.dp, 2.dp),
    HEATMAP(WHEEL_MAX, 3.dp)
}

/**
 * Minihjulet (#320): de sex kärnkänslorna som sektorer i hjulets ordning (samma geometri som `FeelingWheel`), var och en
 * i kärnans ton (`AppColors.extended.feelingTones`) – fylld mot tonens innehållsfärg ju oftare kärnan valts
 * ([CoreCount.weight] i [tally]); en kärna utan val står i tonens behållarfärg. Inga tal. [labels] skriver kärnans namn
 * och dess (upp till tre) vanligaste nyanser i sektorn på en platta i tonens behållarfärg, för [FeelingWheelSize.HEATMAP]. [selected] ritar en mörk ring
 * runt hjulet (vald dag i veckan); tryck och valt läge för TalkBack ligger hos anroparen. Hjulet är en bild för TalkBack
 * som läser [contentDescription] – meningen i ord (`feelingSentence`).
 */
@Composable
fun FeelingMiniWheel(
    tally: FeelingTally,
    contentDescription: String,
    modifier: Modifier = Modifier,
    size: FeelingWheelSize = FeelingWheelSize.CARD,
    labels: Boolean = false,
    selected: Boolean = false
) {
    val described = modifier.clearAndSetSemantics {
        this.contentDescription = contentDescription
        role = Role.Image
    }
    if (size == FeelingWheelSize.HEATMAP) {
        BoxWithConstraints(described.fillMaxWidth(), contentAlignment = Alignment.Center) {
            MiniWheel(tally, min(maxWidth, size.diameter), size.gap, labels, selected, Modifier)
        }
    } else {
        MiniWheel(tally, size.diameter, size.gap, labels, selected, described)
    }
}

@Composable
private fun MiniWheel(tally: FeelingTally, diameter: Dp, gap: Dp, labels: Boolean, selected: Boolean, modifier: Modifier = Modifier) {
    val tones = AppColors.extended.feelingTones
    val fills = tally.cores.map { fillOf(it, tones.forCore(it.core)) }
    val ring = MaterialTheme.colorScheme.onSurface
    Box(
        modifier.size(diameter).drawBehind {
            if (selected) drawCircle(ring, radius = size.minDimension / 2 - RING_WIDTH.toPx() / 2, style = Stroke(RING_WIDTH.toPx()))
        }
    ) {
        Box(Modifier.fillMaxSize().padding(RING_INSET).feelingSectors(fills, gap, hubFraction = 0f))
        if (labels) {
            tally.cores.forEachIndexed { index, count ->
                SectorWords(count, tones.forCore(count.core), sectorOffset(index, (diameter / 2 - RING_INSET) * LABEL_FRACTION), diameter * LABEL_WIDTH_FRACTION)
            }
        }
    }
}

/**
 * Kärnans namn och dess vanligaste nyanser mitt i sektorn, på en platta i tonens behållarfärg med innehållsfärgen som
 * text – den paret klarar 4,5:1 (`ThemeContrastTest`), vilket ingen av dem gör mot en halvfylld sektor.
 */
@Composable
private fun BoxScope.SectorWords(count: CoreCount, tone: ToneColors, offset: Pair<Dp, Dp>, maxWidth: Dp) {
    Column(
        Modifier.align(Alignment.Center)
            .offset(offset.first, offset.second)
            .widthIn(max = maxWidth)
            .clip(AppShapes.smallTile)
            .background(tone.container)
            .padding(horizontal = Spacing.s, vertical = Spacing.xs),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(stringResource(feelingName(count.core)), style = AppTypography.pill, color = tone.content, maxLines = 1, overflow = TextOverflow.Ellipsis)
        count.topNuances().forEach { nuance ->
            Text(stringResource(feelingName(count.core, nuance)), style = AppTypography.itemSubtitle, color = tone.content, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** Sektorns färg: behållarfärgen utan val, annars från [MIN_FILL] till [MAX_FILL] mot innehållsfärgen efter vikten. */
internal fun fillOf(count: CoreCount, tone: ToneColors): Color = if (count.count == 0) tone.container else lerp(tone.container, tone.content, MIN_FILL + (MAX_FILL - MIN_FILL) * count.weight)

private const val MIN_FILL = 0.3f
private const val MAX_FILL = 0.9f
private const val LABEL_WIDTH_FRACTION = 0.34f
private val RING_WIDTH = 2.dp
private val RING_INSET = 3.dp
