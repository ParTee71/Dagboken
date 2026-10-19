package se.partee71.dagboken.core.engine

import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.Test
import se.partee71.dagboken.core.model.Option
import se.partee71.dagboken.core.model.OptionKind

/** Aktivitets- och händelseformulärens typval (AKT-1, AKT-2, SET-9). */
class EntryFormsTest {

    private val walk = Option("walk", OptionKind.ACTIVITY, "Promenad", favorite = true, sortOrder = 2)
    private val work = Option("work", OptionKind.ACTIVITY, "Jobb", sortOrder = 0)
    private val gym = Option("gym", OptionKind.ACTIVITY, "Träning", favorite = true, sortOrder = 1)
    private val old = Option("old", OptionKind.ACTIVITY, "Bowling", sortOrder = 3, archived = true)
    private val migraine = Option("migraine", OptionKind.EVENT, "Migrän", favorite = true)

    @Test fun `stjärnmärkta som chips och övriga under Fler typer, i listans ordning – bara listans egna aktiva`() {
        val choices = typeChoices(listOf(walk, work, gym, old, migraine), OptionKind.ACTIVITY, chosen = "")
        assertEquals(listOf(gym, walk), choices.favorites)
        assertEquals(listOf(work), choices.more)
        assertNull(choices.other)
        assertEquals(TypeChoices(listOf(migraine), emptyList()), typeChoices(listOf(walk, migraine), OptionKind.EVENT, chosen = ""))
    }

    @Test fun `ett arkiverat alternativ som posten redan har står kvar med sitt namn`() {
        assertEquals(listOf(work, old), typeChoices(listOf(walk, work, old), OptionKind.ACTIVITY, chosen = "old").more)
    }

    @Test fun `Övrigt står alltid sist under Fler typer – också när listan saknar det (AKT-2)`() {
        val missing = typeChoices(listOf(walk, work), OptionKind.ACTIVITY, chosen = "", otherId = OTHER_ACTIVITY_ID)
        assertEquals(OTHER_ACTIVITY_ID, missing.other)
        val other = Option(OTHER_ACTIVITY_ID, OptionKind.ACTIVITY, "Övrigt", sortOrder = 0)
        val listed = typeChoices(listOf(walk, other, work), OptionKind.ACTIVITY, chosen = "", otherId = OTHER_ACTIVITY_ID)
        assertEquals(listOf(work, other), listed.more, "sist, oavsett plats i listan")
        assertNull(listed.other)
        val archived = typeChoices(listOf(walk, other.copy(archived = true)), OptionKind.ACTIVITY, chosen = "", otherId = OTHER_ACTIVITY_ID)
        assertEquals(OTHER_ACTIVITY_ID, archived.other, "ett arkiverat Övrigt finns ändå att välja")
    }

    @Test fun `tomt utan alternativ`() {
        assertTrue(typeChoices(emptyList(), OptionKind.EVENT, chosen = "").isEmpty)
        assertFalse(typeChoices(emptyList(), OptionKind.ACTIVITY, chosen = "", otherId = OTHER_ACTIVITY_ID).isEmpty)
    }

    @Test fun `Övrigt har samma id som konverteraren ger det`() {
        assertEquals("activity-ovrigt-c067e8", OTHER_ACTIVITY_ID)
    }
}
