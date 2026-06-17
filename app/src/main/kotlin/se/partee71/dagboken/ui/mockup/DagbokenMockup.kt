@file:Suppress("PreviewAnnotationInFunctionWithParameters")

package se.partee71.dagboken.ui.mockup

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LocalPharmacy
import androidx.compose.material.icons.filled.Medication
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.FitnessCenter
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.LocalPharmacy
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import se.partee71.dagboken.ui.theme.DagbokenTheme
import se.partee71.dagboken.ui.theme.Emerald400

// ─── Fake data ────────────────────────────────────────────────────────────────

private data class FakeMed(val namn: String, val dos: String, val enhet: String, val tidpunkt: String, val tagen: Boolean)
private val fakeMeds = listOf(
    FakeMed("Metformin",  "500", "mg", "Morgon", true),
    FakeMed("Losartan",   "25",  "mg", "Lunch",  false),
    FakeMed("Aspirin",    "75",  "mg", "Kväll",  false),
)

// energy values for Mon–Sun, 0–10 scale
private val fakeEnergyBars = listOf(6f, 7f, 5f, 8f, 7f, 6f, 9f)
private val fakeDayLabels  = listOf("Mån", "Tis", "Ons", "Tor", "Fre", "Lör", "Sön")

// ─── Shared micro-components ──────────────────────────────────────────────────

@Composable
private fun MockAccountBubble(modifier: Modifier = Modifier) {
    Box(modifier = modifier.size(40.dp)) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer)
                .align(Alignment.Center),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Filled.AccountCircle,
                contentDescription = null,
                tint     = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp),
            )
        }
        // Online indicator dot
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

@Suppress("SameParameterValue")
@Composable
private fun MockBottomBar(selected: Int = 0) {
    val items = listOf(
        Triple("Hem",      Icons.Filled.Home,          Icons.Outlined.Home),
        Triple("Aktivitet",Icons.Filled.Bolt,          Icons.Outlined.Bolt),
        Triple("Mediciner",Icons.Filled.LocalPharmacy, Icons.Outlined.LocalPharmacy),
        Triple("Hälsa",    Icons.Filled.FitnessCenter, Icons.Outlined.FitnessCenter),
    )
    NavigationBar {
        items.forEachIndexed { i, (label, filled, outlined) ->
            NavigationBarItem(
                selected = i == selected,
                onClick  = {},
                icon     = { Icon(if (i == selected) filled else outlined, null) },
                label    = { Text(label, style = MaterialTheme.typography.labelSmall) },
                modifier = if (i == 3) Modifier.alpha(0.35f) else Modifier,
                enabled  = i != 3,
            )
        }
    }
}

// ─── PREVIEW: Home – Light ────────────────────────────────────────────────────

@Preview(name = "Home – Light", showBackground = true, widthDp = 390, heightDp = 844)
@Composable
fun MockHomeLight() {
    DagbokenTheme(darkTheme = false) {
        val cs = MaterialTheme.colorScheme

        Scaffold(
            topBar = {
                @OptIn(ExperimentalMaterial3Api::class)
                TopAppBar(
                    navigationIcon = {
                        MockAccountBubble(modifier = Modifier.padding(start = 8.dp))
                    },
                    title = {},
                    actions = {
                        Text(
                            "Lördag 14 juni",
                            style    = MaterialTheme.typography.labelMedium,
                            color    = cs.onSurfaceVariant,
                            modifier = Modifier.padding(end = 16.dp),
                        )
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = cs.surface,
                    ),
                )
            },
            bottomBar = { MockBottomBar(selected = 0) },
            containerColor = cs.background,
        ) { padding ->
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(padding)
                    .padding(horizontal = 16.dp)
                    .padding(top = 12.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {

                // ── 1. Greeting ──────────────────────────────────────────────
                Column(modifier = Modifier.padding(vertical = 8.dp)) {
                    Text(
                        "God morgon",
                        style      = MaterialTheme.typography.headlineLarge,
                        fontWeight = FontWeight.ExtraBold,
                        color      = cs.onBackground,
                    )
                    Text(
                        "Erik Partee",
                        style = MaterialTheme.typography.bodyLarge,
                        color = cs.onSurfaceVariant,
                    )
                }

                // ── 2. At-a-glance stat chips ────────────────────────────────
                Row(
                    modifier              = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    // Meds chip
                    ElevatedCard(
                        modifier = Modifier.weight(1f),
                        colors   = CardDefaults.elevatedCardColors(containerColor = cs.surface),
                        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp),
                    ) {
                        Row(
                            modifier          = Modifier.padding(horizontal = 14.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(cs.secondaryContainer),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    Icons.Filled.Medication,
                                    null,
                                    tint     = cs.secondary,
                                    modifier = Modifier.size(20.dp),
                                )
                            }
                            Column {
                                Text(
                                    "1/3",
                                    style      = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.ExtraBold,
                                    color      = cs.onSurface,
                                )
                                Text(
                                    "mediciner",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = cs.onSurfaceVariant,
                                )
                            }
                        }
                    }
                    // Energy chip
                    ElevatedCard(
                        modifier  = Modifier.weight(1f),
                        colors    = CardDefaults.elevatedCardColors(containerColor = cs.surface),
                        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp),
                    ) {
                        Row(
                            modifier          = Modifier.padding(horizontal = 14.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(Emerald400.copy(alpha = 0.15f)),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    Icons.Filled.Bolt,
                                    null,
                                    tint     = Emerald400,
                                    modifier = Modifier.size(20.dp),
                                )
                            }
                            Column {
                                Text(
                                    "+9",
                                    style      = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.ExtraBold,
                                    color      = Emerald400,
                                )
                                Text(
                                    "energi idag",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = cs.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }

                // ── 3. Mediciner idag ────────────────────────────────────────
                ElevatedCard(
                    modifier  = Modifier.fillMaxWidth(),
                    colors    = CardDefaults.elevatedCardColors(containerColor = cs.surface),
                    elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp),
                ) {
                    Column(modifier = Modifier.padding(top = 16.dp, start = 16.dp, end = 16.dp)) {
                        Row(
                            modifier              = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment     = Alignment.CenterVertically,
                        ) {
                            Text(
                                "Mediciner idag",
                                style      = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                            )
                            TextButton(
                                onClick          = {},
                                contentPadding   = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                            ) {
                                Text("Visa alla", style = MaterialTheme.typography.labelLarge)
                            }
                        }

                        // Progress
                        Row(
                            modifier          = Modifier.fillMaxWidth().padding(bottom = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            LinearProgressIndicator(
                                progress  = { 1f / 3f },
                                modifier  = Modifier.weight(1f),
                                color     = cs.secondary,
                                trackColor = cs.secondaryContainer,
                                strokeCap  = StrokeCap.Round,
                            )
                            Text(
                                "1 av 3",
                                style = MaterialTheme.typography.labelMedium,
                                color = cs.onSurfaceVariant,
                            )
                        }

                        HorizontalDivider(color = cs.outlineVariant)
                    }

                    // Med rows
                    fakeMeds.forEachIndexed { index, med ->
                        MedRow(med = med, isLast = index == fakeMeds.lastIndex)
                    }
                }

                // ── 4. Energy trend ──────────────────────────────────────────
                ElevatedCard(
                    modifier  = Modifier.fillMaxWidth(),
                    colors    = CardDefaults.elevatedCardColors(containerColor = cs.surface),
                    elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp),
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier              = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment     = Alignment.CenterVertically,
                        ) {
                            Column {
                                Text(
                                    "Energi — 7 dagar",
                                    style      = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold,
                                )
                                Text(
                                    "Genomsnitt: 6.9",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = cs.onSurfaceVariant,
                                )
                            }
                            TextButton(
                                onClick        = {},
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                            ) {
                                Text("Diagram", style = MaterialTheme.typography.labelLarge)
                            }
                        }

                        Spacer(Modifier.height(12.dp))

                        // Bar chart
                        BarChart(
                            values    = fakeEnergyBars,
                            labels    = fakeDayLabels,
                            barColor  = cs.primary,
                        )
                    }
                }

                // ── 5. Quick actions ─────────────────────────────────────────
                Row(
                    modifier              = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    FilledTonalButton(
                        onClick   = {},
                        modifier  = Modifier.weight(1f),
                        shape     = MaterialTheme.shapes.large,
                        contentPadding = PaddingValues(vertical = 14.dp),
                    ) {
                        Icon(Icons.Filled.Bolt, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Logga aktivitet", fontWeight = FontWeight.SemiBold)
                    }
                    FilledTonalButton(
                        onClick   = {},
                        modifier  = Modifier.weight(1f),
                        shape     = MaterialTheme.shapes.large,
                        contentPadding = PaddingValues(vertical = 14.dp),
                    ) {
                        Icon(Icons.Filled.Medication, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Mediciner", fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}

// ─── PREVIEW: Home – Dark ─────────────────────────────────────────────────────

@Preview(name = "Home – Dark", showBackground = true, widthDp = 390, heightDp = 844)
@Composable
fun MockHomeDark() {
    DagbokenTheme(darkTheme = true) {
        val cs = MaterialTheme.colorScheme

        Scaffold(
            topBar = {
                @OptIn(ExperimentalMaterial3Api::class)
                TopAppBar(
                    navigationIcon = {
                        MockAccountBubble(modifier = Modifier.padding(start = 8.dp))
                    },
                    title = {},
                    actions = {
                        Text(
                            "Lördag 14 juni",
                            style    = MaterialTheme.typography.labelMedium,
                            color    = cs.onSurfaceVariant,
                            modifier = Modifier.padding(end = 16.dp),
                        )
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = cs.surface,
                    ),
                )
            },
            bottomBar = { MockBottomBar(selected = 0) },
            containerColor = cs.background,
        ) { padding ->
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(padding)
                    .padding(horizontal = 16.dp)
                    .padding(top = 12.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Column(modifier = Modifier.padding(vertical = 8.dp)) {
                    Text(
                        "God morgon",
                        style      = MaterialTheme.typography.headlineLarge,
                        fontWeight = FontWeight.ExtraBold,
                        color      = cs.onBackground,
                    )
                    Text(
                        "Erik Partee",
                        style = MaterialTheme.typography.bodyLarge,
                        color = cs.onSurfaceVariant,
                    )
                }

                Row(
                    modifier              = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    ElevatedCard(
                        modifier  = Modifier.weight(1f),
                        colors    = CardDefaults.elevatedCardColors(containerColor = cs.surface),
                        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp),
                    ) {
                        Row(
                            modifier          = Modifier.padding(horizontal = 14.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(cs.secondaryContainer),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(Icons.Filled.Medication, null, tint = cs.secondary, modifier = Modifier.size(20.dp))
                            }
                            Column {
                                Text("1/3", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold, color = cs.onSurface)
                                Text("mediciner", style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant)
                            }
                        }
                    }
                    ElevatedCard(
                        modifier  = Modifier.weight(1f),
                        colors    = CardDefaults.elevatedCardColors(containerColor = cs.surface),
                        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp),
                    ) {
                        Row(
                            modifier          = Modifier.padding(horizontal = 14.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(Emerald400.copy(alpha = 0.18f)),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(Icons.Filled.Bolt, null, tint = Emerald400, modifier = Modifier.size(20.dp))
                            }
                            Column {
                                Text("+9", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold, color = Emerald400)
                                Text("energi idag", style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant)
                            }
                        }
                    }
                }

                ElevatedCard(
                    modifier  = Modifier.fillMaxWidth(),
                    colors    = CardDefaults.elevatedCardColors(containerColor = cs.surface),
                    elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp),
                ) {
                    Column(modifier = Modifier.padding(top = 16.dp, start = 16.dp, end = 16.dp)) {
                        Row(
                            modifier              = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment     = Alignment.CenterVertically,
                        ) {
                            Text("Mediciner idag", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                            TextButton(onClick = {}, contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)) {
                                Text("Visa alla", style = MaterialTheme.typography.labelLarge)
                            }
                        }
                        Row(
                            modifier          = Modifier.fillMaxWidth().padding(bottom = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            LinearProgressIndicator(
                                progress   = { 1f / 3f },
                                modifier   = Modifier.weight(1f),
                                color      = cs.secondary,
                                trackColor = cs.secondaryContainer,
                                strokeCap  = StrokeCap.Round,
                            )
                            Text("1 av 3", style = MaterialTheme.typography.labelMedium, color = cs.onSurfaceVariant)
                        }
                        HorizontalDivider(color = cs.outlineVariant)
                    }
                    fakeMeds.forEachIndexed { index, med ->
                        MedRow(med = med, isLast = index == fakeMeds.lastIndex)
                    }
                }

                ElevatedCard(
                    modifier  = Modifier.fillMaxWidth(),
                    colors    = CardDefaults.elevatedCardColors(containerColor = cs.surface),
                    elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp),
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier              = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment     = Alignment.CenterVertically,
                        ) {
                            Column {
                                Text("Energi — 7 dagar", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                                Text("Genomsnitt: 6.9", style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant)
                            }
                            TextButton(onClick = {}, contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)) {
                                Text("Diagram", style = MaterialTheme.typography.labelLarge)
                            }
                        }
                        Spacer(Modifier.height(12.dp))
                        BarChart(values = fakeEnergyBars, labels = fakeDayLabels, barColor = cs.primary)
                    }
                }

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    FilledTonalButton(
                        onClick        = {},
                        modifier       = Modifier.weight(1f),
                        shape          = MaterialTheme.shapes.large,
                        contentPadding = PaddingValues(vertical = 14.dp),
                    ) {
                        Icon(Icons.Filled.Bolt, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Logga aktivitet", fontWeight = FontWeight.SemiBold)
                    }
                    FilledTonalButton(
                        onClick        = {},
                        modifier       = Modifier.weight(1f),
                        shape          = MaterialTheme.shapes.large,
                        contentPadding = PaddingValues(vertical = 14.dp),
                    ) {
                        Icon(Icons.Filled.Medication, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Mediciner", fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}

// ─── Shared sub-composables ───────────────────────────────────────────────────

@Composable
private fun MedRow(med: FakeMed, isLast: Boolean) {
    val cs = MaterialTheme.colorScheme
    Row(
        modifier          = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(if (med.tagen) Emerald400 else cs.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            if (med.tagen) {
                Icon(
                    Icons.Filled.CheckCircle,
                    null,
                    tint     = Color.White,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                med.namn,
                style          = MaterialTheme.typography.bodyMedium,
                fontWeight     = if (med.tagen) FontWeight.Normal else FontWeight.SemiBold,
                color          = if (med.tagen) cs.onSurface.copy(alpha = 0.4f) else cs.onSurface,
                textDecoration = if (med.tagen) TextDecoration.LineThrough else TextDecoration.None,
                maxLines       = 1,
                overflow       = TextOverflow.Ellipsis,
            )
            Text(
                "${med.dos} ${med.enhet}  •  ${med.tidpunkt}",
                style  = MaterialTheme.typography.bodySmall,
                color  = cs.onSurfaceVariant.copy(alpha = if (med.tagen) 0.5f else 1f),
            )
        }
    }
    if (!isLast) {
        HorizontalDivider(
            modifier  = Modifier.padding(horizontal = 16.dp),
            color     = cs.outlineVariant.copy(alpha = 0.5f),
        )
    }
}

@Composable
private fun BarChart(
    values   : List<Float>,
    labels   : List<String>,
    barColor : Color,
) {
    val cs = MaterialTheme.colorScheme
    val maxVal = 10f
    val barHeightMax = 56.dp
    val barWidth = 24.dp
    val cornerRadius = 4.dp

    Column {
        // Bars
        Row(
            modifier              = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment     = Alignment.Bottom,
        ) {
            values.forEach { value ->
                val fraction = (value / maxVal).coerceIn(0f, 1f)
                val heightDp = (barHeightMax.value * fraction).coerceAtLeast(6f).dp
                val isMax = value == values.max()

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    // Value label above tallest bar only
                    if (isMax) {
                        Text(
                            value.toInt().toString(),
                            style      = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color      = barColor,
                        )
                    } else {
                        Spacer(Modifier.height(16.dp))
                    }

                    Box(
                        modifier = Modifier
                            .width(barWidth)
                            .height(heightDp)
                            .clip(RoundedCornerShape(topStart = cornerRadius, topEnd = cornerRadius))
                            .background(
                                if (isMax) barColor
                                else barColor.copy(alpha = 0.28f)
                            ),
                    )
                }
            }
        }
        // Day labels
        Spacer(Modifier.height(6.dp))
        Row(
            modifier              = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            labels.forEach { label ->
                Text(
                    label,
                    style  = MaterialTheme.typography.labelSmall,
                    color  = cs.onSurfaceVariant,
                    modifier = Modifier.width(24.dp),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
            }
        }
    }
}
