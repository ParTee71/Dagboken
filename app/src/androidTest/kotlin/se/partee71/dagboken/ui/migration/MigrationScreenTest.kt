package se.partee71.dagboken.ui.migration

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import se.partee71.dagboken.util.retryOnRenderGlitch

/**
 * Migreringsskärmen är första mötet med appen och var otestad. Testerna kör den
 * tillståndslösa [MigrationContent], så inget Drive- eller auth-beroende behövs.
 *
 * Migrerad enligt POC i #112 — se SjukdomarScreenTest för fullständig förklaring.
 */
@RunWith(AndroidJUnit4::class)
class MigrationScreenTest {

    @get:Rule
    val composeRule = createEmptyComposeRule()

    private class Actions {
        var start = 0
        var chooseFile = 0
        var skip = 0
        var signIn = 0
    }

    private fun launch(state: MigrationState, actions: Actions = Actions()): ActivityScenario<ComponentActivity> {
        val scenario = ActivityScenario.launch(ComponentActivity::class.java)
        scenario.onActivity {
            it.setContent {
                MaterialTheme {
                    MigrationContent(
                        state        = state,
                        onStart      = { actions.start++ },
                        onChooseFile = { actions.chooseFile++ },
                        onSkip       = { actions.skip++ },
                        onSignIn     = { actions.signIn++ },
                    )
                }
            }
        }
        return scenario
    }

    @Test fun `idle offers drive import file import and starting fresh`() = retryOnRenderGlitch {
        val scenario = launch(MigrationState.Idle)
        try {
            composeRule.onNodeWithText("Välkommen till Dagboken").assertIsDisplayed()
            composeRule.onNodeWithText("Importera från Google Drive").assertIsDisplayed()
            composeRule.onNodeWithText("Välj säkerhetskopia från fil").assertIsDisplayed()
            composeRule.onNodeWithText("Börja från början").assertIsDisplayed()
        } finally {
            scenario.close()
        }
    }

    @Test fun `idle drive button starts the migration`() = retryOnRenderGlitch {
        val actions = Actions()
        val scenario = launch(MigrationState.Idle, actions)
        try {
            composeRule.onNodeWithText("Importera från Google Drive").performClick()
            composeRule.waitForIdle()
            assertEquals(1, actions.start)
        } finally {
            scenario.close()
        }
    }

    @Test fun `idle start-fresh button skips the migration`() = retryOnRenderGlitch {
        val actions = Actions()
        val scenario = launch(MigrationState.Idle, actions)
        try {
            composeRule.onNodeWithText("Börja från början").performClick()
            composeRule.waitForIdle()
            assertEquals(1, actions.skip)
        } finally {
            scenario.close()
        }
    }

    @Test fun `no account state offers sign-in`() = retryOnRenderGlitch {
        val actions = Actions()
        val scenario = launch(MigrationState.NoAccountSignedIn, actions)
        try {
            // Rubriken och knappen har samma text ("Logga in med Google"), så klicket
            // måste peka ut den klickbara noden.
            composeRule.onNode(hasText("Logga in med Google") and hasClickAction()).performClick()
            composeRule.waitForIdle()
            assertEquals(1, actions.signIn)
        } finally {
            scenario.close()
        }
    }

    @Test fun `no backup found still offers file import`() = retryOnRenderGlitch {
        val actions = Actions()
        val scenario = launch(MigrationState.NoBackupFound, actions)
        try {
            composeRule.onNodeWithText("Ingen säkerhetskopia hittades på Google Drive.").assertIsDisplayed()
            composeRule.onNodeWithText("Välj säkerhetskopia från fil").performClick()
            composeRule.waitForIdle()
            assertEquals(1, actions.chooseFile)
        } finally {
            scenario.close()
        }
    }

    @Test fun `done state reports how much was imported`() = retryOnRenderGlitch {
        val scenario = launch(MigrationState.Done(aktiviteter = 12, mediciner = 7))
        try {
            composeRule.onNodeWithText("Import klar!").assertIsDisplayed()
            composeRule.onNodeWithText("12", substring = true).assertIsDisplayed()
        } finally {
            scenario.close()
        }
    }

    @Test fun `error state shows the message and offers retry`() = retryOnRenderGlitch {
        val actions = Actions()
        val scenario = launch(MigrationState.Error("Nätverket är nere"), actions)
        try {
            composeRule.onNodeWithText("Fel: Nätverket är nere").assertIsDisplayed()
            composeRule.onNodeWithText("Hoppa över").performClick()
            composeRule.waitForIdle()
            assertEquals(1, actions.skip)
        } finally {
            scenario.close()
        }
    }
}
