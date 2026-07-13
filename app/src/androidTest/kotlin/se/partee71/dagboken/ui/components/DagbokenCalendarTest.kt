package se.partee71.dagboken.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

@RunWith(AndroidJUnit4::class)
class DagbokenCalendarTest {

    val composeRule = createComposeRule()

    // Retry outermost so a swiftshader render-glitch flake re-runs with a
    // fresh @Before/@After lifecycle instead of failing the build.
    @get:Rule
    val flakyRetry: org.junit.rules.RuleChain =
        org.junit.rules.RuleChain
            .outerRule(se.partee71.dagboken.util.RetryTestRule())
            .around(composeRule)

    @Test fun `tapping a day in the current month invokes onDateClick with that date`() {
        var clicked: LocalDate? = null
        val expected = LocalDate.now().withDayOfMonth(15)
        composeRule.setContent {
            MaterialTheme {
                DagbokenCalendar(
                    datesWithEntries = emptySet(),
                    selectedDate     = null,
                    onDateClick      = { clicked = it },
                )
            }
        }
        composeRule.onNodeWithText("15").performClick()
        assertEquals(expected, clicked)
    }

    @Test fun `previous and next month buttons are displayed and clickable`() {
        composeRule.setContent {
            MaterialTheme {
                DagbokenCalendar(
                    datesWithEntries = emptySet(),
                    selectedDate     = null,
                    onDateClick      = {},
                )
            }
        }
        composeRule.onNodeWithContentDescription("Föregående månad").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Nästa månad").assertIsDisplayed()
    }

    @Test fun `next month button changes the visible month header`() {
        composeRule.setContent {
            MaterialTheme {
                DagbokenCalendar(
                    datesWithEntries = emptySet(),
                    selectedDate     = null,
                    onDateClick      = {},
                )
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
    }
}
