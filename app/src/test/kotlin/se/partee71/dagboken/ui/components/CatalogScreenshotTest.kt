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
import se.partee71.dagboken.testing.captureLightAndDark
import se.partee71.dagboken.ui.theme.AppColors
import se.partee71.dagboken.ui.theme.AppTypography
import se.partee71.dagboken.ui.theme.Spacing
import se.partee71.dagboken.ui.theme.Tone

/**
 * Skärmdumpar (ljust + mörkt) av katalogens komponenter i vila – en bild per komponent med
 * dess viktigaste lägen. Popup-komponenter och ramar fotograferas i sina egna tester.
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
        captureLightAndDark("DateField_lagen") {
            Sheet {
                DateField("Datum", LocalDate(2026, 10, 4), {})
                DateField("Startdatum", null, {}, error = "Välj ett startdatum")
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
    fun `laddning och navigering`() {
        captureLightAndDark("AppLoading_standard") { Box(Modifier.background(MaterialTheme.colorScheme.background).padding(Spacing.xxl)) { AppLoading() } }
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
}
