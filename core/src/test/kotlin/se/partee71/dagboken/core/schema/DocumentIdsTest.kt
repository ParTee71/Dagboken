package se.partee71.dagboken.core.schema

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** ID:n som kommer utifrån (djuplänken från en påminnelse). */
class DocumentIdsTest {

    @Test
    fun `Firestores och verktygens ID godtas`() {
        assertTrue(DocumentIds.isValid("aZ09bC12dE34fG56hI78"))
        assertTrue(DocumentIds.isValid("gran-canaria"))
        assertTrue(DocumentIds.isValid("rad_1"))
        assertTrue(DocumentIds.isValid("x".repeat(128)))
        assertTrue(DocumentIds.isValid("recept_6f1c2a9e-0b7d_2026-09-21_Förmiddag"), "3.x receptdos (DAT-8)")
        assertTrue(DocumentIds.isValid("recept_r1_2026-09-21_Kväll"))
    }

    @Test
    fun `tomt, sökvägar, punkter, mellanslag och för långt nekas`() {
        listOf("", "a/b", "../t1", ".", "..", "t 1", "t1\n", "é", "ü", "x".repeat(129), "x".repeat(2000)).forEach {
            assertFalse(DocumentIds.isValid(it), "'$it'")
        }
    }
}
