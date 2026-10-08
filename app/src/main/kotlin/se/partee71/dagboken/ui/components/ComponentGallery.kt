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
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus
import kotlinx.datetime.LocalTime
import se.partee71.dagboken.R
import se.partee71.dagboken.core.engine.TypeChoices
import se.partee71.dagboken.core.engine.OccasionStatus
import se.partee71.dagboken.data.auth.AuthUser
import se.partee71.dagboken.core.model.Option
import se.partee71.dagboken.core.model.OptionKind
import se.partee71.dagboken.core.model.SymptomScore
import se.partee71.dagboken.core.engine.IntervalPoint
import se.partee71.dagboken.core.engine.StackedPoint
import se.partee71.dagboken.core.engine.TrendDirection
import se.partee71.dagboken.data.common.DataError
import se.partee71.dagboken.ui.common.DetailUiState
import se.partee71.dagboken.ui.common.EditorUiState
import se.partee71.dagboken.ui.common.ArchiveEvent
import se.partee71.dagboken.ui.common.DateFormat
import se.partee71.dagboken.ui.common.durationText
import se.partee71.dagboken.ui.common.ListUiState
import se.partee71.dagboken.ui.common.color
import se.partee71.dagboken.ui.common.scaleLevel
import se.partee71.dagboken.ui.diagram.ChartBand
import se.partee71.dagboken.ui.diagram.ChartSeries
import se.partee71.dagboken.ui.diagram.CompactDropdownButton
import se.partee71.dagboken.ui.diagram.IntervalBarChart
import se.partee71.dagboken.ui.diagram.LineChart
import se.partee71.dagboken.ui.diagram.MinMaxCaption
import se.partee71.dagboken.ui.diagram.SparklineChart
import se.partee71.dagboken.ui.diagram.StackSegment
import se.partee71.dagboken.ui.diagram.StackedBarChart
import se.partee71.dagboken.ui.diagram.sleepStageSegments
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
            GallerySection("Idag och konto") { Today() }
            GallerySection("Diagram") { Diagrams() }
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
        AppMenu(
            listOf(AppMenuItem("Loratadin 10 mg", {}), AppMenuItem("Betapred 0,5 mg", {}, section = "Recept")),
            contentDescription = "Fler, med avdelning",
        )
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
        SectionHeader("Flytten", icon = R.drawable.ic_database, count = "Kontrollerat", countTone = Tone.Positive)
        SectionHeader("Mediciner", icon = R.drawable.ic_pill, count = "2 / 3")
        ItemRow(
            "Promenad",
            subtitle = "08:30 · 45 min · energi +3",
            leading = { Text("🚶", style = AppTypography.sectionTitle) },
            navigates = true,
            onClick = {},
        )
        AppDivider()
        ItemRow("Importera från fil", subtitle = "3.x-backup (.json) eller 4.0-export", icon = R.drawable.ic_file, navigates = true, onClick = {})
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
        CheckRow(
            "D-vitamin 20 µg", false, {}, subtitle = "Förmiddag · 10:00", note = "Tas med mat.",
            menu = listOf(AppMenuItem("Hoppa över", {}, R.drawable.ic_close)), below = { InfoPill("Försenat", tone = Tone.Warning) },
        )
        AppDivider()
        SwitchRow("Medicinpåminnelser", reminders, { reminders = it }, subtitle = "6 tider om dagen")
        SwitchRow("Måendepåminnelse", reminders, { reminders = it }, subtitle = "Frukost · 08:00", onClick = {})
        AppDivider()
        var favorite by remember { mutableStateOf(true) }
        ItemRow("Alvedon 500 mg", subtitle = "Minst 4 h mellan · högst 8 per dag", navigates = true, onClick = {}, trailing = { FavoriteStar("Alvedon", favorite, { favorite = !favorite }) })
    }
    NoticeBanner("Kåvepenin slutar i morgon. Höjningen av Sertralin slutar i morgon – sedan 50 mg.", R.drawable.ic_bell, {})
    NoticeBanner("Health Connect kopplad", R.drawable.ic_watch, onClick = null, tone = Tone.Positive, detail = "Steg, puls, sömn och sömnkvalitet visas här.")
    NoticeBanner("Health Connect saknas", R.drawable.ic_watch, {}, detail = "Installera Health Connect från Play Butik för att se klockans data.", action = "Installera")
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
    var boost by remember { mutableStateOf("25") }
    AppTextField(boost, { boost = it }, "Höjning", suffix = "mg")
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
    var boostEnd by remember { mutableStateOf<LocalDate?>(null) }
    DateField("Slutdatum", boostEnd, { boostEnd = it }, emptyLabel = "Periodens slut", onClear = { boostEnd = null })
    var unit by remember { mutableStateOf("mg") }
    UnitChoice(unit, { unit = it })
    var time by remember { mutableStateOf(LocalTime(8, 30)) }
    TimeField("Tid", time, { time = it })
    LabeledGroup("Tillfälle") {
        ChoiceChips(listOf(0, 1, 2, 3), occasion, { occasion = it }, { listOf("Frukost", "Lunch", "Middag", "Kväll")[it] })
    }
    FieldError("Ingen anslutning just nu. Det du sparar skickas när nätet är tillbaka.")
    AppFilterChip("Sjukdom", selected = false, onClick = {}, enabled = false)
    AppFilterChip("Alvedon 500 mg", selected = false, onClick = {}, icon = R.drawable.ic_note, onLongClick = {}, onLongClickLabel = "Fler val")
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
    var active by remember { mutableStateOf(true) }
    DagbokenEntryCard(
        "Sertralin 50 mg",
        onClick = {},
        subtitle = "Morgon · dagligen · tills vidare",
        accent = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
        onEdit = {},
        inactive = !active,
        toggle = EntryToggle(active, { active = it }, "Sertralin aktivt"),
        below = { InfoPill("Idag 75 mg (+25)") },
    )
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
    var type by remember { mutableStateOf("promenad") }
    TypeChoiceField(GALLERY_TYPES, type, { type = it }, otherLabel = "Övrigt")
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
        StatPill(R.drawable.ic_sun, stringResource(R.string.value_missing), "Sömn", tone = Tone.Primary, onClick = {}, onClickLabel = "Begär åtkomst")
    }
    StatPill(R.drawable.ic_lock, "2 saknas", "Träning · Syremättnad", Modifier.fillMaxWidth(), tone = Tone.Sun, onClick = {}, action = "Ge åtkomst")
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

@Composable
private fun Today() {
    val today = LocalDate(2026, 10, 5)
    var week by remember { mutableStateOf(today) }
    var day by remember { mutableStateOf(today) }
    var done by remember { mutableIntStateOf(4) }
    var celebrate by remember { mutableStateOf(false) }
    Label("DateStrip")
    DateStrip(
        week,
        day,
        { day = it },
        { week = it },
        today = today,
        datesWithEntries = setOf(LocalDate(2026, 9, 28), LocalDate(2026, 9, 30), LocalDate(2026, 10, 1), LocalDate(2026, 10, 3), today),
        todayDone = done == GALLERY_TOTAL,
    )
    Label("ProgressBar")
    ProgressBar(done, GALLERY_TOTAL)
    ProgressBar(GALLERY_TOTAL, GALLERY_TOTAL, celebrate = false)
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
        AppButton("Bocka av", { done = (done + 1).coerceAtMost(GALLERY_TOTAL) }, variant = ButtonVariant.Secondary)
        AppButton("Börja om", { done = 0 }, variant = ButtonVariant.Text)
    }
    Label("OccasionRow")
    AppCard { GalleryOccasionRows() }
    // En tidigare dag: "Ej loggad" utan varningston (HEM-4).
    AppCard { OccasionRow("Lunch", OccasionStatus.NOT_LOGGED, {}, time = "12:00") }
    Label("DayDoneCard")
    DayDoneCard(6.8, 0.5, 12, play = celebrate, onConfettiFinished = { celebrate = false })
    AppButton("Fira igen", { celebrate = true }, variant = ButtonVariant.Text)
    Label("AccountAvatar")
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
        AccountAvatar("Anna Berg", {})
        AccountAvatar(null, {})
    }
}

private const val GALLERY_TOTAL = 9

@Composable
private fun Diagrams() {
    val periods = listOf("7 dagar", "14 dagar", "Månad", "3 månader", "Allt")
    var period by remember { mutableIntStateOf(1) }
    var withPrevious by remember { mutableStateOf(false) }
    Label("CompactDropdownButton · LineChart")
    AppCard {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("Energi per dag", style = AppTypography.sectionTitle)
            CompactDropdownButton(
                periods[period],
                periods.mapIndexed { i, name -> AppMenuItem(name, { period = i }, icon = if (i == period) R.drawable.ic_check else null) },
            )
        }
        LineChart(
            listOf(ChartSeries("Energi", GalleryCharts.energy)),
            xLabels = GalleryCharts.days14,
            previous = if (withPrevious) listOf(ChartSeries("Energi", GalleryCharts.energyPrevious)) else emptyList(),
            label = "Energi per dag",
        )
        SwitchRow("Föregående period", withPrevious, { withPrevious = it })
    }
    Label("IntervalBarChart")
    AppCard { IntervalBarChart(GalleryCharts.energySpans, xLabels = GalleryCharts.days7, label = "Energi (dag)") }
    Label("StackedBarChart")
    AppCard { StackedBarChart(GalleryCharts.sleep, sleepStageSegments(), xLabels = GalleryCharts.days7, label = "Sömnstadier") }
    Label("StackedBarChart · staplar, linje och fält (Händelser och sjukdom)")
    AppCard {
        StackedBarChart(
            GalleryCharts.eventBars,
            listOf(StackSegment("Händelser", AppColors.tone(Tone.Warning).content)),
            xLabels = GalleryCharts.days14,
            label = "Händelser och sjukdom",
            line = ChartSeries("Incheckningar", GalleryCharts.checkins),
            bands = GalleryCharts.episodes,
        )
    }
    Label("SparklineChart")
    AppCard { SparklineChart(GalleryCharts.week, xLabels = GalleryCharts.weekdays, label = "Energi senaste veckan", onOpenTrends = {}) }
    Label("MinMaxCaption · tomt läge")
    MinMaxCaption(3f, 8f, average = 6.2f, trend = TrendDirection.RISING)
    AppCard { LineChart(listOf(ChartSeries("Vilopuls", listOf(null, 58f, null))), label = "Vilopuls") }
}

/** Påhittade diagramdata för galleriet och dess skärmdumpar – luckor där inget loggats. */
internal object GalleryCharts {
    private val start = LocalDate(2026, 9, 22)

    /** "22 sep" … "5 okt". */
    val days14: List<String> = (0 until 14).map { DateFormat.short(start.plus(DatePeriod(days = it))) }

    val days7: List<String> = days14.takeLast(7)

    /** "tis" … "mån" – sju dagar som slutar idag (mån 5 okt). */
    val weekdays: List<String> = (7 until 14).map { DateFormat.weekdayShort(start.plus(DatePeriod(days = it))) }

    val energy: List<Float?> = listOf(5f, 6f, 5.5f, null, 6f, 7f, 6.5f, null, null, 6f, 7f, 6.5f, 7.5f, 8f)
    val energyPrevious: List<Float?> = listOf(6f, 5f, 5f, 4.5f, 5f, 6f, 5f, 5.5f, 5f, null, 6f, 5f, 5.5f, 6f)
    val week: List<Float?> = listOf(6f, 7f, 5.5f, null, 6.5f, 7f, 8f)

    val energySpans: List<IntervalPoint?> = listOf(
        IntervalPoint(3f, 4.5f, 6f),
        IntervalPoint(5f, 6f, 7f),
        null,
        IntervalPoint(2f, 3.5f, 5f),
        IntervalPoint(6f, 7f, 8f),
        IntervalPoint(7f, 7.5f, 8f),
        IntervalPoint(5f, 6.3f, 8f),
    )

    /** Händelser och sjukdom (TRD-21): händelsernas svårighet som staplar, incheckningarna som linje och en förkylning som fält. */
    val eventBars: List<StackedPoint> = listOf(null, 6f, null, null, 4f, null, null, null, 7f, 5f, null, null, null, 3f).map { StackedPoint(listOf(it)) }
    val checkins: List<Float?> = listOf(null, null, null, null, null, null, null, 6f, 7f, 5f, 4f, 3f, null, 2f)
    val episodes: List<ChartBand> = listOf(ChartBand(7, 13, "Förkylning"))

    /** Timmar per natt: djup, REM, lätt, vaken (TRD-16). En natt utan klockan och en utan REM-mätning. */
    val sleep: List<StackedPoint> = listOf(
        StackedPoint(listOf(1.2f, 1.6f, 4.1f, 0.5f)),
        StackedPoint(listOf(1.0f, 1.4f, 3.8f, 0.8f)),
        StackedPoint(listOf(null, null, null, null)),
        StackedPoint(listOf(1.4f, 1.8f, 4.4f, 0.4f)),
        StackedPoint(listOf(0.8f, null, 3.2f, 1.1f)),
        StackedPoint(listOf(1.3f, 1.7f, 4.0f, 0.6f)),
        StackedPoint(listOf(1.5f, 1.9f, 4.3f, 0.3f)),
    )
}

/** Dagens fyra måendetillfällen i var sitt läge (påhittade värden) – för galleriet och dess skärmdumpar. */
@Composable
internal fun GalleryOccasionRows() {
    OccasionRow(
        "Efter frukost",
        OccasionStatus.LOGGED,
        {},
        time = "08:12",
        values = listOf(OccasionValue("Energi", 7), OccasionValue("Stress", 4, higherIsBetter = false)),
        onClick = {},
    )
    OccasionRow("Lunch", OccasionStatus.LATE, {}, time = "12:00")
    OccasionRow("Kvällsmat", OccasionStatus.SOON, {}, time = "18:00")
    OccasionRow("Läggdags", OccasionStatus.UPCOMING, {}, time = "22:00")
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
    var fieldDialog by remember { mutableStateOf(false) }
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
    AppButton("Dialog med fält", { fieldDialog = true }, variant = ButtonVariant.Secondary)
    AppButton("Arkivera Yoga", { archived = UndoRequest("yoga", "Yoga") }, variant = ButtonVariant.Text)
    UndoSnackbar(archived, snackbar, onUndo = { archived = null }, onDismissed = { archived = null })
    if (dialog) {
        ConfirmDialog("Radera Promenad?", "Aktiviteten tas bort för gott. Det går inte att ångra.", "Radera", { dialog = false }, { dialog = false }, destructive = true)
    }
    if (fieldDialog) {
        // Ett fält i frågan (SJ-4): slutdatumet när en sjukdomsepisod avslutas.
        var end by remember { mutableStateOf(LocalDate(2026, 10, 6)) }
        ConfirmDialog("Avsluta episoden?", "Förkylning markeras som avslutad. Incheckningarna står kvar.", "Avsluta", { fieldDialog = false }, { fieldDialog = false }) {
            DateField("Slutdatum", end, { end = it })
        }
    }
    if (sheet) {
        // Ändra minuterna: då frågar bakåt, svep ner och tryck utanför "Släng ändringar?" (dirty, NFR-10).
        var minutes by remember { mutableIntStateOf(45) }
        AppBottomSheet("Promenad", onDismiss = { sheet = false }, dirty = minutes != 45) {
            QuantityStepper(minutes, { minutes = it }, "minuter")
            AppButton("Spara", { sheet = false }, variant = ButtonVariant.Text)
        }
    }
    when (appSheet) {
        AppSheet.Account -> AccountSheet(
            onDismiss = { appSheet = null },
            onSignOut = { appSheet = null },
            onOpenGallery = { appSheet = null },
            account = AuthUser("galleri", "Anna Berg", "anna.berg@exempel.se"),
            onOpen = { appSheet = null },
        )
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
                examples = listOf(EmptyExample("Promenad") { items = listOf("Promenad") }),
            ),
            add = AddAction("Lägg till", {}),
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
            empty = EmptyContent(R.drawable.ic_pill, "Inga recept än", ""),
            add = AddAction("Nytt recept", {}),
            key = { it },
        ) {}
    }
    Label("EntityListScreen – utan lägg till, dagar utan räknare och sidfot")
    Box(Modifier.fillMaxWidth().height(FRAME_HEIGHT)) {
        EntityListScreen(
            title = "Dagbok",
            state = ListUiState.Content(listOf("Idag" to "Efter frukost", "Idag" to "Levaxin 100 µg", "Igår" to "Promenad")),
            empty = EmptyContent(R.drawable.ic_book, "Inga poster än", "Logga med plusknappen."),
            add = null,
            key = { it.second },
            group = { ListGroup(it.first, tone = Tone.Neutral, cards = true, showCount = false) },
            footer = { AppButton("Visa äldre än ett år", {}, Modifier.fillMaxWidth(), variant = ButtonVariant.Secondary, icon = R.drawable.ic_expand_more) },
        ) { DagbokenEntryCard(it.second, onClick = {}) }
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
            empty = EmptyContent(R.drawable.ic_pill, "", ""),
            add = AddAction("", {}),
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
    Label("UpcomingScreen")
    Box(Modifier.fillMaxWidth().height(FRAME_HEIGHT)) {
        UpcomingScreen("Export och import", R.drawable.ic_download, "Spara en kopia av dagboken eller läs in en tidigare.", onBack = onBack)
    }
}

private val FRAME_HEIGHT = 480.dp

/** Aktivitetstyperna i galleriet: två stjärnmärkta som chips, resten under "Fler typer" med Övrigt sist. */
private val GALLERY_TYPES = TypeChoices(
    favorites = listOf(Option("promenad", OptionKind.ACTIVITY, "Promenad", favorite = true), Option("jobb", OptionKind.ACTIVITY, "Jobb", favorite = true)),
    more = listOf(Option("vila", OptionKind.ACTIVITY, "Vila", sortOrder = 2)),
    other = "ovrigt",
)
