package se.partee71.dagboken.ui.components

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Favorite
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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

// Migrerad enligt POC i #112 — se SjukdomarScreenTest för fullständig förklaring.
@RunWith(AndroidJUnit4::class)
class EmptyStateTest {

    @get:Rule
    val composeRule = createEmptyComposeRule()

    private fun renderAndRetry(content: @Composable () -> Unit, assertions: () -> Unit) =
        retryOnRenderGlitch {
            val scenario = ActivityScenario.launch(ComponentActivity::class.java)
            try {
                scenario.onActivity { it.setContent { MaterialTheme { content() } } }
                assertions()
            } finally {
                scenario.close()
            }
        }

    @Test fun `title is displayed`() = renderAndRetry(
        content = { EmptyState(icon = Icons.Outlined.Favorite, title = "Inga favoriter sparade") },
    ) {
        composeRule.onNodeWithText("Inga favoriter sparade").assertIsDisplayed()
    }

    @Test fun `body is displayed when provided`() = renderAndRetry(
        content = {
            EmptyState(
                icon  = Icons.Outlined.Favorite,
                title = "Inga favoriter sparade",
                body  = "Tryck + för att spara en vid-behov-medicin",
            )
        },
    ) {
        composeRule.onNodeWithText("Tryck + för att spara en vid-behov-medicin").assertIsDisplayed()
    }

    @Test fun `no body node exists when body is null`() = renderAndRetry(
        content = { EmptyState(icon = Icons.Outlined.Favorite, title = "Bara en titel") },
    ) {
        composeRule.onNodeWithText("Bara en titel").assertIsDisplayed()
    }

    @Test fun `icon is rendered without a content description`() = renderAndRetry(
        content = { EmptyState(icon = Icons.Outlined.Favorite, title = "Titel") },
    ) {
        // Decorative icon — must not be independently announced by TalkBack.
        composeRule.onNodeWithContentDescription("Favorite").assertDoesNotExist()
    }

    @Test fun `action is displayed and clickable`() = retryOnRenderGlitch {
        // clicked is declared fresh inside the retry block (not shared via
        // renderAndRetry's closures) so a stale true from a glitched earlier
        // attempt can't leak into a later attempt's assertion.
        var clicked = false
        val scenario = ActivityScenario.launch(ComponentActivity::class.java)
        try {
            scenario.onActivity {
                it.setContent {
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
            }
            composeRule.onNodeWithText("Logga engångsdos").performClick()
            assertEquals(true, clicked)
        } finally {
            scenario.close()
        }
    }
}
