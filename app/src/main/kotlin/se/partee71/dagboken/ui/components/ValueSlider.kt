@file:OptIn(ExperimentalMaterial3Api::class)

package se.partee71.dagboken.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import se.partee71.dagboken.R
import se.partee71.dagboken.ui.common.color
import se.partee71.dagboken.ui.common.scaleLevel
import se.partee71.dagboken.ui.common.scaleValueText
import se.partee71.dagboken.ui.theme.AppColors
import se.partee71.dagboken.ui.theme.AppTypography
import se.partee71.dagboken.ui.theme.TRACK_HEIGHT
import se.partee71.ui.components.GroupLabel
import se.partee71.ui.components.InfoPill
import se.partee71.ui.components.inactive
import se.partee71.ui.theme.Spacing

/**
 * Det enda reglaget (AKT-4, AKT-5, AKT-6, SCR-1): etikett, värdet som text och nivån som tonad pill
 * ([scaleLevel]) ovanför ett färgat spår ur energiskalan. [higherIsBetter] = `true` (energi,
 * sömnkvalitet) går från rött till grönt åt höger; `false` (stress, symptom, smärta) från grönt till
 * rött. Går skalan under noll (aktivitetens energi −10…+10) markeras nollan mitt på spåret och den
 * fyllda delen utgår från den. Färgen bär aldrig informationen ensam – text och pill visar samma
 * sak, och TalkBack läser etiketten, värdet och nivån ("Energi, 7, Hög").
 *
 * [step] låter reglaget bara stanna på var [step]:e värde från `valueRange.first` (standard varje heltal). En längd
 * eller ett antal som inte är en skala får [valueText]: värdet skrivs då med den texten ("2 min 15 s"), utan nivåpill,
 * och spåret är temats primärfärg i stället för energiskalan – TalkBack läser etiketten och texten (Stundens längd,
 * NOT-20). Utan [valueText] och [step] ser reglaget ut som förut.
 */
@Composable
fun ValueSlider(
    label: String,
    value: Int,
    onValueChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    valueRange: IntRange = 0..10,
    higherIsBetter: Boolean = true,
    enabled: Boolean = true,
    step: Int = 1,
    valueText: String? = null
) {
    require(step >= 1) { "ValueSlider: step måste vara minst 1" }
    val level = scaleLevel(value, valueRange, higherIsBetter)
    val levelText = stringResource(level.label)
    val shown = valueText ?: scaleValueText(value, valueRange)
    val state = valueText ?: stringResource(R.string.scale_value_format, shown, levelText)
    val energy = AppColors.extended.energy
    val primary = MaterialTheme.colorScheme.primary
    val gradient = when {
        valueText != null -> listOf(primary, primary)
        higherIsBetter -> listOf(energy.low, energy.mid, energy.high)
        else -> listOf(energy.high, energy.mid, energy.low)
    }
    val span = (valueRange.last - valueRange.first).coerceAtLeast(1)
    val fraction = (value.coerceIn(valueRange) - valueRange.first).toFloat() / span
    val anchor = if (valueRange.first < 0 && valueRange.last > 0) -valueRange.first.toFloat() / span else 0f
    Column(modifier.fillMaxWidth().inactive(!enabled), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        // Det synliga huvudet läses inte separat – reglaget bär etikett, värde och nivå.
        Row(Modifier.clearAndSetSemantics {}, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
            // Etiketten över en formulärkontroll har en stil överallt: GroupLabel, som i LabeledGroup.
            GroupLabel(label, Modifier.weight(1f))
            Text(shown, style = AppTypography.quantity, color = MaterialTheme.colorScheme.onSurface)
            if (valueText == null) InfoPill(levelText, tone = level.tone)
        }
        val slider = remember(span, valueRange.first, step) {
            SliderState(value.toFloat(), (span / step - 1).coerceAtLeast(0), valueRange.first.toFloat()..valueRange.last.toFloat())
        }
        // Värdet ägs av anroparen; reglaget visar alltid det.
        SideEffect { slider.value = value.toFloat() }
        Slider(
            state = slider,
            onValueChange = { onValueChange(snap(it, valueRange, step)) },
            modifier = Modifier.fillMaxWidth().semantics {
                contentDescription = label
                stateDescription = state
            },
            enabled = enabled,
            thumb = { Thumb(if (valueText != null) primary else level.color) },
            track = { GradientTrack(gradient, fraction, anchor) }
        )
    }
}

/** Reglagets läge [position] som värde: närmaste [step]-steg från `valueRange.first`, inom [valueRange]. */
private fun snap(position: Float, valueRange: IntRange, step: Int): Int = (valueRange.first + ((position - valueRange.first) / step).roundToInt() * step).coerceIn(valueRange)

/** Tummen: en ring i nivåns färg, i en 48 dp stor yta – så blir hela reglaget minst 48 dp högt (NFR-14). */
@Composable
private fun Thumb(color: Color) {
    Box(Modifier.size(TOUCH_TARGET), contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .size(THUMB)
                .background(AppColors.extended.card, CircleShape)
                .border(THUMB_BORDER, color, CircleShape)
        )
    }
}

/** Hela skalan svagt, den fyllda delen (från [anchor] till [fraction]) i full färg, och nollan som ett streck. */
@Composable
private fun GradientTrack(colors: List<Color>, fraction: Float, anchor: Float) {
    val zeroMark = MaterialTheme.colorScheme.onSurfaceVariant
    Canvas(Modifier.fillMaxWidth().height(TRACK_HEIGHT)) {
        val y = size.height / 2
        val stroke = size.height
        val brush = Brush.horizontalGradient(colors, startX = 0f, endX = size.width)
        drawLine(brush, Offset(0f, y), Offset(size.width, y), stroke, StrokeCap.Round, alpha = TRACK_ALPHA)
        val from = minOf(anchor, fraction) * size.width
        val to = maxOf(anchor, fraction) * size.width
        if (to > from) drawLine(brush, Offset(from, y), Offset(to, y), stroke, StrokeCap.Round)
        if (anchor > 0f) {
            val x = anchor * size.width
            drawLine(zeroMark, Offset(x, 0f), Offset(x, size.height), ZERO_MARK.toPx())
        }
    }
}

private val THUMB = 24.dp
private val THUMB_BORDER = 4.dp
private val ZERO_MARK = 2.dp
private const val TRACK_ALPHA = 0.3f
