package se.partee71.dagboken.ui.common

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.res.stringResource
import se.partee71.dagboken.R
import se.partee71.dagboken.core.engine.EndDateError
import se.partee71.dagboken.core.engine.SleepFlag
import se.partee71.dagboken.core.engine.SleepQualityKind
import se.partee71.dagboken.core.engine.TrendDirection
import se.partee71.dagboken.core.engine.WatchMetric
import se.partee71.dagboken.core.engine.WatchUnit
import se.partee71.dagboken.core.engine.health.OptionalHealthMetric
import se.partee71.dagboken.core.model.IllnessEpisode
import se.partee71.dagboken.core.model.Occasion
import se.partee71.dagboken.core.model.OptionKind
import se.partee71.dagboken.core.model.Sex
import se.partee71.dagboken.core.model.Slot

// Namnen på modellens val i UI:t – enda stället (regel 4).

/** Medicintidpunktens namn i UI:t (DAT-1) – enda stället, för påminnelser, recept och doser. */
@StringRes
fun Slot.label(): Int = when (this) {
    Slot.MORNING -> R.string.slot_morning
    Slot.MIDMORNING -> R.string.slot_midmorning
    Slot.LUNCH -> R.string.slot_lunch
    Slot.AFTERNOON -> R.string.slot_afternoon
    Slot.EVENING -> R.string.slot_evening
    Slot.NIGHT -> R.string.slot_night
    Slot.AS_NEEDED -> R.string.slot_as_needed
}

/** Måendetillfällets namn i UI:t (NOT-4, HEM-4) – enda stället, för påminnelser och Idag. */
@StringRes
fun Occasion.label(): Int = when (this) {
    Occasion.BREAKFAST -> R.string.occasion_breakfast
    Occasion.LUNCH -> R.string.occasion_lunch
    Occasion.DINNER -> R.string.occasion_dinner
    Occasion.BEDTIME -> R.string.occasion_bedtime
}

/** Könets namn i UI:t (HLS-11) – Profil och sömnkvalitetens förklaring. */
@StringRes
fun Sex.label(): Int = when (this) {
    Sex.FEMALE -> R.string.sex_female
    Sex.MALE -> R.string.sex_male
    Sex.UNSPECIFIED -> R.string.sex_unspecified
}

/** Alternativlistans namn i UI:t (DAT-9) – Listor och formulärens val. */
@StringRes
fun OptionKind.label(): Int = when (this) {
    OptionKind.ACTIVITY -> R.string.lists_activities
    OptionKind.SYMPTOM -> R.string.symptoms
    OptionKind.EVENT -> R.string.lists_events
}

/** Trendens riktning som text ("Trend uppåt", TRD-13) – trendpillen under diagrammen och kortens sammanfattning i Trender. */
@StringRes
fun TrendDirection.label(): Int = when (this) {
    TrendDirection.RISING -> R.string.chart_trend_rising
    TrendDirection.FALLING -> R.string.chart_trend_falling
    TrendDirection.FLAT -> R.string.chart_trend_flat
}

/** Klockmåttets namn i sitt eget diagram (TRD-11, TRD-15, HLS-12) – Trender → Klocka och Hälsa idag (HLS-6). */
@StringRes
fun WatchMetric.label(): Int = when (this) {
    WatchMetric.STEPS -> R.string.trends_card_steps
    WatchMetric.RESTING_HEART_RATE -> R.string.trends_card_heart_rate
    WatchMetric.HEART_RATE_AVG -> R.string.trends_series_heart_rate_avg
    WatchMetric.SLEEP_TOTAL -> R.string.trends_series_sleep_total
    WatchMetric.SLEEP_DEEP -> R.string.trends_series_sleep_deep
    WatchMetric.SLEEP_REM -> R.string.trends_series_sleep_rem
    WatchMetric.SLEEP_LIGHT -> R.string.trends_series_sleep_light
    WatchMetric.SLEEP_AWAKE -> R.string.trends_series_sleep_awake
    WatchMetric.EXERCISE -> R.string.trends_card_exercise
    WatchMetric.ACTIVE_CALORIES -> R.string.trends_card_calories
    WatchMetric.DISTANCE -> R.string.trends_card_distance
    WatchMetric.OXYGEN_SATURATION -> R.string.trends_card_oxygen
}

/** Klockmåttets namn utanför sitt eget diagram (TRD-17): "Dygnssnittspuls", "Sömnlängd", "Djupsömn", "REM-sömn". */
@StringRes
fun WatchMetric.qualifiedLabel(): Int = when (this) {
    WatchMetric.HEART_RATE_AVG -> R.string.trends_compare_heart_rate_avg
    WatchMetric.SLEEP_TOTAL -> R.string.trends_compare_sleep_total
    WatchMetric.SLEEP_DEEP -> R.string.trends_series_sleep_deep_share
    WatchMetric.SLEEP_REM -> R.string.trends_compare_sleep_rem
    else -> label()
}

/** Enhetens kortform ("bpm", "h", "skala") – legender och Hälsa idag. */
@StringRes
fun WatchUnit.label(): Int = when (this) {
    WatchUnit.STEPS -> R.string.unit_steps
    WatchUnit.BPM -> R.string.unit_bpm
    WatchUnit.HOURS -> R.string.unit_hours
    WatchUnit.MINUTES -> R.string.unit_minutes
    WatchUnit.KCAL -> R.string.unit_kcal
    WatchUnit.KM -> R.string.unit_km
    WatchUnit.PERCENT -> R.string.unit_percent
    WatchUnit.POINTS -> R.string.unit_points
    WatchUnit.SCALE -> R.string.unit_scale
}

/** Sömnkvalitetens delpoäng (HLS-10): "Längd", "Effektivitet" … – Trender och Hälsa idag. */
@StringRes
fun SleepQualityKind.label(): Int = when (this) {
    SleepQualityKind.DURATION -> R.string.trends_series_sleep_duration
    SleepQualityKind.EFFICIENCY -> R.string.trends_series_sleep_efficiency
    SleepQualityKind.REGULARITY -> R.string.trends_series_sleep_regularity
    SleepQualityKind.DEEP -> R.string.trends_series_sleep_deep_share
    SleepQualityKind.REM -> R.string.trends_series_sleep_rem_share
    SleepQualityKind.WASO -> R.string.trends_series_sleep_waso
}

/** Sömnkvalitetens varningsrader (HLS-10): låg syremättnad och förhöjd sovpuls – bara i Hälsa idag. */
@StringRes
fun SleepFlag.label(): Int = when (this) {
    SleepFlag.LOW_OXYGEN_SATURATION -> R.string.health_sleep_flag_low_oxygen
    SleepFlag.ELEVATED_SLEEPING_HEART_RATE -> R.string.health_sleep_flag_high_heart_rate
}

/** Ett valfritt klockmått som kan sakna åtkomst (HLS-14) – samma namn som måttets kort i Klocka. */
@StringRes
fun OptionalHealthMetric.label(): Int = when (this) {
    OptionalHealthMetric.EXERCISE -> R.string.trends_card_exercise
    OptionalHealthMetric.ACTIVE_ENERGY -> R.string.trends_card_calories
    OptionalHealthMetric.DISTANCE -> R.string.trends_card_distance
    OptionalHealthMetric.OXYGEN_SATURATION -> R.string.trends_card_oxygen
    OptionalHealthMetric.HISTORY -> R.string.health_metric_history
}

/** Sjukdomsepisodens namn – typen, eller "Sjukdom" utan typ (HEM-12, HIST-9): Idag och Dagbok. */
@Composable
@ReadOnlyComposable
fun IllnessEpisode.title(): String = type.ifBlank { stringResource(R.string.log_illness) }

/** En fritext som namn: `null` när den saknas eller bara är blanksteg – så att namnet ur listan tar över. */
fun String?.nonBlank(): String? = this?.takeIf { it.isNotBlank() }

/** SJ-4, SJ-12: felet för ett slutdatum som `endDateError` avvisar – samma text i avsluta-frågan och episodformuläret. */
@get:StringRes
val EndDateError.message: Int
    get() = when (this) {
        EndDateError.BEFORE_START -> R.string.prescription_error_end_before_start
        EndDateError.AFTER_TODAY -> R.string.illness_end_after_today
    }
