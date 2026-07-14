package se.partee71.dagboken.ui.components

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import se.partee71.dagboken.util.retryOnRenderGlitch
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

// Migrerad enligt POC i #112 — se SjukdomarScreenTest för fullständig förklaring.
// Varje test skriver retryOnRenderGlitch direkt (utan en delad renderAndRetry-hjälpare)
// eftersom de flesta fångar lokalt muterbart state i closures — variabeln måste då
// deklareras om inuti varje försök, annars kan ett stale värde från ett glitchat
// tidigare försök läcka in i en senare attempts assertion.
@RunWith(AndroidJUnit4::class)
class DagbokenCalendarTest {

    @get:Rule
    val composeRule = createEmptyComposeRule()

    @Test fun `tapping a day in the current month invokes onDateClick with that date`() = retryOnRenderGlitch {
        var clicked: LocalDate? = null
        val expected = LocalDate.now().withDayOfMonth(15)
        val scenario = ActivityScenario.launch(ComponentActivity::class.java)
        try {
            scenario.onActivity {
                it.setContent {
                    MaterialTheme {
                        DagbokenCalendar(
                            datesWithEntries = emptySet(),
                            selectedDate     = null,
                            onDateClick      = { clicked = it },
                        )
                    }
                }
            }
            composeRule.onNodeWithText("15").performClick()
            assertEquals(expected, clicked)
        } finally {
            scenario.close()
        }
    }

    @Test fun `previous and next month buttons are displayed and clickable`() = retryOnRenderGlitch {
        val scenario = ActivityScenario.launch(ComponentActivity::class.java)
        try {
            scenario.onActivity {
                it.setContent {
                    MaterialTheme {
                        DagbokenCalendar(
                            datesWithEntries = emptySet(),
                            selectedDate     = null,
                            onDateClick      = {},
                        )
                    }
                }
            }
            composeRule.onNodeWithContentDescription("Föregående månad").assertIsDisplayed()
            composeRule.onNodeWithContentDescription("Nästa månad").assertIsDisplayed()
        } finally {
            scenario.close()
        }
    }

    @Test fun `next month button changes the visible month header`() = retryOnRenderGlitch {
        val scenario = ActivityScenario.launch(ComponentActivity::class.java)
        try {
            scenario.onActivity {
                it.setContent {
                    MaterialTheme {
                        DagbokenCalendar(
                            datesWithEntries = emptySet(),
                            selectedDate     = null,
                            onDateClick      = {},
                        )
                    }
                }
            }
            val locale = Locale("sv")
            val currentHeader = LocalDate.now().let { "${it.month.getDisplayName(TextStyle.FULL, locale)} ${it.year}" }
            val nextHeader = LocalDate.now().plusMonths(1)
                .let { "${it.month.getDisplayName(TextStyle.FULL, locale)} ${it.year}" }

            composeRule.onNodeWithText(currentHeader).assertIsDisplayed()

            composeRule.onNodeWithContentDescription("Nästa månad").performClick()
            composeRule.waitForIdle()

            composeRule.onNodeWithText(nextHeader).assertIsDisplayed()
            composeRule.onNodeWithText(currentHeader).assertDoesNotExist()
        } finally {
            scenario.close()
        }
    }
}
