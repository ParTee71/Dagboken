package se.partee71.dagboken.ui.components

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import se.partee71.dagboken.util.retryOnRenderGlitch

/**
 * Komponenten skyddar mot att osparade ändringar går förlorade och var helt otestad.
 * Testerna kör den guardade onBack-lambdan via en knapp i stället för systemets
 * tillbaka-gest, eftersom det är samma väg skärmarna använder.
 *
 * Migrerad enligt POC i #112 — se SjukdomarScreenTest för fullständig förklaring.
 */
@RunWith(AndroidJUnit4::class)
class UnsavedChangesBackHandlerTest {

    @get:Rule
    val composeRule = createEmptyComposeRule()

    private class Recorder {
        var saved = 0
        var discarded = 0
    }

    private fun launch(
        isDirty: Boolean,
        canSave: Boolean = true,
        recorder: Recorder,
    ): ActivityScenario<ComponentActivity> {
        val scenario = ActivityScenario.launch(ComponentActivity::class.java)
        scenario.onActivity {
            it.setContent {
                MaterialTheme {
                    val guardedBack = UnsavedChangesBackHandler(
                        isDirty   = isDirty,
                        canSave   = canSave,
                        onSave    = { recorder.saved++ },
                        onDiscard = { recorder.discarded++ },
                    )
                    androidx.compose.material3.TextButton(onClick = guardedBack) {
                        Text("Tillbaka")
                    }
                }
            }
        }
        return scenario
    }

    @Test fun `back navigates straight away when nothing is dirty`() = retryOnRenderGlitch {
        val recorder = Recorder()
        val scenario = launch(isDirty = false, recorder = recorder)
        try {
            composeRule.onNodeWithText("Tillbaka").performClick()
            composeRule.waitForIdle()

            assertEquals(1, recorder.discarded)
            assertEquals(0, recorder.saved)
            composeRule.onNodeWithText("Osparade ändringar").assertDoesNotExist()
        } finally {
            scenario.close()
        }
    }

    @Test fun `back shows the confirmation dialog when there are unsaved changes`() = retryOnRenderGlitch {
        val recorder = Recorder()
        val scenario = launch(isDirty = true, recorder = recorder)
        try {
            composeRule.onNodeWithText("Tillbaka").performClick()
            composeRule.waitForIdle()

            composeRule.onNodeWithText("Osparade ändringar").assertIsDisplayed()
            composeRule.onNodeWithText("Vill du spara innan du går vidare?").assertIsDisplayed()
            // Ingenting händer förrän användaren väljer i dialogen.
            assertEquals(0, recorder.discarded)
            assertEquals(0, recorder.saved)
        } finally {
            scenario.close()
        }
    }

    @Test fun `discard leaves without saving`() = retryOnRenderGlitch {
        val recorder = Recorder()
        val scenario = launch(isDirty = true, recorder = recorder)
        try {
            composeRule.onNodeWithText("Tillbaka").performClick()
            composeRule.waitForIdle()
            composeRule.onNodeWithText("Kasta").performClick()
            composeRule.waitForIdle()

            assertEquals(1, recorder.discarded)
            assertEquals(0, recorder.saved)
        } finally {
            scenario.close()
        }
    }

    @Test fun `save is offered and invoked when the form can be saved`() = retryOnRenderGlitch {
        val recorder = Recorder()
        val scenario = launch(isDirty = true, canSave = true, recorder = recorder)
        try {
            composeRule.onNodeWithText("Tillbaka").performClick()
            composeRule.waitForIdle()
            composeRule.onNodeWithText("Spara").performClick()
            composeRule.waitForIdle()

            assertEquals(1, recorder.saved)
            assertEquals(0, recorder.discarded)
        } finally {
            scenario.close()
        }
    }

    @Test fun `save is not offered when the form cannot be saved`() = retryOnRenderGlitch {
        val recorder = Recorder()
        val scenario = launch(isDirty = true, canSave = false, recorder = recorder)
        try {
            composeRule.onNodeWithText("Tillbaka").performClick()
            composeRule.waitForIdle()

            composeRule.onNodeWithText("Osparade ändringar").assertIsDisplayed()
            composeRule.onNodeWithText("Spara").assertDoesNotExist()
        } finally {
            scenario.close()
        }
    }

    @Test fun `cancel closes the dialog without leaving`() = retryOnRenderGlitch {
        val recorder = Recorder()
        val scenario = launch(isDirty = true, recorder = recorder)
        try {
            composeRule.onNodeWithText("Tillbaka").performClick()
            composeRule.waitForIdle()
            composeRule.onNodeWithText("Avbryt").performClick()
            composeRule.waitForIdle()

            composeRule.onNodeWithText("Osparade ändringar").assertDoesNotExist()
            assertTrue(recorder.saved == 0 && recorder.discarded == 0)
        } finally {
            scenario.close()
        }
    }
}
