package se.partee71.dagboken.widget

import androidx.datastore.preferences.core.MutablePreferences
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
    /** Vilket screeningtillfälle (t.ex. "Lunch") som loggas — tomt för en fristående screening. */
    val LABEL = stringPreferencesKey("screening_label")
}

data class ScreeningDraft(
    val step: Int,
    val energy: Int,
    val stress: Int,
    val symptomScores: Map<String, Int>,
    val label: String,
)

fun Preferences.toScreeningDraft(): ScreeningDraft = ScreeningDraft(
    step = this[ScreeningWidgetKeys.STEP] ?: SCREENING_STEP_INACTIVE,
    energy = this[ScreeningWidgetKeys.ENERGY] ?: 5,
    stress = this[ScreeningWidgetKeys.STRESS] ?: 5,
    symptomScores = SymptomUtils.decode(this[ScreeningWidgetKeys.SYMPTOM_SCORES] ?: ""),
    label = this[ScreeningWidgetKeys.LABEL] ?: "",
)

/*
 * Skrivningarna nedan muterar mottagaren direkt, och inget annat.
 *
 * `updateAppWidgetState(context, glanceId) { prefs -> ... }` är Preferences-överlagringen
 * med signaturen `suspend (MutablePreferences) -> Unit`: `prefs` *är* objektet som sparas.
 * Widgetarna gjorde tidigare `prefs.toMutablePreferences().apply { ... }` inuti den lambdan
 * — en kopia som muterades och sedan slängdes, eftersom lambdans returvärde coercas till
 * `Unit`. Det kompilerade utan varning och ingen enda screening-/vid behov-skrivning nådde
 * disk (#164, felsökt genom 3.15.0–3.15.3). Som rena extensions på [MutablePreferences] går
 * de att enhetstesta mot `mutablePreferencesOf()`, vilket fångar exakt det misstaget.
 */

/** Startar guiden på energisteget med nollställd draft, för [label]s screeningtillfälle. */
fun MutablePreferences.startScreeningDraft(label: String) {
    this[ScreeningWidgetKeys.STEP] = SCREENING_STEP_ENERGY
    this[ScreeningWidgetKeys.ENERGY] = 5
    this[ScreeningWidgetKeys.STRESS] = 5
    this[ScreeningWidgetKeys.SYMPTOM_SCORES] = ""
    this[ScreeningWidgetKeys.LABEL] = label
}

/** Flyttar guiden ett steg enligt [nextScreeningStep]. */
fun MutablePreferences.stepScreeningDraft(direction: String, hasFavoriteSymptoms: Boolean) {
    val current = this[ScreeningWidgetKeys.STEP] ?: SCREENING_STEP_INACTIVE
    this[ScreeningWidgetKeys.STEP] = nextScreeningStep(current, direction, hasFavoriteSymptoms)
}

/** +/- på energi- eller stressvärdet, klampat till 0..10. */
fun MutablePreferences.adjustScreeningValue(key: Preferences.Key<Int>, delta: Int) {
    this[key] = clampStepperValue(this[key] ?: 5, delta)
}

/** +/- på ett enskilt symptoms gradering, klampat till 0..10. */
fun MutablePreferences.adjustScreeningSymptomScore(name: String, delta: Int) {
    val scores = SymptomUtils.decode(this[ScreeningWidgetKeys.SYMPTOM_SCORES] ?: "")
    this[ScreeningWidgetKeys.SYMPTOM_SCORES] =
        SymptomUtils.encode(adjustSymptomScore(scores, name, delta))
}

/** Nollställer guiden till utgångsläget efter sparad (eller avbruten) screening. */
fun MutablePreferences.clearScreeningDraft() {
    this[ScreeningWidgetKeys.STEP] = SCREENING_STEP_INACTIVE
    remove(ScreeningWidgetKeys.ENERGY)
    remove(ScreeningWidgetKeys.STRESS)
    remove(ScreeningWidgetKeys.SYMPTOM_SCORES)
    remove(ScreeningWidgetKeys.LABEL)
}
