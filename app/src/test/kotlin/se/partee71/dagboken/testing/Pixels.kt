package se.partee71.dagboken.testing

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.captureToImage

/** Antal bildpunkter i noden som har exakt [color] – för att visa att något ritas i en viss färg. */
fun SemanticsNodeInteraction.pixels(color: Color): Int {
    val map = captureToImage().toPixelMap()
    var count = 0
    for (x in 0 until map.width) for (y in 0 until map.height) if (map[x, y] == color) count++
    return count
}
