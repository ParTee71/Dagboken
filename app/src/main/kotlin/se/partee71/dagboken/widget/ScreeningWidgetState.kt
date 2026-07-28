package se.partee71.dagboken.widget

import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import se.partee71.dagboken.domain.usecase.SymptomUtils

/**
 * Per-widgetinstans-state för screeningguiden (WID-3), lagrad via Glances
 * [androidx.glance.state.PreferencesGlanceStateDefinition]. Flyktig UI-state — inte i
 * backupen (samma resonemang som redan gäller för widgetens övriga state, se #120/#156).
 */
object ScreeningWidgetKeys {
    val STEP = intPreferencesKey("screening_step")
    val ENERGY = intPreferencesKey("screening_energy")
    val STRESS = intPreferencesKey("screening_stress")
    val SYMPTOM_SCORES = stringPreferencesKey("screening_symptom_scores")
}

data class ScreeningDraft(
    val step: Int,
    val energy: Int,
    val stress: Int,
    val symptomScores: Map<String, Int>,
)

fun Preferences.toScreeningDraft(): ScreeningDraft = ScreeningDraft(
    step = this[ScreeningWidgetKeys.STEP] ?: SCREENING_STEP_INACTIVE,
    energy = this[ScreeningWidgetKeys.ENERGY] ?: 5,
    stress = this[ScreeningWidgetKeys.STRESS] ?: 5,
    symptomScores = SymptomUtils.decode(this[ScreeningWidgetKeys.SYMPTOM_SCORES] ?: ""),
)
