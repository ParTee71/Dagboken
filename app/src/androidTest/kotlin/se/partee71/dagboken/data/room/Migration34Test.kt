package se.partee71.dagboken.data.room

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class Migration34Test {

    private val TEST_DB = "migration34-test"

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java,
    )

    @Test fun `migration repairs broken notes schema from intermediate dev build`() {
        helper.createDatabase(TEST_DB, 3).use { db ->
            // Simulate the broken notes table from the old design (commit 309a2f4)
            db.execSQL("DROP TABLE IF EXISTS notes")
            db.execSQL(
                """CREATE TABLE IF NOT EXISTS notes (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    target_type TEXT NOT NULL,
                    target_id TEXT NOT NULL,
                    text TEXT NOT NULL,
                    updated_at INTEGER NOT NULL
                )"""
            )
            db.execSQL(
                "CREATE UNIQUE INDEX IF NOT EXISTS index_notes_target_type_target_id ON notes (target_type, target_id)"
            )
        }

        helper.runMigrationsAndValidate(TEST_DB, 4, true, AppDatabase.MIGRATION_3_4).use { db ->
            // Verify the table now has the correct schema
            db.execSQL("INSERT INTO notes (target, entityId, text) VALUES ('ACTIVITY', 'a1', 'repaired')")
            val cursor = db.query("SELECT target, entityId, text FROM notes")
            cursor.moveToFirst()
            assertEquals("ACTIVITY", cursor.getString(0))
            assertEquals("a1", cursor.getString(1))
            assertEquals("repaired", cursor.getString(2))
            cursor.close()
        }
    }

    @Test fun `migration is no-op when notes schema is already correct`() {
        helper.createDatabase(TEST_DB, 3).use { db ->
            db.execSQL("INSERT INTO notes (target, entityId, text) VALUES ('MEDICATION', 'm1', 'preserved')")
        }

        helper.runMigrationsAndValidate(TEST_DB, 4, true, AppDatabase.MIGRATION_3_4).use { db ->
            val cursor = db.query("SELECT target, entityId, text FROM notes WHERE entityId = 'm1'")
            assertEquals(1, cursor.count)
            cursor.moveToFirst()
            assertEquals("preserved", cursor.getString(2))
            cursor.close()
        }
    }

    @Test fun `full migration chain 2 to 4 produces correct schema`() {
        helper.createDatabase(TEST_DB, 2).use { /* create v2 schema */ }

        helper.runMigrationsAndValidate(
            TEST_DB, 4, true,
            AppDatabase.MIGRATION_2_3,
            AppDatabase.MIGRATION_3_4,
        ).use { db ->
            db.execSQL("INSERT INTO notes (target, entityId, text) VALUES ('SCREENING', 's1', 'ok')")
            val cursor = db.query("SELECT COUNT(*) FROM notes")
            cursor.moveToFirst()
            assertEquals(1, cursor.getInt(0))
            cursor.close()
        }
    }
}
