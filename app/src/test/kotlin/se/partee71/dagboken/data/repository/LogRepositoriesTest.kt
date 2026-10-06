package se.partee71.dagboken.data.repository

import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import org.junit.Test
import se.partee71.dagboken.core.engine.ensureDoses
import se.partee71.dagboken.core.model.Checkin
import se.partee71.dagboken.core.model.Dose
import se.partee71.dagboken.core.model.DoseIds
import se.partee71.dagboken.core.model.DoseStatus
import se.partee71.dagboken.core.model.IllnessEpisode
import se.partee71.dagboken.core.model.Prescription
import se.partee71.dagboken.core.model.Schedule
import se.partee71.dagboken.core.model.Slot
import se.partee71.dagboken.core.model.SymptomScore
import se.partee71.dagboken.core.schema.CheckinCodec
import se.partee71.dagboken.core.schema.CollectionNames
import se.partee71.dagboken.core.schema.DocumentRules
import se.partee71.dagboken.core.schema.DoseCodec
import se.partee71.dagboken.core.schema.IllnessEpisodeCodec
import se.partee71.dagboken.data.FakeCollectionFactory
import se.partee71.dagboken.data.common.DataError
import se.partee71.dagboken.data.common.dataError
import se.partee71.dagboken.data.FixedClock
import se.partee71.dagboken.data.firestore.Paths

/**
 * Dosformulärets och sjukdomsformulärens data mot `FakeCollection` (MED-11, MED-15, MED-16, SJ-1, SJ-2, SJ-11): ny
 * med alla fält, ändrad fältvis, flytt av en receptdos och radering – och att det som skrivs godtas av rules.
 * Klockan står på tisdag 6 oktober 2026 kl. 14:20 i Europe/Stockholm.
 */
class LogRepositoriesTest {
    private val zone = TimeZone.of("Europe/Stockholm")
    private val day = LocalDate(2026, 10, 5)
    private val today = LocalDate(2026, 10, 6)
    private val clock = FixedClock(at(today, 14, 20))
    private val factory = FakeCollectionFactory(clock = clock)
    private val doses = testDoses(factory, zone, clock)
    private val illnesses = DefaultIllnessRepository(factory, clock)

    private fun at(date: LocalDate, hour: Int, minute: Int = 0): Instant = LocalDateTime(date, LocalTime(hour, minute)).toInstant(zone)

    private val uid: String get() = factory.scope.uid.value!!

    private val levaxin = Prescription("levaxin", "Levaxin", "100", "µg", listOf(Slot.MORNING), Schedule.Repeating(), createdAt = at(LocalDate(2026, 9, 1), 8))
    private val recipeDose = Dose(
        DoseIds.prescribed("levaxin", day, Slot.MORNING), day, Slot.MORNING, "Levaxin", "100", "µg", DoseStatus.TAKEN,
        plannedTime = LocalTime(7, 0), takenAt = at(day, 7, 12), prescriptionId = "levaxin", createdAt = at(day, 7), note = "Med frukost",
    )

    // ── Dos ───────────────────────────────────────────────────────────────

    @Test
    fun `en engångsdos med alla fält sparas, godtas av rules och läses tillbaka lika (MED-11, regel 1)`() = runTest {
        val new = doses.newOneOff(day, LocalTime(13, 5))
        assertEquals(Dose(new.id, day, Slot.AS_NEEDED, unit = "mg", status = DoseStatus.TAKEN, plannedTime = LocalTime(13, 5), takenAt = at(day, 13, 5), createdAt = clock.now()), new)
        assertNull(factory.doses().get(new.id).getOrThrow(), "inget sparas förrän formuläret sparas")
        val full = new.copy(slot = Slot.EVENING, name = "Melatonin", dose = "3", unit = "mg", note = "Svårt att somna")
        doses.save(null, full).getOrThrow()
        assertEquals(full, doses.get(full.id).getOrThrow())
        assertEquals(emptyList(), DocumentRules.validate(CollectionNames.DOSES, DoseCodec.encode(full)))
        assertTrue(doses.save(null, full.copy(id = "utan", createdAt = null)).exceptionOrNull() is IllegalArgumentException, "ny utan createdAt skrivs inte")
        assertTrue(doses.save(null, full.copy(id = "sen", takenAt = clock.now() + 1.minutes)).exceptionOrNull() is IllegalArgumentException, "aldrig i framtiden (MED-16)")
        assertNull(factory.doses().get("sen").getOrThrow())
    }

    @Test
    fun `en ändrad dos skriver bara ändrade fält – okända fält och en anteckning från en annan enhet står kvar (MED-15)`() = runTest {
        val prn = Dose("p", day, Slot.AS_NEEDED, "Alvedon", "500", "mg", DoseStatus.TAKEN, LocalTime(14, 0), at(day, 14), prnId = "alvedon", createdAt = at(day, 14))
        factory.store.set(Paths.doses(uid), "p", DoseCodec.encode(prn.copy(note = "Annan enhet")) + ("framtidaFält" to "kvar"), merge = false)
        doses.save(prn, prn.copy(dose = "1000", takenAt = at(day, 15), plannedTime = LocalTime(15, 0))).getOrThrow()
        val raw = factory.store.read(Paths.doses(uid), "p")!!
        assertEquals("1000", raw["dose"])
        assertEquals(at(day, 15), doses.get("p").getOrThrow()?.takenAt)
        assertEquals("Annan enhet", raw["note"])
        assertEquals("kvar", raw["framtidaFält"])
    }

    @Test
    fun `en receptdos som flyttas får måldagens id med receptet kvar, och ursprungsdagen får sin dos igen (MED-15, MED-4)`() = runTest {
        factory.store.set(Paths.doses(uid), recipeDose.id, DoseCodec.encode(recipeDose) + ("framtidaFält" to "kvar"), merge = false)
        val moved = recipeDose.copy(date = today, takenAt = at(today, 7, 12))
        factory.store.online = false
        assertEquals(DataError.Offline, doses.save(recipeDose, moved).dataError(), "flytten prövas mot servern – offline skrivs inget")
        assertEquals(listOf(recipeDose.id), factory.doses().getAll().getOrThrow().map { it.id })
        factory.store.online = true
        doses.save(recipeDose, moved).getOrThrow()

        val all = factory.doses().getAll().getOrThrow()
        assertEquals(1, all.size, "flyttad, inte kopierad – ingen dubblett")
        val stored = all.single()
        assertEquals(DoseIds.prescribed("levaxin", today, Slot.MORNING), stored.id, "måldagens datumbundna id")
        assertEquals(moved.copy(id = stored.id), stored, "alla fält följer med, receptkopplingen också")
        // Måldagen har nu sin dos: dosgenereringen skapar ingen andra planerad dos där.
        assertEquals(emptyList(), ensureDoses(listOf(levaxin), all.filter { it.date == today }, today, today, zone).create)
        assertEquals("kvar", factory.store.read(Paths.doses(uid), stored.id)?.get("framtidaFält"), "okända fält följer med (regel 1)")
        assertEquals(emptyList(), DocumentRules.validate(CollectionNames.DOSES, DoseCodec.encode(stored)))
        // Ursprungsdagens schemalagda dos saknas nu och skapas igen som planerad; den flyttade rörs inte.
        assertEquals(listOf(recipeDose.id), ensureDoses(listOf(levaxin), all.filter { it.date == day }, day, today, zone).create.map { it.id })

        doses.save(stored, stored.copy(note = "Tog den sent")).getOrThrow()
        assertEquals(1, factory.doses().getAll().getOrThrow().size, "en flyttad dos som ändras igen samma dag stannar")
    }

    @Test
    fun `en flytt till en dag som redan har dosen för tidpunkten avvisas och skriver inget (MED-15)`() = runTest {
        val target = recipeDose.copy(id = DoseIds.prescribed("levaxin", today, Slot.MORNING), date = today, status = DoseStatus.PLANNED, takenAt = null, note = null)
        factory.doses().batch(listOf(recipeDose, target)).getOrThrow()
        val result = doses.save(recipeDose, recipeDose.copy(date = today, takenAt = at(today, 7, 12)))
        assertTrue(result.exceptionOrNull() is DoseSlotTaken)
        assertEquals(recipeDose, factory.doses().get(recipeDose.id).getOrThrow(), "källan står kvar")
        assertEquals(target, factory.doses().get(target.id).getOrThrow(), "måldagens dos skrivs inte över")
        assertEquals(2, factory.doses().getAll().getOrThrow().size)
    }

    @Test
    fun `en receptdos utan datumbundet id byter dag med en vanlig fältvis ändring, också offline (MED-15)`() = runTest {
        val earlier = recipeDose.copy(id = "slumpat", note = "Annan enhet")
        factory.store.set(Paths.doses(uid), earlier.id, DoseCodec.encode(earlier) + ("framtidaFält" to "kvar"), merge = false)
        factory.store.online = false
        val loaded = earlier.copy(note = "Med frukost")
        doses.save(loaded, loaded.copy(date = LocalDate(2026, 10, 4), takenAt = at(LocalDate(2026, 10, 4), 7, 12))).getOrThrow()
        val raw = factory.store.read(Paths.doses(uid), "slumpat")!!
        assertEquals("2026-10-04", raw[DoseCodec.DATE])
        assertEquals("Annan enhet", raw["note"], "bara ändrade fält skrivs")
        assertEquals("kvar", raw["framtidaFält"])
        assertEquals(listOf("slumpat"), factory.doses().getAll().getOrThrow().map { it.id }, "samma id, ingen kopia")
    }

    @Test
    fun `en migrerad receptdos utan koppling får receptet när den flyttas, och hoppas över när den raderas (MED-15)`() = runTest {
        val legacy = recipeDose.copy(id = "recept_levaxin_2026-10-05_Morgon", prescriptionId = null)
        factory.doses().upsert(legacy).getOrThrow()
        doses.save(legacy, legacy.copy(date = today)).getOrThrow()
        assertEquals("levaxin", factory.doses().getAll().getOrThrow().single().prescriptionId)
        assertEquals(DataError.NotFound, doses.save(legacy, legacy.copy(date = LocalDate(2026, 10, 3))).dataError(), "redan flyttad (raderad) – återuppstår inte")
        assertEquals(1, factory.doses().getAll().getOrThrow().size)

        val other = legacy.copy(id = "recept_levaxin_2026-10-04_Morgon", date = LocalDate(2026, 10, 4))
        factory.doses().upsert(other).getOrThrow()
        doses.delete(other.id).getOrThrow()
        assertEquals(DoseStatus.SKIPPED, factory.doses().get(other.id).getOrThrow()?.status, "överhoppad – annars skapas den igen")
    }

    @Test
    fun `dosformulärets Radera hoppar över en receptdos och raderar en dos utan recept (MED-3, MED-15, HIST-5)`() = runTest {
        val prn = Dose("p", day, Slot.AS_NEEDED, "Alvedon", status = DoseStatus.TAKEN, prnId = "alvedon", note = "Huvudvärk")
        factory.doses().batch(listOf(recipeDose, prn)).getOrThrow()
        factory.store.online = false
        doses.delete(recipeDose.id).getOrThrow()
        doses.delete(prn.id).getOrThrow()
        assertEquals(DoseStatus.SKIPPED, factory.doses().get(recipeDose.id).getOrThrow()?.status)
        assertEquals("Med frukost", factory.doses().get(recipeDose.id).getOrThrow()?.note, "anteckningen står kvar")
        assertNull(factory.doses().get(prn.id).getOrThrow())
        assertTrue(doses.delete("finns-inte").isSuccess)
    }

    @Test
    fun `vid behov i efterhand får formulärets anteckning (MED-11, MED-16)`() = runTest {
        val alvedon = se.partee71.dagboken.core.model.PrnMedicine("alvedon", "Alvedon", "500", "mg", note = "Med mat")
        val log = doses.logAsNeeded(alvedon, at(day, 21), note = "Huvudvärk efter jobbet").getOrThrow()
        assertEquals("Huvudvärk efter jobbet", factory.doses().get(log.dose!!.id).getOrThrow()?.note)
        val plain = doses.logAsNeeded(alvedon, at(today, 9)).getOrThrow()
        assertEquals("Med mat", plain.dose?.note, "som förval medicinens")
        assertEquals(at(day, 21), log.dose.takenAt)
    }

    // ── Sjukdom ───────────────────────────────────────────────────────────

    @Test
    fun `en ny episod med första incheckningen och alla fält sparas, godtas av rules och läses tillbaka lika (SJ-1, SJ-2, regel 1)`() = runTest {
        factory.store.online = false
        val episode = illnesses.newEpisode(day).copy(type = "Förkylning", note = "Började i halsen")
        val first = illnesses.newCheckin(episode.id, day, LocalTime(14, 20)).copy(
            severity = 6, symptoms = listOf(SymptomScore("symptom-huvudvark-d55e7d", 4), SymptomScore("symptom-ovrigt-c067e8", 3, "Ont i halsen")), note = "Feber 38,2",
        )
        assertEquals(IllnessRepository.DEFAULT_SEVERITY, illnesses.newCheckin(episode.id, day, LocalTime(9, 0)).severity)
        assertEquals(clock.now(), episode.createdAt)
        illnesses.startEpisode(episode, first).getOrThrow()

        assertEquals(episode, illnesses.getEpisode(episode.id).getOrThrow())
        assertEquals(listOf(first), illnesses.observeCheckins(episode.id).first())
        assertEquals(emptyList(), DocumentRules.validate(CollectionNames.ILLNESS_EPISODES, IllnessEpisodeCodec.encode(episode)))
        assertEquals(emptyList(), DocumentRules.validate(CollectionNames.CHECKINS, CheckinCodec.encode(first)))
        assertTrue(illnesses.startEpisode(episode.copy(id = "x"), first.copy(createdAt = null)).exceptionOrNull() is IllegalArgumentException)
        assertNull(illnesses.getEpisode("x").getOrThrow(), "ingenting skrivs utan incheckningens skapandetid")
    }

    @Test
    fun `en ändrad incheckning och episod skriver bara ändrade fält under sin episod (SJ-11, SJ-12)`() = runTest {
        val created = Instant.fromEpochSeconds(1_790_000_000)
        val episode = IllnessEpisode("flu", "Förkylning", day, createdAt = created)
        val checkin = Checkin("c", day, LocalTime(9, 0), severity = 3, createdAt = created)
        factory.store.set(Paths.illnessEpisodes(uid), "flu", IllnessEpisodeCodec.encode(episode) + ("framtidaFält" to "kvar"), merge = false)
        factory.store.set(Paths.checkins(uid, "flu"), "c", CheckinCodec.encode(checkin.copy(note = "Annan enhet")) + ("framtidaFält" to "kvar"), merge = false)

        val store = illnesses.checkins("flu")
        store.save(checkin, checkin.copy(severity = 7, time = LocalTime(10, 0))).getOrThrow()
        illnesses.saveEpisode(episode, episode.copy(type = "Influensa")).getOrThrow()

        val raw = factory.store.read(Paths.checkins(uid, "flu"), "c")!!
        assertEquals(7L, raw["severity"])
        assertEquals("10:00", raw["time"])
        assertEquals("Annan enhet", raw["note"])
        assertEquals("kvar", raw["framtidaFält"])
        assertEquals(created, store.get("c").getOrThrow()?.createdAt)
        assertEquals("Influensa", factory.store.read(Paths.illnessEpisodes(uid), "flu")!!["type"])
        assertEquals("kvar", factory.store.read(Paths.illnessEpisodes(uid), "flu")!!["framtidaFält"])

        store.delete("c").getOrThrow()
        assertNull(store.get("c").getOrThrow())
        store.save(checkin, checkin.copy(severity = 1))
        assertNull(factory.store.read(Paths.checkins(uid, "flu"), "c"), "en raderad incheckning återuppstår inte")
    }
}
