package se.partee71.dagboken.ui.components

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
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

@RunWith(AndroidJUnit4::class)
class DagbokenScaffoldTest {

    val composeRule = createComposeRule()

    // Retry outermost so a swiftshader render-glitch flake re-runs with a
    // fresh @Before/@After lifecycle instead of failing the build.
    @get:Rule
    val flakyRetry: org.junit.rules.RuleChain =
        org.junit.rules.RuleChain
            .outerRule(se.partee71.dagboken.util.RetryTestRule())
            .around(composeRule)

    @Test fun `title is displayed`() {
        composeRule.setContent {
            MaterialTheme {
                DagbokenScaffold(title = "Inställningar") { }
            }
        }
        composeRule.onNodeWithText("Inställningar").assertIsDisplayed()
    }

    @Test fun `no back icon shown when onBack is not provided`() {
        composeRule.setContent {
            MaterialTheme {
                DagbokenScaffold(title = "Utan tillbaka") { }
            }
        }
        composeRule.onNodeWithContentDescription("Tillbaka").assertDoesNotExist()
    }

    @Test fun `back icon has content description and invokes onBack when clicked`() {
        var backClicked = false
        composeRule.setContent {
            MaterialTheme {
                DagbokenScaffold(title = "Med tillbaka", onBack = { backClicked = true }) { }
            }
        }
        composeRule.onNodeWithContentDescription("Tillbaka").assertIsDisplayed().performClick()
        assertEquals(true, backClicked)
    }

    @Test fun `custom navigationIcon overrides the default back button`() {
        composeRule.setContent {
            MaterialTheme {
                DagbokenScaffold(
                    title = "Egen ikon",
                    onBack = {},
                    navigationIcon = { Text("Konto") },
                ) { }
            }
        }
        composeRule.onNodeWithText("Konto").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Tillbaka").assertDoesNotExist()
    }

    @Test fun `floatingActionButton is displayed and clickable`() {
        var fabClicked = false
        composeRule.setContent {
            MaterialTheme {
                DagbokenScaffold(
                    title = "Med FAB",
                    floatingActionButton = {
                        FloatingActionButton(onClick = { fabClicked = true }) {
                            Text("+")
                        }
                    },
                ) { }
            }
        }
        composeRule.onNodeWithText("+").performClick()
        assertEquals(true, fabClicked)
    }

    @Test fun `content is rendered with the provided padding`() {
        composeRule.setContent {
            MaterialTheme {
                DagbokenScaffold(title = "Innehåll") { padding ->
                    Text("Skärminnehåll", modifier = Modifier.padding(padding))
                }
            }
        }
        composeRule.onNodeWithText("Skärminnehåll").assertIsDisplayed()
    }
}
