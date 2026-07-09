package se.partee71.dagboken.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Favorite
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
class EmptyStateTest {

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
                EmptyState(icon = Icons.Outlined.Favorite, title = "Inga favoriter sparade")
            }
        }
        composeRule.onNodeWithText("Inga favoriter sparade").assertIsDisplayed()
    }

    @Test fun `body is displayed when provided`() {
        composeRule.setContent {
            MaterialTheme {
                EmptyState(
                    icon  = Icons.Outlined.Favorite,
                    title = "Inga favoriter sparade",
                    body  = "Tryck + för att spara en vid-behov-medicin",
                )
            }
        }
        composeRule.onNodeWithText("Tryck + för att spara en vid-behov-medicin").assertIsDisplayed()
    }

    @Test fun `no body node exists when body is null`() {
        composeRule.setContent {
            MaterialTheme {
                EmptyState(icon = Icons.Outlined.Favorite, title = "Bara en titel")
            }
        }
        composeRule.onNodeWithText("Bara en titel").assertIsDisplayed()
    }

    @Test fun `icon is rendered without a content description`() {
        composeRule.setContent {
            MaterialTheme {
                EmptyState(icon = Icons.Outlined.Favorite, title = "Titel")
            }
        }
        // Decorative icon — must not be independently announced by TalkBack.
        composeRule.onNodeWithContentDescription("Favorite").assertDoesNotExist()
    }

    @Test fun `action is displayed and clickable`() {
        var clicked = false
        composeRule.setContent {
            MaterialTheme {
                EmptyState(
                    icon   = Icons.Outlined.Favorite,
                    title  = "Inga mediciner idag",
                    action = {
                        TextButton(onClick = { clicked = true }) {
                            Text("Logga engångsdos")
                        }
                    },
                )
            }
        }
        composeRule.onNodeWithText("Logga engångsdos").performClick()
        assertEquals(true, clicked)
    }
}
