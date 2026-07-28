package se.partee71.dagboken.widget

/**
 * Ren stegmaskin för widgetens screeningguide (WID-3) — ingen Android-beroende, så den
 * kan unit-testas utan Glance/instrumentering. Symptomsteget hoppas över när det inte
 * finns favoritmarkerade symptom (widgetytan rymmer inte hela symptomlistan, #157).
 */
const val SCREENING_STEP_INACTIVE = -1
const val SCREENING_STEP_ENERGY = 0
const val SCREENING_STEP_STRESS = 1
const val SCREENING_STEP_SYMPTOM = 2

fun screeningLastStep(hasFavoriteSymptoms: Boolean): Int =
    if (hasFavoriteSymptoms) SCREENING_STEP_SYMPTOM else SCREENING_STEP_STRESS

/** direction: "next" | "back" | "cancel" */
fun nextScreeningStep(current: Int, direction: String, hasFavoriteSymptoms: Boolean): Int {
    if (direction == "cancel") return SCREENING_STEP_INACTIVE
    val last = screeningLastStep(hasFavoriteSymptoms)
    return when (direction) {
        "next" -> (current + 1).coerceAtMost(last)
        "back" -> (current - 1).takeIf { it >= SCREENING_STEP_ENERGY } ?: SCREENING_STEP_INACTIVE
        else -> current
    }
}

fun clampStepperValue(value: Int, delta: Int): Int = (value + delta).coerceIn(0, 10)

fun adjustSymptomScore(scores: Map<String, Int>, name: String, delta: Int): Map<String, Int> {
    val updated = ((scores[name] ?: 0) + delta).coerceIn(0, 10)
    return if (updated == 0) scores - name else scores + (name to updated)
}
