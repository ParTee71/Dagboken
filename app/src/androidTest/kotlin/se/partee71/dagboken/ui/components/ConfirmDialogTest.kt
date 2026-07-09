package se.partee71.dagboken.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.espresso.Espresso
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ConfirmDialogTest {

    @get:Rule val composeRule = createComposeRule()

    @Test fun `title and text are displayed`() {
        composeRule.setContent {
            MaterialTheme {
                ConfirmDialog(
                    title     = "Ta bort episod",
                    text      = "Ta bort X och alla incheckningar?",
                    onConfirm = {},
                    onDismiss = {},
                )
            }
        }
        composeRule.onNodeWithText("Ta bort episod").assertIsDisplayed()
        composeRule.onNodeWithText("Ta bort X och alla incheckningar?").assertIsDisplayed()
    }

    @Test fun `default button labels come from string resources`() {
        composeRule.setContent {
            MaterialTheme {
                ConfirmDialog(title = "Titel", text = "Text", onConfirm = {}, onDismiss = {})
            }
        }
        composeRule.onNodeWithText("Ta bort").assertIsDisplayed()
        composeRule.onNodeWithText("Avbryt").assertIsDisplayed()
    }

    @Test fun `custom confirm label overrides the default`() {
        composeRule.setContent {
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
        composeRule.onNodeWithText("Hoppa över").assertIsDisplayed()
    }

    @Test fun `onConfirm is invoked when confirm button is clicked`() {
        var confirmed = false
        composeRule.setContent {
            MaterialTheme {
                ConfirmDialog(
                    title     = "Titel",
                    text      = "Text",
                    onConfirm = { confirmed = true },
                    onDismiss = {},
                )
            }
        }
        composeRule.onNodeWithText("Ta bort").performClick()
        assertEquals(true, confirmed)
    }

    @Test fun `onDismiss is invoked when cancel button is clicked`() {
        var dismissed = false
        composeRule.setContent {
            MaterialTheme {
                ConfirmDialog(
                    title     = "Titel",
                    text      = "Text",
                    onConfirm = {},
                    onDismiss = { dismissed = true },
                )
            }
        }
        composeRule.onNodeWithText("Avbryt").performClick()
        assertEquals(true, dismissed)
    }

    @Test fun `onDismiss is invoked when dismissed outside the dialog`() {
        var dismissed = false
        composeRule.setContent {
            MaterialTheme {
                ConfirmDialog(
                    title     = "Titel",
                    text      = "Text",
                    onConfirm = {},
                    onDismiss = { dismissed = true },
                )
            }
        }
        composeRule.onNodeWithText("Titel").assertIsDisplayed()
        // Pressing back triggers onDismissRequest, same as tapping the scrim.
        Espresso.pressBack()
        composeRule.waitForIdle()
        assertEquals(true, dismissed)
    }

    @Test fun `non-destructive dialog still renders confirm and dismiss buttons`() {
        composeRule.setContent {
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
        composeRule.onNodeWithText("OK").assertIsDisplayed()
        composeRule.onNodeWithText("Avbryt").assertIsDisplayed()
    }
}
