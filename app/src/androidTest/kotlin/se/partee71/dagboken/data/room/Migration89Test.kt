package se.partee71.dagboken.data.room

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class Migration89Test {

    private val TEST_DB = "migration89-test"

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java,
    )

    @Test fun `migration drops anteckning column from sjukdomsepisoder but preserves other fields`() {
        helper.createDatabase(TEST_DB, 8).use { db ->
            db.execSQL(
                """INSERT INTO sjukdomsepisoder (id, typ, start_datum, slut_datum, anteckning, timestamp)
                   VALUES ('e1', 'migrän', '2026-01-10', '2026-01-12', 'Svår period', 1700000000000)"""
            )
        }

        helper.runMigrationsAndValidate(TEST_DB, 9, true, AppDatabase.MIGRATION_8_9).use { db ->
            val cursor = db.query("SELECT typ, start_datum, slut_datum FROM sjukdomsepisoder WHERE id = 'e1'")
            cursor.moveToFirst()
            assertEquals("migrän", cursor.getString(0))
            assertEquals("2026-01-10", cursor.getString(1))
            assertEquals("2026-01-12", cursor.getString(2))
            cursor.close()

            val cols = db.query("PRAGMA table_info(sjukdomsepisoder)")
            val hasAnteckning = cols.use { c ->
                generateSequence { if (c.moveToNext()) c else null }
                    .any { c.getString(c.getColumnIndexOrThrow("name")) == "anteckning" }
            }
            assertFalse(hasAnteckning)
        }
    }

    @Test fun `migration backfills sjukdomsepisoder anteckning into notes under SJUKDOM_EPISOD target`() {
        helper.createDatabase(TEST_DB, 8).use { db ->
            db.execSQL(
                """INSERT INTO sjukdomsepisoder (id, typ, start_datum, slut_datum, anteckning, timestamp)
                   VALUES ('e1', 'migrän', '2026-01-10', '', 'Svår period', 1700000000000)"""
            )
        }

        helper.runMigrationsAndValidate(TEST_DB, 9, true, AppDatabase.MIGRATION_8_9).use { db ->
            val cursor = db.query("SELECT text FROM notes WHERE target = 'SJUKDOM_EPISOD' AND entityId = 'e1'")
            cursor.moveToFirst()
            assertEquals("Svår period", cursor.getString(0))
            cursor.close()
        }
    }

    @Test fun `migration drops anteckning column from sjukdoms_incheckningar and preserves the FK relation`() {
        helper.createDatabase(TEST_DB, 8).use { db ->
            db.execSQL(
                """INSERT INTO sjukdomsepisoder (id, typ, start_datum, slut_datum, anteckning, timestamp)
                   VALUES ('e1', 'migrän', '2026-01-10', '', '', 1700000000000)"""
            )
            db.execSQL(
                """INSERT INTO sjukdoms_incheckningar (id, episod_id, datum, tid, svarighetsgrad, symptom, somatiska, anteckning, timestamp)
                   VALUES ('i1', 'e1', '2026-01-10', '10:00', 8, 'Yrsel:3', 2, 'Tog medicin', 1700000111000)"""
            )
        }

        helper.runMigrationsAndValidate(TEST_DB, 9, true, AppDatabase.MIGRATION_8_9).use { db ->
            val inch = db.query("SELECT episod_id, svarighetsgrad FROM sjukdoms_incheckningar WHERE id = 'i1'")
            inch.moveToFirst()
            assertEquals("e1", inch.getString(0))
            assertEquals(8, inch.getInt(1))
            inch.close()

            val cols = db.query("PRAGMA table_info(sjukdoms_incheckningar)")
            val hasAnteckning = cols.use { c ->
                generateSequence { if (c.moveToNext()) c else null }
                    .any { c.getString(c.getColumnIndexOrThrow("name")) == "anteckning" }
            }
            assertFalse(hasAnteckning)

            val note = db.query("SELECT text FROM notes WHERE target = 'SJUKDOM_INCHECKNING' AND entityId = 'i1'")
            note.moveToFirst()
            assertEquals("Tog medicin", note.getString(0))
            note.close()
        }
    }

    @Test fun `migration preserves cascade delete from episod to incheckningar`() {
        helper.createDatabase(TEST_DB, 8).use { db ->
            db.execSQL(
                """INSERT INTO sjukdomsepisoder (id, typ, start_datum, slut_datum, anteckning, timestamp)
                   VALUES ('e1', 'migrän', '2026-01-10', '', '', 1700000000000)"""
            )
            db.execSQL(
                """INSERT INTO sjukdoms_incheckningar (id, episod_id, datum, tid, svarighetsgrad, symptom, somatiska, anteckning, timestamp)
                   VALUES ('i1', 'e1', '2026-01-10', '10:00', 5, '', 0, '', 1700000000000)"""
            )
        }

        helper.runMigrationsAndValidate(TEST_DB, 9, true, AppDatabase.MIGRATION_8_9).use { db ->
            db.execSQL("PRAGMA foreign_keys=ON")
            db.execSQL("DELETE FROM sjukdomsepisoder WHERE id = 'e1'")
            val cursor = db.query("SELECT COUNT(*) FROM sjukdoms_incheckningar WHERE episod_id = 'e1'")
            cursor.moveToFirst()
            assertEquals(0, cursor.getInt(0))
            cursor.close()
        }
    }

    @Test fun `migration does not create notes rows for blank anteckning`() {
        helper.createDatabase(TEST_DB, 8).use { db ->
            db.execSQL(
                """INSERT INTO sjukdomsepisoder (id, typ, start_datum, slut_datum, anteckning, timestamp)
                   VALUES ('e1', 'migrän', '2026-01-10', '', '', 1700000000000)"""
            )
        }

        helper.runMigrationsAndValidate(TEST_DB, 9, true, AppDatabase.MIGRATION_8_9).use { db ->
            val cursor = db.query("SELECT COUNT(*) FROM notes WHERE target = 'SJUKDOM_EPISOD' AND entityId = 'e1'")
            cursor.moveToFirst()
            assertEquals(0, cursor.getInt(0))
            cursor.close()
        }
    }
}
