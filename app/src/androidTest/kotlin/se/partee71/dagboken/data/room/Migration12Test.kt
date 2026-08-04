package se.partee71.dagboken.data.room

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * MIGRATION_1_2 lägger till tabellen `health_events`. Testet fanns inte tidigare — 1→2
 * och 4→5 var de enda migreringarna utan täckning.
 */
@RunWith(AndroidJUnit4::class)
class Migration12Test {

    private val TEST_DB = "migration12-test"

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java,
    )

    @Test fun `migration creates health_events and keeps existing rows`() {
        helper.createDatabase(TEST_DB, 1).use { db ->
            db.execSQL(
                """INSERT INTO aktiviteter
                   (id, timestamp, datum, tid, aktivitet, energy, stress, somatiska, symptom,
                    aterhamtande, energitjuv, type, spentTime)
                   VALUES ('a1', '2026-01-15T09:00:00Z', '2026-01-15', '09:00', 'Promenad',
                           7, 3, 2, 'Huvudvärk:4', 1, 0, 'aktivitet', 45)"""
            )
        }

        helper.runMigrationsAndValidate(TEST_DB, 2, true, AppDatabase.MIGRATION_1_2).use { db ->
            // Befintlig data rörs inte av migreringen.
            db.query("SELECT aktivitet, energy, spentTime FROM aktiviteter WHERE id = 'a1'").use { c ->
                assertTrue(c.moveToFirst())
                assertEquals("Promenad", c.getString(0))
                assertEquals(7, c.getInt(1))
                assertEquals(45, c.getInt(2))
            }

            // Den nya tabellen finns och går att skriva till.
            db.execSQL(
                """INSERT INTO health_events
                   (id, timestamp, datum, tid, typ, svarighetsgrad, varaktighetMinuter,
                    triggers, atgarder, anteckning)
                   VALUES ('h1', '2026-01-15T13:00:00Z', '2026-01-15', '13:00', 'Ögonmigrän',
                           6, 30, 'Skärmtid', 'Vila', 'Gick över')"""
            )
            db.query("SELECT typ, svarighetsgrad, anteckning FROM health_events WHERE id = 'h1'").use { c ->
                assertTrue(c.moveToFirst())
                assertEquals("Ögonmigrän", c.getString(0))
                assertEquals(6, c.getInt(1))
                assertEquals("Gick över", c.getString(2))
            }
        }
    }

    @Test fun `migration creates the datum index on health_events`() {
        helper.createDatabase(TEST_DB, 1).close()

        helper.runMigrationsAndValidate(TEST_DB, 2, true, AppDatabase.MIGRATION_1_2).use { db ->
            db.query("SELECT name FROM sqlite_master WHERE type = 'index' AND tbl_name = 'health_events'").use { c ->
                val indexes = generateSequence { if (c.moveToNext()) c.getString(0) else null }.toList()
                assertTrue("Saknar index på datum: $indexes", indexes.contains("index_health_events_datum"))
            }
        }
    }
}
