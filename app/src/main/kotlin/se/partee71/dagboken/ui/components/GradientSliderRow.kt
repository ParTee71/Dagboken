package se.partee71.dagboken.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
) {
    val cs = MaterialTheme.colorScheme
    val eColor = accentColor ?: screeningEnergyColor(value.toInt(), cs)
    val density = LocalDensity.current
    val rangeSize = valueRange.endInclusive - valueRange.start

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        // Label + big value row (matches mockup Preview 8)
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
            if (displayValue != null) {
                Text(
                    text       = displayValue,
                    fontSize   = 28.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color      = eColor,
                    lineHeight = 28.sp,
                )
            } else {
                Row(
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.spacedBy(1.dp),
                ) {
                    Text(
                        text       = value.toInt().toString(),
                        fontSize   = 28.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color      = eColor,
                        lineHeight = 28.sp,
                    )
                    Text(
                        text     = " /10",
                        fontSize = 12.sp,
                        color    = cs.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 3.dp),
                    )
                }
            }
        }

        // Custom track + invisible Slider for touch
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
        ) {
            if (rangeSize == 0f) return@BoxWithConstraints

            val thumbSize = 44.dp
            val fraction = (value - valueRange.start) / rangeSize
            val thumbOffsetX = (maxWidth - thumbSize) * fraction
            val fullWidthPx = with(density) { maxWidth.toPx() }

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
                    .background(
                        Brush.horizontalGradient(
                            colors = gradientColors(),
                            startX = 0f,
                            endX   = fullWidthPx,
                        )
                    ),
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
                    text       = value.toInt().toString(),
                    fontWeight = FontWeight.ExtraBold,
                    fontSize   = 15.sp,
                    color      = eColor,
                )
            }

            // Invisible Slider handles all touch/drag events
            Slider(
                value         = value,
                onValueChange = onValueChange,
                valueRange    = valueRange,
                steps         = steps,
                modifier      = Modifier
                    .fillMaxWidth()
                    .align(Alignment.Center)
                    .alpha(0f),
            )
        }

        // Optional end labels (e.g. "0  😴" / "😊  10")
        if (startLabel.isNotEmpty() || endLabel.isNotEmpty()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(startLabel, style = MaterialTheme.typography.labelSmall, color = Rose500)
                Text(endLabel,   style = MaterialTheme.typography.labelSmall, color = Emerald400)
            }
        }
    }
}
