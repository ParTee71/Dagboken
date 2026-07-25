package se.partee71.dagboken.data.room

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class Migration910Test {

    private val TEST_DB = "migration910-test"

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java,
    )

    @Test fun `migration adds a nullable tagenTid column and preserves existing rows`() {
        helper.createDatabase(TEST_DB, 9).use { db ->
            db.execSQL(
                """INSERT INTO mediciner (id, timestamp, datum, tid, namn, dos, enhet, tidpunkt, tagen, receptId, skipped)
                   VALUES ('m1', '2026-01-15T09:00:00.000Z', '2026-01-15', '09:00', 'Ibuprofen', '400', 'mg', 'Morgon', 1, NULL, 0)"""
            )
        }

        helper.runMigrationsAndValidate(TEST_DB, 10, true, AppDatabase.MIGRATION_9_10).use { db ->
            val cursor = db.query("SELECT namn, tagen, tagenTid FROM mediciner WHERE id = 'm1'")
            cursor.moveToFirst()
            assertEquals("Ibuprofen", cursor.getString(0))
            assertEquals(1, cursor.getInt(1))
            assertNull("Existing rows have no tagningstid yet", cursor.getString(2))
            cursor.close()

            val cols = db.query("PRAGMA table_info(mediciner)")
            val hasTagenTid = cols.use { c ->
                generateSequence { if (c.moveToNext()) c else null }
                    .any { c.getString(c.getColumnIndexOrThrow("name")) == "tagenTid" }
            }
            assertTrue(hasTagenTid)
        }
    }
}
