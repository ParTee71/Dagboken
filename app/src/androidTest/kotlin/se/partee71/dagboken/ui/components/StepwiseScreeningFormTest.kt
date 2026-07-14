package se.partee71.dagboken.ui.components

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import se.partee71.dagboken.data.datastore.SymptomOption
import se.partee71.dagboken.util.retryOnRenderGlitch

// Migrerad enligt POC i #112 — se SjukdomarScreenTest för fullständig förklaring.
@RunWith(AndroidJUnit4::class)
class StepwiseScreeningFormTest {

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

    private fun form(
        symptomOptions: List<SymptomOption> = emptyList(),
        onSave: () -> Unit = {},
    ): @Composable () -> Unit = {
        var energy by remember { mutableStateOf(0) }
        var stress by remember { mutableStateOf(0) }
        var scores by remember { mutableStateOf(emptyMap<String, Int>()) }
        StepwiseScreeningForm(
            energy                  = energy,
            onEnergyChange          = { energy = it },
            stress                  = stress,
            onStressChange          = { stress = it },
            symptomOptions          = symptomOptions,
            symptomScores           = scores,
            onScoresChange          = { scores = it },
            onToggleSymptomFavorite = {},
            onSave                  = onSave,
        )
    }

    @Test fun starts_on_the_energy_step() = renderAndRetry(form()) {
        composeRule.onNodeWithTag("screening_step_energy").assertIsDisplayed()
        composeRule.onNodeWithTag("screening_next").assertIsDisplayed()
    }

    @Test fun next_advances_to_the_stress_step() = renderAndRetry(form()) {
        composeRule.onNodeWithTag("screening_next").performClick()
        composeRule.waitUntil(20_000) {
            composeRule.onAllNodesWithTag("screening_step_stress").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("screening_step_stress").assertIsDisplayed()
        composeRule.onNodeWithTag("screening_prev").assertIsDisplayed()
    }

    @Test fun without_symptom_options_the_stress_step_is_last_and_shows_save() =
        renderAndRetry(form(symptomOptions = emptyList())) {
            composeRule.onNodeWithTag("screening_next").performClick()
            composeRule.waitUntil(20_000) {
                composeRule.onAllNodesWithTag("screening_save").fetchSemanticsNodes().isNotEmpty()
            }
            composeRule.onNodeWithTag("screening_save").assertIsDisplayed()
        }

    @Test fun with_symptom_options_there_is_a_third_symptom_step() =
        renderAndRetry(form(symptomOptions = listOf(SymptomOption("Yrsel")))) {
            composeRule.onNodeWithTag("screening_next").performClick()   // energy -> stress
            composeRule.waitUntil(20_000) {
                composeRule.onAllNodesWithTag("screening_step_stress").fetchSemanticsNodes().isNotEmpty()
            }
            composeRule.onNodeWithTag("screening_next").performClick()   // stress -> symptom
            composeRule.waitUntil(20_000) {
                composeRule.onAllNodesWithTag("screening_step_symptom").fetchSemanticsNodes().isNotEmpty()
            }
            composeRule.onNodeWithTag("screening_save").assertIsDisplayed()
        }

    @Test fun save_on_the_last_step_invokes_the_callback() = retryOnRenderGlitch {
        // saved declared fresh inside the retry block, not shared via renderAndRetry's
        // closures, so stale state from a glitched earlier attempt can't leak in.
        var saved = false
        val scenario = ActivityScenario.launch(ComponentActivity::class.java)
        try {
            scenario.onActivity { it.setContent { MaterialTheme { form(onSave = { saved = true })() } } }
            composeRule.onNodeWithTag("screening_next").performClick()
            composeRule.waitUntil(20_000) {
                composeRule.onAllNodesWithTag("screening_save").fetchSemanticsNodes().isNotEmpty()
            }
            composeRule.onNodeWithTag("screening_save").performClick()
            composeRule.waitUntil(20_000) { saved }
            assert(saved)
        } finally {
            scenario.close()
        }
    }
}
