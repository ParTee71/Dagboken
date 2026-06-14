@file:Suppress("PreviewAnnotationInFunctionWithParameters")

package se.partee71.dagboken.ui.mockup

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Circle
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LocalPharmacy
import androidx.compose.material.icons.filled.OpenInFull
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.FitnessCenter
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.LocalPharmacy
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import se.partee71.dagboken.ui.theme.Amber400
import se.partee71.dagboken.ui.theme.DagbokenTheme
import se.partee71.dagboken.ui.theme.Emerald400
import se.partee71.dagboken.ui.theme.Rose500
import se.partee71.dagboken.ui.theme.Violet500
import kotlin.math.cos
import kotlin.math.sin

// ─── Fake data ────────────────────────────────────────────────────────────────

private data class FakeMed(val namn: String, val dos: String, val tidpunkt: String, val tagen: Boolean)
private val fakeMeds = listOf(
    FakeMed("Metformin", "500 mg", "Morgon", true),
    FakeMed("Losartan", "25 mg", "Lunch", false),
    FakeMed("Aspirin", "75 mg", "Kväll", false),
)

private val fakeEnergyPoints = listOf(6f, 7f, 5f, 8f, 7f, 6f, 9f)

private data class FakeEntry(val namn: String, val tid: String, val energy: Int, val type: String, val stress: Int = 2)
private val fakeEntries = listOf(
    FakeEntry("Promenad", "08:15", 8, "aktivitet", stress = 1),
    FakeEntry("Jobbmöte", "13:00", -4, "aktivitet", stress = 7),
    FakeEntry("Daglig screening", "07:30", 9, "screening", stress = 2),
)

// ─── Color helpers ─────────────────────────────────────────────────────────────

private fun energyColor(e: Int): Color = when {
    e >= 7  -> Emerald400
    e >= 3  -> Amber400
    e >= -2 -> Amber400
    else    -> Rose500
}

// ─── Shared micro-components ──────────────────────────────────────────────────

@Composable
private fun MockAccountBubble(signedIn: Boolean = true, modifier: Modifier = Modifier) {
    Box(modifier = modifier.size(40.dp)) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(
                    if (signedIn) MaterialTheme.colorScheme.primaryContainer
                    else MaterialTheme.colorScheme.surfaceVariant,
                )
                .align(Alignment.Center),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = if (signedIn) Icons.Filled.AccountCircle else Icons.Outlined.AccountCircle,
                contentDescription = null,
                tint = if (signedIn) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(24.dp),
            )
        }
        if (signedIn) {
            Box(
                modifier = Modifier
                    .size(11.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.background)
                    .align(Alignment.BottomEnd),
            )
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(Emerald400)
                    .align(Alignment.BottomEnd)
                    .offset((-1).dp, (-1).dp),
            )
        }
    }
}

@Composable
private fun MockSparkline(points: List<Float>, lineColor: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.fillMaxWidth().height(72.dp)) {
        if (points.size < 2) return@Canvas
        val w = size.width
        val h = size.height
        val xStep = w / (points.size - 1)
        fun xAt(i: Int) = i * xStep
        fun yAt(v: Float) = h - (v / 10f).coerceIn(0f, 1f) * h * 0.82f - h * 0.09f

        val areaPath = Path().apply {
            moveTo(xAt(0), h)
            lineTo(xAt(0), yAt(points[0]))
            for (i in 1 until points.size) lineTo(xAt(i), yAt(points[i]))
            lineTo(xAt(points.size - 1), h)
            close()
        }
        drawPath(areaPath, brush = Brush.verticalGradient(listOf(lineColor.copy(alpha = 0.25f), Color.Transparent)))

        val linePath = Path().apply {
            moveTo(xAt(0), yAt(points[0]))
            for (i in 1 until points.size) lineTo(xAt(i), yAt(points[i]))
        }
        drawPath(linePath, color = lineColor, style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round))
        points.forEachIndexed { i, v ->
            drawCircle(color = lineColor, radius = 4.dp.toPx(), center = Offset(xAt(i), yAt(v)))
            drawCircle(color = Color.White.copy(alpha = 0.9f), radius = 2.dp.toPx(), center = Offset(xAt(i), yAt(v)))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MockTopBar(title: String, actions: @Composable () -> Unit = {}) {
    TopAppBar(
        title = { Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold) },
        navigationIcon = { MockAccountBubble(modifier = Modifier.padding(start = 8.dp)) },
        actions = { actions() },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
    )
}

@Composable
private fun MockBottomBar(selected: Int = 0) {
    val items = listOf(
        Triple("Hem", Icons.Filled.Home, Icons.Outlined.Home),
        Triple("Aktivitet", Icons.Filled.Bolt, Icons.Outlined.Bolt),
        Triple("Mediciner", Icons.Filled.LocalPharmacy, Icons.Outlined.LocalPharmacy),
        Triple("Hälsa", Icons.Filled.FitnessCenter, Icons.Outlined.FitnessCenter),
    )
    NavigationBar {
        items.forEachIndexed { i, (label, filled, outlined) ->
            NavigationBarItem(
                selected = i == selected,
                onClick = {},
                icon = { Icon(if (i == selected) filled else outlined, null) },
                label = { Text(label, style = MaterialTheme.typography.labelSmall) },
                modifier = if (i == 3) Modifier.alpha(0.35f) else Modifier,
                enabled = i != 3,
            )
        }
    }
}

// ─── PREVIEW 1: Dashboard ─────────────────────────────────────────────────────

@Preview(name = "Dashboard – Dark", showBackground = true, widthDp = 390, heightDp = 844)
@Composable
fun MockDashboardDark() = MockDashboard(darkTheme = true)

@Preview(name = "Dashboard – Light", showBackground = true, widthDp = 390, heightDp = 844)
@Composable
fun MockDashboardLight() = MockDashboard(darkTheme = false)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MockDashboard(darkTheme: Boolean) {
    DagbokenTheme(darkTheme = darkTheme) {
        Scaffold(
            topBar = { MockTopBar("Hem") },
            bottomBar = { MockBottomBar(selected = 0) },
            containerColor = MaterialTheme.colorScheme.background,
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState()),
            ) {
                // ── Hero greeting strip ──────────────────────────────────────
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.verticalGradient(
                                listOf(
                                    MaterialTheme.colorScheme.primaryContainer,
                                    MaterialTheme.colorScheme.background,
                                ),
                            ),
                        )
                        .padding(horizontal = 20.dp, vertical = 20.dp),
                ) {
                    Column {
                        Text(
                            "God morgon, Erik! 👋",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground,
                        )
                        Text(
                            "Lördag 14 juni · Vecka 24",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                Column(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    // ── Big-number stat row ──────────────────────────────────
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        // Meds pill
                        Surface(
                            modifier = Modifier.weight(1f),
                            shape = MaterialTheme.shapes.extraLarge,
                            color = MaterialTheme.colorScheme.secondaryContainer,
                        ) {
                            Column(
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                            ) {
                                Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Text(
                                        "1",
                                        fontSize = 40.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = MaterialTheme.colorScheme.secondary,
                                        lineHeight = 40.sp,
                                    )
                                    Text(
                                        "/3",
                                        fontSize = 20.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.6f),
                                        modifier = Modifier.padding(bottom = 4.dp),
                                    )
                                }
                                Text(
                                    "💊 Mediciner idag",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                )
                            }
                        }
                        // Energy pill
                        Surface(
                            modifier = Modifier.weight(1f),
                            shape = MaterialTheme.shapes.extraLarge,
                            color = MaterialTheme.colorScheme.tertiaryContainer,
                        ) {
                            Column(
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                            ) {
                                Text(
                                    "+9",
                                    fontSize = 40.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = Emerald400,
                                    lineHeight = 40.sp,
                                )
                                Text(
                                    "⚡ Energi idag",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                                )
                            }
                        }
                    }

                    // ── Energy trend card ────────────────────────────────────
                    ElevatedCard(shape = MaterialTheme.shapes.extraLarge) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    "Energi — 7 dagar",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold,
                                )
                                Surface(
                                    color = Emerald400.copy(alpha = 0.15f),
                                    shape = MaterialTheme.shapes.small,
                                ) {
                                    Text(
                                        "↑ Bra trend",
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Emerald400,
                                        fontWeight = FontWeight.Bold,
                                    )
                                }
                            }
                            Spacer(Modifier.height(8.dp))
                            MockSparkline(fakeEnergyPoints, lineColor = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.height(4.dp))
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                listOf("Mån", "Tis", "Ons", "Tor", "Fre", "Lör", "Sön").forEach {
                                    Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                            TextButton(
                                onClick = {},
                                modifier = Modifier.align(Alignment.End),
                                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 4.dp),
                            ) {
                                Icon(Icons.Default.BarChart, null, modifier = Modifier.size(14.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Visa diagram →", style = MaterialTheme.typography.labelLarge)
                            }
                        }
                    }

                    // ── Mediciner idag card ──────────────────────────────────
                    ElevatedCard(shape = MaterialTheme.shapes.extraLarge) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text("Mediciner idag", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                                TextButton(onClick = {}) { Text("Visa alla →") }
                            }
                            Spacer(Modifier.height(4.dp))
                            fakeMeds.forEach { med ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                ) {
                                    // Circle check button
                                    Box(
                                        modifier = Modifier
                                            .size(36.dp)
                                            .clip(CircleShape)
                                            .background(
                                                if (med.tagen) Emerald400
                                                else MaterialTheme.colorScheme.surfaceVariant,
                                            ),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        if (med.tagen) {
                                            Icon(Icons.Default.Check, null, tint = Color.White, modifier = Modifier.size(18.dp))
                                        }
                                    }
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            med.namn,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = if (med.tagen) FontWeight.Normal else FontWeight.SemiBold,
                                            color = if (med.tagen) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f) else MaterialTheme.colorScheme.onSurface,
                                            textDecoration = if (med.tagen) TextDecoration.LineThrough else TextDecoration.None,
                                        )
                                        Text(
                                            "${med.dos} · ${med.tidpunkt}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = if (med.tagen) 0.4f else 1f),
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // ── Quick actions ────────────────────────────────────────
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(
                            onClick = {},
                            modifier = Modifier.weight(1f),
                            shape = MaterialTheme.shapes.extraLarge,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                            ),
                        ) {
                            Icon(Icons.Default.Bolt, null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Ny aktivitet", fontWeight = FontWeight.SemiBold)
                        }
                        FilledTonalButton(
                            onClick = {},
                            modifier = Modifier.weight(1f),
                            shape = MaterialTheme.shapes.extraLarge,
                        ) {
                            Icon(Icons.Default.Add, null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Vid behov")
                        }
                    }

                    Spacer(Modifier.height(16.dp))
                }
            }
        }
    }
}

// ─── PREVIEW 2: Aktiviteter (Historik tab) ────────────────────────────────────

@Preview(name = "Aktiviteter – Dark", showBackground = true, widthDp = 390, heightDp = 844)
@Composable
fun MockAktiviteterDark() = MockAktiviteter(darkTheme = true)

@Preview(name = "Aktiviteter – Light", showBackground = true, widthDp = 390, heightDp = 844)
@Composable
fun MockAktiviteterLight() = MockAktiviteter(darkTheme = false)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MockAktiviteter(darkTheme: Boolean) {
    DagbokenTheme(darkTheme = darkTheme) {
        Scaffold(
            topBar = {
                MockTopBar("Aktiviteter") {
                    IconButton(onClick = {}) {
                        Icon(Icons.Default.BarChart, "Diagram", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            },
            bottomBar = { MockBottomBar(selected = 1) },
            floatingActionButton = {
                androidx.compose.material3.FloatingActionButton(
                    onClick = {},
                    containerColor = MaterialTheme.colorScheme.primary,
                    shape = CircleShape,
                ) {
                    Icon(Icons.Default.Add, "Lägg till", tint = Color.White)
                }
            },
            containerColor = MaterialTheme.colorScheme.background,
        ) { padding ->
            Column(modifier = Modifier.fillMaxSize().padding(padding)) {
                // Tab row
                TabRow(selectedTabIndex = 2, containerColor = MaterialTheme.colorScheme.surface) {
                    listOf("⚡ Aktivitet", "📊 Screening", "🕒 Historik").forEachIndexed { i, label ->
                        Tab(
                            selected = i == 2,
                            onClick = {},
                            text = { Text(label, style = MaterialTheme.typography.labelLarge) },
                        )
                    }
                }

                // Filter chips
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Visa:", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    FilterChip(selected = true, onClick = {}, label = { Text("Aktivitet") })
                    FilterChip(selected = true, onClick = {}, label = { Text("Screening") })
                }

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    // Date header
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary))
                        Text(
                            "Lördag 14 juni",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Box(modifier = Modifier.weight(1f).height(1.dp).background(MaterialTheme.colorScheme.outlineVariant))
                    }

                    fakeEntries.forEach { entry ->
                        val eColor = energyColor(entry.energy)
                        ElevatedCard(
                            modifier = Modifier.fillMaxWidth(),
                            shape = MaterialTheme.shapes.large,
                        ) {
                            Row(modifier = Modifier.fillMaxWidth()) {
                                // Colored left accent bar
                                Box(
                                    modifier = Modifier
                                        .width(5.dp)
                                        .fillMaxHeight()
                                        .background(
                                            brush = Brush.verticalGradient(
                                                listOf(eColor, eColor.copy(alpha = 0.3f)),
                                            ),
                                            shape = RoundedCornerShape(topStart = 12.dp, bottomStart = 12.dp),
                                        )
                                        .height(72.dp),
                                )
                                Row(
                                    modifier = Modifier
                                        .weight(1f)
                                        .padding(horizontal = 14.dp, vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        ) {
                                            Text(
                                                entry.namn,
                                                style = MaterialTheme.typography.bodyLarge,
                                                fontWeight = FontWeight.SemiBold,
                                            )
                                            if (entry.type == "screening") {
                                                Surface(
                                                    color = MaterialTheme.colorScheme.tertiaryContainer,
                                                    shape = MaterialTheme.shapes.extraSmall,
                                                ) {
                                                    Text(
                                                        "Screening",
                                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp),
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                                                    )
                                                }
                                            }
                                        }
                                        Text(
                                            "${entry.tid} · Stress ${entry.stress}/10",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                    // Big energy number
                                    Surface(
                                        color = eColor.copy(alpha = 0.13f),
                                        shape = MaterialTheme.shapes.medium,
                                    ) {
                                        Text(
                                            "${if (entry.energy > 0) "+" else ""}${entry.energy}",
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                            fontSize = 20.sp,
                                            fontWeight = FontWeight.ExtraBold,
                                            color = eColor,
                                        )
                                    }
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(88.dp))
                }
            }
        }
    }
}

// ─── PREVIEW 3: Mediciner (Idag tab) ──────────────────────────────────────────

@Preview(name = "Mediciner – Dark", showBackground = true, widthDp = 390, heightDp = 844)
@Composable
fun MockMedicinerDark() = MockMediciner(darkTheme = true)

@Preview(name = "Mediciner – Light", showBackground = true, widthDp = 390, heightDp = 844)
@Composable
fun MockMedicinerLight() = MockMediciner(darkTheme = false)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MockMediciner(darkTheme: Boolean) {
    DagbokenTheme(darkTheme = darkTheme) {
        Scaffold(
            topBar = {
                MockTopBar("Mediciner") {
                    IconButton(onClick = {}) {
                        Icon(Icons.Default.BarChart, "Diagram", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            },
            bottomBar = { MockBottomBar(selected = 2) },
            containerColor = MaterialTheme.colorScheme.background,
        ) { padding ->
            Column(modifier = Modifier.fillMaxSize().padding(padding)) {
                TabRow(selectedTabIndex = 0, containerColor = MaterialTheme.colorScheme.surface) {
                    listOf("💊 Idag", "📋 Schema", "⭐ Vid behov").forEachIndexed { i, label ->
                        Tab(selected = i == 0, onClick = {}, text = { Text(label, style = MaterialTheme.typography.labelLarge) })
                    }
                }

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState()),
                ) {
                    // Progress banner
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f))
                            .padding(horizontal = 20.dp, vertical = 14.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Column {
                                Text(
                                    "1 av 3 tagna",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onBackground,
                                )
                                Text(
                                    "2 kvar för idag",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            // Circle progress indicator (static mock)
                            Box(contentAlignment = Alignment.Center) {
                                Surface(
                                    modifier = Modifier.size(56.dp),
                                    shape = CircleShape,
                                    color = MaterialTheme.colorScheme.surfaceVariant,
                                ) {}
                                Canvas(modifier = Modifier.size(56.dp)) {
                                    val stroke = 5.dp.toPx()
                                    drawArc(
                                        color = Emerald400,
                                        startAngle = -90f,
                                        sweepAngle = 360f * (1f / 3f),
                                        useCenter = false,
                                        style = Stroke(width = stroke, cap = StrokeCap.Round),
                                    )
                                }
                                Text(
                                    "33%",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(8.dp))

                    Column(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        // Time groups
                        data class TimeGroup(val label: String, val emoji: String, val meds: List<FakeMed>)
                        val groups = listOf(
                            TimeGroup("Morgon", "🌅", listOf(fakeMeds[0])),
                            TimeGroup("Lunch", "☀️", listOf(fakeMeds[1])),
                            TimeGroup("Kväll", "🌙", listOf(fakeMeds[2])),
                        )

                        groups.forEach { group ->
                            Row(
                                modifier = Modifier.padding(top = 6.dp, bottom = 2.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                Text(group.emoji, fontSize = 14.sp)
                                Text(
                                    group.label,
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            group.meds.forEach { med ->
                                ElevatedCard(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = MaterialTheme.shapes.extraLarge,
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 16.dp, vertical = 12.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                med.namn,
                                                style = MaterialTheme.typography.bodyLarge,
                                                fontWeight = if (med.tagen) FontWeight.Normal else FontWeight.SemiBold,
                                                textDecoration = if (med.tagen) TextDecoration.LineThrough else TextDecoration.None,
                                                color = if (med.tagen) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                                                        else MaterialTheme.colorScheme.onSurface,
                                            )
                                            Text(
                                                med.dos,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(
                                                    alpha = if (med.tagen) 0.35f else 1f,
                                                ),
                                            )
                                        }
                                        // Large check circle
                                        Box(
                                            modifier = Modifier
                                                .size(52.dp)
                                                .clip(CircleShape)
                                                .background(
                                                    when {
                                                        med.tagen -> Emerald400
                                                        else -> Color.Transparent
                                                    },
                                                )
                                                .border(
                                                    width = if (med.tagen) 0.dp else 2.5.dp,
                                                    color = if (med.tagen) Color.Transparent
                                                            else MaterialTheme.colorScheme.outline,
                                                    shape = CircleShape,
                                                ),
                                            contentAlignment = Alignment.Center,
                                        ) {
                                            if (med.tagen) {
                                                Icon(
                                                    Icons.Default.Check,
                                                    null,
                                                    tint = Color.White,
                                                    modifier = Modifier.size(26.dp),
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        Spacer(Modifier.height(16.dp))
                    }
                }
            }
        }
    }
}

// ─── PREVIEW 4: Diagram (sub-screen) ─────────────────────────────────────────

@Preview(name = "Diagram – Dark", showBackground = true, widthDp = 390, heightDp = 844)
@Composable
fun MockDiagramDark() = MockDiagram(darkTheme = true)

@Preview(name = "Diagram – Light", showBackground = true, widthDp = 390, heightDp = 844)
@Composable
fun MockDiagramLight() = MockDiagram(darkTheme = false)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MockDiagram(darkTheme: Boolean) {
    DagbokenTheme(darkTheme = darkTheme) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Aktiviteter — Diagram", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold) },
                    navigationIcon = {
                        IconButton(onClick = {}) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Tillbaka") }
                    },
                    actions = {
                        IconButton(onClick = {}) {
                            Icon(Icons.Default.OpenInFull, "Helskärm", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
                )
            },
            containerColor = MaterialTheme.colorScheme.background,
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                // Range filter
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("7d", "14d", "30d", "90d").forEach { label ->
                        FilterChip(selected = label == "14d", onClick = {}, label = { Text(label) })
                    }
                }

                // Series selector
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.large,
                    color = MaterialTheme.colorScheme.surfaceVariant,
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                                Box(modifier = Modifier.size(10.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary))
                                Text("Energi", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                                Box(modifier = Modifier.size(10.dp).clip(CircleShape).background(MaterialTheme.colorScheme.secondary))
                                Text("Stress", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Ändra", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                            Icon(Icons.Default.ArrowDropDown, null, tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                }

                // Chart card
                ElevatedCard(shape = MaterialTheme.shapes.extraLarge) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        val energyPts = listOf(6f, 7f, 5f, 8f, 7f, 6f, 8f, 7f, 9f, 8f, 7f, 6f, 8f, 7f)
                        val stressPts = listOf(4f, 3f, 5f, 2f, 3f, 4f, 2f, 3f, 1f, 2f, 3f, 4f, 2f, 3f)
                        val primaryColor = MaterialTheme.colorScheme.primary
                        val secondaryColor = MaterialTheme.colorScheme.secondary

                        Canvas(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(200.dp),
                        ) {
                            val w = size.width
                            val h = size.height
                            fun xAt(i: Int) = i * w / (energyPts.size - 1)
                            fun yAt(v: Float) = h - (v / 10f) * h * 0.82f - h * 0.06f

                            // Energy area
                            val ea = Path().apply {
                                moveTo(xAt(0), h)
                                lineTo(xAt(0), yAt(energyPts[0]))
                                energyPts.drop(1).forEachIndexed { i, v -> lineTo(xAt(i + 1), yAt(v)) }
                                lineTo(xAt(energyPts.size - 1), h); close()
                            }
                            drawPath(ea, brush = Brush.verticalGradient(listOf(primaryColor.copy(0.2f), Color.Transparent)))

                            // Energy line
                            val el = Path().apply {
                                moveTo(xAt(0), yAt(energyPts[0]))
                                energyPts.drop(1).forEachIndexed { i, v -> lineTo(xAt(i + 1), yAt(v)) }
                            }
                            drawPath(el, primaryColor, style = Stroke(3.dp.toPx(), cap = StrokeCap.Round))

                            // Stress line
                            val sl = Path().apply {
                                moveTo(xAt(0), yAt(stressPts[0]))
                                stressPts.drop(1).forEachIndexed { i, v -> lineTo(xAt(i + 1), yAt(v)) }
                            }
                            drawPath(sl, secondaryColor.copy(alpha = 0.8f), style = Stroke(2.5.dp.toPx(), cap = StrokeCap.Round))
                        }

                        Spacer(Modifier.height(8.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                            listOf("Snitt" to "7.1", "Lägst" to "5", "Högst" to "9").forEach { (label, value) ->
                                val color = when (value) { "5" -> Rose500; "9" -> Emerald400; else -> MaterialTheme.colorScheme.primary }
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(value, fontSize = 24.sp, fontWeight = FontWeight.ExtraBold, color = color)
                                    Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// ─── PREVIEW 5: Account Bottom Sheet ─────────────────────────────────────────

@Preview(name = "Account Sheet – Dark", showBackground = true, widthDp = 390, heightDp = 520)
@Composable
fun MockAccountSheetDark() = MockAccountSheet(darkTheme = true)

@Preview(name = "Account Sheet – Light", showBackground = true, widthDp = 390, heightDp = 520)
@Composable
fun MockAccountSheetLight() = MockAccountSheet(darkTheme = false)

@Composable
private fun MockAccountSheet(darkTheme: Boolean) {
    DagbokenTheme(darkTheme = darkTheme) {
        Surface(
            color = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        ) {
            Column(modifier = Modifier.fillMaxWidth().padding(24.dp)) {
                Box(
                    modifier = Modifier
                        .size(width = 36.dp, height = 4.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f))
                        .align(Alignment.CenterHorizontally),
                )
                Spacer(Modifier.height(20.dp))

                // Profile
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Box(
                        modifier = Modifier
                            .size(60.dp)
                            .clip(CircleShape)
                            .background(
                                Brush.radialGradient(
                                    listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.primaryContainer),
                                ),
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("EP", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    }
                    Column {
                        Text("Erik Partee", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Text("partee71@gmail.com", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Surface(
                            color = Emerald400.copy(alpha = 0.15f),
                            shape = MaterialTheme.shapes.extraSmall,
                            modifier = Modifier.padding(top = 4.dp),
                        ) {
                            Text(
                                "● Synkad nyligen",
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                style = MaterialTheme.typography.labelSmall,
                                color = Emerald400,
                            )
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))
                OutlinedButton(
                    onClick = {},
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.extraLarge,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) { Text("Logga ut") }

                HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp), color = MaterialTheme.colorScheme.outlineVariant)

                listOf(
                    Triple(Icons.Default.Settings, "Inställningar", "Tema, notiser, konfiguration"),
                    Triple(Icons.Default.CloudSync, "Säkerhetskopiera nu", "Senast synkad: idag 06:30"),
                    Triple(Icons.Default.Download, "Importera data", "Från Google Drive eller fil"),
                ).forEach { (icon, title, subtitle) ->
                    ListItem(
                        headlineContent = { Text(title, fontWeight = FontWeight.Medium) },
                        supportingContent = { Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant) },
                        leadingContent = {
                            Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = MaterialTheme.shapes.small) {
                                Icon(icon, null, modifier = Modifier.padding(6.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    )
                }
            }
        }
    }
}

// ─── PREVIEW 6: Samsung Health Placeholder ────────────────────────────────────

@Preview(name = "Samsung Health – Dark", showBackground = true, widthDp = 390, heightDp = 844)
@Composable
fun MockSamsungHealthDark() = MockSamsungHealth(true)

@Preview(name = "Samsung Health – Light", showBackground = true, widthDp = 390, heightDp = 844)
@Composable
fun MockSamsungHealthLight() = MockSamsungHealth(false)

@Composable
private fun MockSamsungHealth(darkTheme: Boolean) {
    DagbokenTheme(darkTheme = darkTheme) {
        Scaffold(
            topBar = { MockTopBar("Hälsa") },
            bottomBar = { MockBottomBar(selected = 3) },
            containerColor = MaterialTheme.colorScheme.background,
        ) { padding ->
            Column(
                modifier = Modifier.fillMaxSize().padding(padding),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Box(
                    modifier = Modifier
                        .size(110.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.radialGradient(
                                listOf(
                                    MaterialTheme.colorScheme.primaryContainer,
                                    MaterialTheme.colorScheme.surfaceVariant,
                                ),
                            ),
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Default.FitnessCenter,
                        null,
                        modifier = Modifier.size(52.dp),
                        tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
                    )
                }
                Spacer(Modifier.height(28.dp))
                Text("Samsung Health", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                Surface(
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    shape = MaterialTheme.shapes.extraSmall,
                ) {
                    Text(
                        "Kommer snart",
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        fontWeight = FontWeight.Bold,
                    )
                }
                Spacer(Modifier.height(20.dp))
                Text(
                    "Vi jobbar på att hämta steg,\nsömn och hjärtfrekvens\nautomatiskt från Samsung Health.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 40.dp),
                )
                Spacer(Modifier.height(32.dp))
                OutlinedButton(onClick = {}, modifier = Modifier.alpha(0.35f), enabled = false) {
                    Text("Anslut Samsung Health")
                }
            }
        }
    }
}

// ─── PREVIEW 7: Settings (new sections) ──────────────────────────────────────

@Preview(name = "Settings – Dark", showBackground = true, widthDp = 390, heightDp = 900)
@Composable
fun MockSettingsDark() = MockSettings(darkTheme = true)

@Preview(name = "Settings – Light", showBackground = true, widthDp = 390, heightDp = 900)
@Composable
fun MockSettingsLight() = MockSettings(darkTheme = false)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MockSettings(darkTheme: Boolean) {
    DagbokenTheme(darkTheme = darkTheme) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Inställningar", fontWeight = FontWeight.SemiBold) },
                    navigationIcon = {
                        IconButton(onClick = {}) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Tillbaka") }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
                )
            },
            containerColor = MaterialTheme.colorScheme.background,
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                @Composable
                fun SettingsCard(emoji: String, title: String, content: @Composable () -> Unit) {
                    ElevatedCard(modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.extraLarge) {
                        Column(modifier = Modifier.padding(20.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(emoji, fontSize = 18.sp)
                                Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                            }
                            Spacer(Modifier.height(14.dp))
                            content()
                        }
                    }
                }

                SettingsCard("🎨", "Utseende") {
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("Mörkt tema", modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                        Switch(checked = darkTheme, onCheckedChange = {})
                    }
                }

                SettingsCard("⏰", "Tema-schema") {
                    Text(
                        "Tema-läge",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(8.dp))
                    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                        listOf("Ljust" to Icons.Default.WbSunny, "Mörkt" to Icons.Default.Schedule, "Auto" to Icons.Default.Circle)
                            .forEachIndexed { i, (label, icon) ->
                                SegmentedButton(
                                    selected = i == 2,
                                    onClick = {},
                                    shape = SegmentedButtonDefaults.itemShape(i, 3),
                                    icon = {
                                        SegmentedButtonDefaults.Icon(active = i == 2) {
                                            Icon(icon, null, modifier = Modifier.size(SegmentedButtonDefaults.IconSize))
                                        }
                                    },
                                ) { Text(label) }
                            }
                    }
                    Spacer(Modifier.height(14.dp))
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("🌅  Ljust tema från", modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                        Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = MaterialTheme.shapes.medium) {
                            Text("07:00", modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer)
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("🌙  Mörkt tema från", modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                        Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = MaterialTheme.shapes.medium) {
                            Text("21:00", modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                SettingsCard("🔔", "Påminnelser") {
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Medicinpåminnelser", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                            Text("15 min före tidpunkt", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(checked = true, onCheckedChange = {})
                    }
                    HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), color = MaterialTheme.colorScheme.outlineVariant)
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Screeningpåminnelse", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                            Text("Daglig påminnelse", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(checked = false, onCheckedChange = {})
                    }
                    Spacer(Modifier.height(4.dp))
                    Row(modifier = Modifier.fillMaxWidth().alpha(0.38f), verticalAlignment = Alignment.CenterVertically) {
                        Text("🌅  Tid för screening", modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                        Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = MaterialTheme.shapes.medium) {
                            Text("08:00", modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }

                Spacer(Modifier.height(8.dp))
            }
        }
    }
}

// ─── Shared data for rating concepts ─────────────────────────────────────────

private data class Symptom(val label: String, val emoji: String, val value: Float)
private val fakeSymptoms = listOf(
    Symptom("Energi",  "⚡", 7f),
    Symptom("Smärta",  "🔥", 3f),
    Symptom("Stress",  "🌀", 6f),
)

private fun gradientColors() = listOf(Rose500, Amber400, Emerald400)

// ─── PREVIEW 8: Concept A — Gradient Slider ──────────────────────────────────

@Preview(name = "Rating A – Gradient Slider – Light", showBackground = true, widthDp = 390, heightDp = 720)
@Composable
fun MockRatingSliderLight() = MockRatingSlider(false)

@Preview(name = "Rating A – Gradient Slider – Dark", showBackground = true, widthDp = 390, heightDp = 720)
@Composable
fun MockRatingSliderDark() = MockRatingSlider(true)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MockRatingSlider(darkTheme: Boolean) {
    DagbokenTheme(darkTheme = darkTheme) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Screening — Energi & Symtom", fontWeight = FontWeight.SemiBold) },
                    navigationIcon = { IconButton(onClick = {}) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) } },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
                )
            },
            containerColor = MaterialTheme.colorScheme.background,
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = MaterialTheme.shapes.small,
                ) {
                    Text(
                        "A · Gradient-reglage",
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
                Spacer(Modifier.height(8.dp))

                fakeSymptoms.forEach { s ->
                    GradientSliderRow(s.label, s.emoji, s.value)
                    Spacer(Modifier.height(16.dp))
                }

                Spacer(Modifier.weight(1f))
                Button(
                    onClick = {},
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.extraLarge,
                ) {
                    Text("Spara screening", fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
private fun GradientSliderRow(label: String, emoji: String, value: Float) {
    val eColor = energyColor(value.toInt())
    val density = LocalDensity.current

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom,
        ) {
            Text("$emoji  $label", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(1.dp)) {
                Text(
                    value.toInt().toString(),
                    fontSize = 28.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = eColor,
                    lineHeight = 28.sp,
                )
                Text(
                    " /10",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 3.dp),
                )
            }
        }

        // Slider track + thumb
        BoxWithConstraints(modifier = Modifier.fillMaxWidth().height(48.dp)) {
            val thumbSize = 44.dp
            val trackFraction = value / 10f
            val thumbOffsetX = (maxWidth - thumbSize) * trackFraction
            val fullWidthPx = with(density) { maxWidth.toPx() }

            // Dim background track
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(10.dp)
                    .align(Alignment.CenterStart)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
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
                            endX = fullWidthPx,
                        ),
                    ),
            )
            // Thumb: white circle with coloured border, value inside
            Box(
                modifier = Modifier
                    .offset(x = thumbOffsetX, y = 2.dp)
                    .size(thumbSize)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surface)
                    .border(2.5.dp, eColor, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    value.toInt().toString(),
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 15.sp,
                    color = eColor,
                )
            }
        }

        // End labels
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("0  😴", style = MaterialTheme.typography.labelSmall, color = Rose500)
            Text("😊  10", style = MaterialTheme.typography.labelSmall, color = Emerald400)
        }
    }
}

// ─── PREVIEW 9: Concept B — Bubble Row ───────────────────────────────────────

@Preview(name = "Rating B – Bubble Row – Light", showBackground = true, widthDp = 390, heightDp = 720)
@Composable
fun MockRatingBubblesLight() = MockRatingBubbles(false)

@Preview(name = "Rating B – Bubble Row – Dark", showBackground = true, widthDp = 390, heightDp = 720)
@Composable
fun MockRatingBubblesDark() = MockRatingBubbles(true)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MockRatingBubbles(darkTheme: Boolean) {
    DagbokenTheme(darkTheme = darkTheme) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Screening — Energi & Symtom", fontWeight = FontWeight.SemiBold) },
                    navigationIcon = { IconButton(onClick = {}) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) } },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
                )
            },
            containerColor = MaterialTheme.colorScheme.background,
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = MaterialTheme.shapes.small,
                ) {
                    Text(
                        "B · Bubblor",
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
                Spacer(Modifier.height(8.dp))

                fakeSymptoms.forEach { s ->
                    BubblePickerRow(s.label, s.emoji, s.value.toInt())
                    Spacer(Modifier.height(20.dp))
                }

                Spacer(Modifier.weight(1f))
                Button(
                    onClick = {},
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.extraLarge,
                ) {
                    Text("Spara screening", fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
private fun BubblePickerRow(label: String, emoji: String, value: Int) {
    val eColor = energyColor(value)
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("$emoji  $label", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Text(
                "$value / 10",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.ExtraBold,
                color = eColor,
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            (0..10).forEach { i ->
                val selected = i == value
                val size = if (selected) 38.dp else 26.dp
                val bg = if (selected) energyColor(i) else MaterialTheme.colorScheme.surfaceVariant
                val border = if (selected) energyColor(i) else MaterialTheme.colorScheme.outlineVariant
                Box(
                    modifier = Modifier
                        .size(size)
                        .clip(CircleShape)
                        .background(bg)
                        .border(1.5.dp, border, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        i.toString(),
                        fontSize = if (selected) 13.sp else 9.sp,
                        fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.Medium,
                        color = if (selected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

// ─── PREVIEW 10: Concept C — Arc Dial (Speedometer) ─────────────────────────

@Preview(name = "Rating C – Arc Dial – Light", showBackground = true, widthDp = 390, heightDp = 720)
@Composable
fun MockRatingArcLight() = MockRatingArc(false)

@Preview(name = "Rating C – Arc Dial – Dark", showBackground = true, widthDp = 390, heightDp = 720)
@Composable
fun MockRatingArcDark() = MockRatingArc(true)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MockRatingArc(darkTheme: Boolean) {
    DagbokenTheme(darkTheme = darkTheme) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Screening — Energi & Symtom", fontWeight = FontWeight.SemiBold) },
                    navigationIcon = { IconButton(onClick = {}) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) } },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
                )
            },
            containerColor = MaterialTheme.colorScheme.background,
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = MaterialTheme.shapes.small,
                ) {
                    Text(
                        "C · Hastighetsmätare",
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
                Spacer(Modifier.height(12.dp))

                // 3 dials side by side
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                ) {
                    fakeSymptoms.forEach { s ->
                        ArcDialWidget(s.label, s.emoji, s.value.toInt())
                    }
                }

                Spacer(Modifier.height(28.dp))

                // Touch hint
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.large,
                    color = MaterialTheme.colorScheme.surfaceVariant,
                ) {
                    Text(
                        "Tryck på en mätare och dra för att justera",
                        modifier = Modifier.padding(12.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }

                Spacer(Modifier.weight(1f))
                Button(
                    onClick = {},
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.extraLarge,
                ) {
                    Text("Spara screening", fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
private fun ArcDialWidget(label: String, emoji: String, value: Int) {
    val eColor = energyColor(value)
    val trackColor = MaterialTheme.colorScheme.surfaceVariant
    val surfaceColor = MaterialTheme.colorScheme.surface

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(modifier = Modifier.size(110.dp), contentAlignment = Alignment.Center) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val sweepStart = 150f
                val sweepTotal = 240f
                val cx = size.width / 2f
                val cy = size.height * 0.58f
                val r = size.width * 0.38f
                val strokeW = 12.dp.toPx()
                val arcTopLeft = Offset(cx - r, cy - r)
                val arcSize = Size(r * 2, r * 2)

                // Background track
                drawArc(
                    color = trackColor,
                    startAngle = sweepStart,
                    sweepAngle = sweepTotal,
                    useCenter = false,
                    topLeft = arcTopLeft,
                    size = arcSize,
                    style = Stroke(width = strokeW, cap = StrokeCap.Round),
                )

                // Gradient fill arc
                if (value > 0) {
                    val fillSweep = sweepTotal * (value / 10f)
                    drawArc(
                        brush = Brush.horizontalGradient(
                            colors = listOf(Rose500, Amber400, Emerald400),
                            startX = cx - r,
                            endX = cx + r,
                        ),
                        startAngle = sweepStart,
                        sweepAngle = fillSweep,
                        useCenter = false,
                        topLeft = arcTopLeft,
                        size = arcSize,
                        style = Stroke(width = strokeW, cap = StrokeCap.Round),
                    )

                    // Thumb dot at tip of arc
                    val tipAngle = Math.toRadians((sweepStart + fillSweep).toDouble())
                    val tx = cx + r * cos(tipAngle).toFloat()
                    val ty = cy + r * sin(tipAngle).toFloat()
                    drawCircle(color = surfaceColor, radius = 8.dp.toPx(), center = Offset(tx, ty))
                    drawCircle(color = eColor, radius = 5.5.dp.toPx(), center = Offset(tx, ty))
                }
            }

            // Value + label centred inside the arc bowl
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.offset(y = 10.dp),
            ) {
                Text(emoji, fontSize = 14.sp, lineHeight = 14.sp)
                Text(
                    value.toString(),
                    fontSize = 26.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = eColor,
                    lineHeight = 26.sp,
                )
            }
        }

        Text(label, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("0", style = MaterialTheme.typography.labelSmall, color = Rose500)
            Text("–", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("10", style = MaterialTheme.typography.labelSmall, color = Emerald400)
        }
    }
}
