package se.partee71.dagboken.data.room

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * MIGRATION_4_5 lägger till sjukdomsepisoder och sjukdoms_incheckningar, med främmande
 * nyckel och kaskadradering mellan dem. Testet fanns inte tidigare.
 */
@RunWith(AndroidJUnit4::class)
class Migration45Test {

    private val TEST_DB = "migration45-test"

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java,
    )

    @Test fun `migration creates both sjukdom tables and keeps existing notes`() {
        helper.createDatabase(TEST_DB, 4).use { db ->
            db.execSQL("INSERT INTO notes (target, entityId, text) VALUES ('EVENT', 'h1', 'En anteckning')")
        }

        helper.runMigrationsAndValidate(TEST_DB, 5, true, AppDatabase.MIGRATION_4_5).use { db ->
            db.query("SELECT text FROM notes WHERE target = 'EVENT' AND entityId = 'h1'").use { c ->
                assertTrue(c.moveToFirst())
                assertEquals("En anteckning", c.getString(0))
            }

            db.execSQL(
                """INSERT INTO sjukdomsepisoder (id, typ, start_datum, slut_datum, anteckning, timestamp)
                   VALUES ('e1', 'Förkylning', '2026-01-10', '', 'Feber', 1760000000000)"""
            )
            db.execSQL(
                """INSERT INTO sjukdoms_incheckningar
                   (id, episod_id, datum, tid, svarighetsgrad, symptom, somatiska, anteckning, timestamp)
                   VALUES ('i1', 'e1', '2026-01-11', '20:00', 5, 'Hosta:3', 4, '', 1760000100000)"""
            )

            db.query("SELECT typ, start_datum FROM sjukdomsepisoder WHERE id = 'e1'").use { c ->
                assertTrue(c.moveToFirst())
                assertEquals("Förkylning", c.getString(0))
                assertEquals("2026-01-10", c.getString(1))
            }
            db.query("SELECT svarighetsgrad, symptom FROM sjukdoms_incheckningar WHERE id = 'i1'").use { c ->
                assertTrue(c.moveToFirst())
                assertEquals(5, c.getInt(0))
                assertEquals("Hosta:3", c.getString(1))
            }
        }
    }

    @Test fun `deleting an episode cascades to its incheckningar`() {
        helper.createDatabase(TEST_DB, 4).close()

        helper.runMigrationsAndValidate(TEST_DB, 5, true, AppDatabase.MIGRATION_4_5).use { db ->
            db.execSQL("PRAGMA foreign_keys=ON")
            db.execSQL(
                """INSERT INTO sjukdomsepisoder (id, typ, start_datum, slut_datum, anteckning, timestamp)
                   VALUES ('e1', 'Influensa', '2026-02-01', '', '', 1)"""
            )
            db.execSQL(
                """INSERT INTO sjukdoms_incheckningar
                   (id, episod_id, datum, tid, svarighetsgrad, symptom, somatiska, anteckning, timestamp)
                   VALUES ('i1', 'e1', '2026-02-02', '08:00', 7, '', 0, '', 2)"""
            )

            db.execSQL("DELETE FROM sjukdomsepisoder WHERE id = 'e1'")

            db.query("SELECT id FROM sjukdoms_incheckningar WHERE episod_id = 'e1'").use { c ->
                assertFalse("Incheckningen skulle ha raderats med episoden", c.moveToFirst())
            }
        }
    }

    @Test fun `migration creates the episod_id index`() {
        helper.createDatabase(TEST_DB, 4).close()

        helper.runMigrationsAndValidate(TEST_DB, 5, true, AppDatabase.MIGRATION_4_5).use { db ->
            db.query(
                "SELECT name FROM sqlite_master WHERE type = 'index' AND tbl_name = 'sjukdoms_incheckningar'",
            ).use { c ->
                val indexes = generateSequence { if (c.moveToNext()) c.getString(0) else null }.toList()
                assertTrue(
                    "Saknar index på episod_id: $indexes",
                    indexes.contains("index_sjukdoms_incheckningar_episod_id"),
                )
            }
        }
    }
}
