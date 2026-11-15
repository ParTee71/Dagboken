package se.kodhast.dagboken.ui.theme

import androidx.compose.foundation.layout.Column
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import se.kodhast.dagboken.R
import se.kodhast.ui.components.EmptyState
import se.kodhast.ui.components.SectionHeader

/**
 * Kodhäst-igenkänningen (DSN-8) slås på i [DagbokenTheme]: prickar framför sektionsrubriker utan ikonruta och hästen
 * i fullstora tomma lägen, men inte i rubriker med ikonruta, kompakta tomma lägen eller felvyer. Laddningens text för
 * skärmläsare prövas i FramesTest ("Laddar").
 */
@RunWith(RobolectricTestRunner::class)
class BrandStyleTest {

    @get:Rule
    val rule = createComposeRule()

    @Test
    fun `sektionsrubrik utan ikonruta får prickarna`() {
        rule.setContent { DagbokenTheme { SectionHeader("Trender") } }
        rule.onNodeWithTag(SECTION_DOTS, useUnmergedTree = true).assertExists()
        rule.onNodeWithText("Trender").assertExists()
    }

    @Test
    fun `sektionsrubrik med ikonruta får inga prickar`() {
        rule.setContent { DagbokenTheme { SectionHeader("Veckan", icon = R.drawable.ic_calendar) } }
        rule.onNodeWithTag(SECTION_DOTS, useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun `fullstort tomt läge visar hästen`() {
        rule.setContent { DagbokenTheme { EmptyState(R.drawable.ic_book, "Inget loggat", "Logga något på Idag.") } }
        rule.onNodeWithTag(EMPTY_STATE_MARK, useUnmergedTree = true).assertExists()
    }

    @Test
    fun `kompakt tomt läge och felvy visar inte hästen`() {
        rule.setContent {
            DagbokenTheme {
                Column {
                    EmptyState(R.drawable.ic_book, "Inget loggat", "Logga något på Idag.", compact = true)
                    EmptyState(R.drawable.ic_wifi_off, "Kunde inte hämta", "Försök igen.", isError = true)
                }
            }
        }
        rule.onNodeWithTag(EMPTY_STATE_MARK, useUnmergedTree = true).assertDoesNotExist()
    }

    private companion object {
        // android-ui:s testtaggar (internal där).
        const val SECTION_DOTS = "section-dots"
        const val EMPTY_STATE_MARK = "empty-state-mark"
    }
}
