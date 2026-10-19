package se.partee71.dagboken.core.engine

import se.partee71.dagboken.core.legacy.LegacyDefaults
import se.partee71.dagboken.core.model.Option
import se.partee71.dagboken.core.model.OptionIds
import se.partee71.dagboken.core.model.OptionKind

// Aktivitets- och händelseformulärens val (AKT-1, AKT-2, AKT-12, SET-9) – vilka typer som visas var, och förvalen.

/**
 * "Övrigt" bland aktivitetstyperna (AKT-2): 3.x:s fasta alternativ för egen beskrivning, med samma id som
 * konverteraren ger det ([OptionIds.of]) – posten sparar typen och beskrivningen i `customText`.
 */
val OTHER_ACTIVITY_ID: String = OptionIds.of(OptionKind.ACTIVITY, LegacyDefaults.OTHER)

/**
 * Typerna i ett formulär: [favorites] som chips och [more] under "Fler typer", båda i listans ordning.
 * [other] är id:t för "Övrigt" när det ska stå sist under "Fler typer" men saknas i listan (anroparen ger
 * det namnet); `null` när det inte behövs.
 */
data class TypeChoices(val favorites: List<Option>, val more: List<Option>, val other: String? = null) {
    /** Om det finns något att välja alls. */
    val isEmpty: Boolean get() = favorites.isEmpty() && more.isEmpty() && other == null
}

/**
 * Typerna att välja bland i listan [kind] (AKT-1, SET-9): de aktiva i listans ordning – stjärnmärkta som chips,
 * övriga under "Fler typer" – och ett arkiverat som posten redan har ([chosen]), så att en ändrad post visar sin
 * typ med namn. Med [otherId] ("Övrigt", AKT-2) står det alternativet alltid sist under "Fler typer", som i 3.x:
 * finns det inte bland de aktiva (aldrig skapat, eller arkiverat) blir det [TypeChoices.other].
 */
fun typeChoices(options: List<Option>, kind: OptionKind, chosen: String, otherId: String? = null): TypeChoices {
    val shown = options.filter { it.kind == kind && (!it.archived || it.id == chosen) }.sortedBy { it.sortOrder }
    val more = shown.filterNot { it.favorite }.sortedBy { it.id == otherId }
    return TypeChoices(shown.filter { it.favorite }, more, otherId?.takeIf { id -> shown.none { it.id == id } })
}
