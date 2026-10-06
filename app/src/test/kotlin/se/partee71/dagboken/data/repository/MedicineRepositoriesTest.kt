package se.partee71.dagboken.data.repository

import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.coroutines.delay
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Duration.Companion.minutes
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import org.junit.Test
import se.partee71.dagboken.core.model.Dose
import se.partee71.dagboken.core.model.DoseIds
import se.partee71.dagboken.core.model.DoseStatus
import se.partee71.dagboken.core.model.Period
import se.partee71.dagboken.core.model.Prescription
import se.partee71.dagboken.core.model.PrnMedicine
import se.partee71.dagboken.core.model.Schedule
import se.partee71.dagboken.core.model.Slot
import se.partee71.dagboken.core.schema.PrescriptionCodec
import se.partee71.dagboken.core.schema.DoseCodec
import se.partee71.dagboken.data.FakeCollection
import se.partee71.dagboken.data.FakeCollectionFactory
import se.partee71.dagboken.data.FixedClock
import se.partee71.dagboken.data.common.CollectionFactory
import se.partee71.dagboken.data.common.DataError
import se.partee71.dagboken.data.common.EntityCollection
import se.partee71.dagboken.data.firestore.Paths

/**
 * Recept, vid behov-mediciner och doser mot `FakeCollection` (REC-5, REC-8, REC-10, MED-4, FAV-2, DAT-11):
 * bara de fält som ändras skrivs, och ingen dos skrivs över.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MedicineRepositoriesTest {

    private val factory = FakeCollectionFactory()
    private val zone = TimeZone.of("Europe/Stockholm")

    /** FixedClock = måndag 21 september 2026. */
    private val today = LocalDate(2026, 9, 21)
    private val tomorrow = LocalDate(2026, 9, 22)

    private val doses = DefaultDoseRepository(factory) { zone }
    private val prescriptions = DefaultPrescriptionRepository(factory, doses, FixedClock()) { zone }
    private val prnMedicines = DefaultPrnMedicineRepository(factory)

    private val levaxin = Prescription(
        "levaxin",
        name = "Levaxin",
        dose = "100",
        unit = "µg",
        slots = listOf(Slot.MORNING, Slot.EVENING),
        schedule = Schedule.Repeating(),
        period = Period(start = LocalDate(2026, 1, 1)),
    )

    private fun planned(date: LocalDate, slot: Slot, status: DoseStatus = DoseStatus.PLANNED) =
        Dose(DoseIds.prescribed(levaxin.id, date, slot), date, slot, "Levaxin", "100", "µg", status, slot.defaultTime, prescriptionId = levaxin.id)

    private fun prescriptionsPath() = Paths.prescriptions(factory.scope.uid.value!!)

    private fun dosesPath() = Paths.doses(factory.scope.uid.value!!)

    @Test
    fun `avaktivering skriver bara active och tar bort dagens planerade doser, inte tagna eller gårdagens (REC-5)`() = runTest {
        // En annan enhet har ändrat dosen; ett fält från en nyare app finns också.
        factory.store.set(prescriptionsPath(), levaxin.id, PrescriptionCodec.encode(levaxin.copy(dose = "50")) + ("framtidaFält" to "kvar"), merge = false)
        val before = factory.store.read(prescriptionsPath(), levaxin.id)!!
        val yesterday = LocalDate(2026, 9, 20)
        factory.doses().batch(
            listOf(
                planned(yesterday, Slot.MORNING),
                planned(today, Slot.MORNING, DoseStatus.TAKEN),
                planned(today, Slot.EVENING),
                planned(tomorrow, Slot.MORNING),
            ),
        ).getOrThrow()

        prescriptions.setActive(levaxin, false).getOrThrow()

        assertEquals(before + ("active" to false), factory.store.read(prescriptionsPath(), levaxin.id), "bara active skrivs – dosen och det okända fältet står kvar (DAT-11)")
        val left = factory.doses().getAll().getOrThrow().map { it.id }.toSet()
        assertEquals(setOf(planned(yesterday, Slot.MORNING).id, planned(today, Slot.MORNING).id), left)
    }

    @Test
    fun `aktivering skapar dagens saknade doser men skriver aldrig över en tagen (MED-4)`() = runTest {
        factory.prescriptions().upsert(levaxin.copy(active = false)).getOrThrow()
        factory.doses().upsert(planned(today, Slot.MORNING, DoseStatus.TAKEN)).getOrThrow()

        prescriptions.setActive(levaxin.copy(active = false), true).getOrThrow()

        val all = factory.doses().getAll().getOrThrow().associateBy { it.id }
        assertEquals(DoseStatus.TAKEN, all.getValue(planned(today, Slot.MORNING).id).status)
        assertEquals(DoseStatus.PLANNED, all.getValue(planned(today, Slot.EVENING).id).status)
        assertEquals(2, all.size, "bara idag – senare dagar fylls på när de visas")
        assertEquals(true, factory.prescriptions().get(levaxin.id).getOrThrow()?.active)
    }

    @Test
    fun `ett recept som raderats under tiden återuppstår inte av reglaget och får inga doser`() = runTest {
        prescriptions.setActive(levaxin, false)
        prescriptions.setActive(levaxin, true)
        assertNull(factory.store.read(prescriptionsPath(), levaxin.id))
        assertEquals(emptyList(), factory.doses().getAll().getOrThrow())
    }

    @Test
    fun `en dos som tagits på en annan enhet efter läsningen varken raderas eller byter namn (REC-5, REC-10)`() = runTest {
        val renamed = levaxin.copy(name = "Levaxin Ny", dose = "150")
        factory.prescriptions().upsert(renamed).getOrThrow()
        val morning = planned(today, Slot.MORNING)
        factory.doses().upsert(morning.copy(status = DoseStatus.TAKEN)).getOrThrow()
        // Läsningen gjordes innan den andra enhetens avbockning nådde servern.
        val staleRead = dosesWith { object : EntityCollection<Dose> by it {
            override suspend fun confirmedFrom(field: String, from: Any): Result<List<Dose>> = Result.success(listOf(morning))
        } }
        val doses = DefaultDoseRepository(staleRead) { zone }

        doses.syncPrescription(renamed, today).getOrThrow()
        assertEquals(morning.copy(status = DoseStatus.TAKEN), factory.doses().get(morning.id).getOrThrow(), "namn och dos följer inte till en tagen dos")

        doses.syncPrescription(renamed.copy(active = false), today).getOrThrow()
        assertEquals(DoseStatus.TAKEN, factory.doses().get(morning.id).getOrThrow()?.status, "prövas mot det lagrade, inte läsningens kopia")
    }

    @Test
    fun `namn och dos följer receptet på planerade doser (REC-10)`() = runTest {
        factory.doses().upsert(planned(today, Slot.MORNING)).getOrThrow()
        doses.syncPrescription(levaxin.copy(name = "Levaxin Ny", dose = "150"), today).getOrThrow()
        val stored = factory.doses().get(planned(today, Slot.MORNING).id).getOrThrow()!!
        assertEquals(listOf("Levaxin Ny", "150"), listOf(stored.name, stored.dose))
    }

    @Test
    fun `två snabba växlingar av-på slutar med receptets lagrade läge och dess doser`() = runTest {
        factory.prescriptions().upsert(levaxin).getOrThrow()
        factory.doses().batch(listOf(planned(today, Slot.MORNING), planned(today, Slot.EVENING))).getOrThrow()
        // Svaret dröjer efter läsningen, så att den andra växlingen hinner läsa samma doser innan den
        // första raderat dem – utan serialisering blir dagen då utan doser.
        val slow = dosesWith { object : EntityCollection<Dose> by it {
            override suspend fun confirmedFrom(field: String, from: Any): Result<List<Dose>> {
                val read = it.confirmedFrom(field, from)
                delay(100)
                return read
            }
        } }
        val repository = prescriptionsWith(factory, DefaultDoseRepository(slow) { zone })

        val off = launch { repository.setActive(levaxin, false).getOrThrow() }
        val on = launch { repository.setActive(levaxin.copy(active = false), true).getOrThrow() }
        runCurrent()
        assertEquals(true, factory.prescriptions().get(levaxin.id).getOrThrow()?.active, "den andra växlingen skrivs direkt, utan att vänta på den förstas synk")
        joinAll(off, on)

        assertEquals(true, factory.prescriptions().get(levaxin.id).getOrThrow()?.active)
        assertEquals(2, factory.doses().getAll().getOrThrow().size, "dagens doser finns – den första växlingens radering kom inte sist")
    }

    @Test
    fun `städningen avslutar utgångna recept och tar deras och de inaktivas planerade doser, på serverbekräftade recept (REC-8, REC-5)`() = runTest {
        val expired = levaxin.copy(id = "utgangen", period = Period(LocalDate(2026, 8, 1), LocalDate(2026, 9, 20)))
        val running = levaxin.copy(id = "pagaende")
        factory.prescriptions().batch(listOf(expired, running)).getOrThrow()
        fun plannedFor(p: Prescription) = Dose(DoseIds.prescribed(p.id, today, Slot.MORNING), today, Slot.MORNING, p.name, status = DoseStatus.PLANNED, prescriptionId = p.id)
        factory.doses().batch(listOf(plannedFor(expired), plannedFor(running))).getOrThrow()

        prescriptions.tidyUp(today).getOrThrow()

        assertEquals(false, factory.prescriptions().get(expired.id).getOrThrow()?.active)
        assertEquals(true, factory.prescriptions().get(running.id).getOrThrow()?.active)
        assertEquals(listOf(plannedFor(running).id), factory.doses().getAll().getOrThrow().map { it.id })
    }

    @Test
    fun `städningen offline gör ingenting och är inget fel`() = runTest {
        factory.prescriptions().upsert(levaxin.copy(period = Period(LocalDate(2026, 8, 1), LocalDate(2026, 9, 20)))).getOrThrow()
        val offline = object : CollectionFactory by factory {
            override fun prescriptions(): EntityCollection<Prescription> = object : EntityCollection<Prescription> by factory.prescriptions() {
                override suspend fun confirmed(): Result<List<Prescription>> = Result.failure(DataError.Offline)
            }
        }
        prescriptionsWith(offline, doses).tidyUp(today).getOrThrow()
        assertEquals(true, factory.prescriptions().get(levaxin.id).getOrThrow()?.active, "ingenting avslutas på cachens recept")
    }

    @Test
    fun `offline läsning gör ingenting och är inget fel`() = runTest {
        factory.prescriptions().upsert(levaxin).getOrThrow()
        factory.doses().upsert(planned(today, Slot.MORNING)).getOrThrow()
        val offline = dosesWith { object : EntityCollection<Dose> by it {
            override suspend fun confirmedFrom(field: String, from: Any): Result<List<Dose>> = Result.failure(DataError.Offline)
        } }
        prescriptionsWith(factory, DefaultDoseRepository(offline) { zone }).setActive(levaxin, false).getOrThrow()
        assertEquals(1, factory.doses().getAll().getOrThrow().size)
    }

    @Test
    fun `städningen tar bara inaktiva recepts planerade doser från idag`() = runTest {
        val yesterday = LocalDate(2026, 9, 20)
        factory.doses().batch(listOf(planned(yesterday, Slot.MORNING), planned(today, Slot.MORNING), planned(today, Slot.EVENING, DoseStatus.SKIPPED))).getOrThrow()
        assertEquals(emptyList(), doses.inactiveWithPlanned(listOf(levaxin), today).getOrThrow(), "aktivt recept: ingen kandidat")
        assertEquals(listOf(levaxin.id), doses.inactiveWithPlanned(listOf(levaxin.copy(active = false)), today).getOrThrow())

        factory.prescriptions().upsert(levaxin.copy(active = false)).getOrThrow()
        prescriptions.tidyUp(today).getOrThrow()
        assertEquals(
            setOf(planned(yesterday, Slot.MORNING).id, planned(today, Slot.EVENING).id),
            factory.doses().getAll().getOrThrow().map { it.id }.toSet(),
        )
    }

    @Test
    fun `städningen samtidigt med att receptet slås på raderar inte de nya doserna`() = runTest {
        factory.prescriptions().upsert(levaxin.copy(active = false)).getOrThrow()
        factory.doses().upsert(planned(today, Slot.MORNING)).getOrThrow()
        // Städningens receptlista dröjer, så att reglaget hinner slå på receptet och synka emellan.
        val slowList = object : CollectionFactory by factory {
            override fun prescriptions(): EntityCollection<Prescription> = object : EntityCollection<Prescription> by factory.prescriptions() {
                override suspend fun confirmed(): Result<List<Prescription>> {
                    val read = factory.prescriptions().confirmed()
                    delay(100)
                    return read
                }
            }
        }
        val repository = prescriptionsWith(slowList, doses)

        val tidy = launch { repository.tidyUp(today).getOrThrow() }
        runCurrent()
        repository.setActive(levaxin.copy(active = false), true).getOrThrow()
        assertEquals(2, factory.doses().getAll().getOrThrow().size, "reglaget har skapat dagens doser")
        tidy.join()

        assertEquals(true, factory.prescriptions().get(levaxin.id).getOrThrow()?.active)
        assertEquals(
            setOf(planned(today, Slot.MORNING).id, planned(today, Slot.EVENING).id),
            factory.doses().getAll().getOrThrow().map { it.id }.toSet(),
            "städningen beslutar på receptet som det är nu, under dess lås – inte på sin gamla lista",
        )
    }

    @Test
    fun `städningen avslutar inte ett recept som under tiden förlängts – beslutet tas under låset`() = runTest {
        val expired = levaxin.copy(period = Period(LocalDate(2026, 8, 1), LocalDate(2026, 9, 20)))
        factory.prescriptions().upsert(expired).getOrThrow()
        val slowList = object : CollectionFactory by factory {
            override fun prescriptions(): EntityCollection<Prescription> = object : EntityCollection<Prescription> by factory.prescriptions() {
                override suspend fun confirmed(): Result<List<Prescription>> {
                    val read = factory.prescriptions().confirmed()
                    delay(100)
                    return read
                }
            }
        }
        val tidy = launch { prescriptionsWith(slowList, doses).tidyUp(today).getOrThrow() }
        runCurrent()
        // En annan enhet förlänger perioden medan städningens lista är på väg.
        factory.prescriptions().upsert(expired.copy(period = Period(LocalDate(2026, 8, 1), LocalDate(2026, 12, 31)))).getOrThrow()
        tidy.join()

        assertEquals(true, factory.prescriptions().get(levaxin.id).getOrThrow()?.active, "avslutas bara om det fortfarande är utgånget enligt servern")
        assertEquals(2, factory.doses().getAll().getOrThrow().size, "och synken ger dagens doser")
    }

    @Test
    fun `en radering som committar efter skyddsnätet väntas in innan nästa synk läser doserna`() = runTest {
        factory.prescriptions().upsert(levaxin).getOrThrow()
        factory.doses().batch(listOf(planned(today, Slot.MORNING), planned(today, Slot.EVENING))).getOrThrow()
        // Dåligt nät: villkorade skrivningar committar efter 20 s, men den som väntar ger upp efter 15 s.
        val slowNet = object : CollectionFactory by factory {
            override fun doses(): EntityCollection<Dose> = FakeCollection(
                factory.store, factory.scope, FixedClock(), DoseCodec, Paths.DOSES, { Paths.doses(it!!) },
                commitLatency = 20.seconds, serverWait = 15.seconds, commitScope = backgroundScope,
            )
        }
        val repository = prescriptionsWith(factory, DefaultDoseRepository(slowNet) { zone })

        repository.setActive(levaxin, false).getOrThrow() // raderingen hinner inte bekräftas – Offline, ingen fel
        repository.setActive(levaxin.copy(active = false), true).getOrThrow()
        advanceTimeBy(1.minutes) // låt de sena commitarna i bakgrunden landa

        assertEquals(
            setOf(planned(today, Slot.MORNING).id, planned(today, Slot.EVENING).id),
            factory.doses().getAll().getOrThrow().map { it.id }.toSet(),
            "den sena raderingen landade före nästa synks läsning, som då skapade doserna igen",
        )
    }

    @Test
    fun `offline hoppas raderingen och skapandet över utan fel - doserna städas nästa gång med nät`() = runTest {
        factory.prescriptions().upsert(levaxin).getOrThrow()
        factory.doses().upsert(planned(today, Slot.MORNING)).getOrThrow()
        val offline = dosesWith { object : EntityCollection<Dose> by it {
            override suspend fun deleteIf(ids: List<String>, condition: (Dose) -> Boolean): Result<Unit> = Result.failure(DataError.Offline)
            override suspend fun updateIf(items: List<Dose>, fields: Set<String>, condition: (Dose) -> Boolean): Result<Unit> = Result.failure(DataError.Offline)
            override suspend fun createIfAbsent(items: List<Dose>): Result<Unit> = Result.failure(DataError.Offline)
        } }
        val offlineDoses = DefaultDoseRepository(offline) { zone }

        prescriptionsWith(factory, offlineDoses).setActive(levaxin, false).getOrThrow()
        assertEquals(false, factory.prescriptions().get(levaxin.id).getOrThrow()?.active, "reglaget skrivs ändå")
        assertEquals(setOf(planned(today, Slot.MORNING).id), factory.doses().getAll().getOrThrow().map { it.id }.toSet())

        offlineDoses.createIfAbsent(listOf(planned(today, Slot.EVENING))).getOrThrow()
        assertNull(factory.store.read(dosesPath(), planned(today, Slot.EVENING).id), "offline: ingen dos")
    }

    @Test
    fun `synken läser bara dagens och senare doser, aldrig hela samlingen`() = runTest {
        factory.prescriptions().upsert(levaxin).getOrThrow()
        val bounded = dosesWith { object : EntityCollection<Dose> by it {
            override suspend fun cached(): Result<List<Dose>> = error("hela samlingen ska inte läsas")
        } }
        prescriptionsWith(factory, DefaultDoseRepository(bounded) { zone }).setActive(levaxin, true).getOrThrow()
        assertEquals(2, factory.doses().getAll().getOrThrow().size)
    }

    @Test
    fun `createIfAbsent skriver aldrig över en dos som finns`() = runTest {
        val taken = planned(today, Slot.MORNING, DoseStatus.TAKEN)
        factory.doses().upsert(taken).getOrThrow()

        doses.createIfAbsent(listOf(planned(today, Slot.MORNING), planned(today, Slot.EVENING))).getOrThrow()
        val all = factory.doses().getAll().getOrThrow().associateBy { it.id }
        assertEquals(DoseStatus.TAKEN, all.getValue(taken.id).status, "en tagen dos skrivs aldrig över")
        assertEquals(DoseStatus.PLANNED, all.getValue(planned(today, Slot.EVENING).id).status)
    }

    @Test
    fun `utgångna recept avslutas med bara active (REC-8)`() = runTest {
        val period = mapOf("start" to "2026-09-01", "end" to "2026-09-20")
        factory.store.set(prescriptionsPath(), "a", mapOf("name" to "Kåvepenin", "active" to true, "note" to "Med mat", "period" to period), merge = false)
        prescriptions.tidyUp(today).getOrThrow()
        assertEquals(mapOf("name" to "Kåvepenin", "active" to false, "note" to "Med mat", "period" to period), factory.store.read(prescriptionsPath(), "a"))
    }

    @Test
    fun `receptet tas bort men dess doser står kvar`() = runTest {
        factory.prescriptions().upsert(levaxin).getOrThrow()
        factory.doses().upsert(planned(today, Slot.MORNING, DoseStatus.TAKEN)).getOrThrow()
        prescriptions.delete(levaxin.id).getOrThrow()
        assertNull(prescriptions.get(levaxin.id).getOrThrow())
        assertEquals(1, factory.doses().getAll().getOrThrow().size)
    }

    @Test
    fun `vid behov - ny får eget id, sparning skriver bara det ändrade och stjärnan bara favorite (FAV-2, FAV-7)`() = runTest {
        prnMedicines.add(PrnMedicine("", name = "Alvedon", dose = "500", unit = "mg")).getOrThrow()
        val added = prnMedicines.observeFirst()
        assertEquals("Alvedon", added.name)

        // En annan enhet stjärnmärker och sätter dispenseringstid medan formuläret är öppet.
        prnMedicines.setFavorite(added, true).getOrThrow()
        factory.store.set(Paths.prnMedicines(factory.scope.uid.value!!), added.id, mapOf("dispensingTime" to "30 min"), merge = true)
        prnMedicines.save(added, added.copy(dose = "1000", maxPerDay = 4)).getOrThrow()

        val stored = prnMedicines.get(added.id).getOrThrow()!!
        assertEquals(added.copy(dose = "1000", maxPerDay = 4, favorite = true, dispensingTime = "30 min"), stored)

        prnMedicines.delete(added.id).getOrThrow()
        prnMedicines.save(added, added.copy(dose = "2")).getOrThrow()
        assertNull(prnMedicines.get(added.id).getOrThrow(), "en raderad medicin återuppstår inte")
    }

    /** Samma fejkdatabas, men med doses-samlingen utbytt av [wrap]. */
    private fun dosesWith(wrap: (EntityCollection<Dose>) -> EntityCollection<Dose>): CollectionFactory = object : CollectionFactory by factory {
        override fun doses(): EntityCollection<Dose> = wrap(factory.doses())
    }

    private fun prescriptionsWith(collections: CollectionFactory, doses: DoseRepository) =
        DefaultPrescriptionRepository(collections, doses, FixedClock()) { zone }

    private suspend fun PrnMedicineRepository.observeFirst(): PrnMedicine = factory.prnMedicines().getAll().getOrThrow().single()
}
