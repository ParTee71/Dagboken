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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.ArrowDropUp
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Medication
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.FitnessCenter
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.LocalPharmacy
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import se.partee71.dagboken.ui.theme.DagbokenTheme

// ─── Fake data ─────────────────────────────────────────────────────────────────

private val symptomPalette = listOf(
    Color(0xFF60a5fa),  // blue   – Huvudvärk
    Color(0xFFfb923c),  // orange – Trötthet
)

private val allSymptoms  = listOf("Huvudvärk", "Trötthet", "Yrsel", "Illamående", "Nackvärk", "Ryggvärk", "Smärta")
private val selectedList = listOf("Huvudvärk", "Trötthet")
private val colorMap     = selectedList.mapIndexed { i, n -> n to symptomPalette[i] }.toMap()

// 30 data points; null = no entry that day (gap in line)
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

// ─── Chart ─────────────────────────────────────────────────────────────────────

@Composable
private fun MockSymptomChart(modifier: Modifier = Modifier) {
    val gridColor  = MaterialTheme.colorScheme.outlineVariant
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val series     = listOf(seriesHuvudvark to symptomPalette[0], seriesTrotthet to symptomPalette[1])
    val maxV       = 5f
    val yLabels    = listOf("5", "4", "3", "2", "1", "0")
    val xLabels    = listOf("dag 1" to 0, "dag 8" to 7, "dag 15" to 14, "dag 22" to 21, "dag 30" to 29)
    val count      = seriesHuvudvark.size

    Column(modifier = modifier) {
        Row(modifier = Modifier.weight(1f)) {
            // Y-axis labels
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
                        color       = gridColor.copy(alpha = if (v == 0) 0.9f else 0.3f),
                        start       = Offset(0f, y),
                        end         = Offset(w, y),
                        strokeWidth = if (v == 0) 1.5f else 0.7f,
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

// ─── Screen ────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SymptomDiagramContent(dropdownOpen: Boolean = false) {
    val cs           = MaterialTheme.colorScheme
    val selectedDays = 30
    val fieldValue   = if (selectedList.isEmpty()) "" else selectedList.joinToString(", ")

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = {}) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
                title = { Text("Symptomöversikt", fontWeight = FontWeight.SemiBold) },
            )
        },
        bottomBar = {
            NavigationBar {
                listOf(
                    Triple("Hem",       Icons.Filled.Home,          Icons.Outlined.Home),
                    Triple("Aktivitet", Icons.Filled.Bolt,          Icons.Outlined.Bolt),
                    Triple("Mediciner", Icons.Filled.Medication,    Icons.Outlined.LocalPharmacy),
                    Triple("Hälsa",     Icons.Filled.FitnessCenter, Icons.Outlined.FitnessCenter),
                ).forEachIndexed { i, (label, filled, outlined) ->
                    NavigationBarItem(
                        selected = i == 1,
                        onClick  = {},
                        icon     = { Icon(if (i == 1) filled else outlined, null) },
                        label    = { Text(label, style = MaterialTheme.typography.labelSmall) },
                    )
                }
            }
        },
        containerColor = cs.background,
    ) { padding ->
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(padding)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {

            // ── Date range chips ─────────────────────────────────────────────
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(7, 14, 30, 90).forEach { days ->
                    FilterChip(
                        selected = days == selectedDays,
                        onClick  = {},
                        label    = { Text("$days d") },
                    )
                }
            }

            // ── Symptom multi-select dropdown ────────────────────────────────
            Column(verticalArrangement = Arrangement.spacedBy(0.dp)) {
                OutlinedTextField(
                    value         = fieldValue,
                    onValueChange = {},
                    readOnly      = true,
                    label         = { Text("Symptom") },
                    trailingIcon  = {
                        Icon(
                            if (dropdownOpen) Icons.Filled.ArrowDropUp else Icons.Filled.ArrowDropDown,
                            contentDescription = null,
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
                if (dropdownOpen) {
                    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                        allSymptoms.forEachIndexed { index, name ->
                            val isSelected = name in selectedList
                            Row(
                                modifier          = Modifier
                                    .fillMaxWidth()
                                    .padding(end = 16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Checkbox(checked = isSelected, onCheckedChange = {})
                                Text(
                                    text  = name,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = if (isSelected) cs.onSurface else cs.onSurfaceVariant,
                                    fontWeight = if (isSelected) FontWeight.Medium else FontWeight.Normal,
                                )
                            }
                            if (index < allSymptoms.lastIndex) {
                                HorizontalDivider(
                                    modifier = Modifier.padding(horizontal = 16.dp),
                                    color    = cs.outlineVariant.copy(alpha = 0.4f),
                                )
                            }
                        }
                    }
                }
            }

            // ── Chart card (hidden when dropdown is open to fit the preview) ─
            if (!dropdownOpen) {
                ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier            = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Row(
                            modifier              = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment     = Alignment.CenterVertically,
                        ) {
                            Text(
                                "Svårighetsgrad  0 – 5",
                                style      = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                "senaste 30 dagar",
                                style = MaterialTheme.typography.labelSmall,
                                color = cs.onSurfaceVariant,
                            )
                        }

                        MockSymptomChart(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(200.dp),
                        )

                        Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
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
                                    Text(
                                        name,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = cs.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// ─── Previews ──────────────────────────────────────────────────────────────────

@Preview(name = "Symptomöversikt – Stängd", showBackground = true, widthDp = 390, heightDp = 844)
@Composable
fun MockSymptomDiagramClosed() {
    DagbokenTheme(darkTheme = false) { SymptomDiagramContent(dropdownOpen = false) }
}

@Preview(name = "Symptomöversikt – Öppen dropdown", showBackground = true, widthDp = 390, heightDp = 844)
@Composable
fun MockSymptomDiagramOpen() {
    DagbokenTheme(darkTheme = true) { SymptomDiagramContent(dropdownOpen = true) }
}
