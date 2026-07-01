package se.partee71.dagboken.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DirectionsRun
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.ImportExport
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Medication
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.CardDefaults
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import se.partee71.dagboken.R
import se.partee71.dagboken.data.datastore.ScreeningEventConfig
import se.partee71.dagboken.data.datastore.SCREENING_EVENT_LABELS
import se.partee71.dagboken.data.datastore.SymptomOption
import se.partee71.dagboken.domain.model.Favorit

private data class SectionDef(val icon: ImageVector, val title: String, val description: String)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onImport: () -> Unit,
    vm: SettingsViewModel = hiltViewModel(),
) {
    val state          by vm.state.collectAsState()
    val medicinFavoriter by vm.medicinFavoriter.collectAsState()
    val context         = LocalContext.current
    val isLargeScreen   = LocalConfiguration.current.screenWidthDp >= 360
    var selectedSection by remember { mutableIntStateOf(0) }

    val sections = listOf(
        SectionDef(Icons.Filled.AccountCircle, stringResource(R.string.settings_account_section),       "Google-konto och synk"),
        SectionDef(Icons.Filled.ImportExport,  stringResource(R.string.settings_import_section),        "Importera data från JSON"),
        SectionDef(Icons.Filled.Palette,       stringResource(R.string.settings_theme_section),         "Ljust, mörkt eller tidsbaserat"),
        SectionDef(Icons.Filled.Notifications, stringResource(R.string.settings_notifications_section), "Medicin- och screeningnotiser"),
        SectionDef(Icons.Filled.DirectionsRun, stringResource(R.string.settings_aktivitet_section),     "Hantera aktivitetstyper"),
        SectionDef(Icons.Filled.Favorite,      stringResource(R.string.label_symptom),                  "Hantera symptomtyper"),
        SectionDef(Icons.Filled.Medication,    stringResource(R.string.settings_vidbehov_section),      "Hantera favoritmarkerade vid behov-mediciner"),
        SectionDef(Icons.Filled.Bolt,          stringResource(R.string.settings_handelse_typ_section),  "Hantera händelsetyper"),
        SectionDef(Icons.Filled.Info,          stringResource(R.string.settings_about_section),         "Version och appinfo"),
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back))
                    }
                },
            )
        },
    ) { padding ->
        if (isLargeScreen) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
            ) {
                SettingsNavSidebar(
                    sections        = sections,
                    selectedIndex   = selectedSection,
                    onSectionClick  = { selectedSection = it },
                )
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    when (selectedSection) {
                        0 -> AccountCard(
                            email       = state.googleAccountEmail,
                            photoUrl    = state.googleAccountPhotoUrl,
                            isSigningIn = state.isSigningIn,
                            signInError = state.signInError,
                            onSignIn    = { vm.signIn(context) },
                            onSignOut   = { vm.signOut() },
                        )
                        1 -> ImportCard(onImport = onImport)
                        2 -> ThemeCard(
                            themeMode       = state.themeMode,
                            themeLightStart = state.themeLightStart,
                            themeDarkStart  = state.themeDarkStart,
                            onSetMode       = vm::setThemeMode,
                            onSetLightStart = vm::setThemeLightStart,
                            onSetDarkStart  = vm::setThemeDarkStart,
                        )
                        3 -> NotificationsCard(
                            medsEnabled        = state.medsNotificationsEnabled,
                            screeningConfigs   = state.screeningEventConfigs,
                            onToggleMeds       = { vm.toggleMedsNotifications() },
                            onToggleScreening  = { vm.toggleScreeningEvent(it) },
                            onSetScreeningTime = { i, h, m -> vm.setScreeningEventTime(i, "%02d:%02d".format(h, m)) },
                        )
                        4 -> OptionSettingsCard(
                            title            = stringResource(R.string.settings_aktivitet_section),
                            newOptionLabel   = stringResource(R.string.settings_new_aktivitet_type),
                            options          = state.aktivitetOptions,
                            newOption        = state.newAktivitetOption,
                            onValueChange    = vm::setNewAktivitetOption,
                            onAdd            = vm::addAktivitetOption,
                            onDelete         = vm::deleteAktivitetOption,
                            onToggleFavorite = vm::toggleAktivitetFavorite,
                            onRename         = vm::renameAktivitetOption,
                        )
                        5 -> OptionSettingsCard(
                            title            = stringResource(R.string.label_symptom),
                            newOptionLabel   = stringResource(R.string.settings_new_symptom),
                            options          = state.symptomOptions,
                            newOption        = state.newSymptomOption,
                            onValueChange    = vm::setNewSymptomOption,
                            onAdd            = vm::addSymptomOption,
                            onDelete         = vm::deleteSymptomOption,
                            onToggleFavorite = vm::toggleSymptomFavorite,
                            onRename         = vm::renameSymptomOption,
                        )
                        6 -> VidBehovFavoritSettingsCard(
                            favoriter        = medicinFavoriter,
                            onToggleFavorite = vm::toggleMedicinFavorite,
                        )
                        7 -> OptionSettingsCard(
                            title            = stringResource(R.string.settings_handelse_typ_section),
                            newOptionLabel   = stringResource(R.string.settings_new_handelse_typ),
                            options          = state.handelseTypOptions,
                            newOption        = state.newHandelseTypOption,
                            onValueChange    = vm::setNewHandelseTypOption,
                            onAdd            = vm::addHandelseTypOption,
                            onDelete         = vm::deleteHandelseTypOption,
                            onToggleFavorite = vm::toggleHandelseTypFavorite,
                            onRename         = vm::renameHandelseTypOption,
                        )
                        8 -> AboutCard()
                    }
                }
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                AccountCard(
                    email       = state.googleAccountEmail,
                    photoUrl    = state.googleAccountPhotoUrl,
                    isSigningIn = state.isSigningIn,
                    signInError = state.signInError,
                    onSignIn    = { vm.signIn(context) },
                    onSignOut   = { vm.signOut() },
                )
                ImportCard(onImport = onImport)
                ThemeCard(
                    themeMode       = state.themeMode,
                    themeLightStart = state.themeLightStart,
                    themeDarkStart  = state.themeDarkStart,
                    onSetMode       = vm::setThemeMode,
                    onSetLightStart = vm::setThemeLightStart,
                    onSetDarkStart  = vm::setThemeDarkStart,
                )
                NotificationsCard(
                    medsEnabled        = state.medsNotificationsEnabled,
                    screeningConfigs   = state.screeningEventConfigs,
                    onToggleMeds       = { vm.toggleMedsNotifications() },
                    onToggleScreening  = { vm.toggleScreeningEvent(it) },
                    onSetScreeningTime = { i, h, m -> vm.setScreeningEventTime(i, "%02d:%02d".format(h, m)) },
                )
                OptionSettingsCard(
                    title            = stringResource(R.string.settings_aktivitet_section),
                    newOptionLabel   = stringResource(R.string.settings_new_aktivitet_type),
                    options          = state.aktivitetOptions,
                    newOption        = state.newAktivitetOption,
                    onValueChange    = vm::setNewAktivitetOption,
                    onAdd            = vm::addAktivitetOption,
                    onDelete         = vm::deleteAktivitetOption,
                    onToggleFavorite = vm::toggleAktivitetFavorite,
                    onRename         = vm::renameAktivitetOption,
                )
                OptionSettingsCard(
                    title            = stringResource(R.string.label_symptom),
                    newOptionLabel   = stringResource(R.string.settings_new_symptom),
                    options          = state.symptomOptions,
                    newOption        = state.newSymptomOption,
                    onValueChange    = vm::setNewSymptomOption,
                    onAdd            = vm::addSymptomOption,
                    onDelete         = vm::deleteSymptomOption,
                    onToggleFavorite = vm::toggleSymptomFavorite,
                    onRename         = vm::renameSymptomOption,
                )
                VidBehovFavoritSettingsCard(
                    favoriter        = medicinFavoriter,
                    onToggleFavorite = vm::toggleMedicinFavorite,
                )
                OptionSettingsCard(
                    title            = stringResource(R.string.settings_handelse_typ_section),
                    newOptionLabel   = stringResource(R.string.settings_new_handelse_typ),
                    options          = state.handelseTypOptions,
                    newOption        = state.newHandelseTypOption,
                    onValueChange    = vm::setNewHandelseTypOption,
                    onAdd            = vm::addHandelseTypOption,
                    onDelete         = vm::deleteHandelseTypOption,
                    onToggleFavorite = vm::toggleHandelseTypFavorite,
                    onRename         = vm::renameHandelseTypOption,
                )
                AboutCard()
            }
        }
    }
}

@Composable
private fun SettingsNavSidebar(
    sections: List<SectionDef>,
    selectedIndex: Int,
    onSectionClick: (Int) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxHeight()
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .padding(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        sections.forEachIndexed { index, section ->
            SettingsRailItem(
                section    = section,
                isSelected = index == selectedIndex,
                onClick    = { onSectionClick(index) },
            )
        }
    }
}

@Composable
private fun SettingsRailItem(section: SectionDef, isSelected: Boolean, onClick: () -> Unit) {
    var isHovered by remember { mutableStateOf(false) }
    var isFocused by remember { mutableStateOf(false) }
    val showPopup  = isHovered || isFocused
    val cs         = MaterialTheme.colorScheme

    val rightOfAnchor = remember {
        object : PopupPositionProvider {
            override fun calculatePosition(
                anchorBounds: IntRect,
                windowSize: IntSize,
                layoutDirection: LayoutDirection,
                popupContentSize: IntSize,
            ): IntOffset = IntOffset(
                x = anchorBounds.right,
                y = anchorBounds.top + (anchorBounds.height - popupContentSize.height) / 2,
            )
        }
    }

    Box(
        modifier = Modifier.pointerInput(Unit) {
            awaitPointerEventScope {
                while (true) {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    when (event.type) {
                        PointerEventType.Enter -> isHovered = true
                        PointerEventType.Exit  -> isHovered = false
                        else                   -> {}
                    }
                }
            }
        },
    ) {
        Surface(
            onClick  = onClick,
            shape    = MaterialTheme.shapes.medium,
            color    = if (isSelected) cs.primaryContainer else Color.Transparent,
            modifier = Modifier
                .padding(horizontal = 8.dp, vertical = 2.dp)
                .onFocusChanged { isFocused = it.isFocused },
        ) {
            Box(
                modifier         = Modifier.size(48.dp),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector        = section.icon,
                    contentDescription = section.title,
                    modifier           = Modifier.size(24.dp),
                    tint               = if (isSelected) cs.onPrimaryContainer
                                         else cs.onSurfaceVariant,
                )
            }
        }

        if (showPopup) {
            Popup(
                popupPositionProvider = rightOfAnchor,
                properties            = PopupProperties(focusable = false),
            ) {
                ElevatedCard(
                    modifier  = Modifier.padding(start = 4.dp),
                    elevation = CardDefaults.elevatedCardElevation(defaultElevation = 6.dp),
                ) {
                    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                        Text(
                            text       = section.title,
                            style      = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            text  = section.description,
                            style = MaterialTheme.typography.bodySmall,
                            color = cs.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AccountCard(
    email: String?,
    photoUrl: String?,
    isSigningIn: Boolean,
    signInError: String?,
    onSignIn: () -> Unit,
    onSignOut: () -> Unit,
) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(stringResource(R.string.settings_account_section), style = MaterialTheme.typography.titleSmall)
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            if (email != null) {
                Row(
                    modifier              = Modifier.fillMaxWidth(),
                    verticalAlignment     = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    if (photoUrl != null) {
                        AsyncImage(
                            model              = photoUrl,
                            contentDescription = stringResource(R.string.profile_photo),
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
                        Text(text = email, style = MaterialTheme.typography.bodyMedium)
                        Text(
                            text  = stringResource(R.string.settings_signed_in_subtitle),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                OutlinedButton(onClick = onSignOut, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.sign_out))
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
                        text     = stringResource(R.string.settings_not_signed_in),
                        style    = MaterialTheme.typography.bodyMedium,
                        color    = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                }
                signInError?.let { err ->
                    Text(
                        text     = err,
                        color    = MaterialTheme.colorScheme.error,
                        style    = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 4.dp, bottom = 4.dp),
                    )
                }
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                Button(
                    onClick  = onSignIn,
                    modifier = Modifier.fillMaxWidth(),
                    enabled  = !isSigningIn,
                ) {
                    if (isSigningIn) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                        Spacer(Modifier.width(8.dp))
                    }
                    Text(stringResource(R.string.sign_in_with_google))
                }
            }
        }
    }
}

@Composable
private fun ImportCard(onImport: () -> Unit) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(stringResource(R.string.settings_import_section), style = MaterialTheme.typography.titleSmall)
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            Text(
                stringResource(R.string.settings_import_body),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            Button(onClick = onImport, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.settings_import_button))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ThemeCard(
    themeMode: String,
    themeLightStart: Int,
    themeDarkStart: Int,
    onSetMode: (String) -> Unit,
    onSetLightStart: (Int) -> Unit,
    onSetDarkStart: (Int) -> Unit,
) {
    var showLightPicker by remember { mutableStateOf(false) }
    var showDarkPicker  by remember { mutableStateOf(false) }

    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(stringResource(R.string.settings_theme_section), style = MaterialTheme.typography.titleSmall)
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            val themeModes = listOf(
                "light" to stringResource(R.string.theme_light),
                "dark"  to stringResource(R.string.theme_dark),
                "auto"  to stringResource(R.string.theme_auto),
            )
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                themeModes.forEachIndexed { index, (mode, label) ->
                    SegmentedButton(
                        selected = themeMode == mode,
                        onClick  = { onSetMode(mode) },
                        shape    = SegmentedButtonDefaults.itemShape(index = index, count = themeModes.size),
                        label    = { Text(label) },
                    )
                }
            }
            if (themeMode == "auto") {
                Spacer(Modifier.height(12.dp))
                ThemeTimeRow(
                    label    = stringResource(R.string.settings_theme_light_from),
                    hour     = themeLightStart,
                    onClick  = { showLightPicker = true },
                )
                Spacer(Modifier.height(8.dp))
                ThemeTimeRow(
                    label    = stringResource(R.string.settings_theme_dark_from),
                    hour     = themeDarkStart,
                    onClick  = { showDarkPicker = true },
                )
            }
        }
    }

    if (showLightPicker) {
        val state = rememberTimePickerState(initialHour = themeLightStart, initialMinute = 0, is24Hour = true)
        AlertDialog(
            onDismissRequest = { showLightPicker = false },
            title            = { Text(stringResource(R.string.settings_theme_light_from)) },
            text             = { TimePicker(state = state) },
            confirmButton    = {
                TextButton(onClick = { onSetLightStart(state.hour); showLightPicker = false }) {
                    Text(stringResource(R.string.ok))
                }
            },
            dismissButton = {
                TextButton(onClick = { showLightPicker = false }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }

    if (showDarkPicker) {
        val state = rememberTimePickerState(initialHour = themeDarkStart, initialMinute = 0, is24Hour = true)
        AlertDialog(
            onDismissRequest = { showDarkPicker = false },
            title            = { Text(stringResource(R.string.settings_theme_dark_from)) },
            text             = { TimePicker(state = state) },
            confirmButton    = {
                TextButton(onClick = { onSetDarkStart(state.hour); showDarkPicker = false }) {
                    Text(stringResource(R.string.ok))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDarkPicker = false }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }
}

@Composable
private fun ThemeTimeRow(label: String, hour: Int, onClick: () -> Unit) {
    Row(
        modifier          = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        TextButton(onClick = onClick) {
            Text(
                text       = String.format(java.util.Locale.ROOT, "%02d:00", hour),
                style      = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.width(4.dp))
            Icon(Icons.Default.Alarm, contentDescription = stringResource(R.string.settings_pick_time), modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
private fun NotificationsCard(
    medsEnabled: Boolean,
    screeningConfigs: List<ScreeningEventConfig>,
    onToggleMeds: () -> Unit,
    onToggleScreening: (Int) -> Unit,
    onSetScreeningTime: (Int, Int, Int) -> Unit,
) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(stringResource(R.string.settings_notifications_section), style = MaterialTheme.typography.titleSmall)
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.settings_meds_notifications))
                    Text(
                        stringResource(R.string.settings_meds_notifications_subtitle),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked         = medsEnabled,
                    onCheckedChange = { onToggleMeds() },
                )
            }
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            Text(stringResource(R.string.settings_screening_notifications), style = MaterialTheme.typography.bodyMedium)
            Text(
                stringResource(R.string.settings_screening_notifications_subtitle),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            screeningConfigs.forEachIndexed { index, config ->
                if (index > 0) HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                ScreeningEventRow(
                    label          = SCREENING_EVENT_LABELS[index],
                    config         = config,
                    onToggle       = { onToggleScreening(index) },
                    onTimeSelected = { h, m -> onSetScreeningTime(index, h, m) },
                )
            }
        }
    }
}

@Composable
private fun OptionSettingsCard(
    title: String,
    newOptionLabel: String,
    options: List<SymptomOption>,
    newOption: String,
    onValueChange: (String) -> Unit,
    onAdd: () -> Unit,
    onDelete: (String) -> Unit,
    onToggleFavorite: (String) -> Unit,
    onRename: (String, String) -> Unit,
) {
    var editingName  by remember { mutableStateOf<String?>(null) }
    var editValue    by remember { mutableStateOf("") }
    var deleteTarget by remember { mutableStateOf<String?>(null) }

    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            HorizontalDivider()

            options.forEachIndexed { index, opt ->
                if (index > 0) HorizontalDivider(modifier = Modifier.padding(vertical = 2.dp))
                Row(
                    modifier          = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(
                        onClick  = { onToggleFavorite(opt.name) },
                        modifier = Modifier.size(36.dp),
                    ) {
                        Icon(
                            Icons.Default.Star,
                            contentDescription = stringResource(R.string.symptom_favorite),
                            tint = if (opt.isFavorite) MaterialTheme.colorScheme.primary
                                   else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
                            modifier = Modifier.size(20.dp),
                        )
                    }

                    if (editingName == opt.name) {
                        OutlinedTextField(
                            value         = editValue,
                            onValueChange = { editValue = it },
                            modifier      = Modifier.weight(1f),
                            singleLine    = true,
                        )
                        IconButton(
                            onClick  = { onRename(opt.name, editValue); editingName = null },
                            enabled  = editValue.isNotBlank(),
                            modifier = Modifier.size(36.dp),
                        ) {
                            Icon(Icons.Default.Check, contentDescription = stringResource(R.string.ok), modifier = Modifier.size(20.dp))
                        }
                        IconButton(
                            onClick  = { editingName = null },
                            modifier = Modifier.size(36.dp),
                        ) {
                            Icon(Icons.Default.Close, contentDescription = stringResource(R.string.cancel), modifier = Modifier.size(20.dp))
                        }
                    } else {
                        Text(
                            text     = opt.name,
                            style    = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f),
                        )
                        IconButton(
                            onClick  = { editingName = opt.name; editValue = opt.name },
                            modifier = Modifier.size(36.dp),
                        ) {
                            Icon(Icons.Default.Edit, contentDescription = stringResource(R.string.edit), modifier = Modifier.size(20.dp))
                        }
                        IconButton(
                            onClick  = { deleteTarget = opt.name },
                            modifier = Modifier.size(36.dp),
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.delete), modifier = Modifier.size(20.dp))
                        }
                    }
                }
            }

            HorizontalDivider()
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value         = newOption,
                    onValueChange = onValueChange,
                    label         = { Text(newOptionLabel) },
                    modifier      = Modifier.weight(1f),
                    singleLine    = true,
                )
                IconButton(onClick = onAdd, enabled = newOption.isNotBlank()) {
                    Icon(Icons.Default.Add, stringResource(R.string.add))
                }
            }
        }
    }

    deleteTarget?.let { name ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title            = { Text(stringResource(R.string.symptom_delete_confirm_title)) },
            text             = { Text(stringResource(R.string.format_symptom_delete_confirm, name)) },
            confirmButton = {
                TextButton(onClick = { onDelete(name); deleteTarget = null }) {
                    Text(stringResource(R.string.delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }
}

@Composable
private fun VidBehovFavoritSettingsCard(
    favoriter: List<Favorit>,
    onToggleFavorite: (Favorit) -> Unit,
) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.settings_vidbehov_section), style = MaterialTheme.typography.titleSmall)
            HorizontalDivider()

            if (favoriter.isEmpty()) {
                Text(
                    stringResource(R.string.settings_vidbehov_empty),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                favoriter.forEachIndexed { index, fav ->
                    if (index > 0) HorizontalDivider(modifier = Modifier.padding(vertical = 2.dp))
                    Row(
                        modifier          = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        IconButton(
                            onClick  = { onToggleFavorite(fav) },
                            modifier = Modifier.size(36.dp),
                        ) {
                            Icon(
                                Icons.Filled.Star,
                                contentDescription = stringResource(R.string.symptom_favorite),
                                tint = if (fav.isFavorite) MaterialTheme.colorScheme.primary
                                       else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
                                modifier = Modifier.size(20.dp),
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(fav.namn, style = MaterialTheme.typography.bodyMedium)
                            Text(
                                "${fav.dos} ${fav.enhet}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AboutCard() {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(stringResource(R.string.settings_about_section), style = MaterialTheme.typography.titleSmall)
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            Text(stringResource(R.string.app_name), style = MaterialTheme.typography.bodyMedium)
            Text(
                "se.partee71.dagboken",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ScreeningEventRow(
    label: String,
    config: ScreeningEventConfig,
    onToggle: () -> Unit,
    onTimeSelected: (hour: Int, minute: Int) -> Unit,
) {
    val parts  = config.time.split(":")
    val hour   = parts.getOrNull(0)?.toIntOrNull() ?: 8
    val minute = parts.getOrNull(1)?.toIntOrNull() ?: 0
    var showPicker by remember { mutableStateOf(false) }

    Row(
        modifier          = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        TextButton(
            onClick = { showPicker = true },
            enabled = config.enabled,
        ) {
            Text(
                text       = config.time,
                style      = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.width(4.dp))
            Icon(Icons.Default.Alarm, contentDescription = stringResource(R.string.settings_pick_time), modifier = Modifier.size(18.dp))
        }
        Switch(checked = config.enabled, onCheckedChange = { onToggle() })
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
                }) { Text(stringResource(R.string.ok)) }
            },
            dismissButton = {
                TextButton(onClick = { showPicker = false }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }
}

