package se.partee71.dagboken.ui.components

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
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
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import se.partee71.dagboken.util.retryOnRenderGlitch

// Migrerad enligt POC i #112 — se SjukdomarScreenTest för fullständig förklaring.
@RunWith(AndroidJUnit4::class)
class DagbokenCardTest {

    @get:Rule
    val composeRule = createEmptyComposeRule()

    @Test fun `title is shown when provided`() = retryOnRenderGlitch {
        val scenario = ActivityScenario.launch(ComponentActivity::class.java)
        try {
            scenario.onActivity {
                it.setContent {
                    MaterialTheme {
                        DagbokenCard(title = "En titel") { Text("Innehåll") }
                    }
                }
            }
            composeRule.onNodeWithText("En titel").assertIsDisplayed()
            composeRule.onNodeWithText("Innehåll").assertIsDisplayed()
        } finally {
            scenario.close()
        }
    }

    @Test fun `no title is shown when title is null`() = retryOnRenderGlitch {
        val scenario = ActivityScenario.launch(ComponentActivity::class.java)
        try {
            scenario.onActivity {
                it.setContent {
                    MaterialTheme {
                        DagbokenCard { Text("Bara innehåll") }
                    }
                }
            }
            composeRule.onNodeWithText("Bara innehåll").assertIsDisplayed()
        } finally {
            scenario.close()
        }
    }

    @Test fun `onClick is invoked when card is tapped`() = retryOnRenderGlitch {
        var clicked = false
        val scenario = ActivityScenario.launch(ComponentActivity::class.java)
        try {
            scenario.onActivity {
                it.setContent {
                    MaterialTheme {
                        DagbokenCard(onClick = { clicked = true }) { Text("Tryck mig") }
                    }
                }
            }
            composeRule.onNodeWithText("Tryck mig").performClick()
            assertEquals(true, clicked)
        } finally {
            scenario.close()
        }
    }

    @Test fun `onLongClick is invoked on long press`() = retryOnRenderGlitch {
        var longClicked = false
        val scenario = ActivityScenario.launch(ComponentActivity::class.java)
        try {
            scenario.onActivity {
                it.setContent {
                    MaterialTheme {
                        DagbokenCard(
                            onClick = {},
                            onLongClick = { longClicked = true },
                        ) {
                            Text("Håll intryckt", modifier = Modifier.testTag("content"))
                        }
                    }
                }
            }
            composeRule.onNodeWithTag("content", useUnmergedTree = true).performTouchInput {
                down(center)
                advanceEventTime(600L)
                up()
            }
            assertEquals(true, longClicked)
        } finally {
            scenario.close()
        }
    }

    @Test fun `accentColor renders an accent bar alongside content`() = retryOnRenderGlitch {
        val scenario = ActivityScenario.launch(ComponentActivity::class.java)
        try {
            scenario.onActivity {
                it.setContent {
                    MaterialTheme {
                        DagbokenCard(accentColor = Color.Red) {
                            Box(modifier = Modifier.testTag("body").size(40.dp))
                        }
                    }
                }
            }
            composeRule.onNodeWithTag("body").assertIsDisplayed()
        } finally {
            scenario.close()
        }
    }

    // ─── titleTrailing — #149 (Trenders periodväljare i övre högra hörnet) ──────

    @Test fun `titleTrailing is shown alongside the title when provided`() = retryOnRenderGlitch {
        val scenario = ActivityScenario.launch(ComponentActivity::class.java)
        try {
            scenario.onActivity {
                it.setContent {
                    MaterialTheme {
                        DagbokenCard(title = "En titel", titleTrailing = { Text("Trailing") }) {
                            Text("Innehåll")
                        }
                    }
                }
            }
            composeRule.onNodeWithText("En titel").assertIsDisplayed()
            composeRule.onNodeWithText("Trailing").assertIsDisplayed()
        } finally {
            scenario.close()
        }
    }

    @Test fun `titleTrailing is positioned to the right of the title`() = retryOnRenderGlitch {
        val scenario = ActivityScenario.launch(ComponentActivity::class.java)
        try {
            scenario.onActivity {
                it.setContent {
                    MaterialTheme {
                        DagbokenCard(title = "En titel", titleTrailing = { Text("Trailing") }) {
                            Text("Innehåll")
                        }
                    }
                }
            }
            val titleLeft = composeRule.onNodeWithText("En titel").fetchSemanticsNode().boundsInRoot.left
            val trailingLeft = composeRule.onNodeWithText("Trailing").fetchSemanticsNode().boundsInRoot.left
            assert(trailingLeft > titleLeft) {
                "Förväntade titleTrailing till höger om titeln ($trailingLeft > $titleLeft)"
            }
        } finally {
            scenario.close()
        }
    }

    @Test fun `contentPadding of zero removes default card padding`() = retryOnRenderGlitch {
        val scenario = ActivityScenario.launch(ComponentActivity::class.java)
        try {
            scenario.onActivity {
                it.setContent {
                    MaterialTheme {
                        DagbokenCard(contentPadding = PaddingValues(0.dp)) {
                            Box(modifier = Modifier.testTag("zero-padded").size(20.dp))
                        }
                    }
                }
            }
            composeRule.onNodeWithTag("zero-padded").assertIsDisplayed().assertHeightIsAtLeast(20.dp)
        } finally {
            scenario.close()
        }
    }
}
