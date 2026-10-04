package se.partee71.dagboken.core.schema

/**
 * Hur ett dokument-ID får se ut när det kommer utifrån, t.ex. i en djuplänk från en notis:
 * Firestores egna ID:n (20 tecken) och verktygens läsbara ("rx_abc_2026-10-04_1") ryms, men aldrig ett
 * `/` (som byter sökväg), `.`/`..` eller något orimligt långt.
 */
object DocumentIds {
    private val allowed = Regex("[A-Za-z0-9_-]{1,128}")

    fun isValid(id: String): Boolean = allowed.matches(id)
}
