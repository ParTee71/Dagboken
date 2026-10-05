package se.partee71.dagboken.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import se.partee71.dagboken.R
import se.partee71.dagboken.core.model.Option
import se.partee71.dagboken.core.model.OptionKind
import se.partee71.dagboken.core.model.SymptomScore
import se.partee71.dagboken.data.common.DataError
import se.partee71.dagboken.ui.common.DetailUiState
import se.partee71.dagboken.ui.common.EditorUiState
import se.partee71.dagboken.ui.common.ArchiveEvent
import se.partee71.dagboken.ui.common.DateFormat
import se.partee71.dagboken.ui.common.durationText
import se.partee71.dagboken.ui.common.ListUiState
import se.partee71.dagboken.ui.common.color
import se.partee71.dagboken.ui.common.scaleLevel
import se.partee71.dagboken.ui.theme.AppColors
import se.partee71.dagboken.ui.theme.AppTypography
import se.partee71.dagboken.ui.theme.Spacing
import se.partee71.dagboken.ui.theme.Tone

/**
 * Alla komponenter och ramar på en skärm, med riktiga svenska texter (skill
 * shared-ui-components). Nås från inställningsarket → "Komponentgalleri" i debug-bygget.
 * `UiConsistencyTest` kontrollerar att varje publik komponent finns här.
 */
@Composable
fun ComponentGallery(onBack: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxSize()) {
        AppTopBar("Komponenter", size = TopBarSize.Small, onBack = onBack)
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = Spacing.l, vertical = Spacing.s),
            verticalArrangement = Arrangement.spacedBy(Spacing.l),
        ) {
            GallerySection("Knappar och meny") { Buttons() }
            GallerySection("Kort, rader och rubriker") { Rows() }
            GallerySection("Fält, val och mängder") { Fields() }
            GallerySection("Poster, reglage och kalender") { Diary() }
            GallerySection("Laddning och navigering") { Progress() }
            GallerySection("Dialog, sheet, meddelanden") { Overlays() }
            GallerySection("Ramar") { Frames(onBack) }
        }
    }
}

@Composable
private fun GallerySection(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
        Text(title, style = AppTypography.headline, modifier = Modifier.semantics { heading() })
        content()
    }
}

@Composable
private fun Label(text: String) = GroupLabel(text)

@Composable
private fun Buttons() {
    Label("AppButton")
    AppButton("Nytt recept", {}, Modifier.fillMaxWidth(), icon = R.drawable.ic_add)
    AppButton("Sparar …", {}, Modifier.fillMaxWidth(), loading = true)
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
        AppButton("Logga nu", {}, variant = ButtonVariant.Secondary)
        AppButton("Avbryt", {}, variant = ButtonVariant.Text)
        AppButton("Spara", {}, enabled = false)
    }
    AppButton("Radera", {}, variant = ButtonVariant.Destructive)
    Label("AppIconButton · AppMenu · AddSplitButton")
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s), verticalAlignment = Alignment.CenterVertically) {
        AppIconButton(R.drawable.ic_arrow_back, "Tillbaka", {}, variant = IconButtonVariant.Tonal)
        AppIconButton(R.drawable.ic_add, "Lägg till", {}, variant = IconButtonVariant.Tonal)
        AppMenu(sampleMenu())
    }
    AddSplitButton("Lägg till", {}, menuItems = listOf(AppMenuItem("Logga dos i efterhand", {}), AppMenuItem("Visa avslutade", {})))
    AddSplitButton("Ny vid behov-medicin", {})
}

@Composable
private fun sampleMenu() = listOf(
    AppMenuItem("Arkivera", {}, R.drawable.ic_archive),
    AppMenuItem("Radera", {}, R.drawable.ic_delete, destructive = true),
)

@Composable
private fun Rows() {
    var taken by remember { mutableStateOf(false) }
    var reminders by remember { mutableStateOf(true) }
    AppCard {
        SectionHeader("Mediciner", icon = R.drawable.ic_pill, count = "2 / 3")
        ItemRow(
            "Promenad",
            subtitle = "08:30 · 45 min · energi +3",
            leading = { Text("🚶", style = AppTypography.sectionTitle) },
            navigates = true,
            onClick = {},
        )
        AppDivider()
        ItemRow(
            "Förkylning",
            subtitle = "sedan fre 2 okt · 3 incheckningar",
            trailing = { InfoPill("Pågående", tone = Tone.Warning) },
            onClick = {},
            navigates = true,
        )
        AppDivider()
        PausableRow("Levaxin 50 µg", "1 tablett · morgon", "Dagligen", active = true, onClick = {})
        AppDivider()
        PausableRow("Prednisolon 5 mg", "2 tabletter · morgon", "Kur", active = false, onClick = {})
        AppDivider()
        CheckRow("Metformin 500 mg", taken, { taken = it }, subtitle = "12:00 · 2 tabletter", trailing = { InfoPill("Lunch", tone = Tone.Neutral) })
        CheckRow("Omega-3", true, {}, subtitle = "08:00 · 2 kapslar")
        CheckRow("Ipren 400 mg", false, {}, subtitle = "vid behov · tidigast 14:30", onClick = {}, accent = AppColors.swatch(2), trailing = { InfoPill("Snart", tone = Tone.Sun) })
        AppDivider()
        SwitchRow("Medicinpåminnelser", reminders, { reminders = it }, subtitle = "6 tider om dagen")
        SwitchRow("Måendepåminnelse", reminders, { reminders = it }, subtitle = "Frukost · 08:00", onClick = {})
    }
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
        InfoPill("Mediciner")
        InfoPill("Klar", tone = Tone.Positive)
        InfoPill("Snart", tone = Tone.Sun)
        InfoPill("Försenad", tone = Tone.Warning)
    }
    InfoPill(stringResource(R.string.sync_pending), tone = Tone.Neutral, icon = R.drawable.ic_cloud_upload, onClick = {})
    var filter by remember { mutableIntStateOf(0) }
    ChipRow {
        listOf("Alla", "Mående", "Aktiviteter", "Doser", "Händelser", "Sjukdom").forEachIndexed { i, label ->
            AppFilterChip(label, selected = i == filter, onClick = { filter = i })
        }
    }
}

@Composable
private fun Fields() {
    var name by remember { mutableStateOf("Promenad") }
    var date by remember { mutableStateOf<LocalDate?>(LocalDate(2026, 10, 4)) }
    var occasion by remember { mutableIntStateOf(0) }
    var theme by remember { mutableIntStateOf(2) }
    var minutes by remember { mutableIntStateOf(45) }
    AppTextField(name, { name = it }, "Namn", helper = "Visas i dagboken")
    AppTextField("", {}, "Namn", error = "Ange ett namn")
    var medicine by remember { mutableStateOf("Alved") }
    SuggestionField(
        medicine,
        { medicine = it },
        "Namn",
        suggestions = listOf(Suggestion("Alvedon 500 mg", "Filmdragerad tablett", 0..4), Suggestion("Alvedon forte 1 g", "Filmdragerad tablett", 0..4))
            .takeIf { medicine == "Alved" }.orEmpty(),
        onPick = { medicine = "Alvedon" },
        footer = "Från dina vid behov-mediciner",
    )
    DateField("Datum", date, { date = it })
    var time by remember { mutableStateOf(LocalTime(8, 30)) }
    TimeField("Tid", time, { time = it })
    LabeledGroup("Tillfälle") {
        ChoiceChips(listOf(0, 1, 2, 3), occasion, { occasion = it }, { listOf("Frukost", "Lunch", "Middag", "Kväll")[it] })
    }
    FieldError("Ingen anslutning just nu. Det du sparar skickas när nätet är tillbaka.")
    AppFilterChip("Sjukdom", selected = false, onClick = {}, enabled = false)
    ExampleChips(listOf("Promenad", "Yoga", "Städning"), {}, chosen = "Promenad")
    AppSegmentedChoice(listOf("Ljust", "Mörkt", "Auto"), theme, { theme = it })
    QuantityStepper(minutes, { minutes = it }, "minuter")
    var emoji by remember { mutableStateOf("🚶") }
    var color by remember { mutableStateOf(AppColors.SWATCH_HEX[0]) }
    Label("EmojiPicker")
    EmojiPicker(emoji, { emoji = it })
    Label("ColorSwatchPicker")
    ColorSwatchPicker(color, { color = it })
}

@Composable
private fun Diary() {
    var deleted by remember { mutableStateOf(false) }
    Label("DagbokenEntryCard")
    DagbokenEntryCard(
        "Promenad",
        onClick = {},
        subtitle = "${DateFormat.time(LocalTime(8, 30))} · ${durationText(45)}",
        leading = { Text("🚶", style = AppTypography.sectionTitle) },
        accent = scaleLevel(3, -10..10).color,
        status = { InfoPill("+3", tone = Tone.Positive) },
        note = "Gick i skogen med hunden, lätt regn.",
        expandedContent = { Text("Symptom: huvudvärk 2", style = AppTypography.body) },
        onEdit = {},
        delete = DeleteAction("Radera Promenad?", "Posten tas bort för gott. Det går inte att ångra.") { deleted = true },
    )
    DagbokenEntryCard("Prednisolon 5 mg", onClick = {}, subtitle = "Avslutat 30 sep", inactive = true, onEdit = {})
    if (deleted) Text("Raderad", style = AppTypography.itemSubtitle)
    var expanded by remember { mutableStateOf(true) }
    AppCard {
        Foldout("Mätvärden", expanded, { expanded = !expanded }, summary = "Energi 7 · stress 3") {
            var energy by remember { mutableIntStateOf(7) }
            var stress by remember { mutableIntStateOf(3) }
            var activity by remember { mutableIntStateOf(3) }
            ValueSlider("Energi", energy, { energy = it })
            ValueSlider("Stress", stress, { stress = it }, higherIsBetter = false)
            ValueSlider("Aktivitetens energi", activity, { activity = it }, valueRange = -10..10)
        }
    }
    var symptoms by remember { mutableStateOf(listOf(SymptomScore("huvudvark", 4), SymptomScore("ovrigt", 2, "Ont i knät"))) }
    SymptomLogCard(GALLERY_SYMPTOMS, symptoms, { symptoms = it }, otherOptionId = "ovrigt")
    var note by remember { mutableStateOf("Sov dåligt, vaknade vid fyra.") }
    AppCard { NoteField(note, { note = it }) }
    var date by remember { mutableStateOf(LocalDate(2026, 10, 4)) }
    var time by remember { mutableStateOf(LocalTime(8, 30)) }
    DateTimeRow(date, time, { date = it }, { time = it })
    var minutes by remember { mutableIntStateOf(45) }
    DurationRow(minutes, { minutes = it })
    var reminder by remember { mutableStateOf(true) }
    var reminderTime by remember { mutableStateOf(LocalTime(8, 0)) }
    AppCard {
        ReminderTimeRow("Efter frukost", reminderTime, { reminderTime = it }, enabled = reminder, onEnabledChange = { reminder = it })
        ReminderTimeRow("Periodslut", LocalTime(9, 0), {})
    }
    Label("WheelPicker")
    var hour by remember { mutableIntStateOf(1) }
    WheelPicker((0..23).map { it.toString() }, hour, { hour = it }, "timmar")
    Label("StatPill")
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
        StatPill(R.drawable.ic_activity, "7 842", "Steg")
        StatPill(R.drawable.ic_sun, "—", "Sömn", tone = Tone.Primary, onClick = {}, onClickLabel = "Begär åtkomst")
    }
    var month by remember { mutableStateOf(LocalDate(2026, 10, 1)) }
    var picked by remember { mutableStateOf<LocalDate?>(LocalDate(2026, 10, 4)) }
    AppCard {
        DagbokenCalendar(
            month,
            { month = it },
            setOf(LocalDate(2026, 10, 1), LocalDate(2026, 10, 2), LocalDate(2026, 10, 4)),
            picked,
            { picked = it },
            today = LocalDate(2026, 10, 5),
        )
    }
    Label("StepwiseScreeningForm")
    var energy by remember { mutableIntStateOf(6) }
    var stress by remember { mutableIntStateOf(4) }
    var screeningSymptoms by remember { mutableStateOf(emptyList<SymptomScore>()) }
    AppCard {
        StepwiseScreeningForm(energy, { energy = it }, stress, { stress = it }, GALLERY_SYMPTOMS, screeningSymptoms, { screeningSymptoms = it }, onSave = {})
    }
}

/** Påhittade symptomalternativ för galleriet och dess skärmdumpar. */
internal val GALLERY_SYMPTOMS = listOf(
    Option("huvudvark", OptionKind.SYMPTOM, "Huvudvärk", favorite = true),
    Option("trotthet", OptionKind.SYMPTOM, "Trötthet", sortOrder = 1),
    Option("yrsel", OptionKind.SYMPTOM, "Yrsel", sortOrder = 2),
    Option("ovrigt", OptionKind.SYMPTOM, "Övrigt", sortOrder = 3),
)

@Composable
private fun Progress() {
    Box(Modifier.fillMaxWidth().height(96.dp)) { AppLoading() }
    var tab by remember { mutableIntStateOf(0) }
    AppFloatingToolbar(
        items = listOf(
            ToolbarItem("Idag", R.drawable.ic_sun),
            ToolbarItem("Dagbok", R.drawable.ic_book),
            ToolbarItem("Trender", R.drawable.ic_trend),
            ToolbarItem("Mediciner", R.drawable.ic_pill),
        ),
        selectedIndex = tab,
        onSelect = { tab = it },
        action = ToolbarAction("Logga", R.drawable.ic_add) {},
    )
    CompositionLocalProvider(LocalSyncIndicator provides SyncIndicator(pending = true)) {
        AppTopBar("Förkylning", size = TopBarSize.Small, onBack = {})
    }
}

/** Appens fasta ark som galleriet kan öppna. */
private enum class AppSheet { Account, LogMenu }

@Composable
private fun Overlays() {
    var dialog by remember { mutableStateOf(false) }
    var sheet by remember { mutableStateOf(false) }
    var appSheet by remember { mutableStateOf<AppSheet?>(null) }
    var celebrate by remember { mutableStateOf(false) }
    var archived by remember { mutableStateOf<UndoRequest?>(null) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
        AppButton("Dialog", { dialog = true }, variant = ButtonVariant.Secondary)
        AppButton("Sheet", { sheet = true }, variant = ButtonVariant.Secondary)
        AppButton("Snackbar", { scope.launch { snackbar.showSnackbar("Ingen anslutning just nu.") } }, variant = ButtonVariant.Secondary)
    }
    Label("AccountSheet · LogMenuSheet")
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
        AppButton("Inställningsark", { appSheet = AppSheet.Account }, variant = ButtonVariant.Secondary)
        AppButton("Loggmeny", { appSheet = AppSheet.LogMenu }, variant = ButtonVariant.Secondary)
    }
    AppButton("Arkivera Yoga", { archived = UndoRequest("yoga", "Yoga") }, variant = ButtonVariant.Text)
    UndoSnackbar(archived, snackbar, onUndo = { archived = null }, onDismissed = { archived = null })
    if (dialog) {
        ConfirmDialog("Radera Promenad?", "Aktiviteten tas bort för gott. Det går inte att ångra.", "Radera", { dialog = false }, { dialog = false }, destructive = true)
    }
    if (sheet) {
        AppBottomSheet("Promenad", onDismiss = { sheet = false }) {
            QuantityStepper(45, {}, "minuter")
            AppButton("Spara", {}, variant = ButtonVariant.Text)
        }
    }
    when (appSheet) {
        AppSheet.Account -> AccountSheet(onDismiss = { appSheet = null }, onSignOut = { appSheet = null }, onOpenGallery = { appSheet = null })
        AppSheet.LogMenu -> LogMenuSheet(onDismiss = { appSheet = null }, onPick = { appSheet = null })
        null -> Unit
    }
    EmptyState(R.drawable.ic_book, "Inget loggat än", "Det du loggar med plusknappen hamnar här, dag för dag.") {
        AppButton("Logga", {}, Modifier.fillMaxWidth(), icon = R.drawable.ic_add)
    }
    Box(Modifier.fillMaxWidth().height(120.dp)) {
        AppButton("Allt klart för idag", { celebrate = !celebrate }, Modifier.align(Alignment.Center), variant = ButtonVariant.Text)
        Confetti(Modifier.fillMaxSize(), play = celebrate)
    }
    AppSnackbarHost(snackbar)
}

@Composable
private fun Frames(onBack: () -> Unit) {
    var undo by remember { mutableStateOf<UndoRequest?>(null) }
    var items by remember { mutableStateOf(listOf("Promenad", "Yoga", "Städning")) }
    Label("EntityListScreen · SwipeToHide · UndoSnackbar")
    Box(Modifier.fillMaxWidth().height(FRAME_HEIGHT)) {
        EntityListScreen(
            title = "Aktivitetstyper",
            state = if (items.isEmpty()) ListUiState.Empty else ListUiState.Content(items),
            empty = EmptyContent(
                R.drawable.ic_activity,
                "Inga aktivitetstyper än",
                "Promenad, yoga, städning – det du brukar göra.",
                "Lägg till",
                examples = listOf(EmptyExample("Promenad") { items = listOf("Promenad") }),
            ),
            onAdd = {},
            key = { it },
            archive = ListArchive(undo = undo) { event ->
                when (event) {
                    ArchiveEvent.Undo -> {
                        undo?.let { items = items + it.name }
                        undo = null
                    }
                    ArchiveEvent.UndoDismissed -> undo = null
                    else -> Unit
                }
            },
        ) { item ->
            SwipeToHide(onHide = { items = items - item; undo = UndoRequest(item, item) }) { swipe ->
                ItemRow(item, swipe, subtitle = "Favorit", navigates = true, onClick = {})
            }
        }
    }
    Label("EntityListScreen – fel")
    Box(Modifier.fillMaxWidth().height(FRAME_HEIGHT)) {
        EntityListScreen<String>(
            title = "Recept",
            state = ListUiState.Error(DataError.Offline),
            empty = EmptyContent(R.drawable.ic_pill, "Inga recept än", "", "Nytt recept"),
            onAdd = {},
            key = { it },
        ) {}
    }
    Label("EntityListScreen – rubrik, grupper, undergrupper, kolumner, hopfällbar")
    val grouped = listOf(
        "Morgon" to "Levaxin 50 µg", "Morgon" to "D-vitamin", "" to "Alvedon", "" to "Ipren", "" to "Desloratadin", "-" to "Prednisolon",
    )
    Box(Modifier.fillMaxWidth().height(FRAME_HEIGHT)) {
        EntityListScreen(
            title = "Mediciner",
            subtitle = "sön 4 okt",
            state = ListUiState.Content(grouped),
            empty = EmptyContent(R.drawable.ic_pill, "", "", ""),
            onAdd = {},
            key = { it.second },
            onBack = {},
            group = { (slot, _) ->
                when (slot) {
                    "" -> ListGroup("Vid behov", count = "3", columns = 2)
                    "-" -> ListGroup("Avslutade", count = "1", collapsible = true)
                    else -> ListGroup("Recept", tone = Tone.Neutral, count = "1 / 2")
                }
            },
            subgroup = { (slot, _) -> if (slot.length > 1) ListSubgroup(slot, AppColors.swatch(0)) else null },
        ) { (_, name) -> CheckRow(name, name.startsWith("L"), {}) }
    }
    Label("EntityDetailScreen – finns inte längre")
    Box(Modifier.fillMaxWidth().height(FRAME_HEIGHT)) {
        EntityDetailScreen<String>(DetailUiState.Error(DataError.NotFound), header = { DetailHeader(it) }, onBack = {}) {}
    }
    Label("EntityEditScreen")
    var name by remember { mutableStateOf("") }
    Box(Modifier.fillMaxWidth().height(FRAME_HEIGHT)) {
        EntityEditScreen(
            title = "Ny aktivitetstyp",
            state = EditorUiState(value = name, errors = if (name.isBlank()) mapOf("name" to R.string.error_unknown) else emptyMap(), isValid = name.isNotBlank(), isDirty = name.isNotEmpty()),
            effects = emptyFlow(),
            onSave = {},
            onClose = onBack,
            onArchive = {},
            delete = DeleteAction("Radera Yoga?", "Aktivitetstypen tas bort för gott. Det går inte att ångra.") {},
        ) {
            AppTextField(name, { name = it }, "Namn")
        }
    }
    Label("EntityDetailScreen")
    Box(Modifier.fillMaxWidth().height(FRAME_HEIGHT)) {
        EntityDetailScreen(
            state = DetailUiState.Content("Förkylning"),
            header = { DetailHeader(it, "sedan fre 2 okt · pågående") },
            onBack = onBack,
            onEdit = {},
            menu = { listOf(AppMenuItem("Avsluta episoden", {}, R.drawable.ic_check)) },
            leading = { Text("🤧", style = AppTypography.headline) },
        ) {
            AppCard {
                SectionHeader("Incheckningar", icon = R.drawable.ic_thermometer, count = "1")
                ItemRow("sön 4 okt · 08:15", subtitle = "Svårighetsgrad 4 · hosta, snuva", navigates = true, onClick = {})
            }
        }
    }
}

private val FRAME_HEIGHT = 480.dp
