@file:Suppress("PreviewAnnotationInFunctionWithParameters")

package se.partee71.dagboken.ui.mockup

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import se.partee71.dagboken.ui.diagram.DiagramLayout
import se.partee71.dagboken.ui.theme.DagbokenTheme

// ─── Fake data ─────────────────────────────────────────────────────────────────

private val symptomPalette = listOf(
    Color(0xFF60a5fa),  // blue   – Huvudvärk
    Color(0xFFfb923c),  // orange – Trötthet
)

private val allSymptoms  = listOf("Huvudvärk", "Trötthet", "Yrsel", "Illamående")
private val selectedList = listOf("Huvudvärk", "Trötthet")
private val colorMap     = selectedList.mapIndexed { i, n -> n to symptomPalette[i] }.toMap()
private val allRanges    = listOf(7, 14, 30, 90)
private val selectedDays = 30

private val seriesHuvudvark: List<Float?> = listOf(
    2f, null, 3f, 2f, null, 1f, 2f, null, null, 3f,
    4f, 2f, null, 1f, 3f, null, 2f, 3f, null, 1f,
    2f, null, 3f, 2f, null, 1f, null, 2f, 3f, null,
)
private val seriesTrotthet: List<Float?> = listOf(
    3f, 2f, 4f, 3f, 2f, 3f, 4f, 3f, 2f, 3f,
    5f, 4f, 3f, 2f, 3f, 4f, 3f, 2f, 3f, 4f,
    3f, 2f, 3f, null, 2f, 3f, 4f, 3f, 2f, 3f,
)

// ─── Shared chart composable ───────────────────────────────────────────────────

@Composable
private fun MockChart(modifier: Modifier = Modifier) {
    val gridColor  = MaterialTheme.colorScheme.outlineVariant
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val series     = listOf(seriesHuvudvark to symptomPalette[0], seriesTrotthet to symptomPalette[1])
    val maxV       = 5f
    val yLabels    = listOf("5", "4", "3", "2", "1", "0")
    val xLabels    = listOf("dag 1" to 0, "dag 10" to 9, "dag 20" to 19, "dag 30" to 29)
    val count      = seriesHuvudvark.size

    Column(modifier = modifier) {
        Row(modifier = Modifier.weight(1f)) {
            Column(
                modifier            = Modifier.fillMaxHeight().width(28.dp),
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                yLabels.forEach { label ->
                    Text(
                        text      = label,
                        style     = MaterialTheme.typography.labelSmall,
                        fontSize  = 9.sp,
                        color     = labelColor,
                        textAlign = TextAlign.End,
                        modifier  = Modifier.fillMaxWidth(),
                    )
                }
            }
            Spacer(Modifier.width(4.dp))
            Canvas(modifier = Modifier.weight(1f).fillMaxHeight()) {
                val w    = size.width
                val h    = size.height
                val step = if (count > 1) w / (count - 1) else w

                for (v in 0..5) {
                    val y = h * (1f - v / maxV)
                    drawLine(
                        color       = gridColor.copy(alpha = if (v == 0) 0.85f else 0.55f),
                        start       = Offset(0f, y),
                        end         = Offset(w, y),
                        strokeWidth = if (v == 0) 1.5f else 0.9f,
                    )
                }

                series.forEach { (points, color) ->
                    val path = Path()
                    var started = false
                    points.forEachIndexed { i, v ->
                        if (v == null) { started = false; return@forEachIndexed }
                        val x = i * step
                        val y = h * (1f - v / maxV)
                        if (!started) { path.moveTo(x, y); started = true } else path.lineTo(x, y)
                    }
                    drawPath(path, color, style = Stroke(width = 3.5f, cap = StrokeCap.Round))
                    points.forEachIndexed { i, v ->
                        if (v == null) return@forEachIndexed
                        val x = i * step
                        val y = h * (1f - v / maxV)
                        drawCircle(color,                          radius = 5f,   center = Offset(x, y))
                        drawCircle(Color.White.copy(alpha = 0.7f), radius = 2.5f, center = Offset(x, y))
                    }
                }
            }
        }
        Spacer(Modifier.height(4.dp))
        Box(modifier = Modifier.fillMaxWidth().padding(start = 32.dp)) {
            xLabels.forEach { (label, index) ->
                val fraction = if (count > 1) index.toFloat() / (count - 1) else 0f
                Text(
                    text      = label,
                    style     = MaterialTheme.typography.labelSmall,
                    fontSize  = 9.sp,
                    color     = labelColor,
                    modifier  = Modifier.fillMaxWidth(fraction.coerceAtLeast(0.01f)),
                    textAlign = if (fraction == 0f) TextAlign.Start else TextAlign.End,
                )
            }
        }
    }
}

// ─── Shared selector + chips ───────────────────────────────────────────────────

@Composable
private fun MockSelector() {
    var expanded by remember { mutableStateOf(false) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("Symptom", style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.width(8.dp))
        Box {
            OutlinedButton(onClick = { expanded = true }) {
                Text(selectedList.joinToString(", "), maxLines = 1)
                Icon(Icons.Default.ArrowDropDown, null, modifier = Modifier.size(18.dp))
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                allSymptoms.forEach { name ->
                    DropdownMenuItem(
                        text = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(checked = name in selectedList, onCheckedChange = {})
                                Spacer(Modifier.width(4.dp))
                                Text(name)
                            }
                        },
                        onClick = {},
                    )
                }
            }
        }
    }
}

@Composable
private fun MockRangeChips() {
    allRanges.forEach { d ->
        FilterChip(selected = d == selectedDays, onClick = {}, label = { Text("${d}d") })
    }
}

@Composable
private fun MockLegend() {
    selectedList.forEach { name ->
        Row(
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(colorMap[name] ?: symptomPalette[0]),
            )
            Text(name, style = MaterialTheme.typography.labelSmall)
        }
    }
}

// ─── Previews via DiagramLayout ────────────────────────────────────────────────

@Preview(name = "#45 Portrait (light)", showBackground = true, widthDp = 390, heightDp = 844)
@Composable
fun MockDiagramPortrait() {
    DagbokenTheme(darkTheme = false) {
        DiagramLayout(
            title       = "Symptomöversikt",
            onBack      = {},
            selector    = { MockSelector() },
            rangeChips  = { MockRangeChips() },
            chart       = { m -> MockChart(m) },
            legend      = { MockLegend() },
        )
    }
}

@Preview(name = "#45 Landscape – fullscreen (light)", showBackground = true, widthDp = 844, heightDp = 390)
@Composable
fun MockDiagramLandscape() {
    DagbokenTheme(darkTheme = false) {
        DiagramLayout(
            title       = "Symptomöversikt",
            onBack      = {},
            selector    = { MockSelector() },
            rangeChips  = { MockRangeChips() },
            chart       = { m -> MockChart(m) },
            legend      = { MockLegend() },
        )
    }
}

@Preview(name = "#45 Landscape – fullscreen (dark)", showBackground = true, widthDp = 844, heightDp = 390)
@Composable
fun MockDiagramLandscapeDark() {
    DagbokenTheme(darkTheme = true) {
        DiagramLayout(
            title       = "Symptomöversikt",
            onBack      = {},
            selector    = { MockSelector() },
            rangeChips  = { MockRangeChips() },
            chart       = { m -> MockChart(m) },
            legend      = { MockLegend() },
        )
    }
}
