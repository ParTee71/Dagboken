package se.partee71.dagboken.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import se.partee71.dagboken.R

/** Medicinenheterna att välja bland i recept- och vid behov-formuläret (REC-1, FAV-1) – "sprut" för sprejer. */
val MEDICINE_UNITS = listOf("mg", "ml", "st", "g", "mcg", "IE", "dropp", "sprut", "tablett", "kapsel", "puff", "dos")

/**
 * Enheten som val under etiketten "Enhet": [ChoiceChips] med [units]. En lagrad enhet utanför listan
 * står kvar som eget val; en tom enhet ger inget extra chip och inget valt.
 */
@Composable
fun UnitChoice(unit: String, onUnitChange: (String) -> Unit, modifier: Modifier = Modifier, units: List<String> = MEDICINE_UNITS) {
    LabeledGroup(stringResource(R.string.prn_unit), modifier) {
        val shown = if (unit in units || unit.isBlank()) units else units + unit
        ChoiceChips(shown, unit, onUnitChange, label = { it })
    }
}
