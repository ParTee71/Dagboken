package se.partee71.dagboken.data.repository

import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import org.junit.Test
import se.partee71.dagboken.core.model.Checkin
import se.partee71.dagboken.core.model.IllnessEpisode
import se.partee71.dagboken.core.model.SymptomScore
import se.partee71.dagboken.core.model.somatic
import se.partee71.dagboken.core.schema.CheckinCodec
import se.partee71.dagboken.core.schema.IllnessEpisodeCodec
import se.partee71.dagboken.data.FakeCollectionFactory
import se.partee71.dagboken.data.FixedClock
import se.partee71.dagboken.data.common.CollectionFactory
import se.partee71.dagboken.data.common.DataError
import se.partee71.dagboken.data.common.EntityCollection
import se.partee71.dagboken.data.common.FieldMerge
import se.partee71.dagboken.data.common.dataError
import se.partee71.dagboken.data.firestore.Paths

/**
 * Sjukdomsdetaljens data mot `FakeCollection` (#240): episoden med sina incheckningar, att avsluta en episod (SJ-4),
 * kaskadraderingen (SJ-9, DAT-7) – också avbruten – och att incheckningar bara skrivs under en episod som finns och
 * redigeras utan att tappa id, skapandetid eller okända fält (SJ-2, SJ-11, SJ-12, DAT-6). Syntetisk data.
 */
class IllnessRepositoryTest {
    private val created = Instant.fromEpochSeconds(1_790_000_000)
    private val clock = FixedClock(created)
    private val factory = FakeCollectionFactory(clock = clock)
    private val illnesses = DefaultIllnessRepository(factory, clock)
    private val day = LocalDate(2026, 10, 3)
    private val today = LocalDate(2026, 10, 6)
    private val flu = IllnessEpisode("flu", "Förkylning", day, createdAt = created, note = "Började i halsen")
    private val migraine = IllnessEpisode("migran", "Migrän", day, createdAt = created)

    private val uid: String get() = factory.scope.uid.value!!

    private fun checkin(id: String, severity: Int = 4) =
        Checkin(id, day, LocalTime(9, 0), severity = severity, createdAt = created, note = "Anteckning $id")

    /** [count] incheckningar under [episodeId], skrivna med `batch` (som importen). */
    private suspend fun checkins(episodeId: String, count: Int): List<String> {
        val ids = List(count) { "c%04d".format(it) }
        factory.checkins(episodeId).batch(ids.map { checkin(it, severity = it.takeLast(1).toInt()) }).getOrThrow()
        return ids
    }

    /** Varje lagrad incheckning hör till en episod som finns – inga föräldralösa. */
    private fun assertNoOrphans() {
        val episodeIds = factory.store.documents.value[Paths.illnessEpisodes(uid)].orEmpty().keys
        val orphans = factory.store.documents.value.filter { (path, docs) ->
            path.endsWith("/${Paths.CHECKINS}") && docs.isNotEmpty() && path.split("/").dropLast(1).last() !in episodeIds
        }
        assertEquals(emptyMap(), orphans, "incheckningar utan episod")
    }

    // ── Läsning ───────────────────────────────────────────────────────────

    @Test
    fun `episoden följs med sina incheckningar och blir null när den raderats`() = runTest {
        factory.illnessEpisodes().batch(listOf(flu, migraine)).getOrThrow()
        factory.checkins("flu").upsert(checkin("a")).getOrThrow()
        factory.checkins("migran").upsert(checkin("x")).getOrThrow()

        assertEquals(EpisodeWithCheckins(flu, listOf(checkin("a"))), illnesses.observeEpisode("flu").first())

        illnesses.checkins("flu").save(null, checkin("b", severity = 7)).getOrThrow()
        assertEquals(listOf("a", "b"), illnesses.observeEpisode("flu").first { it?.checkins?.size == 2 }!!.checkins.map { it.id })

        illnesses.deleteEpisode("flu").getOrThrow()
        assertNull(illnesses.observeEpisode("flu").first())
        assertNull(illnesses.observeEpisode("finns-inte").first())
        assertEquals(listOf("x"), illnesses.observeEpisode("migran").first()!!.checkins.map { it.id })
    }

    @Test
    fun `antalet incheckningar för bekräftelsen är serverns lista, samma som raderingen tar, och kräver nät`() = runTest {
        factory.illnessEpisodes().upsert(flu).getOrThrow()
        checkins("flu", 3)

        assertEquals(3, illnesses.checkinCount("flu").getOrThrow())
        assertEquals(0, illnesses.checkinCount("finns-inte").getOrThrow())
        factory.store.online = false
        assertEquals(DataError.Offline, illnesses.checkinCount("flu").dataError())
    }

    @Test
    fun `bara episoden följs, utan incheckningarna, och blir null när den raderats`() = runTest {
        factory.illnessEpisodes().upsert(flu).getOrThrow()
        assertEquals(flu, illnesses.observeEpisodeOnly("flu").first())
        factory.illnessEpisodes().delete("flu").getOrThrow()
        assertNull(illnesses.observeEpisodeOnly("flu").first())
    }

    // ── Avsluta och redigera episoden (SJ-4, SJ-12) ──────────────────────

    @Test
    fun `att avsluta skriver bara slutdatumet, bevarar okända fält och skapar ingen incheckning (SJ-4, SJ-12)`() = runTest {
        factory.store.set(Paths.illnessEpisodes(uid), "flu", IllnessEpisodeCodec.encode(flu) + ("framtidaFält" to "kvar"), merge = false)
        factory.store.online = false

        illnesses.finishEpisode(flu, today, today).getOrThrow()

        val raw = factory.store.read(Paths.illnessEpisodes(uid), "flu")!!
        assertEquals("2026-10-06", raw["end"])
        assertEquals("kvar", raw["framtidaFält"])
        assertEquals(flu.copy(end = LocalDate(2026, 10, 6)), illnesses.getEpisode("flu").getOrThrow())
        assertEquals(emptyList(), illnesses.observeCheckins("flu").first())

        illnesses.finishEpisode(flu, day, today).getOrThrow()
        assertEquals("2026-10-03", factory.store.read(Paths.illnessEpisodes(uid), "flu")!!["end"], "samma dag som starten går")
    }

    @Test
    fun `ett slut före starten eller efter idag skriver ingenting, och en raderad episod återuppstår inte när den avslutas`() = runTest {
        factory.illnessEpisodes().upsert(flu).getOrThrow()

        assertTrue(illnesses.finishEpisode(flu, LocalDate(2026, 10, 2), today).exceptionOrNull() is IllegalArgumentException)
        assertTrue(illnesses.finishEpisode(flu, LocalDate(2026, 10, 7), today).exceptionOrNull() is IllegalArgumentException)
        assertNull(illnesses.getEpisode("flu").getOrThrow()!!.end)

        factory.illnessEpisodes().delete("flu").getOrThrow()
        illnesses.finishEpisode(flu, today, today)
        assertNull(factory.store.read(Paths.illnessEpisodes(uid), "flu"))
    }

    @Test
    fun `episodredigering skapar ingen incheckning (SJ-12)`() = runTest {
        factory.illnessEpisodes().upsert(flu).getOrThrow()
        factory.checkins("flu").upsert(checkin("a")).getOrThrow()

        illnesses.saveEpisode(flu, flu.copy(type = "Influensa", start = LocalDate(2026, 10, 2), note = null)).getOrThrow()

        assertEquals(listOf(checkin("a")), illnesses.observeCheckins("flu").first())
        assertEquals(flu.copy(type = "Influensa", start = LocalDate(2026, 10, 2), note = null), illnesses.getEpisode("flu").getOrThrow())
    }

    // ── Incheckningar (SJ-2, SJ-11, DAT-6) ───────────────────────────────

    @Test
    fun `en ny incheckning skrivs bara under en episod som finns (SJ-2)`() = runTest {
        assertEquals(DataError.NotFound, illnesses.checkins("saknas").save(null, checkin("a")).dataError())
        assertNull(factory.store.read(Paths.checkins(uid, "saknas"), "a"))

        factory.illnessEpisodes().upsert(flu).getOrThrow()
        factory.store.online = false
        illnesses.checkins("flu").save(null, checkin("a")).getOrThrow()
        assertEquals(listOf(checkin("a")), illnesses.observeCheckins("flu").first(), "offline först när episoden finns i cachen")
    }

    @Test
    fun `en redigerad incheckning behåller id, skapandetid och okända fält och summan räknas ur symptomen (SJ-11, DAT-6)`() = runTest {
        factory.illnessEpisodes().upsert(flu).getOrThrow()
        val stored = checkin("a").copy(symptoms = listOf(SymptomScore("symptom-huvudvark-d55e7d", 4)))
        factory.store.set(Paths.checkins(uid, "flu"), "a", CheckinCodec.encode(stored) + ("framtidaFält" to "kvar"), merge = false)
        clock.instant = created + 2.hours

        val edited = stored.copy(severity = 2, symptoms = listOf(SymptomScore("symptom-huvudvark-d55e7d", 1), SymptomScore("symptom-ovrigt-c067e8", 3, "Hosta")))
        val store = illnesses.checkins("flu")
        assertTrue(store.save(stored, edited.copy(createdAt = clock.now())).exceptionOrNull() is IllegalArgumentException)
        assertEquals(stored, store.get("a").getOrThrow(), "en ny skapandetid skriver ingenting")
        store.save(stored, edited.copy(id = "annat")).getOrThrow()

        val read = illnesses.checkins("flu").get("a").getOrThrow()!!
        assertEquals(edited, read)
        assertEquals(4, read.symptoms.somatic)
        assertNull(factory.store.read(Paths.checkins(uid, "flu"), "annat"), "redigeringen skriver där den lästes")
        val raw = factory.store.read(Paths.checkins(uid, "flu"), "a")!!
        assertEquals("kvar", raw["framtidaFält"])
        assertFalse("somatic" in raw || "somatiska" in raw, "summan lagras inte")
    }

    // ── Kaskadradering (SJ-9, DAT-7) ─────────────────────────────────────

    @Test
    fun `en episod raderas med alla sina incheckningar och anteckningar, fler än 500, och andra episoder står kvar (SJ-9)`() = runTest {
        factory.illnessEpisodes().batch(listOf(flu, migraine)).getOrThrow()
        checkins("flu", 1_201)
        checkins("migran", 2)

        illnesses.deleteEpisode("flu").getOrThrow()

        assertNull(factory.store.read(Paths.illnessEpisodes(uid), "flu"))
        assertEquals(emptyMap(), factory.store.documents.value[Paths.checkins(uid, "flu")].orEmpty())
        assertEquals(2, illnesses.observeCheckins("migran").first().size)
        assertEquals(migraine, illnesses.getEpisode("migran").getOrThrow())
        assertNoOrphans()
    }

    @Test
    fun `en episod utan incheckningar raderas, och en som inte finns är inget fel`() = runTest {
        factory.illnessEpisodes().upsert(flu).getOrThrow()
        illnesses.deleteEpisode("flu").getOrThrow()
        assertNull(illnesses.getEpisode("flu").getOrThrow())
        illnesses.deleteEpisode("flu").getOrThrow()
    }

    @Test
    fun `en avbruten kaskad lämnar aldrig incheckningar utan episod, och ett nytt försök tar resten`() = runTest {
        factory.illnessEpisodes().upsert(flu).getOrThrow()
        checkins("flu", 1_201)
        // Den första biten om 500 hinner raderas, sedan bryts batchen (som FirestoreCollection.batch i bitar).
        val aborting = DefaultIllnessRepository(abortingCheckins(factory, committed = 500), clock)

        assertEquals(DataError.Offline, aborting.deleteEpisode("flu").dataError())

        assertEquals(flu, illnesses.getEpisode("flu").getOrThrow(), "episoden raderas sist")
        assertEquals(701, illnesses.observeCheckins("flu").first().size)
        assertNoOrphans()

        illnesses.deleteEpisode("flu").getOrThrow()
        assertNull(illnesses.getEpisode("flu").getOrThrow())
        assertEquals(emptyList(), illnesses.observeCheckins("flu").first())
        assertNoOrphans()
    }

    @Test
    fun `tappas nätet efter incheckningarna raderas inte episoden, och utan nät raderas ingenting`() = runTest {
        factory.illnessEpisodes().upsert(flu).getOrThrow()
        checkins("flu", 3)
        val losesNetwork = DefaultIllnessRepository(abortingCheckins(factory, committed = Int.MAX_VALUE, beforeBatch = { factory.store.online = false }), clock)
        // Batchen läggs i cachen men når aldrig servern: incheckningarna är borta lokalt, episoden står kvar.

        assertEquals(DataError.Offline, losesNetwork.deleteEpisode("flu").dataError())
        assertNotNull(illnesses.getEpisode("flu").getOrThrow(), "servern har inte bekräftat raderingen av incheckningarna")
        assertNoOrphans()

        factory.illnessEpisodes().upsert(migraine).getOrThrow()
        checkins("migran", 2)
        assertEquals(DataError.Offline, illnesses.deleteEpisode("migran").dataError())
        assertEquals(2, illnesses.observeCheckins("migran").first().size, "listan som raderas är serverns, aldrig cachens")
        assertNotNull(illnesses.getEpisode("migran").getOrThrow())
    }

    @Test
    fun `en incheckning från en annan enhet mellan varven raderas också innan episoden`() = runTest {
        factory.illnessEpisodes().upsert(flu).getOrThrow()
        checkins("flu", 3)
        var batches = 0
        // Efter första batchen skriver "en annan enhet" en incheckning under episoden (som rules godtar, den finns).
        val racing = DefaultIllnessRepository(
            abortingCheckins(factory, committed = Int.MAX_VALUE, afterBatch = {
                if (batches++ == 0) factory.store.set(Paths.checkins(uid, "flu"), "annan-enhet", CheckinCodec.encode(checkin("annan-enhet")), merge = false)
            }),
            clock,
        )

        racing.deleteEpisode("flu").getOrThrow()

        assertEquals(2, batches, "listan läses om efter batchen och den nya raderas i ett andra varv")
        assertNull(illnesses.getEpisode("flu").getOrThrow())
        assertEquals(emptyMap(), factory.store.documents.value[Paths.checkins(uid, "flu")].orEmpty())
        assertNoOrphans()
    }

    @Test
    fun `kommer det hela tiden nya incheckningar ger raderingen upp efter taket och episoden står kvar`() = runTest {
        factory.illnessEpisodes().upsert(flu).getOrThrow()
        checkins("flu", 1)
        var batches = 0
        val endless = DefaultIllnessRepository(
            abortingCheckins(factory, committed = Int.MAX_VALUE, afterBatch = {
                factory.store.set(Paths.checkins(uid, "flu"), "ny-${batches++}", CheckinCodec.encode(checkin("ny")), merge = false)
            }),
            clock,
        )

        assertEquals(DataError.Unknown, endless.deleteEpisode("flu").dataError())

        assertEquals(IllnessRepository.MAX_CASCADE_ROUNDS, batches)
        assertEquals(flu, illnesses.getEpisode("flu").getOrThrow())
        assertNoOrphans()
    }

    /**
     * Samlingarna, men incheckningarnas `batch` raderar bara de [committed] första och misslyckas sedan med
     * [DataError.Offline] – som en batch i bitar där en senare bit inte når fram. [beforeBatch] och [afterBatch] körs
     * före respektive efter varje batch.
     */
    private fun abortingCheckins(
        factory: FakeCollectionFactory,
        committed: Int,
        beforeBatch: () -> Unit = {},
        afterBatch: () -> Unit = {},
    ): CollectionFactory =
        object : CollectionFactory by factory {
            override fun checkins(episodeId: String): EntityCollection<Checkin> = object : EntityCollection<Checkin> by factory.checkins(episodeId) {
                private val real = factory.checkins(episodeId)

                override suspend fun batch(upserts: List<Checkin>, deletes: List<String>, merges: List<FieldMerge<Checkin>>): Result<Unit> {
                    beforeBatch()
                    real.batch(upserts, deletes.take(committed), merges).getOrThrow()
                    afterBatch()
                    return if (deletes.size > committed) Result.failure(DataError.Offline) else Result.success(Unit)
                }
            }
        }
}
