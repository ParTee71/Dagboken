package se.partee71.dagboken.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import se.partee71.dagboken.R
import se.partee71.dagboken.core.model.FeelingCatalog
import se.partee71.dagboken.ui.common.feelingName
import se.partee71.dagboken.ui.theme.AppColors
import se.partee71.dagboken.ui.theme.AppShapes
import se.partee71.dagboken.ui.theme.AppTypography
import se.partee71.dagboken.ui.theme.FeelingColors
import se.partee71.dagboken.ui.theme.Spacing

/**
 * Känslohjulet (SCR-7, SCR-8): de sex kärnkänslorna ur [FeelingCatalog] som sektorer runt ett nav, i hjulets ordning
 * medurs från klockan 12, var och en i sin ton (`AppColors.extended.feelingTones`) med namnet vågrätt i sektorn. En
 * kärna i [selected] (kärntokens som har minst ett val) fylls med tonens innehållsfärg och läses "vald". Tryck var som
 * helst i en sektor anropar [onCoreClick] med kärnans token; för TalkBack är varje sektor en knapp (minst 48 dp) med
 * kärnans namn. Ingången i `FeelingPicker`.
 */
@Composable
fun FeelingWheel(selected: Set<String>, onCoreClick: (String) -> Unit, modifier: Modifier = Modifier) {
    val tones = AppColors.extended.feelingTones
    val cores = FeelingCatalog.cores
    val click by rememberUpdatedState(onCoreClick)
    BoxWithConstraints(modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        val diameter = min(maxWidth, WHEEL_MAX)
        Box(
            Modifier.size(diameter)
                // Mellanrummen mellan sektorerna och navet stansas ut, så att hjulet ligger på vilken yta som helst.
                .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
                .drawBehind { drawWheel(cores, selected, tones) }
                .pointerInput(cores) { detectTapGestures { position -> coreAt(position, size.width / 2f, HUB_FRACTION)?.let { click(cores[it]) } } }
        ) {
            cores.forEachIndexed { index, core ->
                SectorLabel(core, core in selected, tones, labelOffset(index, diameter / 2 * LABEL_FRACTION)) { click(core) }
            }
            Text(
                stringResource(R.string.feelings_wheel_center),
                Modifier.align(Alignment.Center).clearAndSetSemantics {},
                style = AppTypography.caption,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/** Kärnans namn i sektorn – tryckytan och TalkBack-noden för sektorn ("Lugn", "vald"). */
@Composable
private fun BoxScope.SectorLabel(
    core: String,
    isSelected: Boolean,
    tones: FeelingColors,
    offset: Pair<Dp, Dp>,
    onClick: () -> Unit
) {
    val name = stringResource(feelingName(core))
    val selectedState = stringResource(R.string.feeling_selected_state)
    val tone = tones.forCore(core)
    Box(
        Modifier.align(Alignment.Center)
            .offset(offset.first, offset.second)
            .sizeIn(minWidth = TOUCH_TARGET, minHeight = TOUCH_TARGET)
            .clip(AppShapes.pill)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics {
                contentDescription = name
                if (isSelected) stateDescription = selectedState
            }
            .padding(horizontal = Spacing.xs),
        contentAlignment = Alignment.Center
    ) {
        Text(
            name,
            Modifier.clearAndSetSemantics {},
            style = AppTypography.itemTitle,
            color = if (isSelected) tone.container else tone.content,
            textAlign = TextAlign.Center,
            maxLines = 1
        )
    }
}

/** Sektorerna i kärnornas toner (vald: innehållsfärgen), sedan mellanrummen och navet utstansade. */
private fun DrawScope.drawWheel(cores: List<String>, selected: Set<String>, tones: FeelingColors) {
    val radius = size.minDimension / 2
    cores.forEachIndexed { index, core ->
        val tone = tones.forCore(core)
        drawArc(
            color = if (core in selected) tone.content else tone.container,
            startAngle = START_ANGLE + SWEEP * index,
            sweepAngle = SWEEP,
            useCenter = true
        )
    }
    repeat(cores.size) { index ->
        val angle = Math.toRadians((SWEEP * index).toDouble())
        val end = center + Offset((sin(angle) * radius).toFloat(), (-cos(angle) * radius).toFloat())
        drawLine(Color.Black, center, end, strokeWidth = GAP.toPx(), blendMode = BlendMode.Clear)
    }
    drawCircle(Color.Black, radius = radius * HUB_FRACTION, blendMode = BlendMode.Clear)
}

/**
 * Vilken sektor (index i hjulets ordning) [position] ligger i, räknat medurs från klockan 12 – `null` i navet och
 * utanför hjulet.
 */
internal fun coreAt(position: Offset, radius: Float, hubFraction: Float, count: Int = FeelingCatalog.cores.size): Int? {
    val dx = position.x - radius
    val dy = position.y - radius
    val distance = hypot(dx, dy)
    if (distance > radius || distance < radius * hubFraction) return null
    val degrees = (Math.toDegrees(atan2(dx.toDouble(), -dy.toDouble())) + FULL_TURN) % FULL_TURN
    return (degrees / (FULL_TURN / count)).toInt().coerceIn(0, count - 1)
}

/** Var namnet står: mitt i sektorn [index] på [distance] från mitten. */
private fun labelOffset(index: Int, distance: Dp): Pair<Dp, Dp> {
    val angle = Math.toRadians((SWEEP * index + SWEEP / 2).toDouble())
    return distance * sin(angle).toFloat() to distance * -cos(angle).toFloat()
}

/** Hjulets geometri (som i prototypen: radie 150, namnen på 100, navet 30). */
private val WHEEL_MAX = 320.dp
private val GAP = 3.dp
private const val LABEL_FRACTION = 2f / 3f
private const val HUB_FRACTION = 0.2f
private const val FULL_TURN = 360.0
private val SWEEP = (FULL_TURN / FeelingCatalog.cores.size).toFloat()

/** Canvas räknar vinklar medurs från klockan 3; hjulet börjar klockan 12. */
private const val START_ANGLE = -90f
