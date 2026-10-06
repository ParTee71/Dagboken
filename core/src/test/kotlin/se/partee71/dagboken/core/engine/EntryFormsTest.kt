package se.partee71.dagboken.core.engine

import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import org.junit.Test
import se.partee71.dagboken.core.model.Activity
import se.partee71.dagboken.core.model.Option
import se.partee71.dagboken.core.model.OptionKind

/** Aktivitets- och händelseformulärens typval (AKT-1, AKT-2, SET-9) och förvalen för en ny aktivitet (AKT-12). */
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

    @Test fun `en ny aktivitet får den senast loggades typ, beskrivning och tidsåtgång (AKT-12)`() {
        val day = LocalDate(2026, 10, 5)
        val new = Activity("ny", LocalDate(2026, 10, 6), LocalTime(10, 0), energy = 0)
        val earlier = Activity("a", day, LocalTime(8, 0), optionId = "walk", minutes = 30, energy = 4)
        val latest = Activity("b", day, LocalTime(18, 0), optionId = OTHER_ACTIVITY_ID, customText = "Svamplockning", minutes = 90, energy = -3, note = "Regn", createdAt = Instant.fromEpochSeconds(2))
        val sameTimeOlder = Activity("c", day, LocalTime(18, 0), optionId = "work", minutes = 15, createdAt = Instant.fromEpochSeconds(1))
        val prefilled = new.prefilledFrom(listOf(earlier, latest, sameTimeOlder))
        assertEquals(new.copy(optionId = OTHER_ACTIVITY_ID, customText = "Svamplockning", minutes = 90), prefilled, "bara typ och tidsåtgång – inte energi eller anteckning")
        assertEquals(new, new.prefilledFrom(emptyList()))
    }

    @Test fun `en arkiverad typ förifylls inte – bara tidsåtgången, och Övrigt alltid (AKT-12)`() {
        val new = Activity("ny", LocalDate(2026, 10, 6), LocalTime(10, 0))
        val bowling = Activity("b", LocalDate(2026, 10, 5), optionId = "old", minutes = 120)
        assertEquals(new.copy(minutes = 120), new.prefilledFrom(listOf(bowling), listOf(old)))
        assertEquals(new.copy(optionId = "old", minutes = 120), new.prefilledFrom(listOf(bowling), listOf(old.copy(archived = false))))
        val other = Activity("o", LocalDate(2026, 10, 5), optionId = OTHER_ACTIVITY_ID, customText = "Bad", minutes = 30)
        val archivedOther = Option(OTHER_ACTIVITY_ID, OptionKind.ACTIVITY, "Övrigt", archived = true)
        assertEquals(new.copy(optionId = OTHER_ACTIVITY_ID, customText = "Bad", minutes = 30), new.prefilledFrom(listOf(other), listOf(archivedOther)))
    }
}
