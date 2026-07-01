package se.partee71.dagboken.data.room

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class Migration56Test {

    private val TEST_DB = "migration56-test"

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java,
    )

    @Test fun `migration adds isFavorite column defaulting to false and preserves existing rows`() {
        helper.createDatabase(TEST_DB, 5).use { db ->
            db.execSQL(
                """INSERT INTO favoriter (id, namn, dos, enhet, tidpunkt, anteckning, minTidMellan, dispenseringsTid, maxDoserPerDag)
                   VALUES ('fav1', 'Paracetamol', '500', 'mg', 'Vid behov', '', 4, '', 0)"""
            )
        }

        helper.runMigrationsAndValidate(TEST_DB, 6, true, AppDatabase.MIGRATION_5_6).use { db ->
            val cursor = db.query("SELECT namn, dos, isFavorite FROM favoriter WHERE id = 'fav1'")
            cursor.moveToFirst()
            assertEquals("Paracetamol", cursor.getString(0))
            assertEquals("500", cursor.getString(1))
            assertEquals(0, cursor.getInt(2))
            cursor.close()
        }
    }

    @Test fun `migrated isFavorite column can be updated after migration`() {
        helper.createDatabase(TEST_DB, 5).use { db ->
            db.execSQL(
                """INSERT INTO favoriter (id, namn, dos, enhet, tidpunkt, anteckning, minTidMellan, dispenseringsTid, maxDoserPerDag)
                   VALUES ('fav1', 'Ibuprofen', '400', 'mg', 'Vid behov', '', 0, '', 0)"""
            )
        }

        helper.runMigrationsAndValidate(TEST_DB, 6, true, AppDatabase.MIGRATION_5_6).use { db ->
            db.execSQL("UPDATE favoriter SET isFavorite = 1 WHERE id = 'fav1'")
            val cursor = db.query("SELECT isFavorite FROM favoriter WHERE id = 'fav1'")
            cursor.moveToFirst()
            assertEquals(1, cursor.getInt(0))
            cursor.close()
        }
    }
}
