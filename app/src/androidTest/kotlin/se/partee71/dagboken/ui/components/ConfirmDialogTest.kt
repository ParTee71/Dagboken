package se.partee71.dagboken.ui.components

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import se.partee71.dagboken.util.retryOnRenderGlitch

// Migrerad enligt POC i #112 — se SjukdomarScreenTest för fullständig förklaring.
@RunWith(AndroidJUnit4::class)
class ConfirmDialogTest {

    @get:Rule
    val composeRule = createEmptyComposeRule()

    @Test fun `title and text are displayed`() = retryOnRenderGlitch {
        val scenario = ActivityScenario.launch(ComponentActivity::class.java)
        try {
            scenario.onActivity {
                it.setContent {
                    MaterialTheme {
                        ConfirmDialog(
                            title     = "Ta bort episod",
                            text      = "Ta bort X och alla incheckningar?",
                            onConfirm = {},
                            onDismiss = {},
                        )
                    }
                }
            }
            composeRule.onNodeWithText("Ta bort episod").assertIsDisplayed()
            composeRule.onNodeWithText("Ta bort X och alla incheckningar?").assertIsDisplayed()
        } finally {
            scenario.close()
        }
    }

    @Test fun `default button labels come from string resources`() = retryOnRenderGlitch {
        val scenario = ActivityScenario.launch(ComponentActivity::class.java)
        try {
            scenario.onActivity {
                it.setContent {
                    MaterialTheme {
                        ConfirmDialog(title = "Titel", text = "Text", onConfirm = {}, onDismiss = {})
                    }
                }
            }
            composeRule.onNodeWithText("Ta bort").assertIsDisplayed()
            composeRule.onNodeWithText("Avbryt").assertIsDisplayed()
        } finally {
            scenario.close()
        }
    }

    @Test fun `custom confirm label overrides the default`() = retryOnRenderGlitch {
        val scenario = ActivityScenario.launch(ComponentActivity::class.java)
        try {
            scenario.onActivity {
                it.setContent {
                    MaterialTheme {
                        ConfirmDialog(
                            title        = "Hoppa över dos?",
                            text         = "Vill du hoppa över dosen?",
                            confirmLabel = "Hoppa över",
                            onConfirm    = {},
                            onDismiss    = {},
                        )
                    }
                }
            }
            composeRule.onNodeWithText("Hoppa över").assertIsDisplayed()
        } finally {
            scenario.close()
        }
    }

    @Test fun `onConfirm is invoked when confirm button is clicked`() = retryOnRenderGlitch {
        var confirmed = false
        val scenario = ActivityScenario.launch(ComponentActivity::class.java)
        try {
            scenario.onActivity {
                it.setContent {
                    MaterialTheme {
                        ConfirmDialog(
                            title     = "Titel",
                            text      = "Text",
                            onConfirm = { confirmed = true },
                            onDismiss = {},
                        )
                    }
                }
            }
            composeRule.onNodeWithText("Ta bort").performClick()
            assertEquals(true, confirmed)
        } finally {
            scenario.close()
        }
    }

    @Test fun `onDismiss is invoked when cancel button is clicked`() = retryOnRenderGlitch {
        var dismissed = false
        val scenario = ActivityScenario.launch(ComponentActivity::class.java)
        try {
            scenario.onActivity {
                it.setContent {
                    MaterialTheme {
                        ConfirmDialog(
                            title     = "Titel",
                            text      = "Text",
                            onConfirm = {},
                            onDismiss = { dismissed = true },
                        )
                    }
                }
            }
            composeRule.onNodeWithText("Avbryt").performClick()
            assertEquals(true, dismissed)
        } finally {
            scenario.close()
        }
    }

    @Test fun `onDismiss is invoked when dismissed outside the dialog`() = retryOnRenderGlitch {
        var dismissed = false
        val scenario = ActivityScenario.launch(ComponentActivity::class.java)
        try {
            scenario.onActivity {
                it.setContent {
                    MaterialTheme {
                        ConfirmDialog(
                            title     = "Titel",
                            text      = "Text",
                            onConfirm = {},
                            onDismiss = { dismissed = true },
                        )
                    }
                }
            }
            composeRule.onNodeWithText("Titel").assertIsDisplayed()
            // Pressing back triggers onDismissRequest, same as tapping the scrim.
            Espresso.pressBack()
            composeRule.waitForIdle()
            assertEquals(true, dismissed)
        } finally {
            scenario.close()
        }
    }

    @Test fun `non-destructive dialog still renders confirm and dismiss buttons`() = retryOnRenderGlitch {
        val scenario = ActivityScenario.launch(ComponentActivity::class.java)
        try {
            scenario.onActivity {
                it.setContent {
                    MaterialTheme {
                        ConfirmDialog(
                            title        = "Markera frisk",
                            text         = "Markera episoden som avslutad idag?",
                            confirmLabel = "OK",
                            destructive  = false,
                            onConfirm    = {},
                            onDismiss    = {},
                        )
                    }
                }
            }
            composeRule.onNodeWithText("OK").assertIsDisplayed()
            composeRule.onNodeWithText("Avbryt").assertIsDisplayed()
        } finally {
            scenario.close()
        }
    }
}
