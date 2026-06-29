package se.partee71.dagboken.data.room

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class Migration23Test {

    private val TEST_DB = "migration23-test"

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java,
    )

    // ─── MIGRATION_2_3 ────────────────────────────────────────────────────────

    @Test fun `migration creates notes table with correct schema`() {
        helper.createDatabase(TEST_DB, 2).use { /* just create v2 schema */ }

        helper.runMigrationsAndValidate(TEST_DB, 3, true, AppDatabase.MIGRATION_2_3).use { db ->
            // Insert a row to verify schema is correct
            db.execSQL("INSERT INTO notes (target, entityId, text) VALUES ('ACTIVITY', 'a1', 'test')")
            val cursor = db.query("SELECT target, entityId, text FROM notes")
            assertEquals(1, cursor.count)
            cursor.moveToFirst()
            assertEquals("ACTIVITY", cursor.getString(0))
            assertEquals("a1", cursor.getString(1))
            assertEquals("test", cursor.getString(2))
            cursor.close()
        }
    }

    @Test fun `migration skips events with empty anteckning`() {
        helper.createDatabase(TEST_DB, 2).use { db ->
            db.execSQL(
                """INSERT INTO health_events
                   (id, timestamp, datum, tid, typ, svarighetsgrad, varaktighetMinuter, triggers, atgarder, anteckning)
                   VALUES ('evt_empty', '2026-01-02T10:00:00.000Z', '2026-01-02', '10:00',
                           'Trötthet', 3, 120, '[]', '[]', '')"""
            )
        }

        helper.runMigrationsAndValidate(TEST_DB, 3, true, AppDatabase.MIGRATION_2_3).use { db ->
            val cursor = db.query("SELECT COUNT(*) FROM notes")
            cursor.moveToFirst()
            assertEquals(0, cursor.getInt(0))
            cursor.close()
        }
    }

    @Test fun `migration does not copy anteckning to notes`() {
        helper.createDatabase(TEST_DB, 2).use { db ->
            db.execSQL(
                """INSERT INTO health_events
                   (id, timestamp, datum, tid, typ, svarighetsgrad, varaktighetMinuter, triggers, atgarder, anteckning)
                   VALUES ('evt1', '2026-01-01T10:00:00.000Z', '2026-01-01', '10:00',
                           'Huvudvärk', 5, 30, '[]', '[]', 'Tog Alvedon')"""
            )
        }

        helper.runMigrationsAndValidate(TEST_DB, 3, true, AppDatabase.MIGRATION_2_3).use { db ->
            val cursor = db.query("SELECT COUNT(*) FROM notes")
            cursor.moveToFirst()
            assertEquals(0, cursor.getInt(0))
            cursor.close()
        }
    }

    @Test fun `migration preserves all health_event fields`() {
        helper.createDatabase(TEST_DB, 2).use { db ->
            db.execSQL(
                """INSERT INTO health_events
                   (id, timestamp, datum, tid, typ, svarighetsgrad, varaktighetMinuter, triggers, atgarder, anteckning)
                   VALUES ('evt1', '2026-03-15T14:30:00.000Z', '2026-03-15', '14:30',
                           'Migrän', 8, 180, '["Skärm"]', '["Vila"]', 'Svårt anfall')"""
            )
        }

        helper.runMigrationsAndValidate(TEST_DB, 3, true, AppDatabase.MIGRATION_2_3).use { db ->
            val c = db.query("SELECT * FROM health_events WHERE id = 'evt1'")
            c.moveToFirst()
            assertEquals("2026-03-15T14:30:00.000Z", c.getString(c.getColumnIndexOrThrow("timestamp")))
            assertEquals("2026-03-15", c.getString(c.getColumnIndexOrThrow("datum")))
            assertEquals("14:30", c.getString(c.getColumnIndexOrThrow("tid")))
            assertEquals("Migrän", c.getString(c.getColumnIndexOrThrow("typ")))
            assertEquals(8, c.getInt(c.getColumnIndexOrThrow("svarighetsgrad")))
            assertEquals(180, c.getInt(c.getColumnIndexOrThrow("varaktighetMinuter")))
            assertEquals("""["Skärm"]""", c.getString(c.getColumnIndexOrThrow("triggers")))
            assertEquals("""["Vila"]""", c.getString(c.getColumnIndexOrThrow("atgarder")))
            c.close()
        }
    }

    @Test fun `migration leaves notes table empty regardless of anteckning values`() {
        helper.createDatabase(TEST_DB, 2).use { db ->
            listOf(
                Triple("e1", "Tog Alvedon", true),
                Triple("e2", "", false),
                Triple("e3", "Vila och värme", true),
            ).forEach { (id, note, _) ->
                db.execSQL(
                    """INSERT INTO health_events
                       (id, timestamp, datum, tid, typ, svarighetsgrad, varaktighetMinuter, triggers, atgarder, anteckning)
                       VALUES ('$id', '2026-01-01T10:00:00.000Z', '2026-01-01', '10:00',
                               'Test', 1, 10, '[]', '[]', '$note')"""
                )
            }
        }

        helper.runMigrationsAndValidate(TEST_DB, 3, true, AppDatabase.MIGRATION_2_3).use { db ->
            val cursor = db.query("SELECT COUNT(*) FROM notes")
            cursor.moveToFirst()
            assertEquals(0, cursor.getInt(0))
            cursor.close()
        }
    }
}
