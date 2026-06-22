package se.partee71.dagboken.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt
import se.partee71.dagboken.R
import se.partee71.dagboken.ui.theme.Emerald400
import se.partee71.dagboken.ui.theme.Rose500
import se.partee71.dagboken.ui.theme.gradientColors
import se.partee71.dagboken.ui.theme.screeningEnergyColor

@Composable
fun GradientSliderRow(
    label: String,
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    emoji: String = "",
    valueRange: ClosedFloatingPointRange<Float> = 0f..10f,
    steps: Int = 9,
    startLabel: String = "",
    endLabel: String = "",
    displayValue: String? = null,   // overrides the "N /10" header format (e.g. "+7" for −10..10 range)
    accentColor: Color? = null,     // overrides the auto screeningEnergyColor (e.g. for activity energy scale)
    reverseColors: Boolean = false, // 0=green 10=red (symptoms/stress); default is 0=red 10=green (energy)
    enabled: Boolean = true,
) {
    val cs = MaterialTheme.colorScheme
    val intValue = value.toInt()
    val eColor = accentColor ?: if (reverseColors) when {
        intValue >= 7 -> cs.error
        intValue >= 4 -> cs.secondary
        else          -> cs.tertiary
    } else screeningEnergyColor(intValue, cs)
    val density = LocalDensity.current
    val rangeSize = valueRange.endInclusive - valueRange.start
    val stepSize = if (steps > 0) rangeSize / (steps + 1) else rangeSize

    Column(
        modifier = modifier.alpha(if (enabled) 1f else 0.38f),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        // Label + big value row
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text       = if (emoji.isNotEmpty()) "$emoji  $label" else label,
                style      = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color      = cs.onSurface,
            )
            Row(
                verticalAlignment     = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(1.dp),
            ) {
                Text(
                    text       = displayValue ?: intValue.toString(),
                    fontSize   = 28.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color      = eColor,
                    lineHeight = 28.sp,
                )
                if (displayValue == null) {
                    Text(
                        text     = " /10",
                        fontSize = 12.sp,
                        color    = cs.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 3.dp),
                    )
                }
            }
        }

        // ± step buttons flanking the custom track
        Row(
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            IconButton(
                onClick  = { onValueChange((value - stepSize).coerceAtLeast(valueRange.start)) },
                enabled  = enabled && value > valueRange.start,
                modifier = Modifier.size(36.dp),
            ) {
                Icon(Icons.Default.Remove, contentDescription = stringResource(R.string.decrease), modifier = Modifier.size(20.dp))
            }

        // Custom track + invisible Slider for touch
        BoxWithConstraints(
            modifier = Modifier
                .weight(1f)
                .height(48.dp),
        ) {
            if (rangeSize == 0f) return@BoxWithConstraints

            val thumbSize = 44.dp
            val fraction = (value - valueRange.start) / rangeSize
            val thumbOffsetX = (maxWidth - thumbSize) * fraction
            val fullWidthPx = with(density) { maxWidth.toPx() }
            val trackColors = if (reverseColors) gradientColors().reversed() else gradientColors()
            val gradientBrush = remember(fullWidthPx, reverseColors) {
                Brush.horizontalGradient(
                    colors = trackColors,
                    startX = 0f,
                    endX   = fullWidthPx,
                )
            }

            // Dim track background
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(10.dp)
                    .align(Alignment.CenterStart)
                    .clip(CircleShape)
                    .background(cs.surfaceVariant),
            )

            // Gradient fill up to thumb centre
            Box(
                modifier = Modifier
                    .width(thumbOffsetX + thumbSize / 2)
                    .height(10.dp)
                    .align(Alignment.CenterStart)
                    .clip(CircleShape)
                    .background(gradientBrush),
            )

            // Thumb: surface circle with coloured border + value inside
            Box(
                modifier = Modifier
                    .offset(x = thumbOffsetX, y = 2.dp)
                    .size(thumbSize)
                    .clip(CircleShape)
                    .background(cs.surface)
                    .border(2.5.dp, eColor, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text       = intValue.toString(),
                    fontWeight = FontWeight.ExtraBold,
                    fontSize   = 15.sp,
                    color      = eColor,
                )
            }

            // Full-area gesture handler: press anywhere to jump, drag to slide.
            // Covers the entire 48 dp height so any touch on the track registers.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight()
                    .align(Alignment.Center)
                    .pointerInput(enabled, valueRange, stepSize) {
                        if (!enabled) return@pointerInput
                        val widthPx = size.width.toFloat()
                        fun snap(x: Float): Float {
                            val fraction = (x / widthPx).coerceIn(0f, 1f)
                            val raw = valueRange.start + fraction * rangeSize
                            val n = ((raw - valueRange.start) / stepSize).roundToInt()
                            return (valueRange.start + n * stepSize)
                                .coerceIn(valueRange.start, valueRange.endInclusive)
                        }
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            down.consume()
                            onValueChange(snap(down.position.x))
                            drag(down.id) { change ->
                                change.consume()
                                onValueChange(snap(change.position.x))
                            }
                        }
                    },
            )
        }

            IconButton(
                onClick  = { onValueChange((value + stepSize).coerceAtMost(valueRange.endInclusive)) },
                enabled  = enabled && value < valueRange.endInclusive,
                modifier = Modifier.size(36.dp),
            ) {
                Icon(Icons.Default.Add, contentDescription = stringResource(R.string.increase), modifier = Modifier.size(20.dp))
            }
        } // end ± Row

        // Optional end labels (e.g. "0  😴" / "😊  10")
        if (startLabel.isNotEmpty() || endLabel.isNotEmpty()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(startLabel, style = MaterialTheme.typography.labelSmall, color = if (reverseColors) Emerald400 else Rose500)
                Text(endLabel,   style = MaterialTheme.typography.labelSmall, color = if (reverseColors) Rose500 else Emerald400)
            }
        }
    }
}
