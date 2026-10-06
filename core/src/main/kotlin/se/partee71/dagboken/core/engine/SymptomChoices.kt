package se.partee71.dagboken.core.engine

import se.partee71.dagboken.core.legacy.LegacyDefaults
import se.partee71.dagboken.core.model.Option
import se.partee71.dagboken.core.model.OptionIds
import se.partee71.dagboken.core.model.OptionKind
import se.partee71.dagboken.core.model.SymptomScore

// Symptomvalen i formulären (SCR-2, AKT-6, SJ-3) – vilka alternativ som visas och vilket som är "Övrigt".

/**
 * "Övrigt" bland symptomen (AKT-6, SCR-2): 3.x:s fasta alternativ för egen beskrivning, med samma id som
 * konverteraren och Listor ger det ([OptionIds.of]) – ett namnbyte behåller id:t och därmed fritexten.
 */
val OTHER_SYMPTOM_ID: String = OptionIds.of(OptionKind.SYMPTOM, LegacyDefaults.OTHER)

/**
 * Symptomen som går att välja i ett formulär (SCR-2): listans aktiva symptom i listans ordning, och de
 * arkiverade som posten redan har ([chosen]) – så att en ändrad post visar sina symptom med namn.
 */
fun symptomChoices(options: List<Option>, chosen: List<SymptomScore>): List<Option> {
    val ids = chosen.mapTo(HashSet()) { it.optionId }
    return options.filter { it.kind == OptionKind.SYMPTOM && (!it.archived || it.id in ids) }
}
