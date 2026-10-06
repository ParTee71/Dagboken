package se.partee71.dagboken.data.repository

import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant
import kotlinx.coroutines.awaitCancellation
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
import se.partee71.dagboken.core.engine.OTHER_ACTIVITY_ID
import se.partee71.dagboken.core.model.SymptomScore
import se.partee71.dagboken.core.model.Option
import se.partee71.dagboken.core.model.OptionKind
import se.partee71.dagboken.data.common.CollectionFactory
import se.partee71.dagboken.data.common.EntityCollection
import se.partee71.dagboken.core.schema.ActivityCodec
import se.partee71.dagboken.core.schema.CollectionNames
import se.partee71.dagboken.core.schema.DocumentRules
import se.partee71.dagboken.core.schema.EventCodec
import se.partee71.dagboken.data.FakeCollectionFactory
import se.partee71.dagboken.data.FixedClock
import se.partee71.dagboken.data.firestore.Paths

/**
 * Dagbokens data mot `FakeCollection`: aktiviteter och händelser ett intervall i taget (HIST-8),
 * formulärens nya poster och sparning (AKT-9, AKT-12, HAN-1) och radering av varje posttyp – posten tas
 * bort med sin anteckning (HIST-5, DAT-7), också utan nät.
 */
class DiaryRepositoriesTest {

    private val clock = FixedClock(Instant.fromEpochSeconds(1_791_270_000))
    private val factory = FakeCollectionFactory(clock = clock)
    private val activities = DefaultActivityRepository(factory, clock)
    private val events = DefaultEventRepository(factory, clock)
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

    // ── Formulären: ny och ändrad (AKT-9, AKT-12, HAN-1) ─────────────────

    private val uid: String get() = factory.scope.uid.value!!


    @Test
    fun `en ny aktivitet får id och skapandetid en gång och den senaste aktivitetens typ och tidsåtgång (AKT-12)`() = runTest {
        factory.activities().batch(
            listOf(
                Activity("äldst", LocalDate(2026, 10, 1), LocalTime(9, 0), optionId = "walk", minutes = 30),
                Activity("senast", LocalDate(2026, 10, 5), LocalTime(18, 0), optionId = "work", minutes = 90, energy = 5, note = "Möte"),
                Activity("förra året", LocalDate(2025, 10, 5), optionId = "gym", minutes = 60),
            ),
        ).getOrThrow()
        factory.store.online = false
        val new = activities.new(day, LocalTime(10, 15), today = day)
        assertEquals(Activity(new.id, day, LocalTime(10, 15), optionId = "work", minutes = 90, createdAt = clock.now()), new, "bara typ och tidsåtgång")
        assertTrue(new.id.isNotEmpty())
        assertNull(factory.activities().get(new.id).getOrThrow(), "inget sparas förrän formuläret sparas")
        assertEquals(Activity("x", day, createdAt = clock.now()), activities.new(day, LocalTime(0, 0), today = LocalDate(2024, 1, 1)).copy(id = "x", time = null), "utan tidigare aktivitet: inga förval")
    }

    @Test
    fun `aktivitet och händelse med alla fält ifyllda sparas, godtas av rules och läses tillbaka lika (regel 1)`() = runTest {
        val activity = activities.new(day, LocalTime(17, 0), today = day).copy(
            optionId = OTHER_ACTIVITY_ID, customText = "Svamplockning", energy = -2, stress = 1,
            symptoms = listOf(SymptomScore("symptom-huvudvark-d55e7d", 1), SymptomScore("symptom-ovrigt-c067e8", 5, "Myggbett")),
            recovering = true, drain = true, minutes = 45, note = "Regn hela vägen",
        )
        val event = events.new(day, LocalTime(10, 5)).copy(
            optionId = "event-yrsel-6db696", severity = 7, durationMinutes = 10, triggers = "Snabb uppresning", actions = "Satte mig ner", note = "Första gången",
        )
        assertEquals(EventRepository.DEFAULT_SEVERITY, events.new(day, LocalTime(10, 5)).severity)
        activities.save(null, activity).getOrThrow()
        events.save(null, event).getOrThrow()
        assertEquals(activity, activities.get(activity.id).getOrThrow())
        assertEquals(event, events.get(event.id).getOrThrow())
        assertEquals(emptyList(), DocumentRules.validate(CollectionNames.ACTIVITIES, ActivityCodec.encode(activity)))
        assertEquals(emptyList(), DocumentRules.validate(CollectionNames.EVENTS, EventCodec.encode(event)))
        assertTrue(activities.save(null, Activity("utan", day)).exceptionOrNull() is IllegalArgumentException, "ny utan createdAt skrivs inte")
        assertNull(factory.activities().get("utan").getOrThrow())
    }

    @Test
    fun `en ändrad aktivitet och händelse skriver bara ändrade fält – id, skapandetid och okända fält står kvar, och raderad återuppstår inte`() = runTest {
        val created = Instant.fromEpochSeconds(1_790_000_000)
        val activity = Activity("a1", day, LocalTime(9, 0), optionId = "walk", energy = 2, createdAt = created)
        val event = Event("e1", day, LocalTime(9, 0), optionId = "migraine", severity = 4, createdAt = created)
        factory.store.set(Paths.activities(uid), "a1", ActivityCodec.encode(activity.copy(note = "Annan enhet")) + ("framtidaFält" to "kvar"), merge = false)
        factory.store.set(Paths.events(uid), "e1", EventCodec.encode(event.copy(note = "Annan enhet")) + ("framtidaFält" to "kvar"), merge = false)

        activities.save(activity, activity.copy(id = "annat", energy = 6, date = LocalDate(2026, 10, 5))).getOrThrow()
        events.save(event, event.copy(severity = 8, triggers = "Stress")).getOrThrow()

        val rawActivity = factory.store.read(Paths.activities(uid), "a1")!!
        assertEquals(6L, rawActivity["energy"])
        assertEquals("2026-10-05", rawActivity["date"])
        assertEquals("Annan enhet", rawActivity["note"], "ett fält som inte ändrats i formuläret skrivs inte över")
        assertEquals("kvar", rawActivity["framtidaFält"])
        assertEquals(created, activities.get("a1").getOrThrow()?.createdAt, "skapandetiden står kvar")
        assertNull(factory.store.read(Paths.activities(uid), "annat"), "id:t är det lästa")
        val rawEvent = factory.store.read(Paths.events(uid), "e1")!!
        assertEquals(8L, rawEvent["severity"])
        assertEquals("Stress", rawEvent["triggers"])
        assertEquals("kvar", rawEvent["framtidaFält"])

        events.delete("e1").getOrThrow()
        events.save(event, event.copy(severity = 1))
        assertNull(factory.store.read(Paths.events(uid), "e1"), "en raderad händelse återuppstår inte")
    }

    @Test
    fun `svarar inte cachen visas en ny aktivitet utan förval efter en kort väntan, och en arkiverad typ förifylls inte (AKT-12)`() = runTest {
        factory.activities().upsert(Activity("förra", day, LocalTime(9, 0), optionId = "bowling", minutes = 120)).getOrThrow()
        factory.options().upsert(Option("bowling", OptionKind.ACTIVITY, "Bowling", archived = true)).getOrThrow()
        assertEquals(null to 120, activities.new(day, LocalTime(10, 0), today = day).let { it.optionId.ifEmpty { null } to it.minutes })

        val silent = object : CollectionFactory by factory {
            override fun activities(): EntityCollection<Activity> = object : EntityCollection<Activity> by factory.activities() {
                override suspend fun cachedBetween(field: String, from: Any, to: Any): Result<List<Activity>> = awaitCancellation()
            }
        }
        val start = testScheduler.currentTime
        val new = DefaultActivityRepository(silent, clock).new(day, LocalTime(10, 0), today = day)
        assertEquals(Activity(new.id, day, LocalTime(10, 0), createdAt = clock.now()), new)
        assertEquals(ActivityRepository.PREFILL_WAIT.inWholeMilliseconds, testScheduler.currentTime - start, "väntar bara den korta stunden")
    }
}
