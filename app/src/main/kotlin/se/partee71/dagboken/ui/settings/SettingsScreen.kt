package se.partee71.dagboken.ui.settings

import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material.icons.filled.Remove
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onImport: () -> Unit,
    vm: SettingsViewModel = hiltViewModel(),
) {
    val state by vm.state.collectAsState()
    val context = LocalContext.current
    val cs = MaterialTheme.colorScheme

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Inställningar") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Tillbaka")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // Google Account / Backup
            ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Google-konto & säkerhetskopiering", style = MaterialTheme.typography.titleSmall)
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                    if (state.googleAccountEmail != null) {
                        Row(
                            modifier              = Modifier.fillMaxWidth(),
                            verticalAlignment     = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            if (state.googleAccountPhotoUrl != null) {
                                AsyncImage(
                                    model              = state.googleAccountPhotoUrl,
                                    contentDescription = "Profilbild",
                                    modifier           = Modifier.size(40.dp).clip(CircleShape),
                                    contentScale       = ContentScale.Crop,
                                )
                            } else {
                                Icon(
                                    imageVector        = Icons.Default.AccountCircle,
                                    contentDescription = null,
                                    modifier           = Modifier.size(40.dp),
                                    tint               = MaterialTheme.colorScheme.primary,
                                )
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(text = state.googleAccountEmail ?: "", style = MaterialTheme.typography.bodyMedium)
                                Text(
                                    text  = "Inloggad — data säkerhetskopieras dagligen",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                        OutlinedButton(onClick = { vm.signOut() }, modifier = Modifier.fillMaxWidth()) {
                            Text("Logga ut")
                        }
                    } else {
                        Row(
                            modifier              = Modifier.fillMaxWidth(),
                            verticalAlignment     = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Icon(
                                imageVector        = Icons.Default.AccountCircle,
                                contentDescription = null,
                                modifier           = Modifier.size(40.dp),
                                tint               = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                text     = "Inte inloggad",
                                style    = MaterialTheme.typography.bodyMedium,
                                color    = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.weight(1f),
                            )
                        }
                        state.signInError?.let { err ->
                            Text(
                                text     = err,
                                color    = MaterialTheme.colorScheme.error,
                                style    = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(top = 4.dp, bottom = 4.dp),
                            )
                        }
                        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                        Button(
                            onClick  = { vm.signIn(context) },
                            modifier = Modifier.fillMaxWidth(),
                            enabled  = !state.isSigningIn,
                        ) {
                            if (state.isSigningIn) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                                Spacer(Modifier.width(8.dp))
                            }
                            Text("Logga in med Google")
                        }
                    }
                }
            }

            // Import
            ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Importera data", style = MaterialTheme.typography.titleSmall)
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                    Text(
                        "Hämta säkerhetskopia från Google Drive eller välj en JSON-fil.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = onImport, modifier = Modifier.fillMaxWidth()) {
                        Text("Importera från säkerhetskopia")
                    }
                }
            }

            // Utseende
            ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Utseende", style = MaterialTheme.typography.titleSmall)
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("Mörkt tema", modifier = Modifier.weight(1f))
                        Switch(
                            checked         = state.themeMode == "dark",
                            onCheckedChange = { vm.setThemeMode(if (it) "dark" else "auto") },
                        )
                    }
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Material You")
                                Text(
                                    "Färger anpassas efter din tapet",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Switch(checked = state.isDynamicColor, onCheckedChange = { vm.toggleDynamicColor() })
                        }
                    }
                }
            }

            // Tema-schema
            ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Tema-schema", style = MaterialTheme.typography.titleSmall)
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                    val themeModes = listOf("light" to "Ljust", "dark" to "Mörkt", "auto" to "Auto")
                    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                        themeModes.forEachIndexed { index, (mode, label) ->
                            SegmentedButton(
                                selected = state.themeMode == mode,
                                onClick  = { vm.setThemeMode(mode) },
                                shape    = SegmentedButtonDefaults.itemShape(index = index, count = themeModes.size),
                                label    = { Text(label) },
                            )
                        }
                    }
                    if (state.themeMode == "auto") {
                        Spacer(Modifier.height(12.dp))
                        TimeStepperRow(
                            emoji          = "🌅",
                            label          = "Ljust tema från",
                            hour           = state.themeLightStart,
                            containerColor = cs.primaryContainer,
                            contentColor   = cs.onPrimaryContainer,
                            onDecrement    = { vm.setThemeLightStart(state.themeLightStart - 1) },
                            onIncrement    = { vm.setThemeLightStart(state.themeLightStart + 1) },
                        )
                        Spacer(Modifier.height(8.dp))
                        TimeStepperRow(
                            emoji          = "🌙",
                            label          = "Mörkt tema från",
                            hour           = state.themeDarkStart,
                            containerColor = cs.surfaceVariant,
                            contentColor   = cs.onSurfaceVariant,
                            onDecrement    = { vm.setThemeDarkStart(state.themeDarkStart - 1) },
                            onIncrement    = { vm.setThemeDarkStart(state.themeDarkStart + 1) },
                        )
                    }
                }
            }

            // Påminnelser
            ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Påminnelser", style = MaterialTheme.typography.titleSmall)
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Medicinpåminnelser")
                            Text(
                                "Notis 15 min innan varje tidpunkt",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Switch(
                            checked        = state.medsNotificationsEnabled,
                            onCheckedChange = { vm.toggleMedsNotifications() },
                        )
                    }
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Screeningpåminnelse")
                            Text(
                                "Daglig notis om du inte loggat screening",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Switch(
                            checked         = state.screeningNotificationsEnabled,
                            onCheckedChange = { vm.toggleScreeningNotifications() },
                        )
                    }
                    if (state.screeningNotificationsEnabled) {
                        Spacer(Modifier.height(12.dp))
                        state.screeningReminderTimes.forEachIndexed { index, time ->
                            if (index > 0) HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                            ScreeningTimeRow(
                                label    = "Påminnelse ${index + 1}",
                                time     = time,
                                onTimeSelected = { h, m ->
                                    vm.setScreeningReminderTime(index, "%02d:%02d".format(h, m))
                                },
                            )
                        }
                    }
                }
            }

            // Aktivitet options
            ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Aktivitetstyper", style = MaterialTheme.typography.titleSmall)
                    HorizontalDivider()
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        state.aktivitetOptions.forEach { opt ->
                            FilterChip(
                                selected = true,
                                onClick  = {},
                                label    = { Text(opt) },
                                trailingIcon = {
                                    IconButton(onClick = { vm.removeAktivitetOption(opt) }) {
                                        Icon(Icons.Default.Close, "Ta bort", modifier = Modifier.padding(2.dp))
                                    }
                                },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                                ),
                            )
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value         = state.newAktivitetOption,
                            onValueChange = vm::setNewAktivitetOption,
                            label         = { Text("Ny typ") },
                            modifier      = Modifier.weight(1f),
                            singleLine    = true,
                        )
                        IconButton(onClick = vm::addAktivitetOption, enabled = state.newAktivitetOption.isNotBlank()) {
                            Icon(Icons.Default.Add, "Lägg till")
                        }
                    }
                }
            }

            // Symptom options
            ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Symptom", style = MaterialTheme.typography.titleSmall)
                    HorizontalDivider()
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        state.symptomOptions.forEach { opt ->
                            FilterChip(
                                selected = true,
                                onClick  = {},
                                label    = { Text(opt) },
                                trailingIcon = {
                                    IconButton(onClick = { vm.removeSymptomOption(opt) }) {
                                        Icon(Icons.Default.Close, "Ta bort", modifier = Modifier.padding(2.dp))
                                    }
                                },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                                ),
                            )
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value         = state.newSymptomOption,
                            onValueChange = vm::setNewSymptomOption,
                            label         = { Text("Nytt symptom") },
                            modifier      = Modifier.weight(1f),
                            singleLine    = true,
                        )
                        IconButton(onClick = vm::addSymptomOption, enabled = state.newSymptomOption.isNotBlank()) {
                            Icon(Icons.Default.Add, "Lägg till")
                        }
                    }
                }
            }

            // About
            ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Om appen", style = MaterialTheme.typography.titleSmall)
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                    Text("Dagboken", style = MaterialTheme.typography.bodyMedium)
                    Text(
                        "se.partee71.dagboken",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ScreeningTimeRow(
    label: String,
    time: String,
    onTimeSelected: (hour: Int, minute: Int) -> Unit,
) {
    val parts  = time.split(":")
    val hour   = parts.getOrNull(0)?.toIntOrNull() ?: 8
    val minute = parts.getOrNull(1)?.toIntOrNull() ?: 0
    var showPicker by remember { mutableStateOf(false) }

    Row(
        modifier          = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        TextButton(onClick = { showPicker = true }) {
            Text(
                text       = time,
                style      = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.width(4.dp))
            Icon(Icons.Default.Alarm, contentDescription = "Välj tid", modifier = Modifier.size(18.dp))
        }
    }

    if (showPicker) {
        val state = rememberTimePickerState(initialHour = hour, initialMinute = minute, is24Hour = true)
        AlertDialog(
            onDismissRequest = { showPicker = false },
            title            = { Text(label) },
            text             = { TimePicker(state = state) },
            confirmButton    = {
                TextButton(onClick = {
                    onTimeSelected(state.hour, state.minute)
                    showPicker = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showPicker = false }) { Text("Avbryt") }
            },
        )
    }
}

@Composable
private fun TimeStepperRow(
    emoji: String,
    label: String,
    hour: Int,
    containerColor: Color,
    contentColor: Color,
    onDecrement: () -> Unit,
    onIncrement: () -> Unit,
) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = containerColor,
    ) {
        Row(
            modifier          = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(emoji, style = MaterialTheme.typography.bodyLarge)
            Spacer(Modifier.width(8.dp))
            Text(
                text     = label,
                modifier = Modifier.weight(1f),
                color    = contentColor,
                style    = MaterialTheme.typography.bodyMedium,
            )
            IconButton(onClick = onDecrement) {
                Icon(Icons.Default.Remove, contentDescription = "Minska", tint = contentColor)
            }
            Text(
                text      = String.format(java.util.Locale.ROOT, "%02d:00", hour),
                style     = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                color     = contentColor,
                textAlign = TextAlign.Center,
                modifier  = Modifier.width(52.dp),
            )
            IconButton(onClick = onIncrement) {
                Icon(Icons.Default.Add, contentDescription = "Öka", tint = contentColor)
            }
        }
    }
}
