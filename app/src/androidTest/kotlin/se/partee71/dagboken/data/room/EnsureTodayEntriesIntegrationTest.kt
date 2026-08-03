package se.partee71.dagboken.data.room

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import se.partee71.dagboken.data.room.entities.MedicinEntity
import se.partee71.dagboken.data.room.entities.ReceptEntity
import se.partee71.dagboken.data.room.entities.toDomain
import se.partee71.dagboken.domain.model.Dosperiod
import se.partee71.dagboken.domain.model.Medicin
import se.partee71.dagboken.domain.usecase.EnsureTodayEntriesUseCase
import java.time.LocalDate

/**
 * Integration: EnsureTodayEntriesUseCase + in-memory Room DAOs.
 * Verifies stable IDs prevent duplication on repeated calls.
 */
@RunWith(AndroidJUnit4::class)
class EnsureTodayEntriesIntegrationTest {

    private lateinit var db: AppDatabase
    private val useCase = EnsureTodayEntriesUseCase()
    private val json = Json { ignoreUnknownKeys = true }

    private fun decode(s: String): List<String> =
        runCatching { json.decodeFromString<List<String>>(s) }.getOrDefault(emptyList())
    private fun decodeInt(s: String): List<Int> =
        runCatching { json.decodeFromString<List<Int>>(s) }.getOrDefault(emptyList())
    private fun decodeDosperioder(s: String): List<Dosperiod> =
        runCatching { json.decodeFromString<List<Dosperiod>>(s) }.getOrDefault(emptyList())

    @Before fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java,
        ).allowMainThreadQueries().build()
    }

    @After fun tearDown() { db.close() }

    @Test fun generatesEntryForActiveRecept() = runTest {
        val today = LocalDate.now()
        val datum = today.toString()
        val medicinDao = db.medicinDao()
        val receptDao = db.receptDao()

        receptDao.upsert(ReceptEntity(
            id = "r1", namn = "Metformin", dos = "500", enhet = "mg",
            tidpunkterJson = """["Morgon"]""", upprepning = "dagligen",
            dagarJson = "[]", intervalDagar = 1, aktiv = true,
            skapad = datum,
        ))

        val recept = receptDao.getActive().map { it.toDomain(::decode, ::decodeInt, ::decodeDosperioder) }
        val existing = medicinDao.getByDate(datum).map { it.toDomain() }
        val newEntries = useCase.compute(recept, existing, today)

        assertEquals(1, newEntries.size)
        assertEquals("recept_r1_${datum}_Morgon", newEntries[0].id)
        assertEquals("Metformin", newEntries[0].namn)
    }

    @Test fun noDuplicatesOnSecondRun() = runTest {
        val today = LocalDate.now()
        val datum = today.toString()
        val medicinDao = db.medicinDao()
        val receptDao = db.receptDao()

        receptDao.upsert(ReceptEntity(
            id = "r1", namn = "Metformin", dos = "500", enhet = "mg",
            tidpunkterJson = """["Morgon"]""", upprepning = "dagligen",
            dagarJson = "[]", intervalDagar = 1, aktiv = true,
            skapad = datum,
        ))

        val recept = receptDao.getActive().map { it.toDomain(::decode, ::decodeInt, ::decodeDosperioder) }
        val first = useCase.compute(recept, emptyList(), today)
        medicinDao.upsertAll(first.map { it.toMedicinEntity() })

        val existing2 = medicinDao.getByDate(datum).map { it.toDomain() }
        val second = useCase.compute(recept, existing2, today)
        assertTrue("No duplicates", second.isEmpty())
    }

    @Test fun generatesOneEntryPerTidpunkt() = runTest {
        val today = LocalDate.now()
        val receptDao = db.receptDao()

        receptDao.upsert(ReceptEntity(
            id = "r2", namn = "Vitamin D", dos = "1", enhet = "st",
            tidpunkterJson = """["Morgon","Kväll"]""", upprepning = "dagligen",
            dagarJson = "[]", intervalDagar = 1, aktiv = true,
            skapad = today.toString(),
        ))

        val recept = receptDao.getActive().map { it.toDomain(::decode, ::decodeInt, ::decodeDosperioder) }
        val entries = useCase.compute(recept, emptyList(), today)
        assertEquals(2, entries.size)
        assertTrue(entries.any { it.tidpunkt == "Morgon" })
        assertTrue(entries.any { it.tidpunkt == "Kväll" })
    }

    @Test fun skipsInactiveRecept() = runTest {
        val today = LocalDate.now()
        val receptDao = db.receptDao()

        receptDao.upsert(ReceptEntity(
            id = "r3", namn = "Inaktiv", dos = "10", enhet = "mg",
            tidpunkterJson = """["Morgon"]""", upprepning = "dagligen",
            dagarJson = "[]", intervalDagar = 1, aktiv = false,
            skapad = today.toString(),
        ))

        // getActive() returns only aktiv=1
        val recept = receptDao.getActive().map { it.toDomain(::decode, ::decodeInt, ::decodeDosperioder) }
        val entries = useCase.compute(recept, emptyList(), today)
        assertTrue(entries.isEmpty())
    }

    @Test fun noEntriesAfterPeriodEnd() = runTest {
        val today = LocalDate.now()
        val receptDao = db.receptDao()

        receptDao.upsert(ReceptEntity(
            id = "r4", namn = "Amoxicillin", dos = "500", enhet = "mg",
            tidpunkterJson = """["Morgon"]""", upprepning = "dagligen",
            dagarJson = "[]", intervalDagar = 1, aktiv = true,
            skapad = today.minusDays(10).toString(),
            startDatum = today.minusDays(10).toString(),
            slutDatum = today.minusDays(1).toString(),
        ))

        val recept = receptDao.getActive().map { it.toDomain(::decode, ::decodeInt, ::decodeDosperioder) }
        assertTrue(useCase.compute(recept, emptyList(), today).isEmpty())
        // Sista dagen i perioden genererar fortfarande en dos
        assertEquals(1, useCase.compute(recept, emptyList(), today.minusDays(1)).size)
    }

    @Test fun dosperiodOverridesBaseDose() = runTest {
        val today = LocalDate.now()
        val receptDao = db.receptDao()

        receptDao.upsert(ReceptEntity(
            id = "r5", namn = "Prednisolon", dos = "5", enhet = "mg",
            tidpunkterJson = """["Morgon"]""", upprepning = "dagligen",
            dagarJson = "[]", intervalDagar = 1, aktiv = true,
            skapad = today.toString(),
            startDatum = today.toString(),
            slutDatum = today.plusDays(9).toString(),
            dosperioderJson = """[{"id":"d1","startDatum":"$today","slutDatum":"$today","dos":"20","enhet":"mg"}]""",
        ))

        val recept = receptDao.getActive().map { it.toDomain(::decode, ::decodeInt, ::decodeDosperioder) }
        assertEquals("20", useCase.compute(recept, emptyList(), today)[0].dos)
        assertEquals("5", useCase.compute(recept, emptyList(), today.plusDays(1))[0].dos)
    }
}

private fun Medicin.toMedicinEntity() = MedicinEntity(
    id = id, timestamp = timestamp, datum = datum, tid = tid,
    namn = namn, dos = dos, enhet = enhet, tidpunkt = tidpunkt,
    tagen = tagen, receptId = receptId, skipped = skipped,
)
