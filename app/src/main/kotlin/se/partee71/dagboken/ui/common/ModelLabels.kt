package se.partee71.dagboken.ui.common

import androidx.annotation.StringRes
import se.partee71.dagboken.R
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
