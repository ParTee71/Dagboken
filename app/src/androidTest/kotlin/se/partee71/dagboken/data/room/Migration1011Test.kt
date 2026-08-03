package se.partee71.dagboken.data.room

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Periodstödet (REC-7…REC-9) lägger till startDatum/slutDatum/dosperioderJson på recept.
 * Befintliga recept ska behålla alla fält och få tomma periodvärden — de saknar alltså
 * periodgräns och faller tillbaka på `skapad` för intervallberäkningen (REC-4), precis
 * som före migreringen.
 */
@RunWith(AndroidJUnit4::class)
class Migration1011Test {

    private val TEST_DB = "migration1011-test"

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java,
    )

    @Test fun `migration adds empty period columns and preserves the recept`() {
        helper.createDatabase(TEST_DB, 10).use { db ->
            db.execSQL(
                """INSERT INTO recept (id, namn, dos, enhet, tidpunkterJson, upprepning, dagarJson, intervalDagar, aktiv, skapad)
                   VALUES ('r1', 'Metformin', '500', 'mg', '["Morgon"]', 'intervall', '[]', 2, 1, '2026-01-05')"""
            )
        }

        helper.runMigrationsAndValidate(TEST_DB, 11, true, AppDatabase.MIGRATION_10_11).use { db ->
            db.query(
                "SELECT namn, dos, upprepning, intervalDagar, skapad, startDatum, slutDatum, dosperioderJson FROM recept WHERE id = 'r1'"
            ).use { c ->
                c.moveToFirst()
                assertEquals("Metformin", c.getString(0))
                assertEquals("500", c.getString(1))
                assertEquals("intervall", c.getString(2))
                assertEquals(2, c.getInt(3))
                assertEquals("2026-01-05", c.getString(4))
                assertEquals("", c.getString(5))
                assertNull(c.getString(6))
                assertEquals("[]", c.getString(7))
            }
        }
    }

    @Test fun `migration keeps every existing recept row`() {
        helper.createDatabase(TEST_DB, 10).use { db ->
            db.execSQL(
                """INSERT INTO recept (id, namn, dos, enhet, tidpunkterJson, upprepning, dagarJson, intervalDagar, aktiv, skapad)
                   VALUES ('r1', 'Metformin', '500', 'mg', '["Morgon"]', 'dagligen', '[]', 2, 1, '2026-01-05')"""
            )
            db.execSQL(
                """INSERT INTO recept (id, namn, dos, enhet, tidpunkterJson, upprepning, dagarJson, intervalDagar, aktiv, skapad)
                   VALUES ('r2', 'Vitamin D', '1', 'st', '["Kväll"]', 'anpassad', '[0,2,4]', 2, 0, '2026-02-01')"""
            )
        }

        helper.runMigrationsAndValidate(TEST_DB, 11, true, AppDatabase.MIGRATION_10_11).use { db ->
            db.query("SELECT COUNT(*) FROM recept").use { c ->
                c.moveToFirst()
                assertEquals(2, c.getInt(0))
            }
            db.query("SELECT skapad, startDatum, dagarJson FROM recept WHERE id = 'r2'").use { c ->
                c.moveToFirst()
                assertEquals("2026-02-01", c.getString(0))
                assertEquals("", c.getString(1))
                assertEquals("[0,2,4]", c.getString(2))
            }
        }
    }
}
