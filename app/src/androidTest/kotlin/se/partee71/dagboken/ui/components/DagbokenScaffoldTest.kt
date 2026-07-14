package se.partee71.dagboken.ui.components

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
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
class DagbokenScaffoldTest {

    @get:Rule
    val composeRule = createEmptyComposeRule()

    @Test fun `title is displayed`() = retryOnRenderGlitch {
        val scenario = ActivityScenario.launch(ComponentActivity::class.java)
        try {
            scenario.onActivity {
                it.setContent { MaterialTheme { DagbokenScaffold(title = "Inställningar") { } } }
            }
            composeRule.onNodeWithText("Inställningar").assertIsDisplayed()
        } finally {
            scenario.close()
        }
    }

    @Test fun `no back icon shown when onBack is not provided`() = retryOnRenderGlitch {
        val scenario = ActivityScenario.launch(ComponentActivity::class.java)
        try {
            scenario.onActivity {
                it.setContent { MaterialTheme { DagbokenScaffold(title = "Utan tillbaka") { } } }
            }
            composeRule.onNodeWithContentDescription("Tillbaka").assertDoesNotExist()
        } finally {
            scenario.close()
        }
    }

    @Test fun `back icon has content description and invokes onBack when clicked`() = retryOnRenderGlitch {
        var backClicked = false
        val scenario = ActivityScenario.launch(ComponentActivity::class.java)
        try {
            scenario.onActivity {
                it.setContent {
                    MaterialTheme {
                        DagbokenScaffold(title = "Med tillbaka", onBack = { backClicked = true }) { }
                    }
                }
            }
            composeRule.onNodeWithContentDescription("Tillbaka").assertIsDisplayed().performClick()
            assertEquals(true, backClicked)
        } finally {
            scenario.close()
        }
    }

    @Test fun `custom navigationIcon overrides the default back button`() = retryOnRenderGlitch {
        val scenario = ActivityScenario.launch(ComponentActivity::class.java)
        try {
            scenario.onActivity {
                it.setContent {
                    MaterialTheme {
                        DagbokenScaffold(
                            title = "Egen ikon",
                            onBack = {},
                            navigationIcon = { Text("Konto") },
                        ) { }
                    }
                }
            }
            composeRule.onNodeWithText("Konto").assertIsDisplayed()
            composeRule.onNodeWithContentDescription("Tillbaka").assertDoesNotExist()
        } finally {
            scenario.close()
        }
    }

    @Test fun `floatingActionButton is displayed and clickable`() = retryOnRenderGlitch {
        var fabClicked = false
        val scenario = ActivityScenario.launch(ComponentActivity::class.java)
        try {
            scenario.onActivity {
                it.setContent {
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
            }
            composeRule.onNodeWithText("+").performClick()
            assertEquals(true, fabClicked)
        } finally {
            scenario.close()
        }
    }

    @Test fun `content is rendered with the provided padding`() = retryOnRenderGlitch {
        val scenario = ActivityScenario.launch(ComponentActivity::class.java)
        try {
            scenario.onActivity {
                it.setContent {
                    MaterialTheme {
                        DagbokenScaffold(title = "Innehåll") { padding ->
                            Text("Skärminnehåll", modifier = Modifier.padding(padding))
                        }
                    }
                }
            }
            composeRule.onNodeWithText("Skärminnehåll").assertIsDisplayed()
        } finally {
            scenario.close()
        }
    }
}
