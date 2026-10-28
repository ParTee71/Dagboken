package se.partee71.dagboken.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

/**
 * Stundens kvällston (NOT-20, skill `ui-style`): en solnedgång som är densamma i ljust och mörkt tema. [sky] är himlen
 * uppifrån och ned (skymningsviolett → bärnsten), [sunCore]/[sunMid]/[sunEdge] solen inifrån och ut, [hillBack] och
 * [hillFront] de två kulllagren som täcker nedre delen av skärmen. [text] och [textMuted] bär texten på himlen och
 * kullarna, [accent] rubriken och nedräkningen och [onSun] fasen ("Andas in") i solen – alla klarar 4,5:1 mot sin
 * bakgrund (`MomentContrastTest`).
 */
@Immutable
data class MomentColors(
    val sky: List<Color>,
    val sunCore: Color,
    val sunMid: Color,
    val sunEdge: Color,
    val hillBack: Color,
    val hillFront: Color,
    val text: Color,
    val textMuted: Color,
    val accent: Color,
    val onSun: Color
)
