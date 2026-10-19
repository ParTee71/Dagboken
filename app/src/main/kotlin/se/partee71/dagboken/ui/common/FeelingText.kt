package se.partee71.dagboken.ui.common

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.res.stringResource
import se.partee71.dagboken.R
import se.partee71.dagboken.core.model.Feeling
import se.partee71.dagboken.core.model.FeelingCatalog

// Känslornas svenska namn (SCR-8) – enda stället som slår upp ett token i strängarna (regel 4).

/**
 * Namnet på ett ord i känslohjulet: kärnan, kärnans [nuance] eller nyansens [detail] (tokens ur [FeelingCatalog]).
 * Ett ord appen inte känner – ett token från en nyare app, eller ett [detail] utan [nuance] – ger "Okänd känsla".
 */
@StringRes
fun feelingName(core: String, nuance: String? = null, detail: String? = null): Int {
    if (nuance == null && detail != null) return R.string.feeling_unknown
    return FEELING_NAMES[Feeling(core, nuance, detail).token] ?: R.string.feeling_unknown
}

/**
 * Pilltexten för en vald känsla (SCR-7): de nivåer som finns, från kärnan inåt – "Lugn › Tacksam › Ödmjuk",
 * "Rädd". En känsla appen inte känner ([FeelingCatalog.isKnown]) visas som "Okänd känsla" i sin helhet.
 */
@Composable
@ReadOnlyComposable
fun feelingsLabel(feeling: Feeling): String {
    if (!FeelingCatalog.isKnown(feeling)) return stringResource(R.string.feeling_unknown)
    val levels = listOfNotNull(
        feelingName(feeling.core),
        feeling.nuance?.let { feelingName(feeling.core, it) },
        feeling.detail?.let { feelingName(feeling.core, feeling.nuance, it) }
    )
    return levels.map { stringResource(it) }.joinToString(stringResource(R.string.feeling_separator))
}

/** Valda känslor på en rad (AKT-13): [feelingsLabel] för var och en, åtskilda med " · " – "Lugn › Tacksam · Glad". */
@Composable
@ReadOnlyComposable
fun feelingsSummary(feelings: List<Feeling>): String = feelings.map { feelingsLabel(it) }.joinToString(stringResource(R.string.feelings_list_separator))

/** Token (`core[.nuance[.detail]]`) → namnet i strängarna, för alla 114 ord i [FeelingCatalog] – byggs en gång. */
private val FEELING_NAMES: Map<String, Int> = mapOf(
    "glad" to R.string.feeling_glad,
    "glad.excited" to R.string.feeling_glad_excited,
    "glad.excited.enthusiastic" to R.string.feeling_glad_excited_enthusiastic,
    "glad.excited.giddy" to R.string.feeling_glad_excited_giddy,
    "glad.energetic" to R.string.feeling_glad_energetic,
    "glad.energetic.alert" to R.string.feeling_glad_energetic_alert,
    "glad.energetic.lively" to R.string.feeling_glad_energetic_lively,
    "glad.playful" to R.string.feeling_glad_playful,
    "glad.playful.mischievous" to R.string.feeling_glad_playful_mischievous,
    "glad.playful.joking" to R.string.feeling_glad_playful_joking,
    "glad.creative" to R.string.feeling_glad_creative,
    "glad.creative.inspired" to R.string.feeling_glad_creative_inspired,
    "glad.creative.curious" to R.string.feeling_glad_creative_curious,
    "glad.hopeful" to R.string.feeling_glad_hopeful,
    "glad.hopeful.optimistic" to R.string.feeling_glad_hopeful_optimistic,
    "glad.hopeful.expectant" to R.string.feeling_glad_hopeful_expectant,
    "glad.content" to R.string.feeling_glad_content,
    "glad.content.satisfied" to R.string.feeling_glad_content_satisfied,
    "glad.content.fulfilled" to R.string.feeling_glad_content_fulfilled,
    "strong" to R.string.feeling_strong,
    "strong.secure" to R.string.feeling_strong_secure,
    "strong.secure.safe" to R.string.feeling_strong_secure_safe,
    "strong.secure.grounded" to R.string.feeling_strong_secure_grounded,
    "strong.proud" to R.string.feeling_strong_proud,
    "strong.proud.successful" to R.string.feeling_strong_proud_successful,
    "strong.proud.worthwhile" to R.string.feeling_strong_proud_worthwhile,
    "strong.brave" to R.string.feeling_strong_brave,
    "strong.brave.determined" to R.string.feeling_strong_brave_determined,
    "strong.brave.fearless" to R.string.feeling_strong_brave_fearless,
    "strong.focused" to R.string.feeling_strong_focused,
    "strong.focused.clear" to R.string.feeling_strong_focused_clear,
    "strong.focused.purposeful" to R.string.feeling_strong_focused_purposeful,
    "strong.appreciated" to R.string.feeling_strong_appreciated,
    "strong.appreciated.seen" to R.string.feeling_strong_appreciated_seen,
    "strong.appreciated.affirmed" to R.string.feeling_strong_appreciated_affirmed,
    "strong.respected" to R.string.feeling_strong_respected,
    "strong.respected.trusted" to R.string.feeling_strong_respected_trusted,
    "strong.respected.important" to R.string.feeling_strong_respected_important,
    "calm" to R.string.feeling_calm,
    "calm.grateful" to R.string.feeling_calm_grateful,
    "calm.grateful.humble" to R.string.feeling_calm_grateful_humble,
    "calm.grateful.moved" to R.string.feeling_calm_grateful_moved,
    "calm.loving" to R.string.feeling_calm_loving,
    "calm.loving.warm" to R.string.feeling_calm_loving_warm,
    "calm.loving.tender" to R.string.feeling_calm_loving_tender,
    "calm.trusting" to R.string.feeling_calm_trusting,
    "calm.trusting.open" to R.string.feeling_calm_trusting_open,
    "calm.trusting.reassured" to R.string.feeling_calm_trusting_reassured,
    "calm.relaxed" to R.string.feeling_calm_relaxed,
    "calm.relaxed.serene" to R.string.feeling_calm_relaxed_serene,
    "calm.relaxed.restful" to R.string.feeling_calm_relaxed_restful,
    "calm.present" to R.string.feeling_calm_present,
    "calm.present.centered" to R.string.feeling_calm_present_centered,
    "calm.present.aware" to R.string.feeling_calm_present_aware,
    "calm.caring" to R.string.feeling_calm_caring,
    "calm.caring.kind" to R.string.feeling_calm_caring_kind,
    "calm.caring.generous" to R.string.feeling_calm_caring_generous,
    "sad" to R.string.feeling_sad,
    "sad.lonely" to R.string.feeling_sad_lonely,
    "sad.lonely.abandoned" to R.string.feeling_sad_lonely_abandoned,
    "sad.lonely.isolated" to R.string.feeling_sad_lonely_isolated,
    "sad.hurt" to R.string.feeling_sad_hurt,
    "sad.hurt.offended" to R.string.feeling_sad_hurt_offended,
    "sad.hurt.devastated" to R.string.feeling_sad_hurt_devastated,
    "sad.guilty" to R.string.feeling_sad_guilty,
    "sad.guilty.ashamed" to R.string.feeling_sad_guilty_ashamed,
    "sad.guilty.remorseful" to R.string.feeling_sad_guilty_remorseful,
    "sad.bored" to R.string.feeling_sad_bored,
    "sad.bored.indifferent" to R.string.feeling_sad_bored_indifferent,
    "sad.bored.restless" to R.string.feeling_sad_bored_restless,
    "sad.tired" to R.string.feeling_sad_tired,
    "sad.tired.exhausted" to R.string.feeling_sad_tired_exhausted,
    "sad.tired.empty" to R.string.feeling_sad_tired_empty,
    "sad.disappointed" to R.string.feeling_sad_disappointed,
    "sad.disappointed.dejected" to R.string.feeling_sad_disappointed_dejected,
    "sad.disappointed.discouraged" to R.string.feeling_sad_disappointed_discouraged,
    "angry" to R.string.feeling_angry,
    "angry.irritated" to R.string.feeling_angry_irritated,
    "angry.irritated.impatient" to R.string.feeling_angry_irritated_impatient,
    "angry.irritated.touchy" to R.string.feeling_angry_irritated_touchy,
    "angry.frustrated" to R.string.feeling_angry_frustrated,
    "angry.frustrated.blocked" to R.string.feeling_angry_frustrated_blocked,
    "angry.frustrated.resigned" to R.string.feeling_angry_frustrated_resigned,
    "angry.critical" to R.string.feeling_angry_critical,
    "angry.critical.skeptical" to R.string.feeling_angry_critical_skeptical,
    "angry.critical.judgmental" to R.string.feeling_angry_critical_judgmental,
    "angry.distant" to R.string.feeling_angry_distant,
    "angry.distant.cold" to R.string.feeling_angry_distant_cold,
    "angry.distant.detached" to R.string.feeling_angry_distant_detached,
    "angry.jealous" to R.string.feeling_angry_jealous,
    "angry.jealous.suspicious" to R.string.feeling_angry_jealous_suspicious,
    "angry.jealous.bitter" to R.string.feeling_angry_jealous_bitter,
    "angry.hostile" to R.string.feeling_angry_hostile,
    "angry.hostile.hateful" to R.string.feeling_angry_hostile_hateful,
    "angry.hostile.aggressive" to R.string.feeling_angry_hostile_aggressive,
    "scared" to R.string.feeling_scared,
    "scared.anxious" to R.string.feeling_scared_anxious,
    "scared.anxious.overwhelmed" to R.string.feeling_scared_anxious_overwhelmed,
    "scared.anxious.tense" to R.string.feeling_scared_anxious_tense,
    "scared.insecure" to R.string.feeling_scared_insecure,
    "scared.insecure.hesitant" to R.string.feeling_scared_insecure_hesitant,
    "scared.insecure.shy" to R.string.feeling_scared_insecure_shy,
    "scared.confused" to R.string.feeling_scared_confused,
    "scared.confused.lost" to R.string.feeling_scared_confused_lost,
    "scared.confused.scattered" to R.string.feeling_scared_confused_scattered,
    "scared.helpless" to R.string.feeling_scared_helpless,
    "scared.helpless.powerless" to R.string.feeling_scared_helpless_powerless,
    "scared.helpless.stuck" to R.string.feeling_scared_helpless_stuck,
    "scared.rejected" to R.string.feeling_scared_rejected,
    "scared.rejected.excluded" to R.string.feeling_scared_rejected_excluded,
    "scared.rejected.unwanted" to R.string.feeling_scared_rejected_unwanted,
    "scared.inferior" to R.string.feeling_scared_inferior,
    "scared.inferior.inadequate" to R.string.feeling_scared_inferior_inadequate,
    "scared.inferior.small" to R.string.feeling_scared_inferior_small
)
