package se.partee71.dagboken.data.repository

import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import org.junit.Test
import se.partee71.dagboken.core.model.Activity
import se.partee71.dagboken.core.model.Checkin
import se.partee71.dagboken.core.model.Dose
import se.partee71.dagboken.core.model.DoseStatus
import se.partee71.dagboken.core.model.Event
import se.partee71.dagboken.core.model.IllnessEpisode
import se.partee71.dagboken.core.model.Slot
import se.partee71.dagboken.data.FakeCollectionFactory

/**
 * Dagbokens data mot `FakeCollection`: aktiviteter och händelser ett intervall i taget (HIST-8) och
 * radering av varje posttyp – posten tas bort med sin anteckning (HIST-5, DAT-7), också utan nät.
 */
class DiaryRepositoriesTest {

    private val factory = FakeCollectionFactory()
    private val activities = DefaultActivityRepository(factory)
    private val events = DefaultEventRepository(factory)
    private val doses = testDoses(factory, TimeZone.of("Europe/Stockholm"))
    private val illnesses = DefaultIllnessRepository(factory)

    private val day = LocalDate(2026, 10, 6)
    private val before = LocalDate(2025, 10, 6)

    @Test
    fun `aktiviteter och händelser läses för ett intervall, båda dagarna inräknade (HIST-8)`() = runTest {
        factory.activities().batch(
            listOf(Activity("in", day, LocalTime(9, 0)), Activity("kant", LocalDate(2025, 10, 7)), Activity("ute", before)),
        ).getOrThrow()
        factory.events().batch(listOf(Event("in", day), Event("ute", before))).getOrThrow()
        assertEquals(setOf("in", "kant"), activities.observeDays(LocalDate(2025, 10, 7), day).first().map { it.id }.toSet())
        assertEquals(listOf("in"), events.observeDays(LocalDate(2025, 10, 7), day).first().map { it.id })
    }

    @Test
    fun `en raderad aktivitet, händelse och vid behov-dos försvinner med sin anteckning – också utan nät (HIST-5, DAT-7)`() = runTest {
        factory.activities().upsert(Activity("a", day, note = "Gick runt sjön")).getOrThrow()
        factory.events().upsert(Event("e", day, note = "Aura först")).getOrThrow()
        val prn = Dose("d", day, Slot.AS_NEEDED, "Alvedon", status = DoseStatus.TAKEN, prnId = "alvedon", note = "Huvudvärk")
        factory.doses().upsert(prn).getOrThrow()
        factory.store.online = false

        activities.delete("a").getOrThrow()
        events.delete("e").getOrThrow()
        doses.remove(prn).getOrThrow()

        assertNull(factory.activities().get("a").getOrThrow())
        assertNull(factory.events().get("e").getOrThrow())
        assertNull(factory.doses().get("d").getOrThrow())
    }

    @Test
    fun `en receptdos raderas inte utan markeras överhoppad, så att den inte skapas igen (MED-15, som 3x)`() = runTest {
        val taken = Dose(
            "recept_levaxin_2026-10-06_Morgon", day, Slot.MORNING, "Levaxin", status = DoseStatus.TAKEN,
            prescriptionId = "levaxin", takenAt = kotlin.time.Instant.fromEpochSeconds(1_791_000_000), note = "Med frukost",
        )
        factory.doses().upsert(taken).getOrThrow()
        factory.store.online = false

        doses.remove(taken).getOrThrow()

        val stored = factory.doses().get(taken.id).getOrThrow()
        assertEquals(DoseStatus.SKIPPED, stored?.status)
        assertNull(stored?.takenAt)
    }

    @Test
    fun `en raderad incheckning försvinner bara under sin episod, och episoden står kvar (HIST-5, SJ-9)`() = runTest {
        factory.illnessEpisodes().batch(listOf(IllnessEpisode("flu", "Förkylning", day), IllnessEpisode("migran", "Migrän", day))).getOrThrow()
        factory.checkins("flu").upsert(Checkin("c", day, note = "Feber 38,2")).getOrThrow()
        factory.checkins("migran").upsert(Checkin("c", day)).getOrThrow()

        illnesses.deleteCheckin("flu", "c").getOrThrow()

        assertEquals(emptyList(), illnesses.observeCheckins("flu").first())
        assertEquals(listOf("c"), illnesses.observeCheckins("migran").first().map { it.id })
        assertEquals(listOf("flu", "migran"), illnesses.observeEpisodes().first().map { it.id })
    }
}
