package se.partee71.dagboken.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import se.partee71.dagboken.data.datastore.SymptomOption
import se.partee71.dagboken.util.RetryTestRule

@RunWith(AndroidJUnit4::class)
class StepwiseScreeningFormTest {

    val composeRule = createComposeRule()

    @get:Rule
    val flakyRetry: RuleChain =
        RuleChain.outerRule(RetryTestRule()).around(composeRule)

    private fun setContent(
        symptomOptions: List<SymptomOption> = emptyList(),
        onSave: () -> Unit = {},
    ) {
        composeRule.setContent {
            MaterialTheme {
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
        }
    }

    @Test fun starts_on_the_energy_step() {
        setContent()
        composeRule.onNodeWithTag("screening_step_energy").assertIsDisplayed()
        composeRule.onNodeWithTag("screening_next").assertIsDisplayed()
    }

    @Test fun next_advances_to_the_stress_step() {
        setContent()
        composeRule.onNodeWithTag("screening_next").performClick()
        composeRule.waitUntil(20_000) {
            composeRule.onAllNodesWithTag("screening_step_stress").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("screening_step_stress").assertIsDisplayed()
        composeRule.onNodeWithTag("screening_prev").assertIsDisplayed()
    }

    @Test fun without_symptom_options_the_stress_step_is_last_and_shows_save() {
        setContent(symptomOptions = emptyList())
        composeRule.onNodeWithTag("screening_next").performClick()
        composeRule.waitUntil(20_000) {
            composeRule.onAllNodesWithTag("screening_save").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("screening_save").assertIsDisplayed()
    }

    @Test fun with_symptom_options_there_is_a_third_symptom_step() {
        setContent(symptomOptions = listOf(SymptomOption("Yrsel")))
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

    @Test fun save_on_the_last_step_invokes_the_callback() {
        var saved = false
        setContent(onSave = { saved = true })
        composeRule.onNodeWithTag("screening_next").performClick()
        composeRule.waitUntil(20_000) {
            composeRule.onAllNodesWithTag("screening_save").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("screening_save").performClick()
        composeRule.waitUntil(20_000) { saved }
        assert(saved)
    }
}
