package se.partee71.dagboken.ui.common

import androidx.annotation.StringRes
import se.partee71.dagboken.R
import se.partee71.dagboken.core.model.BodySensationCatalog
import se.partee71.dagboken.core.model.ContextTagCatalog
import se.partee71.dagboken.core.model.TagCatalog

// Kroppens och sammanhangets svenska namn (SCR-9, DAT-15) – enda stället som slår upp ett token i strängarna (regel 4).

/**
 * Namnet på [token] i [catalog] (resursen `body_<token>` respektive `context_<token>`). Bara katalogernas egna tokens
 * visas som val, så ett okänt token (från en nyare app) slås aldrig upp; det ger `null`.
 */
@StringRes
fun tagName(catalog: TagCatalog, token: String): Int? = when (catalog) {
    BodySensationCatalog -> BODY_NAMES[token]
    ContextTagCatalog -> CONTEXT_NAMES[token]
    else -> null
}

/** Token → namnet, för alla ord i [BodySensationCatalog]. */
private val BODY_NAMES: Map<String, Int> = mapOf(
    "tense_jaw" to R.string.body_tense_jaw,
    "heart_racing" to R.string.body_heart_racing,
    "stomach_knot" to R.string.body_stomach_knot,
    "heavy_chest" to R.string.body_heavy_chest,
    "restless_legs" to R.string.body_restless_legs,
    "warm_face" to R.string.body_warm_face,
    "headache" to R.string.body_headache,
    "tired_body" to R.string.body_tired_body,
    "light_body" to R.string.body_light_body,
    "warm_soft" to R.string.body_warm_soft,
    "nothing" to R.string.body_nothing
)

/** Token → namnet, för alla ord i [ContextTagCatalog]. */
private val CONTEXT_NAMES: Map<String, Int> = mapOf(
    "sleep" to R.string.context_sleep,
    "family" to R.string.context_family,
    "work" to R.string.context_work,
    "health" to R.string.context_health,
    "medicines" to R.string.context_medicines,
    "alone_time" to R.string.context_alone_time,
    "weather" to R.string.context_weather,
    "finances" to R.string.context_finances,
    "friends" to R.string.context_friends,
    "exercise" to R.string.context_exercise,
    "food" to R.string.context_food
)
