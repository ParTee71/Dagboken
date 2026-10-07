package se.partee71.dagboken.data.repository

import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Instant
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import se.partee71.dagboken.core.model.Activity
import se.partee71.dagboken.core.model.Checkin
import se.partee71.dagboken.core.model.Dose
import se.partee71.dagboken.core.model.Event
import se.partee71.dagboken.core.model.Identified
import se.partee71.dagboken.core.model.IllnessEpisode
import se.partee71.dagboken.core.model.Screening
import se.partee71.dagboken.data.FakeCollectionFactory
import se.partee71.dagboken.data.FixedClock
import se.partee71.dagboken.data.common.EntityCollection

/**
 * SJ-11 för varje formulär på `StoredEntries` (mående, aktivitet, händelse, episod, incheckning, dos): en ändring
 * behåller skapandetiden – en ny `createdAt` skriver ingenting – och en äldre post utan `createdAt` går fortfarande
 * att ändra. Syntetisk data.
 */
@RunWith(Parameterized::class)
class EditKeepsCreatedAtTest(private val case: Case<*>) {

    /** En posttyp: dess samling, formulärets [EntryStore], en lagrad post och samma post med ny anteckning respektive skapandetid. */
    class Case<T : Identified>(
        private val name: String,
        val collection: (FakeCollectionFactory) -> EntityCollection<T>,
        val store: (FakeCollectionFactory, FixedClock) -> EntryStore<T>,
        val stored: T,
        val withNote: (T, String) -> T,
        val withCreatedAt: (T, Instant?) -> T,
    ) {
        override fun toString() = name
    }

    private val clock = FixedClock(CREATED)
    private val factory = FakeCollectionFactory(clock = clock)

    @Test
    fun `en ändring med ny skapandetid avvisas och skriver ingenting`() = runTest { check(case) }

    @Test
    fun `en äldre post utan skapandetid går att ändra och får ingen`() = runTest { checkWithoutCreatedAt(case) }

    private suspend fun <T : Identified> check(case: Case<T>) {
        case.collection(factory).upsert(case.stored).getOrThrow()
        val store = case.store(factory, clock)
        val moved = case.withCreatedAt(case.withNote(case.stored, "Ändrad"), Instant.fromEpochSeconds(1_800_000_000))

        assertTrue(store.save(case.stored, moved).exceptionOrNull() is IllegalArgumentException)
        assertEquals(case.stored, store.get(case.stored.id).getOrThrow())

        store.save(case.stored, case.withNote(case.stored, "Ändrad")).getOrThrow()
        assertEquals(case.withNote(case.stored, "Ändrad"), store.get(case.stored.id).getOrThrow())
    }

    private suspend fun <T : Identified> checkWithoutCreatedAt(case: Case<T>) {
        val old = case.withCreatedAt(case.stored, null)
        case.collection(factory).upsert(old).getOrThrow()
        val store = case.store(factory, clock)

        store.save(old, case.withNote(old, "Ändrad")).getOrThrow()
        assertEquals(case.withNote(old, "Ändrad"), store.get(old.id).getOrThrow())
        assertTrue(store.save(old, case.withCreatedAt(old, CREATED)).exceptionOrNull() is IllegalArgumentException, "ingen skapandetid på gissning")
    }

    companion object {
        private val CREATED = Instant.fromEpochSeconds(1_790_000_000)
        private val DAY = LocalDate(2026, 10, 5)

        @JvmStatic
        @Parameterized.Parameters(name = "{0}")
        fun cases(): List<Case<*>> = listOf(
            Case(
                "mående", { it.screenings() }, { f, c -> DefaultScreeningRepository(f, c) },
                Screening("s", DAY, createdAt = CREATED, note = "Före"), { s, n -> s.copy(note = n) }, { s, t -> s.copy(createdAt = t) },
            ),
            Case(
                "aktivitet", { it.activities() }, { f, c -> DefaultActivityRepository(f, c) },
                Activity("a", DAY, createdAt = CREATED, note = "Före"), { a, n -> a.copy(note = n) }, { a, t -> a.copy(createdAt = t) },
            ),
            Case(
                "händelse", { it.events() }, { f, c -> DefaultEventRepository(f, c) },
                Event("h", DAY, createdAt = CREATED, note = "Före"), { e, n -> e.copy(note = n) }, { e, t -> e.copy(createdAt = t) },
            ),
            Case(
                "episod", { it.illnessEpisodes() }, { f, c -> episodeStore(DefaultIllnessRepository(f, c)) },
                IllnessEpisode("flu", "Förkylning", DAY, createdAt = CREATED, note = "Före"), { e, n -> e.copy(note = n) }, { e, t -> e.copy(createdAt = t) },
            ),
            Case(
                "incheckning", { it.checkins("flu") }, { f, c -> DefaultIllnessRepository(f, c).checkins("flu") },
                Checkin("c", DAY, severity = 4, createdAt = CREATED, note = "Före"), { e, n -> e.copy(note = n) }, { e, t -> e.copy(createdAt = t) },
            ),
            Case(
                "dos", { it.doses() }, { f, c -> testDoses(f, TimeZone.of("Europe/Stockholm"), c) },
                Dose("d", DAY, name = "Alvedon", dose = "500", unit = "mg", createdAt = CREATED, note = "Före"), { d, n -> d.copy(note = n) }, { d, t -> d.copy(createdAt = t) },
            ),
        )

        /** Episodens formulär som [EntryStore] – `IllnessRepository.saveEpisode` och `getEpisode`. */
        private fun episodeStore(illnesses: IllnessRepository) = object : EntryStore<IllnessEpisode> {
            override suspend fun get(id: String) = illnesses.getEpisode(id)

            override suspend fun save(loaded: IllnessEpisode?, edited: IllnessEpisode) = illnesses.saveEpisode(loaded, edited)

            override suspend fun delete(id: String) = illnesses.deleteEpisode(id)
        }
    }
}
