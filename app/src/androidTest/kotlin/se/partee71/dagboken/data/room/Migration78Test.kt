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
class Migration78Test {

    private val TEST_DB = "migration78-test"

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java,
    )

    @Test fun `migration drops anteckning column from health_events but preserves other fields`() {
        helper.createDatabase(TEST_DB, 7).use { db ->
            db.execSQL(
                """INSERT INTO health_events (id, timestamp, datum, tid, typ, svarighetsgrad, varaktighetMinuter, triggers, atgarder, anteckning)
                   VALUES ('h1', '2026-01-15T14:30:00.000Z', '2026-01-15', '14:30', 'huvudvärk', 6, 90, '["stress"]', '["vila"]', 'Kom efter möte')"""
            )
        }

        helper.runMigrationsAndValidate(TEST_DB, 8, true, AppDatabase.MIGRATION_7_8).use { db ->
            val cursor = db.query("SELECT typ, svarighetsgrad, varaktighetMinuter FROM health_events WHERE id = 'h1'")
            cursor.moveToFirst()
            assertEquals("huvudvärk", cursor.getString(0))
            assertEquals(6, cursor.getInt(1))
            assertEquals(90, cursor.getInt(2))
            cursor.close()

            val cols = db.query("PRAGMA table_info(health_events)")
            val hasAnteckning = cols.use { c ->
                generateSequence { if (c.moveToNext()) c else null }
                    .any { c.getString(c.getColumnIndexOrThrow("name")) == "anteckning" }
            }
            assertFalse(hasAnteckning)
        }
    }

    @Test fun `migration backfills health_events anteckning into notes under EVENT target`() {
        helper.createDatabase(TEST_DB, 7).use { db ->
            db.execSQL(
                """INSERT INTO health_events (id, timestamp, datum, tid, typ, svarighetsgrad, varaktighetMinuter, triggers, atgarder, anteckning)
                   VALUES ('h1', '2026-01-15T14:30:00.000Z', '2026-01-15', '14:30', 'huvudvärk', 6, 90, '[]', '[]', 'Kom efter möte')"""
            )
        }

        helper.runMigrationsAndValidate(TEST_DB, 8, true, AppDatabase.MIGRATION_7_8).use { db ->
            val cursor = db.query("SELECT text FROM notes WHERE target = 'EVENT' AND entityId = 'h1'")
            cursor.moveToFirst()
            assertEquals("Kom efter möte", cursor.getString(0))
            cursor.close()
        }
    }

    @Test fun `migration does not create a notes row for blank anteckning`() {
        helper.createDatabase(TEST_DB, 7).use { db ->
            db.execSQL(
                """INSERT INTO health_events (id, timestamp, datum, tid, typ, svarighetsgrad, varaktighetMinuter, triggers, atgarder, anteckning)
                   VALUES ('h1', '2026-01-15T14:30:00.000Z', '2026-01-15', '14:30', 'huvudvärk', 6, 90, '[]', '[]', '')"""
            )
        }

        helper.runMigrationsAndValidate(TEST_DB, 8, true, AppDatabase.MIGRATION_7_8).use { db ->
            val cursor = db.query("SELECT COUNT(*) FROM notes WHERE target = 'EVENT' AND entityId = 'h1'")
            cursor.moveToFirst()
            assertEquals(0, cursor.getInt(0))
            cursor.close()
        }
    }

    @Test fun `migration preserves an already-existing datum index`() {
        helper.createDatabase(TEST_DB, 7).use { db ->
            db.execSQL(
                """INSERT INTO health_events (id, timestamp, datum, tid, typ, svarighetsgrad, varaktighetMinuter, triggers, atgarder, anteckning)
                   VALUES ('h1', '2026-01-15T14:30:00.000Z', '2026-01-15', '14:30', 'huvudvärk', 6, 90, '[]', '[]', '')"""
            )
        }

        helper.runMigrationsAndValidate(TEST_DB, 8, true, AppDatabase.MIGRATION_7_8).use { db ->
            val cursor = db.query("SELECT * FROM health_events WHERE datum = '2026-01-15'")
            assertEquals(1, cursor.count)
            cursor.close()
        }
    }
}
