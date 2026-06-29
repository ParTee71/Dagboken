@file:Suppress("PreviewAnnotationInFunctionWithParameters")

package se.partee71.dagboken.ui.mockup

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Medication
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import se.partee71.dagboken.ui.components.DagbokenCard
import se.partee71.dagboken.ui.theme.DagbokenTheme

// ─── NoteField mock ────────────────────────────────────────────────────────────
// Mirrors the NoteField spec from #31:
//  - Empty: collapsed, shows placeholder text
//  - Has text: collapsed, shows 2-line preview + chevron
//  - Expanded: full OutlinedTextField for editing

@Composable
private fun NoteFieldMock(
    text: String,
    expanded: Boolean,
    modifier: Modifier = Modifier,
) {
    val hasText = text.isNotBlank()
    val chevronRotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        label = "note_chevron",
    )

    ElevatedCard(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(horizontal = 16.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { }
                    .padding(vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = if (hasText || expanded) "Anteckning" else "Lägg till en anteckning…",
                    style = MaterialTheme.typography.labelLarge,
                    color = if (hasText || expanded)
                        MaterialTheme.colorScheme.onSurfaceVariant
                    else
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
                    modifier = Modifier.weight(1f),
                )
                Icon(
                    imageVector = Icons.Default.ExpandMore,
                    contentDescription = null,
                    modifier = Modifier.rotate(chevronRotation),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            // Collapsed text preview (max 2 lines)
            AnimatedVisibility(
                visible = !expanded && hasText,
                enter = expandVertically(),
                exit = shrinkVertically(),
            ) {
                Column {
                    HorizontalDivider()
                    Text(
                        text = text,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(vertical = 10.dp),
                    )
                }
            }

            // Expanded: full editable field
            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically(),
                exit = shrinkVertically(),
            ) {
                OutlinedTextField(
                    value = text,
                    onValueChange = {},
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp),
                    minLines = 3,
                    placeholder = { Text("Lägg till en anteckning…") },
                )
            }
        }
    }
}

@Composable
private fun MockLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.outline,
    )
}

// ─── Preview 1: NoteField component states ────────────────────────────────────

@Preview(name = "#31 NoteField – tom (ljust)", showBackground = true, widthDp = 390, heightDp = 160)
@Composable
fun MockNoteFieldEmpty() {
    DagbokenTheme(darkTheme = false) {
        Surface {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                MockLabel("1 / 3  Tom, kollapsad")
                NoteFieldMock(text = "", expanded = false)
            }
        }
    }
}

@Preview(name = "#31 NoteField – text kollapsad (ljust)", showBackground = true, widthDp = 390, heightDp = 200)
@Composable
fun MockNoteFieldCollapsed() {
    DagbokenTheme(darkTheme = false) {
        Surface {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                MockLabel("2 / 3  Har text, kollapsad (2-raders förhandsgranskning)")
                NoteFieldMock(
                    text = "Kände mig extra trött under träningen. Vädret var kallt och blåsigt men det gick bra ändå.",
                    expanded = false,
                )
            }
        }
    }
}

@Preview(name = "#31 NoteField – expanderad (ljust)", showBackground = true, widthDp = 390, heightDp = 280)
@Composable
fun MockNoteFieldExpanded() {
    DagbokenTheme(darkTheme = false) {
        Surface {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                MockLabel("3 / 3  Expanderad – redigeringsläge")
                NoteFieldMock(
                    text = "Kände mig extra trött under träningen. Vädret var kallt och blåsigt men det gick bra ändå.",
                    expanded = true,
                )
            }
        }
    }
}

@Preview(name = "#31 NoteField – expanderad (mörkt)", showBackground = true, widthDp = 390, heightDp = 280)
@Composable
fun MockNoteFieldExpandedDark() {
    DagbokenTheme(darkTheme = true) {
        Surface {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                MockLabel("3 / 3  Expanderad – mörkt tema")
                NoteFieldMock(
                    text = "Kände mig extra trött under träningen. Vädret var kallt och blåsigt men det gick bra ändå.",
                    expanded = true,
                )
            }
        }
    }
}

// ─── Preview 2: HomeScreen – DAY note ─────────────────────────────────────────
// Placering: efter energikortet, före snabbåtgärdsraden

@Preview(name = "#31 HomeScreen – Daganteckning (DAY)", showBackground = true, widthDp = 390, heightDp = 460)
@Composable
fun MockHomeScreenDayNote() {
    DagbokenTheme(darkTheme = false) {
        Surface {
            Column(
                modifier = Modifier
                    .padding(horizontal = 16.dp)
                    .padding(top = 16.dp, bottom = 24.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                DagbokenCard(title = "Energikurva – senaste 7 dagar") {
                    Text(
                        "[ sparkline ]",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    TextButton(onClick = {}, modifier = Modifier.fillMaxWidth()) {
                        Text("Visa diagram")
                    }
                }

                // NoteTarget.DAY — targetId = ISO-datum, t.ex. "2026-06-24"
                NoteFieldMock(
                    text = "Bra dag! Lite ont i nacken på eftermiddagen.",
                    expanded = false,
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    ElevatedCard(
                        onClick = {},
                        modifier = Modifier.weight(1f),
                        colors = CardDefaults.elevatedCardColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                        ),
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 18.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Icon(
                                Icons.Filled.Bolt, null,
                                tint = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.size(28.dp),
                            )
                            Text(
                                "Logga aktivitet",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onPrimary,
                            )
                        }
                    }
                    ElevatedCard(
                        onClick = {},
                        modifier = Modifier.weight(1f),
                        colors = CardDefaults.elevatedCardColors(
                            containerColor = MaterialTheme.colorScheme.secondary,
                        ),
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 18.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Icon(
                                Icons.Filled.Medication, null,
                                tint = MaterialTheme.colorScheme.onSecondary,
                                modifier = Modifier.size(28.dp),
                            )
                            Text(
                                "Mediciner",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSecondary,
                            )
                        }
                    }
                }
            }
        }
    }
}

// ─── Preview 3: LoggaTab – ACTIVITY note ──────────────────────────────────────
// Placering: efter SymptomLogCard, sist i formuläret

@Preview(name = "#31 LoggaTab – Aktivitetsanteckning (ACTIVITY)", showBackground = true, widthDp = 390, heightDp = 300)
@Composable
fun MockLoggaTabActivityNote() {
    DagbokenTheme(darkTheme = false) {
        Surface {
            Column(
                modifier = Modifier
                    .padding(horizontal = 16.dp)
                    .padding(top = 16.dp, bottom = 24.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                DagbokenCard(title = "Symtom") {
                    Text(
                        "[ symptomloggar ]",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                // NoteTarget.ACTIVITY — targetId = aktivitetens id.toString()
                NoteFieldMock(text = "", expanded = false)
            }
        }
    }
}

// ─── Preview 4: ScreeningTab – SCREENING note ─────────────────────────────────
// Placering: sist i fliken, efter SymptomLogCard

@Preview(name = "#31 ScreeningTab – Screeninganteckning (SCREENING)", showBackground = true, widthDp = 390, heightDp = 360)
@Composable
fun MockScreeningTabNote() {
    DagbokenTheme(darkTheme = false) {
        Surface {
            Column(
                modifier = Modifier
                    .padding(horizontal = 16.dp)
                    .padding(top = 16.dp, bottom = 24.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                DagbokenCard(title = "Energi & stress") {
                    Text(
                        "[ energy slider ]\n[ stress slider ]",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                DagbokenCard(title = "Symtom") {
                    Text(
                        "[ symptomloggar ]",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                // NoteTarget.SCREENING — targetId = ISO-datum, t.ex. "2026-06-24"
                NoteFieldMock(
                    text = "Vaknade trött trots 8h sömn.",
                    expanded = false,
                )
            }
        }
    }
}

// ─── Preview 5: AddEditHandelseScreen – EVENT note ────────────────────────────
// NoteField ersätter det befintliga OutlinedTextField för anteckning

@Preview(name = "#31 AddEditHandelse – händelseanteckning (EVENT)", showBackground = true, widthDp = 390, heightDp = 440)
@Composable
fun MockHandelseEventNote() {
    DagbokenTheme(darkTheme = false) {
        Surface {
            Column(
                modifier = Modifier
                    .padding(horizontal = 16.dp)
                    .padding(top = 16.dp, bottom = 24.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                OutlinedTextField(
                    value = "Sol, ansträngning",
                    onValueChange = {},
                    label = { Text("Triggers") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                    maxLines = 4,
                )

                OutlinedTextField(
                    value = "",
                    onValueChange = {},
                    label = { Text("Åtgärder") },
                    placeholder = { Text("Vad hjälpte?") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                    maxLines = 4,
                )

                // NoteTarget.EVENT — ersätter anteckning-fältet, targetId = händelsens id.toString()
                NoteFieldMock(
                    text = "Låg ner i 20 min, blodtrycket stabiliserade sig.",
                    expanded = false,
                )

                FilledTonalButton(
                    onClick = {},
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(
                        Icons.Default.Save,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.size(8.dp))
                    Text("Spara")
                }
            }
        }
    }
}

// ─── Preview 6: Medication intake dialog – MEDICATION note ───────────────────
// Triggas av tryck på läkemedelsraden i IdagTab (inte kryssrutan).
// Låter användaren notera t.ex. biverkningar eller tidpunkt.

@Preview(name = "#31 IdagTab – intags­dialog (MEDICATION note)", showBackground = true, widthDp = 390, heightDp = 680)
@Composable
fun MockMedicinIntaksdialog() {
    DagbokenTheme(darkTheme = false) {
        Surface {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(0.dp),
            ) {
                // Lista för kontext
                Text(
                    "MORGON",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(bottom = 4.dp),
                )
                ListItem(
                    headlineContent = { Text("Metoprolol") },
                    supportingContent = { Text("25 mg") },
                    trailingContent = { Checkbox(checked = true, onCheckedChange = {}) },
                )
                HorizontalDivider()
                ListItem(
                    headlineContent = { Text("Losartan") },
                    supportingContent = { Text("50 mg") },
                    trailingContent = { Checkbox(checked = false, onCheckedChange = {}) },
                )

                Spacer(Modifier.height(20.dp))
                MockLabel("↓  Dialog som öppnas vid tryck på raden (ej kryssrutan)")
                Spacer(Modifier.height(8.dp))

                // Intags­dialog — NoteTarget.MEDICATION, targetId = intags-id
                Surface(
                    shape = MaterialTheme.shapes.large,
                    tonalElevation = 6.dp,
                ) {
                    Column(modifier = Modifier.padding(24.dp)) {
                        Text(
                            "Losartan",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            "50 mg · Morgon",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = 16.dp),
                        )

                        NoteFieldMock(text = "", expanded = true)

                        Spacer(Modifier.height(16.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            TextButton(onClick = {}) { Text("Avbryt") }
                            Spacer(Modifier.size(8.dp))
                            FilledTonalButton(onClick = {}) { Text("Spara") }
                        }
                    }
                }
            }
        }
    }
}
