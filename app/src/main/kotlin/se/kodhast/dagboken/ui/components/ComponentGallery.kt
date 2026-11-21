package se.kodhast.dagboken.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.launch
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.plus
import se.kodhast.dagboken.R
import se.kodhast.dagboken.core.engine.FeelingDay
import se.kodhast.dagboken.core.engine.FeelingEntry
import se.kodhast.dagboken.core.engine.FeelingStats
import se.kodhast.dagboken.core.engine.IntervalPoint
import se.kodhast.dagboken.core.engine.OccasionStatus
import se.kodhast.dagboken.core.engine.StackedPoint
import se.kodhast.dagboken.core.engine.TrendDirection
import se.kodhast.dagboken.core.engine.TypeChoices
import se.kodhast.dagboken.core.engine.computeFeelingStats
import se.kodhast.dagboken.core.model.Feeling
import se.kodhast.dagboken.core.model.FeelingStrength
import se.kodhast.dagboken.core.model.Occasion
import se.kodhast.dagboken.core.model.Option
import se.kodhast.dagboken.core.model.OptionKind
import se.kodhast.dagboken.core.model.Screening
import se.kodhast.dagboken.core.model.SymptomScore
import se.kodhast.dagboken.core.schema.TextLimits
import se.kodhast.dagboken.data.auth.AuthUser
import se.kodhast.dagboken.data.common.DataError
import se.kodhast.dagboken.ui.common.AppErrors
import se.kodhast.dagboken.ui.common.FeelingSentenceKind
import se.kodhast.dagboken.ui.common.color
import se.kodhast.dagboken.ui.common.feelingSentence
import se.kodhast.dagboken.ui.common.scaleLevel
import se.kodhast.dagboken.ui.diagram.ChartBand
import se.kodhast.dagboken.ui.diagram.ChartSeries
import se.kodhast.dagboken.ui.diagram.FeelingWordBars
import se.kodhast.dagboken.ui.diagram.IntervalBarChart
import se.kodhast.dagboken.ui.diagram.LineChart
import se.kodhast.dagboken.ui.diagram.MinMaxCaption
import se.kodhast.dagboken.ui.diagram.SparklineChart
import se.kodhast.dagboken.ui.diagram.StackSegment
import se.kodhast.dagboken.ui.diagram.StackedBarChart
import se.kodhast.dagboken.ui.diagram.sleepStageSegments
import se.kodhast.dagboken.ui.theme.AppColors
import se.kodhast.dagboken.ui.theme.AppTypography
import se.kodhast.dagboken.ui.theme.Neutral
import se.kodhast.dagboken.ui.theme.Positive
import se.kodhast.dagboken.ui.theme.Sun
import se.kodhast.dagboken.ui.theme.Warning
import se.kodhast.ui.common.AuthGate
import se.kodhast.ui.common.DateFormat
import se.kodhast.ui.common.DetailUiState
import se.kodhast.ui.common.EditorEvents
import se.kodhast.ui.common.EditorUiState
import se.kodhast.ui.common.ListUiState
import se.kodhast.ui.components.AboutCard
import se.kodhast.ui.components.AccountRow
import se.kodhast.ui.components.AddAction
import se.kodhast.ui.components.AddSplitButton
import se.kodhast.ui.components.AppBottomSheet
import se.kodhast.ui.components.AppButton
import se.kodhast.ui.components.AppCard
import se.kodhast.ui.components.AppDivider
import se.kodhast.ui.components.AppFilterChip
import se.kodhast.ui.components.AppFloatingToolbar
import se.kodhast.ui.components.AppHeader
import se.kodhast.ui.components.AppIconButton
import se.kodhast.ui.components.AppLoading
import se.kodhast.ui.components.AppMenu
import se.kodhast.ui.components.AppMenuItem
import se.kodhast.ui.components.AppSegmentedChoice
import se.kodhast.ui.components.AppSnackbarHost
import se.kodhast.ui.components.AppTextField
import se.kodhast.ui.components.AppTopBar
import se.kodhast.ui.components.ArchiveEvent
import se.kodhast.ui.components.AuthGateHost
import se.kodhast.ui.components.ButtonVariant
import se.kodhast.ui.components.CheckRow
import se.kodhast.ui.components.ChipRow
import se.kodhast.ui.components.ChoiceChips
import se.kodhast.ui.components.Confetti
import se.kodhast.ui.components.ConfirmDialog
import se.kodhast.ui.components.DateField
import se.kodhast.ui.components.DateTimeRow
import se.kodhast.ui.components.DeleteAction
import se.kodhast.ui.components.DetailHeader
import se.kodhast.ui.components.DropdownPill
import se.kodhast.ui.components.DurationRow
import se.kodhast.ui.components.EmptyContent
import se.kodhast.ui.components.EmptyExample
import se.kodhast.ui.components.EmptyState
import se.kodhast.ui.components.EntityDetailScreen
import se.kodhast.ui.components.EntityEditScreen
import se.kodhast.ui.components.EntityListScreen
import se.kodhast.ui.components.ExampleChips
import se.kodhast.ui.components.FavoriteMark
import se.kodhast.ui.components.FavoriteStar
import se.kodhast.ui.components.FieldError
import se.kodhast.ui.components.Foldout
import se.kodhast.ui.components.GroupLabel
import se.kodhast.ui.components.IconButtonVariant
import se.kodhast.ui.components.InfoPill
import se.kodhast.ui.components.ItemRow
import se.kodhast.ui.components.LabeledGroup
import se.kodhast.ui.components.ListArchive
import se.kodhast.ui.components.ListGroup
import se.kodhast.ui.components.ListSubgroup
import se.kodhast.ui.components.LongPressSemantics
import se.kodhast.ui.components.MakerMark
import se.kodhast.ui.components.NoteCard
import se.kodhast.ui.components.NoticeBanner
import se.kodhast.ui.components.PausableRow
import se.kodhast.ui.components.QuantityStepper
import se.kodhast.ui.components.ReminderTimeRow
import se.kodhast.ui.components.SectionFoldout
import se.kodhast.ui.components.SectionHeader
import se.kodhast.ui.components.SignOutRow
import se.kodhast.ui.components.StatPill
import se.kodhast.ui.components.Suggestion
import se.kodhast.ui.components.SuggestionField
import se.kodhast.ui.components.SwipeToHide
import se.kodhast.ui.components.SwitchRow
import se.kodhast.ui.components.TimeField
import se.kodhast.ui.components.ToolbarAction
import se.kodhast.ui.components.ToolbarItem
import se.kodhast.ui.components.TopBarSize
import se.kodhast.ui.components.UndoRequest
import se.kodhast.ui.components.UndoSnackbar
import se.kodhast.ui.components.asSplitMenu
import se.kodhast.ui.components.favoriteMenuItem
import se.kodhast.ui.components.inactive
import se.kodhast.ui.components.longPress
import se.kodhast.ui.navigation.gallery.ComponentGallery as LibraryComponentGallery
import se.kodhast.ui.navigation.gallery.GallerySection
import se.kodhast.ui.theme.Spacing
import se.kodhast.ui.theme.Tone
/**
 * Komponentgalleriet (debug-bygget, inställningsarket → "Komponentgalleri"): android-ui:s `ComponentGallery` – först
 * bibliotekets sektioner med varje publik komponent i Dagbokens tema, sedan Dagbokens egna nedan, med riktiga svenska
 * texter och påhittad data (skill shared-ui-components). `UiConsistencyTest` kontrollerar att varje publik komponent i
 * Dagboken finns här; bibliotekets kontrollerar biblioteket sina.
 */
@Composable
fun ComponentGallery(onBack: () -> Unit, modifier: Modifier = Modifier) {
    LibraryComponentGallery(
        onBack,
        modifier,
        appSections = listOf(
            GallerySection("Poster, reglage och kalender") { Diary() },
            GallerySection("Idag och konto") { Today() },
            GallerySection("Diagram") { Diagrams() }
        )
    )
}

@Composable
private fun Label(text: String) = GroupLabel(text)

@Composable
private fun Diary() {
    var deleted by remember { mutableStateOf(false) }
    Label("DagbokenEntryCard")
    DagbokenEntryCard(
        "Promenad",
        onClick = {},
        subtitle = "${DateFormat.time(LocalTime(8, 30))} · ${DateFormat.duration(45)}",
        leading = { Text("🚶", style = AppTypography.sectionTitle) },
        accent = scaleLevel(3, -10..10).color,
        status = { InfoPill("+3", tone = Tone.Positive) },
        note = "Gick i skogen med hunden, lätt regn.",
        expandedContent = { Text("Symptom: huvudvärk 2", style = AppTypography.body) },
        onEdit = {},
        delete = DeleteAction("Radera Promenad?", "Posten tas bort för gott. Det går inte att ångra.") { deleted = true }
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
        below = { InfoPill("Idag 75 mg (+25)") }
    )
    var starred by remember { mutableStateOf(true) }
    DagbokenEntryCard(
        "Alvedon 500 mg",
        onClick = {},
        subtitle = "Minst 4 h mellan · högst 8 per dag",
        accent = MaterialTheme.colorScheme.primary,
        onEdit = {},
        favoriteStar = EntryFavorite("Alvedon", starred) { starred = !starred }
    )
    if (deleted) Text("Raderad", style = AppTypography.itemSubtitle)
    GalleryFeelingCards()
    var expanded by remember { mutableStateOf(true) }
    AppCard {
        Foldout("Mätvärden", expanded, { expanded = !expanded }, summary = "Energi 7 · stress 3") {
            var energy by remember { mutableIntStateOf(7) }
            var stress by remember { mutableIntStateOf(3) }
            var activity by remember { mutableIntStateOf(3) }
            ValueSlider("Energi", energy, { energy = it })
            ValueSlider("Stress", stress, { stress = it }, higherIsBetter = false)
            ValueSlider("Aktivitetens energi", activity, { activity = it }, valueRange = -10..10)
            var pause by remember { mutableIntStateOf(135) }
            ValueSlider("Pausens längd", pause, { pause = it }, valueRange = 15..300, step = 15, valueText = DateFormat.durationSeconds(pause))
        }
    }
    var symptoms by remember { mutableStateOf(listOf(SymptomScore("huvudvark", 4), SymptomScore("ovrigt", 2, "Ont i knät"))) }
    SymptomLogCard(GALLERY_SYMPTOMS, symptoms, { symptoms = it }, otherOptionId = "ovrigt")
    var type by remember { mutableStateOf("promenad") }
    TypeChoiceField(GALLERY_TYPES, type, { type = it }, otherLabel = "Övrigt")
    var unit by remember { mutableStateOf("mg") }
    UnitChoice(unit, { unit = it })
    Label("StepwiseScreeningForm")
    var energy by remember { mutableIntStateOf(6) }
    var screeningFeelings by remember { mutableStateOf(emptyList<Feeling>()) }
    var stress by remember { mutableIntStateOf(4) }
    var screeningSymptoms by remember { mutableStateOf(emptyList<SymptomScore>()) }
    AppCard {
        StepwiseScreeningForm(
            energy, { energy = it }, screeningFeelings, { screeningFeelings = it }, stress, { stress = it },
            GALLERY_SYMPTOMS, screeningSymptoms, { screeningSymptoms = it }, onSave = {}
        )
    }
    Label("FeelingWheel")
    var wheel by remember { mutableStateOf(setOf("calm")) }
    FeelingWheel(wheel, { core -> wheel = if (core in wheel) wheel - core else wheel + core })
    Label("FeelingPicker")
    var feelings by remember { mutableStateOf(GALLERY_FEELINGS) }
    AppCard { FeelingPicker(feelings, { feelings = it }) }
    Label("FeelingPicker med styrka och kommentar (FeelingExtras)")
    var post by remember { mutableStateOf(GALLERY_FEELING_POST) }
    AppCard { FeelingPicker(post.feelings, { f -> post = post.withFeelings(f) }, extras = FeelingExtras.of(post) { change -> post = change(post) }) }
    Label("longPress (haptik, anpassad åtgärd)")
    var presses by remember { mutableIntStateOf(0) }
    InfoPill("Håll in: $presses", Modifier.longPress("Starkare", { presses++ }, haptic = true, semantics = LongPressSemantics.CustomAction), onClick = {})
    Label("FeelingPills")
    ChipRow { FeelingPills(GALLERY_POST_FEELINGS) }
    // Styrkans tre intensiteter med prickar (SCR-9).
    ChipRow { FeelingPills(GALLERY_FEELING_POST.feelings, extras = FeelingExtras.shown(GALLERY_FEELING_POST)) }
    // Alla, utan "+N" – Idags Dagens känslor och Trenders valda dag.
    ChipRow { FeelingPills(GALLERY_POST_FEELINGS, max = null) }
    Label("FeelingMiniWheel")
    GalleryMiniWheels()
    Label("FeelingWeekRow")
    var weekDay by remember { mutableStateOf<LocalDate?>(GalleryFeelings.week.last().date) }
    AppCard { FeelingWeekRow(GalleryFeelings.week, weekDay, { weekDay = it.takeUnless { day -> day == weekDay } }) }
    Label("FeelingNoteRow – Trenders Varför (TRD-24)")
    AppCard { GalleryNoteRows() }
}

/** Kommentarsraderna med påhittade kommentarer: med styrka, utan styrka och från en aktivitet. */
@Composable
internal fun GalleryNoteRows() {
    GALLERY_NOTE_ROWS.forEachIndexed { i, (feeling, strength, whenText, text) ->
        if (i > 0) AppDivider()
        FeelingNoteRow(feeling, strength, whenText, text)
    }
}

/** Påhittade kommentarer till känslor (SCR-10) som i mockupen #396. */
internal data class GalleryNote(val feeling: Feeling, val strength: FeelingStrength?, val whenText: String, val text: String)

internal val GALLERY_NOTE_ROWS = listOf(
    GalleryNote(Feeling("angry", "frustrated"), FeelingStrength.STRONG, "fre 6 nov · Lunch", "Bussen kom inte och jag missade tiden."),
    GalleryNote(Feeling("glad", "content"), null, "ons 4 nov · Promenad", "Första gången runt hela sjön sedan förkylningen."),
    GalleryNote(Feeling("calm", "grateful"), FeelingStrength.MILD, "tis 3 nov · Kvällsmat", "Middag med en vän, skönt att prata.")
)

/** Minihjulen med påhittade val: dagens kort och värmekartan med ord (#320); veckans dagar visar `FeelingWeekRow`. */
@Composable
internal fun GalleryMiniWheels() {
    val day = GalleryFeelings.day
    val daySentence = feelingSentence(day.summary, FeelingSentenceKind.DAY)
    // Hjulet läser meningen för TalkBack; texten bredvid är samma mening och läses inte en gång till.
    AppCard {
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.m), verticalAlignment = Alignment.CenterVertically) {
            FeelingMiniWheel(day.tally, daySentence)
            Text(daySentence, Modifier.clearAndSetSemantics {}, style = AppTypography.body)
        }
    }
    val period = GalleryFeelings.period
    val periodSentence = feelingSentence(period.summary, FeelingSentenceKind.PERIOD)
    AppCard {
        FeelingMiniWheel(period.tally, periodSentence, size = FeelingWheelSize.HEATMAP, labels = true)
        Text(periodSentence, Modifier.clearAndSetSemantics {}, style = AppTypography.body)
    }
}

/** Påhittade känsloval för galleriet och dess skärmdumpar: en dag på Idag, en vecka och en månad i Trender. */
internal object GalleryFeelings {
    private val monday = LocalDate(2026, 10, 12)

    private fun entry(date: LocalDate, vararg tokens: String, occasion: Occasion? = null) = FeelingEntry(date, tokens.mapNotNull(Feeling::parse), occasion = occasion)

    /** "Mest glad och lugn. Lite rädd vid lunch." */
    val day: FeelingStats = computeFeelingStats(
        listOf(
            entry(monday, "glad.creative.curious", occasion = Occasion.BREAKFAST),
            entry(monday, "scared.anxious", occasion = Occasion.LUNCH),
            entry(monday, "calm.grateful", "glad.content", occasion = Occasion.DINNER),
            entry(monday, "calm", occasion = Occasion.BEDTIME)
        )
    )

    private val weekEntries: List<FeelingEntry> = listOf(
        entry(monday, "sad.tired", "scared.anxious"),
        entry(monday.plus(DatePeriod(days = 1)), "scared.anxious", "angry.irritated", "sad.lonely"),
        entry(monday.plus(DatePeriod(days = 3)), "calm.relaxed", "glad.hopeful"),
        entry(monday.plus(DatePeriod(days = 4)), "calm.grateful", "glad.content", "calm.present"),
        entry(monday.plus(DatePeriod(days = 5)), "glad.playful"),
        entry(monday.plus(DatePeriod(days = 6)), "calm.grateful", "calm.relaxed", "glad.creative")
    )

    /** Mån–sön, en dag utan känslor (onsdag) – samma gruppering per dag som Trenders vecka. */
    val week: List<FeelingDay> = computeFeelingStats(weekEntries).alignTo((0..6).map { monday.plus(DatePeriod(days = it)) })

    private fun rated(vararg picks: Pair<String, FeelingStrength?>, note: Pair<String, String>? = null) = FeelingEntry(
        monday,
        picks.mapNotNull { Feeling.parse(it.first) },
        strengths = picks.mapNotNull { (token, strength) -> strength?.let { token to it } }.toMap(),
        notes = listOfNotNull(note).toMap()
    )

    /**
     * En period med styrkor och kommentarer (SCR-9, SCR-10): Lugn mest utan styrka, Rädd › Orolig oftast stark, Ledsen
     * bara svag – ordträdets segment och "Starkast kändes Rädd › Orolig.".
     */
    val strengths: FeelingStats = computeFeelingStats(
        List(3) { rated("calm.grateful" to null, "glad.content" to FeelingStrength.MILD) } +
            List(2) { rated("calm.relaxed" to FeelingStrength.MILD, "glad.content" to FeelingStrength.CLEAR) } +
            List(3) { rated("scared.anxious" to FeelingStrength.STRONG, note = "scared.anxious" to "Provsvar kommer på måndag.") } +
            listOf(
                rated("scared.anxious" to FeelingStrength.CLEAR, "angry.frustrated" to FeelingStrength.STRONG, note = "angry.frustrated" to "Bussen kom inte och jag missade tiden."),
                rated("calm.grateful" to FeelingStrength.CLEAR, "sad.tired" to FeelingStrength.MILD),
                rated("angry.frustrated" to null, "calm.present" to FeelingStrength.STRONG)
            )
    )

    /** "Du bor i Lugn och Glad. Rädd är din tredje hemvist, nästan alltid som Orolig. Stark har du knappt rört." */
    val period: FeelingStats = computeFeelingStats(
        List(4) { entry(monday, "calm.grateful", "calm.relaxed", "glad.creative") } +
            List(3) { entry(monday, "calm.present", "glad.content", "scared.anxious") } +
            listOf(
                entry(monday, "scared.anxious.tense", "sad.tired", "glad.hopeful"),
                entry(monday, "scared.insecure", "sad.lonely", "angry.irritated")
            )
    )
}

@Composable
private fun Today() {
    val today = LocalDate(2026, 10, 5)
    var done by remember { mutableIntStateOf(4) }
    var celebrate by remember { mutableStateOf(false) }
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
    // Ett loggat tillfälle med känslor efter energi och stress (HEM-21).
    AppCard { GalleryOccasionRowWithFeelings() }
    Label("SunsetBreathing · med och utan rörelse, klar")
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
        SunsetBreathing(Modifier.size(GALLERY_SUNSET_WIDTH, GALLERY_SUNSET_HEIGHT))
        SunsetBreathing(Modifier.size(GALLERY_SUNSET_WIDTH, GALLERY_SUNSET_HEIGHT), reducedMotion = true)
        SunsetBreathing(Modifier.size(GALLERY_SUNSET_WIDTH, GALLERY_SUNSET_HEIGHT), active = false, reducedMotion = false)
    }
    Label("DayDoneCard")
    DayDoneCard(6.8, 0.5, 12, play = celebrate, onConfettiFinished = { celebrate = false })
    AppButton("Fira igen", { celebrate = true }, variant = ButtonVariant.Text)
    Label("UserMenu · ansluten, utan anslutning, utloggad – tryck för menyn")
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
        UserMenu(GALLERY_ACCOUNT, {}, {}, {}, online = true, onOpenGallery = {})
        UserMenu(GALLERY_ACCOUNT, {}, {}, {}, online = false)
        UserMenu(null, {}, {}, {})
    }
}

private const val GALLERY_TOTAL = 9
private val GALLERY_ACCOUNT = AuthUser("galleri", "Anna Berg", "anna.berg@exempel.se")
private val GALLERY_SUNSET_WIDTH = 110.dp
private val GALLERY_SUNSET_HEIGHT = 200.dp

@Composable
private fun Diagrams() {
    val periods = listOf("7 dagar", "14 dagar", "Månad", "3 månader", "Allt")
    var period by remember { mutableIntStateOf(1) }
    var withPrevious by remember { mutableStateOf(false) }
    Label("LineChart")
    AppCard {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("Energi per dag", style = AppTypography.sectionTitle)
            DropdownPill(
                periods[period],
                periods.mapIndexed { i, name -> AppMenuItem(name, { period = i }, icon = if (i == period) R.drawable.ic_check else null) },
                tone = Tone.Neutral
            )
        }
        LineChart(
            listOf(ChartSeries("Energi", GalleryCharts.energy)),
            xLabels = GalleryCharts.days14,
            previous = if (withPrevious) listOf(ChartSeries("Energi", GalleryCharts.energyPrevious)) else emptyList(),
            label = "Energi per dag"
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
            bands = GalleryCharts.episodes
        )
    }
    Label("FeelingWordBars")
    AppCard { FeelingWordBars(GalleryFeelings.period.tally) }
    Label("FeelingWordBars · med styrka (#396)")
    AppCard { FeelingWordBars(GalleryFeelings.strengths.tally, strengths = true) }
    Label("LineChart · fyra serier (diagrampalett och punktform, TRD-23)")
    AppCard { FourSeriesChart() }
    Label("SparklineChart")
    AppCard { SparklineChart(GalleryCharts.week, xLabels = GalleryCharts.weekdays, label = "Energi senaste veckan", onOpenTrends = {}) }
    Label("MinMaxCaption · tomt läge")
    MinMaxCaption(3f, 8f, average = 6.2f, trend = TrendDirection.RISING)
    AppCard { LineChart(listOf(ChartSeries("Vilopuls", listOf(null, 58f, null))), label = "Vilopuls") }
}

/** Fyra serier i diagrampaletten med varsin punktform – galleriets rad för flera serier och skärmdumpens underlag. */
@Composable
internal fun FourSeriesChart() {
    val palette = AppColors.extended.chartSeries
    val names = listOf("Energi", "Stress", "Sömn", "Steg")
    LineChart(
        names.mapIndexed { i, name -> ChartSeries(name, GalleryCharts.energy.map { v -> v?.plus((1.5f - i) * 1.2f) }, palette[i]) },
        xLabels = GalleryCharts.days14,
        label = "Fyra serier"
    )
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
        IntervalPoint(5f, 6.3f, 8f)
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
        StackedPoint(listOf(1.5f, 1.9f, 4.3f, 0.3f))
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
        onClick = {}
    )
    OccasionRow("Lunch", OccasionStatus.LATE, {}, time = "12:00")
    OccasionRow("Kvällsmat", OccasionStatus.SOON, {}, time = "18:00")
    OccasionRow("Läggdags", OccasionStatus.UPCOMING, {}, time = "22:00")
}

/** Ett loggat tillfälle med energi, stress och två känslor (HEM-21, påhittade värden) – för galleriet och skärmdumpen. */
@Composable
internal fun GalleryOccasionRowWithFeelings() {
    OccasionRow(
        "Kvällsmat",
        OccasionStatus.LOGGED,
        {},
        time = "17:40",
        values = listOf(OccasionValue("Energi", 6), OccasionValue("Stress", 3, higherIsBetter = false)),
        feelings = listOf(Feeling("calm", "grateful"), Feeling("glad", "content")),
        onClick = {}
    )
}

/** En måendelogg med tre känslor, styrka och en kommentar, och en aktivitet med fem – tre och "+2" (HIST-10, SCR-9, SCR-10, påhittade värden). */
@Composable
internal fun GalleryFeelingCards() {
    DagbokenEntryCard(
        "Efter frukost",
        onClick = {},
        subtitle = "${DateFormat.time(LocalTime(8, 5))} · Stress 2",
        accent = scaleLevel(7).color,
        status = { InfoPill("Energi 7", tone = scaleLevel(7).tone) },
        onEdit = {},
        feelings = GALLERY_FEELING_POST.feelings,
        feelingExtras = FeelingExtras.shown(GALLERY_FEELING_POST)
    )
    DagbokenEntryCard(
        "Promenad",
        onClick = {},
        subtitle = "${DateFormat.time(LocalTime(16, 0))} · ${DateFormat.duration(30)}",
        accent = scaleLevel(4, -10..10).color,
        status = { InfoPill("+4", tone = scaleLevel(4, -10..10).tone) },
        onEdit = {},
        feelings = GALLERY_POST_FEELINGS
    )
}

/** Påhittade symptomalternativ för galleriet och dess skärmdumpar. */
internal val GALLERY_SYMPTOMS = listOf(
    Option("huvudvark", OptionKind.SYMPTOM, "Huvudvärk", favorite = true),
    Option("trotthet", OptionKind.SYMPTOM, "Trötthet", sortOrder = 1),
    Option("yrsel", OptionKind.SYMPTOM, "Yrsel", sortOrder = 2),
    Option("ovrigt", OptionKind.SYMPTOM, "Övrigt", sortOrder = 3)
)

/** Påhittade känslor på en post: fem, så att tre visas och resten som "+2" (HIST-10). */
internal val GALLERY_POST_FEELINGS = listOf(Feeling("calm"), Feeling("glad", "content"), Feeling("strong"), Feeling("calm", "grateful"), Feeling("sad"))

/** Påhittad måendelogg med känslornas tre styrkor och en kommentar (SCR-9, SCR-10). */
internal val GALLERY_FEELING_POST = Screening(
    "galleri",
    feelings = listOf(Feeling("calm", "grateful"), Feeling("scared", "anxious", "tense"), Feeling("glad")),
    feelingStrengths = mapOf("calm.grateful" to "clear", "scared.anxious.tense" to "strong", "glad" to "mild"),
    feelingNotes = mapOf("calm.grateful" to "Promenad i solen efter lunchen")
)

internal val GALLERY_FEELINGS = listOf(Feeling("calm", "grateful"), Feeling("scared", "anxious", "tense"), Feeling("glad"))

/** Aktivitetstyperna i galleriet: två stjärnmärkta som chips, resten under "Fler typer" med Övrigt sist. */
private val GALLERY_TYPES = TypeChoices(
    favorites = listOf(Option("promenad", OptionKind.ACTIVITY, "Promenad", favorite = true), Option("jobb", OptionKind.ACTIVITY, "Jobb", favorite = true)),
    more = listOf(Option("vila", OptionKind.ACTIVITY, "Vila", sortOrder = 2)),
    other = "ovrigt"
)
