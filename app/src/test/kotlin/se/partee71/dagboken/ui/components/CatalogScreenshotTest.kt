package se.partee71.dagboken.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import se.partee71.dagboken.R
import se.partee71.dagboken.core.model.OptionKind
import se.partee71.dagboken.core.model.Option
import se.partee71.dagboken.core.engine.TypeChoices
import se.partee71.dagboken.core.model.SymptomScore
import se.partee71.dagboken.ui.common.DateFormat
import se.partee71.dagboken.ui.common.color
import se.partee71.dagboken.ui.common.durationText
import se.partee71.dagboken.ui.common.scaleLevel
import se.partee71.dagboken.testing.captureLightAndDark
import se.partee71.dagboken.ui.theme.AppColors
import se.partee71.dagboken.ui.theme.AppTypography
import se.partee71.dagboken.ui.theme.Spacing
import se.partee71.dagboken.ui.theme.Tone

/**
 * Skärmdumpar (ljust + mörkt) av katalogens komponenter i vila – en bild per komponent med
 * dess viktigaste lägen. Popup-komponenter och ramar fotograferas i sina egna tester, och det som
 * animerar utan slut (`AppLoading`) med pausad klocka i `ComponentsTest`.
 */
@RunWith(RobolectricTestRunner::class)
class CatalogScreenshotTest {


    @Composable
    private fun Sheet(content: @Composable ColumnScope.() -> Unit) {
        Column(
            Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.background).padding(Spacing.l),
            verticalArrangement = Arrangement.spacedBy(Spacing.m),
            content = content,
        )
    }

    @Test
    fun `knappar och meny`() {
        captureLightAndDark("AppButton_destruktiv_och_kompakt") {
            Sheet {
                AppButton("Radera", {}, variant = ButtonVariant.Destructive)
                AppButton("Spara", {}, compact = true)
            }
        }
        captureLightAndDark("AppIconButton_varianter") {
            Sheet {
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                    AppIconButton(R.drawable.ic_arrow_back, "Tillbaka", {}, variant = IconButtonVariant.Tonal)
                    AppIconButton(R.drawable.ic_more_vert, "Fler val", {})
                    AppIconButton(R.drawable.ic_add, "Lägg till", {}, variant = IconButtonVariant.Tonal, enabled = false)
                }
            }
        }
        captureLightAndDark("AddSplitButton_varianter") {
            Sheet {
                AddSplitButton("Lägg till", {}, menuItems = listOf(AppMenuItem("Logga dos i efterhand", {})))
                AddSplitButton("Nytt recept", {})
            }
        }
    }

    @Test
    fun `kort, rader och rubriker`() {
        captureLightAndDark("AppCard_rader") {
            Sheet {
                AppCard {
                    SectionHeader("Mediciner", icon = R.drawable.ic_pill, count = "2 / 3")
                    ItemRow("Levaxin 50 µg", subtitle = "07:00 · 1 tablett")
                    AppDivider()
                    ItemRow("D-vitamin", subtitle = "08:00 · 1 kapsel")
                }
            }
        }
        captureLightAndDark("ItemRow_varianter") {
            Sheet {
                AppCard {
                    ItemRow("Promenad", subtitle = "08:30 · 45 min", leading = { Text("🚶", style = AppTypography.sectionTitle) }, navigates = true, onClick = {})
                    ItemRow("Huvudvärk", subtitle = "Svårighetsgrad 4", trailing = { InfoPill("ändrad") })
                    ItemRow("Förkylning", accent = AppColors.swatch(2), tinted = true)
                    ItemRow("Kvällsmedicin", done = true)
                }
            }
        }
        captureLightAndDark("CheckRow_lagen") {
            Sheet {
                AppCard {
                    CheckRow("Levaxin 50 µg", false, {}, subtitle = "07:00 · 1 tablett", trailing = { InfoPill("Morgon", tone = Tone.Neutral) })
                    CheckRow("D-vitamin", true, {}, subtitle = "08:00 · 1 kapsel")
                    CheckRow("Alvedon 500 mg", false, {}, subtitle = "vid behov", onClick = {}, accent = AppColors.swatch(1), trailing = { InfoPill("Snart", tone = Tone.Sun) })
                }
            }
        }
        captureLightAndDark("SwitchRow_lagen") {
            Sheet {
                AppCard {
                    SwitchRow("Medicinpåminnelser", true, {}, subtitle = "6 tider om dagen")
                    SwitchRow("Visa arkiverade", false, {})
                    SwitchRow("Måendepåminnelse", true, {}, subtitle = "Frukost · 08:00", onClick = {})
                }
            }
        }
        captureLightAndDark("SectionHeader_toner") {
            Sheet {
                SectionHeader("Mediciner", icon = R.drawable.ic_pill, count = "2 / 3")
                SectionHeader("Mående", icon = R.drawable.ic_mood, count = "4 / 4", tone = Tone.Positive)
                SectionHeader("Pågående sjukdom", count = "1", tone = Tone.Warning)
            }
        }
        captureLightAndDark("SuggestionField_forslag") {
            Sheet {
                SuggestionField(
                    "Alved",
                    {},
                    "Namn",
                    listOf(Suggestion("Alvedon 500 mg", "Filmdragerad tablett", 0..4), Suggestion("Alvedon forte 1 g", "Filmdragerad tablett", 0..4)),
                    {},
                    footer = "Från dina vid behov-mediciner · fortsätt skriva om din inte finns",
                )
            }
        }
        captureLightAndDark("InfoPill_toner") {
            Sheet {
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                    InfoPill("Mediciner")
                    InfoPill("Klar", tone = Tone.Positive)
                    InfoPill("Snart", tone = Tone.Sun)
                    InfoPill("Försenad", tone = Tone.Warning)
                    InfoPill("Morgon", tone = Tone.Neutral)
                }
            }
        }
        captureLightAndDark("InfoPill_ikon_klickbar") {
            Sheet { InfoPill("Väntar på synk", tone = Tone.Neutral, icon = R.drawable.ic_cloud_upload, onClick = {}) }
        }
        captureLightAndDark("GroupLabel_over_kort") { Sheet { GroupLabel("Utseende"); AppCard { ItemRow("Tema") } } }
        captureLightAndDark("AppDivider_i_kort") { Sheet { AppCard { ItemRow("Promenad"); AppDivider(); ItemRow("Yoga") } } }
        captureLightAndDark("PausableRow_aktiv_och_pausad") {
            Sheet {
                AppCard {
                    PausableRow("Levaxin 50 µg", "1 tablett · morgon", "Dagligen", active = true, onClick = {})
                    AppDivider()
                    PausableRow("Prednisolon 5 mg", "2 tabletter · morgon", "Kur", active = false, onClick = {})
                }
            }
        }
        captureLightAndDark("ItemRow_pill_och_pausad") {
            Sheet {
                AppCard {
                    ItemRow("Levaxin 50 µg", subtitle = "1 tablett dagligen", trailing = { InfoPill("Dagligen", tone = Tone.Neutral) }, navigates = true, onClick = {})
                    AppDivider()
                    ItemRow(
                        "Prednisolon 5 mg",
                        subtitle = "Pausat – räknas inte med",
                        trailing = { InfoPill("Pausat", tone = Tone.Neutral) },
                        navigates = true,
                        onClick = {},
                        inactive = true,
                    )
                }
            }
        }
    }

    @Test
    fun `fält och val`() {
        captureLightAndDark("AppTextField_lagen") {
            Sheet {
                AppTextField("Promenad", {}, "Namn")
                AppTextField("Gick i skogen med hunden", {}, "Anteckning", helper = "Visas i dagboken")
                AppTextField("", {}, "Namn", error = "Ange ett namn")
            }
        }
        captureLightAndDark("AppTextField_suffix") {
            Sheet {
                AppTextField("25", {}, "Höjning", suffix = "mg")
                AppTextField("", {}, "Höjning", error = "Varje doshöjning måste ha ett värde.", suffix = "mg")
            }
        }
        captureLightAndDark("UnitChoice_val") {
            Sheet {
                UnitChoice("mg", {})
                UnitChoice("tablett", {})
            }
        }
        captureLightAndDark("DateField_lagen") {
            Sheet {
                DateField("Datum", LocalDate(2026, 10, 4), {})
                DateField("Startdatum", null, {}, error = "Välj ett startdatum")
            }
        }
        captureLightAndDark("DateField_tomtext") {
            Sheet {
                DateField("Slutdatum", null, {}, emptyLabel = "Periodens slut", onClear = {})
                DateField("Slutdatum", LocalDate(2026, 10, 12), {}, emptyLabel = "Periodens slut", onClear = {})
            }
        }
        captureLightAndDark("TimeField_lagen") {
            Sheet {
                TimeField("Tid", LocalTime(8, 30), {})
                TimeField("Påminn kl.", LocalTime(21, 0), {}, error = "Välj en tid")
            }
        }
        captureLightAndDark("AppFilterChip_val") {
            Sheet {
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                    AppFilterChip("Alla", true, {})
                    AppFilterChip("Mående", false, {})
                    AppFilterChip("Sjukdom", false, {}, enabled = false)
                }
            }
        }
        captureLightAndDark("ChipRow_varianter") {
            Sheet {
                ChipRow { listOf("Alla", "Mående", "Aktiviteter", "Doser", "Händelser", "Sjukdom").forEachIndexed { i, c -> AppFilterChip(c, i == 0, {}) } }
                ChipRow(wrap = false) { listOf("Frukost", "Lunch", "Middag", "Kväll").forEachIndexed { i, c -> AppFilterChip(c, i < 2, {}) } }
            }
        }
        captureLightAndDark("ExampleChips_valt") { Sheet { ExampleChips(listOf("Promenad", "Yoga", "Städning", "Cykling"), {}, chosen = "Yoga") } }
        captureLightAndDark("ChoiceChips_val") {
            Sheet { ChoiceChips(listOf("Frukost", "Lunch", "Middag", "Kväll"), "Middag", {}, { it }) }
        }
        captureLightAndDark("LabeledGroup_form") {
            Sheet {
                LabeledGroup("Tillfälle") { ChipRow { listOf("Frukost", "Lunch", "Middag").forEachIndexed { i, c -> AppFilterChip(c, i == 0, {}) } } }
                LabeledGroup("Snabbval", helper = "Fyller i namnet – resten väljer du själv") { ChipRow { AppFilterChip("Promenad", false, {}) } }
            }
        }
        captureLightAndDark("FieldError_sparfel") { Sheet { FieldError("Ingen anslutning just nu. Det du sparar skickas när nätet är tillbaka.") } }
        captureLightAndDark("EmojiPicker_val") { Sheet { EmojiPicker("🧘", {}) } }
        captureLightAndDark("ColorSwatchPicker_val") { Sheet { ColorSwatchPicker(AppColors.SWATCH_HEX[3], {}) } }
        captureLightAndDark("AppSegmentedChoice_tema") { Sheet { AppSegmentedChoice(listOf("Ljust", "Mörkt", "Auto"), 2, {}) } }
        captureLightAndDark("QuantityStepper_varde") {
            Sheet {
                QuantityStepper(45, {}, "minuter")
                QuantityStepper(0, {}, "tabletter", range = 0..9)
            }
        }
    }

    @Test
    fun `navigering`() {
        captureLightAndDark("AppTopBar_storlekar") {
            Column(Modifier.background(MaterialTheme.colorScheme.background)) {
                AppTopBar("Idag") { AppMenu(emptyList()) }
                AppTopBar("Promenad", size = TopBarSize.Small, onBack = {}) { AppButton("Spara", {}, compact = true) }
            }
        }
        captureLightAndDark("AppTopBar_synk") {
            CompositionLocalProvider(LocalSyncIndicator provides SyncIndicator(pending = true)) {
                Column(Modifier.background(MaterialTheme.colorScheme.background)) {
                    AppTopBar("Idag") { AppMenu(emptyList()) }
                    AppTopBar("Förkylning", size = TopBarSize.Small, onBack = {}) { AppIconButton(R.drawable.ic_edit, "Redigera", {}) }
                }
            }
        }
        val tabs = listOf(
            ToolbarItem("Idag", R.drawable.ic_sun),
            ToolbarItem("Dagbok", R.drawable.ic_book),
            ToolbarItem("Trender", R.drawable.ic_trend),
            ToolbarItem("Mediciner", R.drawable.ic_pill),
        )
        captureLightAndDark("AppFloatingToolbar_flikar") {
            Box(Modifier.background(MaterialTheme.colorScheme.background).padding(Spacing.l)) {
                AppFloatingToolbar(tabs, selectedIndex = 1, onSelect = {})
            }
        }
        captureLightAndDark("AppFloatingToolbar_plusknapp") {
            Box(Modifier.background(MaterialTheme.colorScheme.background).padding(Spacing.l)) {
                AppFloatingToolbar(tabs, selectedIndex = 3, onSelect = {}, action = ToolbarAction("Logga", R.drawable.ic_add) {})
            }
        }
    }

    @Test
    fun `poster, reglage och kalender`() {
        captureLightAndDark("DagbokenEntryCard_lagen") {
            Sheet {
                DagbokenEntryCard(
                    "Promenad",
                    onClick = {},
                    subtitle = "${DateFormat.time(LocalTime(8, 30))} · ${durationText(45)} · återhämtande",
                    leading = { Text("🚶", style = AppTypography.sectionTitle) },
                    accent = scaleLevel(3, -10..10).color,
                    status = { InfoPill("+3", tone = Tone.Sun) },
                    note = "Gick i skogen med hunden.",
                    expandedContent = { Text("Huvudvärk 2", style = AppTypography.body) },
                    onEdit = {},
                    delete = DeleteAction("Radera?", "") {},
                )
                DagbokenEntryCard("Mående · frukost", onClick = {}, subtitle = "Energi 7 · stress 3", accent = scaleLevel(7).color, onEdit = {})
                DagbokenEntryCard("Prednisolon 5 mg", onClick = {}, subtitle = "Avslutat 30 sep", inactive = true, onEdit = {})
            }
        }
        captureLightAndDark("DagbokenEntryCard_reglage_och_pills") {
            Sheet {
                DagbokenEntryCard(
                    "Sertralin 50 mg",
                    onClick = {},
                    subtitle = "Morgon · dagligen · tills vidare",
                    accent = MaterialTheme.colorScheme.primary,
                    note = "Tas med frukost.",
                    expandedContent = { Text("29 sep – 12 okt: +25 mg", style = AppTypography.body) },
                    onEdit = {},
                    delete = DeleteAction("Ta bort?", "") {},
                    toggle = EntryToggle(true, {}, "Sertralin aktivt"),
                    below = {
                        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                            InfoPill("Idag 75 mg (+25)")
                            InfoPill("Höjning 29 sep – 12 okt", tone = Tone.Neutral)
                        }
                    },
                )
                DagbokenEntryCard(
                    "Levaxin 100 µg",
                    onClick = {},
                    subtitle = "Morgon · dagligen · tills vidare",
                    accent = MaterialTheme.colorScheme.outline,
                    onEdit = {},
                    inactive = true,
                    toggle = EntryToggle(false, {}, "Levaxin aktivt"),
                )
            }
        }
        captureLightAndDark("NoticeBanner_varning") {
            Sheet {
                NoticeBanner("Kåvepenin slutar i morgon. Höjningen av Sertralin slutar i morgon – sedan 50 mg.", R.drawable.ic_bell, {})
                NoticeBanner("Ändringar väntar på att synkas.", R.drawable.ic_cloud_upload, {}, tone = Tone.Neutral)
                NoticeBanner("Health Connect saknas", R.drawable.ic_clock, onClick = null, detail = "Klockans data visas när Health Connect är kopplat.")
            }
        }
        captureLightAndDark("FavoriteStar_lagen") {
            Sheet {
                AppCard {
                    ItemRow("Alvedon 500 mg", subtitle = "Minst 4 h mellan · högst 8 per dag", navigates = true, onClick = {}, trailing = { FavoriteStar("Alvedon", true, {}) })
                    ItemRow("Loratadin 10 mg", subtitle = "Ingen gräns", navigates = true, onClick = {}, trailing = { FavoriteStar("Loratadin", false, {}) })
                }
            }
        }
        captureLightAndDark("Foldout_lagen") {
            Sheet {
                AppCard { Foldout("Mätvärden", false, {}, summary = "Energi 7 · stress 3") {} }
                AppCard { Foldout("Diagram", true, {}, trailing = { InfoPill("30 dagar", tone = Tone.Neutral) }) { Text("Innehållet", style = AppTypography.body) } }
            }
        }
        captureLightAndDark("ValueSlider_riktningar") {
            Sheet {
                AppCard {
                    ValueSlider("Energi", 2, {})
                    ValueSlider("Sömnkvalitet", 8, {})
                    ValueSlider("Stress", 5, {}, higherIsBetter = false)
                    ValueSlider("Smärta", 9, {}, higherIsBetter = false)
                    ValueSlider("Aktivitetens energi", -4, {}, valueRange = -10..10)
                    ValueSlider("Aktivitetens energi", 6, {}, valueRange = -10..10, enabled = false)
                }
            }
        }
        captureLightAndDark("WheelPicker_vald") {
            Sheet {
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.l)) {
                    WheelPicker((0..23).map { it.toString() }, 1, {}, "timmar")
                    WheelPicker((0..55 step 5).map { it.toString() }, 6, {}, "minuter")
                }
            }
        }
        captureLightAndDark("DagbokenCalendar_oktober") {
            Sheet {
                AppCard {
                    DagbokenCalendar(
                        LocalDate(2026, 10, 1),
                        {},
                        setOf(LocalDate(2026, 10, 1), LocalDate(2026, 10, 2), LocalDate(2026, 10, 4), LocalDate(2026, 10, 12)),
                        LocalDate(2026, 10, 4),
                        {},
                        today = LocalDate(2026, 10, 5),
                    )
                }
            }
        }
        captureLightAndDark("DagbokenCalendar_framtid_nedtonad") {
            Sheet {
                AppCard {
                    DagbokenCalendar(
                        LocalDate(2026, 10, 1),
                        {},
                        setOf(LocalDate(2026, 10, 1), LocalDate(2026, 10, 3), LocalDate(2026, 10, 5)),
                        LocalDate(2026, 10, 3),
                        {},
                        today = LocalDate(2026, 10, 5),
                        dimFuture = true,
                    )
                }
            }
        }
        captureLightAndDark("StepwiseScreeningForm_forsta_steget") {
            Sheet { AppCard { StepwiseScreeningForm(6, {}, 4, {}, GALLERY_SYMPTOMS, emptyList(), {}, onSave = {}) } }
        }
        captureLightAndDark("SymptomLogCard_valda") {
            Sheet {
                SymptomLogCard(GALLERY_SYMPTOMS, listOf(SymptomScore("huvudvark", 4), SymptomScore("ovrigt", 7, "Ont i knät")), {}, otherOptionId = "ovrigt")
                SymptomLogCard(GALLERY_SYMPTOMS, emptyList(), {})
            }
        }
        captureLightAndDark("NoteField_lagen") {
            Sheet {
                AppCard { NoteField("", {}) }
                AppCard { NoteField("Sov dåligt, vaknade vid fyra. Tog en promenad före frukost och mådde bättre efteråt.", {}) }
                AppCard { NoteField("Sov dåligt.", {}, initiallyExpanded = true) }
            }
        }
        captureLightAndDark("StatPill_toner") {
            Sheet {
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                    StatPill(R.drawable.ic_activity, "7 842", "Steg")
                    StatPill(R.drawable.ic_sun, "7 h 45 min", "Sömn", tone = Tone.Primary)
                }
                StatPill(R.drawable.ic_thermometer, "—", "Puls", tone = Tone.Warning, onClick = {}, onClickLabel = "Begär åtkomst")
            }
        }
        captureLightAndDark("DateTimeRow_standard") { Sheet { DateTimeRow(LocalDate(2026, 10, 4), LocalTime(8, 30), {}, {}) } }
        captureLightAndDark("DurationRow_snabbval") { Sheet { DurationRow(60, {}) } }
        captureLightAndDark("TypeChoiceField_lagen") {
            Sheet {
                val types = TypeChoices(
                    listOf(Option("walk", OptionKind.ACTIVITY, "Promenad", favorite = true), Option("work", OptionKind.ACTIVITY, "Jobb", favorite = true)),
                    listOf(Option("rest", OptionKind.ACTIVITY, "Vila")),
                    other = "other",
                )
                TypeChoiceField(types, "walk", {}, otherLabel = "Övrigt")
                TypeChoiceField(types, "other", {}, otherLabel = "Övrigt")
                TypeChoiceField(types, "", {}, error = "Välj en typ")
                TypeChoiceField(TypeChoices(emptyList(), emptyList()), "", {})
            }
        }
        captureLightAndDark("NoteField_fel") {
            Sheet { AppCard { NoteField("Sov dåligt.", {}, error = "Värdet går inte att spara – korta eller ändra det") } }
        }
        captureLightAndDark("ReminderTimeRow_lagen") {
            Sheet {
                AppCard {
                    ReminderTimeRow("Efter frukost", LocalTime(8, 0), {}, enabled = true, onEnabledChange = {})
                    ReminderTimeRow("Läggdags", LocalTime(22, 0), {}, enabled = false, onEnabledChange = {})
                    ReminderTimeRow("Periodslut", LocalTime(9, 0), {})
                }
            }
        }
    }
}
