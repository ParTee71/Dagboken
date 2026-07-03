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
class Migration67Test {

    private val TEST_DB = "migration67-test"

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java,
    )

    @Test fun `migration drops anteckning column from mediciner but preserves other fields`() {
        helper.createDatabase(TEST_DB, 6).use { db ->
            db.execSQL(
                """INSERT INTO mediciner (id, timestamp, datum, tid, namn, dos, enhet, tidpunkt, tagen, anteckning, receptId, skipped)
                   VALUES ('m1', '2026-01-15T07:00:00.000Z', '2026-01-15', '07:00', 'Metformin', '500', 'mg', 'Morgon', 1, 'Tas med mat', NULL, 0)"""
            )
        }

        helper.runMigrationsAndValidate(TEST_DB, 7, true, AppDatabase.MIGRATION_6_7).use { db ->
            val cursor = db.query("SELECT namn, dos, tagen FROM mediciner WHERE id = 'm1'")
            cursor.moveToFirst()
            assertEquals("Metformin", cursor.getString(0))
            assertEquals("500", cursor.getString(1))
            assertEquals(1, cursor.getInt(2))
            cursor.close()

            val cols = db.query("PRAGMA table_info(mediciner)")
            val hasAnteckning = cols.use { c ->
                generateSequence { if (c.moveToNext()) c else null }
                    .any { c.getString(c.getColumnIndexOrThrow("name")) == "anteckning" }
            }
            assertFalse(hasAnteckning)
        }
    }

    @Test fun `migration backfills mediciner anteckning into notes under MEDICATION target`() {
        helper.createDatabase(TEST_DB, 6).use { db ->
            db.execSQL(
                """INSERT INTO mediciner (id, timestamp, datum, tid, namn, dos, enhet, tidpunkt, tagen, anteckning, receptId, skipped)
                   VALUES ('m1', '2026-01-15T07:00:00.000Z', '2026-01-15', '07:00', 'Metformin', '500', 'mg', 'Morgon', 1, 'Tas med mat', NULL, 0)"""
            )
        }

        helper.runMigrationsAndValidate(TEST_DB, 7, true, AppDatabase.MIGRATION_6_7).use { db ->
            val cursor = db.query("SELECT text FROM notes WHERE target = 'MEDICATION' AND entityId = 'm1'")
            cursor.moveToFirst()
            assertEquals("Tas med mat", cursor.getString(0))
            cursor.close()
        }
    }

    @Test fun `migration does not overwrite an existing MEDICATION note with the column value`() {
        // Simulates a row whose anteckning column is stale but which already has a newer
        // note saved via the IdagTab dose-note dialog (NoteTarget.MEDICATION, same id).
        helper.createDatabase(TEST_DB, 6).use { db ->
            db.execSQL(
                """INSERT INTO mediciner (id, timestamp, datum, tid, namn, dos, enhet, tidpunkt, tagen, anteckning, receptId, skipped)
                   VALUES ('m1', '2026-01-15T07:00:00.000Z', '2026-01-15', '07:00', 'Metformin', '500', 'mg', 'Morgon', 1, 'Gammal (kolumn)', NULL, 0)"""
            )
            db.execSQL(
                """INSERT INTO notes (target, entityId, text) VALUES ('MEDICATION', 'm1', 'Ny (dialog)')"""
            )
        }

        helper.runMigrationsAndValidate(TEST_DB, 7, true, AppDatabase.MIGRATION_6_7).use { db ->
            val cursor = db.query("SELECT text FROM notes WHERE target = 'MEDICATION' AND entityId = 'm1'")
            cursor.moveToFirst()
            assertEquals("Ny (dialog)", cursor.getString(0))
            cursor.close()
        }
    }

    @Test fun `migration drops anteckning column from recept and backfills notes under RECEPT target`() {
        helper.createDatabase(TEST_DB, 6).use { db ->
            db.execSQL(
                """INSERT INTO recept (id, namn, dos, enhet, tidpunkterJson, upprepning, dagarJson, intervalDagar, anteckning, aktiv, skapad)
                   VALUES ('r1', 'Vitamin D', '1', 'st', '["Morgon"]', 'dagligen', '[]', 1, 'Kväll bäst', 1, '2026-01-01')"""
            )
        }

        helper.runMigrationsAndValidate(TEST_DB, 7, true, AppDatabase.MIGRATION_6_7).use { db ->
            val recept = db.query("SELECT namn FROM recept WHERE id = 'r1'")
            recept.moveToFirst()
            assertEquals("Vitamin D", recept.getString(0))
            recept.close()

            val note = db.query("SELECT text FROM notes WHERE target = 'RECEPT' AND entityId = 'r1'")
            note.moveToFirst()
            assertEquals("Kväll bäst", note.getString(0))
            note.close()
        }
    }

    @Test fun `migration drops anteckning column from favoriter and backfills notes under FAVORIT target`() {
        helper.createDatabase(TEST_DB, 6).use { db ->
            db.execSQL(
                """INSERT INTO favoriter (id, namn, dos, enhet, tidpunkt, anteckning, minTidMellan, dispenseringsTid, maxDoserPerDag, isFavorite)
                   VALUES ('f1', 'Paracetamol', '500', 'mg', 'Vid behov', 'Max 3/dag', 4, '', 3, 1)"""
            )
        }

        helper.runMigrationsAndValidate(TEST_DB, 7, true, AppDatabase.MIGRATION_6_7).use { db ->
            val fav = db.query("SELECT namn, isFavorite FROM favoriter WHERE id = 'f1'")
            fav.moveToFirst()
            assertEquals("Paracetamol", fav.getString(0))
            assertEquals(1, fav.getInt(1))
            fav.close()

            val note = db.query("SELECT text FROM notes WHERE target = 'FAVORIT' AND entityId = 'f1'")
            note.moveToFirst()
            assertEquals("Max 3/dag", note.getString(0))
            note.close()
        }
    }

    @Test fun `migration does not create a notes row for blank anteckning`() {
        helper.createDatabase(TEST_DB, 6).use { db ->
            db.execSQL(
                """INSERT INTO mediciner (id, timestamp, datum, tid, namn, dos, enhet, tidpunkt, tagen, anteckning, receptId, skipped)
                   VALUES ('m1', '2026-01-15T07:00:00.000Z', '2026-01-15', '07:00', 'Metformin', '500', 'mg', 'Morgon', 1, '', NULL, 0)"""
            )
        }

        helper.runMigrationsAndValidate(TEST_DB, 7, true, AppDatabase.MIGRATION_6_7).use { db ->
            val cursor = db.query("SELECT COUNT(*) FROM notes WHERE target = 'MEDICATION' AND entityId = 'm1'")
            cursor.moveToFirst()
            assertEquals(0, cursor.getInt(0))
            cursor.close()
        }
    }
}
