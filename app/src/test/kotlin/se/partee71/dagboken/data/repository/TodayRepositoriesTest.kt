package se.partee71.dagboken.data.repository

import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlin.time.Instant
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import org.junit.Test
import se.partee71.dagboken.core.engine.PrnCheck
import se.partee71.dagboken.core.model.Dose
import se.partee71.dagboken.core.model.DoseIds
import se.partee71.dagboken.core.model.DoseStatus
import se.partee71.dagboken.core.model.Occasion
import se.partee71.dagboken.core.model.Period
import se.partee71.dagboken.core.model.Prescription
import se.partee71.dagboken.core.model.PrnMedicine
import se.partee71.dagboken.core.model.Schedule
import se.partee71.dagboken.core.model.Screening
import se.partee71.dagboken.core.model.Slot
import se.partee71.dagboken.core.schema.DoseCodec
import se.partee71.dagboken.core.schema.PrescriptionCodec
import se.partee71.dagboken.core.schema.ScreeningCodec
import se.partee71.dagboken.data.FakeCollectionFactory
import se.partee71.dagboken.data.FixedClock
import se.partee71.dagboken.data.common.CollectionFactory
import se.partee71.dagboken.data.common.EntityCollection
import se.partee71.dagboken.data.firestore.Paths

/**
 * Idags data mot `FakeCollection`: dagens doser (HEM-10, MED-4), avbockning (MED-2, MED-14), vid
 * behov-loggning (FAV-4, FAV-5, FAV-11, MED-16) och måendeloggarna (SCR-1, SCR-6).
 */
class TodayRepositoriesTest {

    private val factory = FakeCollectionFactory()
    private val zone = TimeZone.of("Europe/Stockholm")

    private val today = LocalDate(2026, 9, 21)
    private val yesterday = LocalDate(2026, 9, 20)
    private val tomorrow = LocalDate(2026, 9, 22)

    /** "Nu" = i morgon kväll, så att dagens och morgondagens doser inte ligger i framtiden. */
    private val clock = FixedClock(at(tomorrow, 23))
    private val doses = testDoses(factory, zone, clock)
    private val prescriptions = testPrescriptions(factory, doses, zone, clock)
    private val screenings = DefaultScreeningRepository(factory, clock)

    private val levaxin = Prescription(
        "levaxin",
        name = "Levaxin",
        dose = "100",
        unit = "µg",
        slots = listOf(Slot.MORNING, Slot.EVENING),
        schedule = Schedule.Repeating(),
        period = Period(start = LocalDate(2026, 1, 1)),
    )

    private val alvedon = PrnMedicine("alvedon", "Alvedon", "500", "mg", minHoursBetween = 4, maxPerDay = 3)

    private fun at(date: LocalDate, hour: Int, minute: Int = 0): Instant = LocalDateTime(date, LocalTime(hour, minute)).toInstant(zone)

    private fun dosesPath() = Paths.doses(factory.scope.uid.value!!)

    private fun screeningsPath() = Paths.screenings(factory.scope.uid.value!!)

    private fun stored() = factory.store.documents.value[dosesPath()].orEmpty()

    // ── Dagens doser (HEM-10, MED-4) ─────────────────────────────────────────

    @Test
    fun `ensureDay två gånger ger inga dubbletter och skriver aldrig över en tagen dos`() = runTest {
        factory.prescriptions().upsert(levaxin).getOrThrow()
        val morning = DoseIds.prescribed(levaxin.id, today, Slot.MORNING)
        factory.store.set(dosesPath(), morning, mapOf(DoseCodec.DATE to "2026-09-21", DoseCodec.STATUS to "taken", "framtidaFält" to "kvar"), merge = false)

        prescriptions.ensureDay(today, today).getOrThrow()
        val first = stored()
        prescriptions.ensureDay(today, today).getOrThrow()

        assertEquals(first, stored(), "andra körningen ändrar ingenting")
        assertEquals(setOf(morning, DoseIds.prescribed(levaxin.id, today, Slot.EVENING)), first.keys)
        assertEquals(mapOf(DoseCodec.DATE to "2026-09-21", DoseCodec.STATUS to "taken", "framtidaFält" to "kvar"), first[morning])
    }

    @Test
    fun `ensureDay för en tidigare dag skapar den dagens doser`() = runTest {
        factory.prescriptions().upsert(levaxin).getOrThrow()
        prescriptions.ensureDay(yesterday, today).getOrThrow()
        assertEquals(listOf(yesterday, yesterday), doses.observeDay(yesterday).first().map { it.date })
        assertEquals(emptyList(), doses.observeDay(today).first())
    }

    @Test
    fun `ensureDay avslutar inget utgånget recept och ger det inga doser (REC-8 bara via tidyUp)`() = runTest {
        val expired = levaxin.copy(period = Period(start = LocalDate(2026, 9, 1), end = yesterday))
        factory.prescriptions().upsert(expired).getOrThrow()
        val before = factory.store.read(Paths.prescriptions(factory.scope.uid.value!!), expired.id)

        prescriptions.ensureDay(yesterday, today).getOrThrow()

        assertEquals(before, factory.store.read(Paths.prescriptions(factory.scope.uid.value!!), expired.id))
        assertEquals(true, before?.get(PrescriptionCodec.ACTIVE))
        assertTrue(stored().isEmpty(), "sett från idag är receptet avslutat – inga doser, inte heller för igår")
    }

    @Test
    fun `ensureDay utan nät gör ingenting och är inget fel`() = runTest {
        factory.prescriptions().upsert(levaxin).getOrThrow()
        factory.store.online = false
        assertTrue(prescriptions.ensureDay(today, today).isSuccess)
        assertTrue(stored().isEmpty())
    }

    @Test
    fun `ensureDay skapar inte doser som en samtidig avaktivering tagit bort`() = runTest {
        // Servern: receptet är just avaktiverat och synken har tagit bort dagens planerade doser. Cachens
        // receptlista har inte hunnit se det och visar det som aktivt.
        factory.prescriptions().upsert(levaxin.copy(active = false)).getOrThrow()
        val staleList = object : CollectionFactory by factory {
            override fun prescriptions(): EntityCollection<Prescription> = object : EntityCollection<Prescription> by factory.prescriptions() {
                override suspend fun cached(): Result<List<Prescription>> = Result.success(listOf(levaxin))
            }
        }
        val repository = testPrescriptions(staleList, doses, zone, clock)

        repository.ensureDay(today, today).getOrThrow()
        repository.ensureDay(yesterday, today).getOrThrow()

        assertTrue(stored().isEmpty(), "under receptets lås läses det igen från servern – inaktivt, inga doser")
    }

    @Test
    fun `ensureDay gör inga serveranrop när cachen redan har dagens doser`() = runTest {
        factory.prescriptions().upsert(levaxin).getOrThrow()
        prescriptions.ensureDay(today, today).getOrThrow()
        var serverCalls = 0
        val counting = object : CollectionFactory by factory {
            override fun doses(): EntityCollection<Dose> = object : EntityCollection<Dose> by factory.doses() {
                override suspend fun createIfAbsent(items: List<Dose>): Result<Unit> = Result.success(Unit).also { serverCalls++ }
            }

            override fun prescriptions(): EntityCollection<Prescription> = object : EntityCollection<Prescription> by factory.prescriptions() {
                override suspend fun confirmed(): Result<List<Prescription>> = factory.prescriptions().confirmed().also { serverCalls++ }
                override suspend fun confirmed(id: String): Result<Prescription?> = factory.prescriptions().confirmed(id).also { serverCalls++ }
            }
        }
        val counted = testDoses(counting, zone, clock)
        testPrescriptions(counting, counted, zone, clock).ensureDay(today, today).getOrThrow()
        assertEquals(0, serverCalls, "varken receptläsning från servern eller transaktion")
    }

    @Test
    fun `ensureDay för en dag efter idag gör ingenting`() = runTest {
        factory.prescriptions().upsert(levaxin).getOrThrow()
        prescriptions.ensureDay(tomorrow, today).getOrThrow()
        assertTrue(stored().isEmpty())
    }

    @Test
    fun `observeDay ger bara dagens doser`() = runTest {
        factory.doses().batch(listOf(Dose("a", yesterday, name = "A"), Dose("b", today, name = "B"), Dose("c", tomorrow, name = "C"))).getOrThrow()
        assertEquals(listOf("b"), doses.observeDay(today).first().map { it.id })
        assertEquals(listOf("a", "b"), doses.observeDays(yesterday, today).first().map { it.id })
    }

    // ── Avbockning (MED-2, MED-14) ───────────────────────────────────────────

    @Test
    fun `setStatus skriver bara status och takenAt och fungerar utan nät`() = runTest {
        val dose = Dose(DoseIds.prescribed(levaxin.id, today, Slot.MORNING), today, Slot.MORNING, "Levaxin", "100", "µg", prescriptionId = levaxin.id)
        // En annan enhet har bytt namn och skrivit ett fält från en nyare app efter att dosen lästs.
        factory.store.set(dosesPath(), dose.id, DoseCodec.encode(dose.copy(name = "Levaxin Ny")) + ("framtidaFält" to "kvar"), merge = false)
        val before = factory.store.read(dosesPath(), dose.id)!!
        factory.store.online = false
        val taken = at(today, 7, 12)

        doses.setStatus(dose, DoseStatus.TAKEN, taken).getOrThrow()
        assertEquals(before + mapOf(DoseCodec.STATUS to "taken", DoseCodec.TAKEN_AT to taken), factory.store.read(dosesPath(), dose.id))

        doses.setStatus(dose, DoseStatus.PLANNED, taken).getOrThrow()
        assertEquals(before + mapOf(DoseCodec.STATUS to "planned", DoseCodec.TAKEN_AT to null), factory.store.read(dosesPath(), dose.id), "ångrad: tagningstiden nollställs")

        doses.setStatus(dose, DoseStatus.SKIPPED).getOrThrow()
        assertEquals("skipped", factory.store.read(dosesPath(), dose.id)?.get(DoseCodec.STATUS))
    }

    @Test
    fun `en tagningstid i framtiden avvisas och skriver inget`() = runTest {
        val dose = Dose("a", today, Slot.MORNING, "Levaxin")
        factory.doses().upsert(dose).getOrThrow()
        assertTrue(doses.setStatus(dose, DoseStatus.TAKEN, clock.instant + 1.minutes).exceptionOrNull() is IllegalArgumentException)
        assertEquals("planned", factory.store.read(dosesPath(), "a")?.get(DoseCodec.STATUS))
    }

    @Test
    fun `en tagen dos utan tagningstid får klockans nu`() = runTest {
        val dose = Dose("a", today, Slot.MORNING, "Levaxin")
        factory.doses().upsert(dose).getOrThrow()
        doses.setStatus(dose, DoseStatus.TAKEN).getOrThrow()
        assertEquals(clock.instant, factory.store.read(dosesPath(), "a")?.get(DoseCodec.TAKEN_AT))
    }

    @Test
    fun `setStatus återskapar inte en dos som raderats`() = runTest {
        doses.setStatus(Dose("borta", today, name = "Levaxin"), DoseStatus.TAKEN, at(today, 8))
        assertNull(factory.store.read(dosesPath(), "borta"))
    }

    // ── Vid behov (FAV-4, FAV-5, FAV-11, MED-16) ─────────────────────────────

    @Test
    fun `vid behov loggas som en ny tagen dos när ingen spärr gäller`() = runTest {
        val now = at(today, 10)
        assertEquals(PrnCheck.Allowed, doses.logAsNeeded(alvedon, now).getOrThrow())
        val logged = doses.observeDay(today).first().single()
        assertEquals(DoseStatus.TAKEN, logged.status)
        assertEquals(alvedon.id, logged.prnId)
        assertEquals(now, logged.takenAt)
    }

    @Test
    fun `kylperioden räknas över midnatt och kan bekräftas bort`() = runTest {
        doses.logAsNeeded(alvedon, at(yesterday, 23)).getOrThrow()
        val check = doses.logAsNeeded(alvedon, at(today, 1)).getOrThrow()
        assertEquals(PrnCheck.Cooldown(2.hours), check)
        assertEquals(1, stored().size, "ingen dos skrivs under kylperioden")

        assertEquals(PrnCheck.Allowed, doses.logAsNeeded(alvedon, at(today, 1), force = true).getOrThrow())
        assertEquals(2, stored().size)
    }

    @Test
    fun `en kylperiod längre än ett dygn läses hela vägen bakåt`() = runTest {
        val rare = alvedon.copy(minHoursBetween = 50, maxPerDay = 0)
        doses.logAsNeeded(rare, at(LocalDate(2026, 9, 19), 12)).getOrThrow() // 46 h före – utanför ett dygns läsning
        val check = doses.logAsNeeded(rare, at(today, 10)).getOrThrow()
        assertEquals(PrnCheck.Cooldown(4.hours), check)
    }

    @Test
    fun `dagsgränsen spärrar alltid, också med force`() = runTest {
        for (hour in listOf(6, 11, 16)) assertEquals(PrnCheck.Allowed, doses.logAsNeeded(alvedon, at(today, hour)).getOrThrow())
        assertEquals(PrnCheck.DailyLimitReached, doses.logAsNeeded(alvedon, at(today, 21), force = true).getOrThrow())
        assertEquals(3, stored().size)
        assertEquals(PrnCheck.Allowed, doses.logAsNeeded(alvedon, at(tomorrow, 6)).getOrThrow(), "ny dag, ny gräns")
    }

    @Test
    fun `två samtidiga loggningar av samma medicin passerar aldrig dagsgränsen`() = runTest {
        // Läsningen ur cachen tar en stund, så att båda tryck hinner läsa innan någon skrivit – utan lås.
        val slowRead = object : CollectionFactory by factory {
            override fun doses(): EntityCollection<Dose> = object : EntityCollection<Dose> by factory.doses() {
                override suspend fun cachedBetween(field: String, from: Any, to: Any): Result<List<Dose>> {
                    delay(10)
                    return factory.doses().cachedBetween(field, from, to)
                }
            }
        }
        val once = alvedon.copy(maxPerDay = 1, minHoursBetween = 0)
        val repository = testDoses(slowRead, zone, clock)
        val results = listOf(async { repository.logAsNeeded(once, at(today, 10)) }, async { repository.logAsNeeded(once, at(today, 10)) }).awaitAll()
        assertEquals(setOf(PrnCheck.Allowed, PrnCheck.DailyLimitReached), results.map { it.getOrThrow() }.toSet())
        assertEquals(1, stored().size)
    }

    @Test
    fun `en äldre dos som tagits nyss spärrar – kylperioden mäts på tagningstiden`() = runTest {
        // Dosen hör till en dag fem dagar bakåt men bockades av för en timme sedan.
        val now = at(today, 10)
        factory.doses().upsert(Dose("gammal", LocalDate(2026, 9, 16), Slot.AS_NEEDED, "Alvedon", status = DoseStatus.TAKEN, takenAt = now - 1.hours)).getOrThrow()
        assertEquals(PrnCheck.Cooldown(3.hours), doses.logAsNeeded(alvedon, now).getOrThrow())
    }

    @Test
    fun `en dos i framtiden loggas inte`() = runTest {
        val later = clock.instant + 1.minutes
        assertTrue(doses.logAsNeeded(alvedon, later).exceptionOrNull() is IllegalArgumentException)
        assertTrue(doses.logExtraDose(levaxin, later).exceptionOrNull() is IllegalArgumentException)
        assertTrue(stored().isEmpty())
        assertEquals(PrnCheck.Allowed, doses.logAsNeeded(alvedon, clock.instant).getOrThrow(), "nu går bra")
    }

    @Test
    fun `vid behov loggas i efterhand och utan nät – bara ur cachen`() = runTest {
        factory.store.online = false
        doses.logAsNeeded(alvedon, at(today, 12)).getOrThrow()
        assertEquals(PrnCheck.Cooldown(2.hours), doses.logAsNeeded(alvedon, at(today, 14)).getOrThrow())
        assertEquals(PrnCheck.Allowed, doses.logAsNeeded(alvedon, at(today, 9)).getOrThrow(), "en senare dos spärrar inte en tidigare (MED-16)")
    }

    @Test
    fun `receptets extrados loggas som vid behov utan receptkoppling`() = runTest {
        doses.logExtraDose(levaxin, at(today, 13)).getOrThrow()
        doses.logExtraDose(levaxin, at(today, 13, 5)).getOrThrow()
        val extra = doses.observeDay(today).first()
        assertEquals(2, extra.size, "ingen kylperiod eller gräns")
        assertTrue(extra.all { it.slot == Slot.AS_NEEDED && it.prescriptionId == null && it.status == DoseStatus.TAKEN })
    }

    // ── Måendeloggar (SCR-1, SCR-2, SCR-6) ───────────────────────────────────

    @Test
    fun `en ny logg sparas med sitt id och createdAt satt en gång`() = runTest {
        val draft = screenings.new(yesterday, Occasion.BREAKFAST)
        val created = clock.instant
        val logged = draft.copy(time = LocalTime(8, 5), energy = 6, stress = 3)
        clock.instant += 5.minutes // formuläret står öppet en stund
        screenings.save(null, logged).getOrThrow()
        clock.instant += 1.minutes
        screenings.save(null, logged).getOrThrow() // nytt försök efter ett fel: samma dokument

        val saved = screenings.observeDay(yesterday).first().single()
        assertEquals(logged, saved)
        assertEquals(created, saved.createdAt, "skapandetiden från new(), inte från sparningarna")
        assertTrue(screenings.save(null, Screening("utan", today)).exceptionOrNull() is IllegalArgumentException, "ny utan createdAt skrivs inte")
        assertNull(factory.store.read(screeningsPath(), "utan"))
        assertEquals(emptyList(), screenings.observeDay(today).first(), "sparad mot den visade dagen (SCR-6)")
    }

    @Test
    fun `en befintlig logg skriver bara ändrade fält och återuppstår inte`() = runTest {
        val loaded = Screening("s1", today, LocalTime(12, 0), Occasion.LUNCH, energy = 5, stress = 2)
        factory.store.set(screeningsPath(), "s1", ScreeningCodec.encode(loaded.copy(note = "Från en annan enhet")) + ("framtidaFält" to "kvar"), merge = false)

        screenings.save(loaded, loaded.copy(energy = 8)).getOrThrow()
        val raw = factory.store.read(screeningsPath(), "s1")!!
        assertEquals(8L, raw["energy"])
        assertEquals("Från en annan enhet", raw["note"])
        assertEquals("kvar", raw["framtidaFält"])

        screenings.delete("s1").getOrThrow()
        screenings.save(loaded, loaded.copy(stress = 9))
        assertNull(factory.store.read(screeningsPath(), "s1"), "en raderad logg återuppstår inte")
    }
}
