package se.partee71.dagboken.data.room

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import se.partee71.dagboken.data.room.daos.FavoritDao
import se.partee71.dagboken.data.room.daos.ReceptDao
import se.partee71.dagboken.data.room.entities.FavoritEntity
import se.partee71.dagboken.data.room.entities.ReceptEntity

@RunWith(AndroidJUnit4::class)
class ReceptFavoritDaoTest {

    private lateinit var db: AppDatabase
    private lateinit var receptDao: ReceptDao
    private lateinit var favoritDao: FavoritDao

    @Before fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java,
        ).allowMainThreadQueries().build()
        receptDao = db.receptDao()
        favoritDao = db.favoritDao()
    }

    @After fun tearDown() { db.close() }

    private fun receptEntity(id: String = "r1", aktiv: Boolean = true) = ReceptEntity(
        id = id, namn = "Metformin", dos = "500", enhet = "mg",
        tidpunkterJson = """["Morgon"]""", upprepning = "dagligen",
        dagarJson = "[]", intervalDagar = 1, anteckning = "", aktiv = aktiv,
        skapad = "2026-01-01",
    )

    private fun favoritEntity(id: String = "f1") = FavoritEntity(
        id = id, namn = "Ibuprofen", dos = "400", enhet = "mg",
        tidpunkt = "Vid behov", anteckning = "", minTidMellan = 4,
        dispenseringsTid = "", maxDoserPerDag = 2,
    )

    // ─── ReceptDao ────────────────────────────────────────────────────────────

    @Test fun recept_upsertAndGetById() = runTest {
        receptDao.upsert(receptEntity())
        assertNotNull(receptDao.getById("r1"))
    }

    @Test fun recept_getActive_returnsOnlyActive() = runTest {
        receptDao.upsert(receptEntity(id = "r1", aktiv = true))
        receptDao.upsert(receptEntity(id = "r2", aktiv = false))
        val active = receptDao.getActive()
        assertEquals(1, active.size)
        assertEquals("r1", active[0].id)
    }

    @Test fun recept_updateAktiv_toggles() = runTest {
        receptDao.upsert(receptEntity(aktiv = true))
        receptDao.updateAktiv("r1", false)
        val r = receptDao.getById("r1")!!
        assertTrue(!r.aktiv)
    }

    @Test fun recept_delete_removes() = runTest {
        val r = receptEntity()
        receptDao.upsert(r)
        receptDao.delete(r)
        assertNull(receptDao.getById("r1"))
    }

    @Test fun recept_upsertAll_insertsMultiple() = runTest {
        receptDao.upsertAll(listOf(receptEntity("r1"), receptEntity("r2")))
        assertEquals(2, receptDao.getActive().size)
    }

    // ─── FavoritDao ───────────────────────────────────────────────────────────

    @Test fun favorit_upsertAndGetById() = runTest {
        favoritDao.upsert(favoritEntity())
        assertNotNull(favoritDao.getById("f1"))
    }

    @Test fun favorit_getAll_returnsAllInserted() = runTest {
        favoritDao.upsert(favoritEntity("f1"))
        favoritDao.upsert(favoritEntity("f2"))
        assertEquals(2, favoritDao.getAll().size)
    }

    @Test fun favorit_upsert_updatesExisting() = runTest {
        favoritDao.upsert(favoritEntity("f1").copy(namn = "Alvedon"))
        favoritDao.upsert(favoritEntity("f1").copy(namn = "Paracetamol"))
        assertEquals("Paracetamol", favoritDao.getById("f1")!!.namn)
    }

    @Test fun favorit_delete_removes() = runTest {
        val f = favoritEntity()
        favoritDao.upsert(f)
        favoritDao.delete(f)
        assertNull(favoritDao.getById("f1"))
    }

    @Test fun favorit_fields_preserved() = runTest {
        favoritDao.upsert(favoritEntity("f1"))
        val result = favoritDao.getById("f1")!!
        assertEquals(4, result.minTidMellan)
        assertEquals(2, result.maxDoserPerDag)
        assertEquals("mg", result.enhet)
    }

    @Test fun favorit_isFavorite_defaults_to_false() = runTest {
        favoritDao.upsert(favoritEntity("f1"))
        assertEquals(false, favoritDao.getById("f1")!!.isFavorite)
    }

    @Test fun favorit_updateFavorite_setsIsFavorite_withoutChangingOtherFields() = runTest {
        favoritDao.upsert(favoritEntity("f1"))

        favoritDao.updateFavorite("f1", true)

        val result = favoritDao.getById("f1")!!
        assertEquals(true, result.isFavorite)
        assertEquals("mg", result.enhet)
        assertEquals(4, result.minTidMellan)
    }

    @Test fun favorit_updateFavorite_canUnmark() = runTest {
        favoritDao.upsert(favoritEntity("f1").copy(isFavorite = true))

        favoritDao.updateFavorite("f1", false)

        assertEquals(false, favoritDao.getById("f1")!!.isFavorite)
    }
}
