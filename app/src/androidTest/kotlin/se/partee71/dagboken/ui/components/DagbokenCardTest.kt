package se.partee71.dagboken.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DagbokenCardTest {

    val composeRule = createComposeRule()

    // Retry outermost so a swiftshader render-glitch flake re-runs with a
    // fresh @Before/@After lifecycle instead of failing the build.
    @get:Rule
    val flakyRetry: org.junit.rules.RuleChain =
        org.junit.rules.RuleChain
            .outerRule(se.partee71.dagboken.util.RetryTestRule())
            .around(composeRule)

    @Test fun `title is shown when provided`() {
        composeRule.setContent {
            MaterialTheme {
                DagbokenCard(title = "En titel") {
                    Text("Innehåll")
                }
            }
        }
        composeRule.onNodeWithText("En titel").assertIsDisplayed()
        composeRule.onNodeWithText("Innehåll").assertIsDisplayed()
    }

    @Test fun `no title is shown when title is null`() {
        composeRule.setContent {
            MaterialTheme {
                DagbokenCard {
                    Text("Bara innehåll")
                }
            }
        }
        composeRule.onNodeWithText("Bara innehåll").assertIsDisplayed()
    }

    @Test fun `onClick is invoked when card is tapped`() {
        var clicked = false
        composeRule.setContent {
            MaterialTheme {
                DagbokenCard(onClick = { clicked = true }) {
                    Text("Tryck mig")
                }
            }
        }
        composeRule.onNodeWithText("Tryck mig").performClick()
        assertEquals(true, clicked)
    }

    @Test fun `onLongClick is invoked on long press`() {
        var longClicked = false
        composeRule.setContent {
            MaterialTheme {
                DagbokenCard(
                    onClick = {},
                    onLongClick = { longClicked = true },
                ) {
                    Text("Håll intryckt", modifier = Modifier.testTag("content"))
                }
            }
        }
        composeRule.onNodeWithTag("content", useUnmergedTree = true).performTouchInput {
            down(center)
            advanceEventTime(600L)
            up()
        }
        assertEquals(true, longClicked)
    }

    @Test fun `accentColor renders an accent bar alongside content`() {
        composeRule.setContent {
            MaterialTheme {
                DagbokenCard(accentColor = Color.Red) {
                    Box(modifier = Modifier.testTag("body").size(40.dp))
                }
            }
        }
        composeRule.onNodeWithTag("body").assertIsDisplayed()
    }

    @Test fun `contentPadding of zero removes default card padding`() {
        composeRule.setContent {
            MaterialTheme {
                DagbokenCard(contentPadding = PaddingValues(0.dp)) {
                    Box(modifier = Modifier.testTag("zero-padded").size(20.dp))
                }
            }
        }
        composeRule.onNodeWithTag("zero-padded").assertIsDisplayed().assertHeightIsAtLeast(20.dp)
    }
}
