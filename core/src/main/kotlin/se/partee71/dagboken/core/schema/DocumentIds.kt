package se.partee71.dagboken.core.schema

/**
 * Hur ett dokument-ID får se ut när det kommer utifrån, t.ex. i en djuplänk från en notis:
 * Firestores egna ID:n (20 tecken), 3.x UUID:er och receptdosernas id med tidpunktens svenska namn
 * ("recept_abc_2026-10-04_Förmiddag", DAT-8) ryms, men aldrig ett `/` (som byter sökväg), `.`/`..`,
 * mellanslag eller något orimligt långt.
 */
object DocumentIds {
    private val allowed = Regex("[A-Za-z0-9_åäöÅÄÖ-]{1,128}")

    fun isValid(id: String): Boolean = allowed.matches(id)
}
