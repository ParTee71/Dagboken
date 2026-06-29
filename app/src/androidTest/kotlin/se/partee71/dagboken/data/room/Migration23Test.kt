package se.partee71.dagboken.data.room

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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

    @Test fun `migration copies non-empty anteckning into notes table`() {
        helper.createDatabase(TEST_DB, 2).use { db ->
            db.execSQL(
                """INSERT INTO health_events
                   (id, timestamp, datum, tid, typ, svarighetsgrad, varaktighetMinuter, triggers, atgarder, anteckning)
                   VALUES ('evt1', '2026-01-01T10:00:00.000Z', '2026-01-01', '10:00',
                           'Huvudvärk', 5, 30, '[]', '[]', 'Tog Alvedon')"""
            )
        }

        helper.runMigrationsAndValidate(TEST_DB, 3, true, AppDatabase.MIGRATION_2_3).use { db ->
            val cursor = db.query(
                "SELECT target_type, target_id, text, updated_at FROM notes WHERE target_type = 'EVENT'"
            )
            assertEquals(1, cursor.count)
            cursor.moveToFirst()
            assertEquals("EVENT", cursor.getString(0))
            assertEquals("evt1", cursor.getString(1))
            assertEquals("Tog Alvedon", cursor.getString(2))
            assertTrue("updated_at should be > 0", cursor.getLong(3) > 0L)
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

    @Test fun `migration drops anteckning column from health_events`() {
        helper.createDatabase(TEST_DB, 2).use { db ->
            db.execSQL(
                """INSERT INTO health_events
                   (id, timestamp, datum, tid, typ, svarighetsgrad, varaktighetMinuter, triggers, atgarder, anteckning)
                   VALUES ('evt1', '2026-01-01T10:00:00.000Z', '2026-01-01', '10:00',
                           'Huvudvärk', 5, 30, '[]', '[]', 'Tog Alvedon')"""
            )
        }

        helper.runMigrationsAndValidate(TEST_DB, 3, true, AppDatabase.MIGRATION_2_3).use { db ->
            val cursor = db.query("SELECT * FROM health_events WHERE id = 'evt1'")
            cursor.moveToFirst()
            assertEquals(-1, cursor.getColumnIndex("anteckning"))
            assertEquals("evt1", cursor.getString(cursor.getColumnIndexOrThrow("id")))
            assertEquals("2026-01-01", cursor.getString(cursor.getColumnIndexOrThrow("datum")))
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

    @Test fun `migration handles multiple events — only non-empty anteckning migrated`() {
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
            val count = db.query("SELECT COUNT(*) FROM notes").also { it.moveToFirst() }
            assertEquals(2, count.getInt(0))
            count.close()

            val ids = mutableSetOf<String>()
            val c = db.query("SELECT target_id FROM notes")
            while (c.moveToNext()) ids += c.getString(0)
            c.close()
            assertEquals(setOf("e1", "e3"), ids)
        }
    }
}
