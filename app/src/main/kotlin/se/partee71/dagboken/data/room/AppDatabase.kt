package se.partee71.dagboken.data.room

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import se.partee71.dagboken.data.room.daos.AktivitetDao
import se.partee71.dagboken.data.room.daos.FavoritDao
import se.partee71.dagboken.data.room.daos.HandelseDao
import se.partee71.dagboken.data.room.daos.MedicinDao
import se.partee71.dagboken.data.room.daos.NoteDao
import se.partee71.dagboken.data.room.daos.ReceptDao
import se.partee71.dagboken.data.room.daos.SjukdomsEpisodDao
import se.partee71.dagboken.data.room.daos.SjukdomsIncheckningDao
import se.partee71.dagboken.data.room.entities.AktivitetEntity
import se.partee71.dagboken.data.room.entities.FavoritEntity
import se.partee71.dagboken.data.room.entities.HandelseEntity
import se.partee71.dagboken.data.room.entities.MedicinEntity
import se.partee71.dagboken.data.room.entities.NoteEntity
import se.partee71.dagboken.data.room.entities.ReceptEntity
import se.partee71.dagboken.data.room.entities.SjukdomsEpisodEntity
import se.partee71.dagboken.data.room.entities.SjukdomsIncheckningEntity

@Database(
    entities = [
        AktivitetEntity::class,
        MedicinEntity::class,
        ReceptEntity::class,
        FavoritEntity::class,
        HandelseEntity::class,
        NoteEntity::class,
        SjukdomsEpisodEntity::class,
        SjukdomsIncheckningEntity::class,
    ],
    version = 7,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun aktivitetDao(): AktivitetDao
    abstract fun medicinDao(): MedicinDao
    abstract fun receptDao(): ReceptDao
    abstract fun favoritDao(): FavoritDao
    abstract fun handelseDao(): HandelseDao
    abstract fun noteDao(): NoteDao
    abstract fun sjukdomsEpisodDao(): SjukdomsEpisodDao
    abstract fun sjukdomsIncheckningDao(): SjukdomsIncheckningDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """CREATE TABLE IF NOT EXISTS health_events (
                        id TEXT NOT NULL PRIMARY KEY,
                        timestamp TEXT NOT NULL,
                        datum TEXT NOT NULL,
                        tid TEXT NOT NULL,
                        typ TEXT NOT NULL,
                        svarighetsgrad INTEGER NOT NULL,
                        varaktighetMinuter INTEGER NOT NULL,
                        triggers TEXT NOT NULL,
                        atgarder TEXT NOT NULL,
                        anteckning TEXT NOT NULL
                    )"""
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_health_events_datum ON health_events (datum)"
                )
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """CREATE TABLE IF NOT EXISTS notes (
                        target TEXT NOT NULL,
                        entityId TEXT NOT NULL,
                        text TEXT NOT NULL,
                        PRIMARY KEY(target, entityId)
                    )"""
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_notes_entityId ON notes (entityId)"
                )
            }
        }

        // Repairs databases that ended up at version 3 with the old notes schema
        // (id INTEGER PK AUTOINCREMENT, target_type, target_id, text, updated_at)
        // from intermediate dev builds between commits 309a2f4 and 9283f7c.
        // Safe no-op for databases that already have the correct schema.
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                val cursor = db.query("PRAGMA table_info(notes)")
                val hasOldSchema = cursor.use { c ->
                    generateSequence { if (c.moveToNext()) c else null }
                        .any { c.getString(c.getColumnIndexOrThrow("name")) == "id" }
                }
                if (hasOldSchema) {
                    db.execSQL("DROP TABLE IF EXISTS notes")
                    db.execSQL(
                        """CREATE TABLE IF NOT EXISTS notes (
                            target TEXT NOT NULL,
                            entityId TEXT NOT NULL,
                            text TEXT NOT NULL,
                            PRIMARY KEY(target, entityId)
                        )"""
                    )
                    db.execSQL(
                        "CREATE INDEX IF NOT EXISTS index_notes_entityId ON notes (entityId)"
                    )
                }
            }
        }

        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""CREATE TABLE IF NOT EXISTS sjukdomsepisoder (
                    id TEXT NOT NULL PRIMARY KEY,
                    typ TEXT NOT NULL,
                    start_datum TEXT NOT NULL,
                    slut_datum TEXT NOT NULL DEFAULT '',
                    anteckning TEXT NOT NULL DEFAULT '',
                    timestamp INTEGER NOT NULL DEFAULT 0
                )""")
                db.execSQL("""CREATE TABLE IF NOT EXISTS sjukdoms_incheckningar (
                    id TEXT NOT NULL PRIMARY KEY,
                    episod_id TEXT NOT NULL,
                    datum TEXT NOT NULL,
                    tid TEXT NOT NULL,
                    svarighetsgrad INTEGER NOT NULL,
                    symptom TEXT NOT NULL DEFAULT '',
                    somatiska INTEGER NOT NULL DEFAULT 0,
                    anteckning TEXT NOT NULL DEFAULT '',
                    timestamp INTEGER NOT NULL DEFAULT 0,
                    FOREIGN KEY(episod_id) REFERENCES sjukdomsepisoder(id) ON DELETE CASCADE
                )""")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_sjukdoms_incheckningar_episod_id ON sjukdoms_incheckningar (episod_id)")
            }
        }

        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE favoriter ADD COLUMN isFavorite INTEGER NOT NULL DEFAULT 0")
            }
        }

        // Drops the per-row `anteckning` column from mediciner/recept/favoriter — anteckning
        // now lives in the generic `notes` table (target=MEDICATION/RECEPT/FAVORIT, entityId=row id).
        // SQLite can't DROP COLUMN on these versions, so each table is recreated without it.
        // `INSERT OR IGNORE` into notes preserves any note already saved under MEDICATION via
        // the IdagTab dose-note dialog (NoteTarget.MEDICATION was already in use for that,
        // keyed by the same mediciner.id) instead of overwriting it with the column's value.
        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """INSERT OR IGNORE INTO notes (target, entityId, text)
                       SELECT 'MEDICATION', id, anteckning FROM mediciner
                       WHERE anteckning IS NOT NULL AND TRIM(anteckning) != ''"""
                )
                db.execSQL(
                    """INSERT OR IGNORE INTO notes (target, entityId, text)
                       SELECT 'RECEPT', id, anteckning FROM recept
                       WHERE anteckning IS NOT NULL AND TRIM(anteckning) != ''"""
                )
                db.execSQL(
                    """INSERT OR IGNORE INTO notes (target, entityId, text)
                       SELECT 'FAVORIT', id, anteckning FROM favoriter
                       WHERE anteckning IS NOT NULL AND TRIM(anteckning) != ''"""
                )

                db.execSQL(
                    """CREATE TABLE mediciner_new (
                        id TEXT NOT NULL PRIMARY KEY,
                        timestamp TEXT NOT NULL,
                        datum TEXT NOT NULL,
                        tid TEXT NOT NULL,
                        namn TEXT NOT NULL,
                        dos TEXT NOT NULL,
                        enhet TEXT NOT NULL,
                        tidpunkt TEXT NOT NULL,
                        tagen INTEGER NOT NULL,
                        receptId TEXT,
                        skipped INTEGER NOT NULL DEFAULT 0
                    )"""
                )
                db.execSQL(
                    """INSERT INTO mediciner_new (id, timestamp, datum, tid, namn, dos, enhet, tidpunkt, tagen, receptId, skipped)
                       SELECT id, timestamp, datum, tid, namn, dos, enhet, tidpunkt, tagen, receptId, skipped FROM mediciner"""
                )
                db.execSQL("DROP TABLE mediciner")
                db.execSQL("ALTER TABLE mediciner_new RENAME TO mediciner")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_mediciner_datum ON mediciner (datum)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_mediciner_namn_datum ON mediciner (namn, datum)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_mediciner_receptId ON mediciner (receptId)")

                db.execSQL(
                    """CREATE TABLE recept_new (
                        id TEXT NOT NULL PRIMARY KEY,
                        namn TEXT NOT NULL,
                        dos TEXT NOT NULL,
                        enhet TEXT NOT NULL,
                        tidpunkterJson TEXT NOT NULL,
                        upprepning TEXT NOT NULL,
                        dagarJson TEXT NOT NULL,
                        intervalDagar INTEGER NOT NULL DEFAULT 2,
                        aktiv INTEGER NOT NULL,
                        skapad TEXT NOT NULL
                    )"""
                )
                db.execSQL(
                    """INSERT INTO recept_new (id, namn, dos, enhet, tidpunkterJson, upprepning, dagarJson, intervalDagar, aktiv, skapad)
                       SELECT id, namn, dos, enhet, tidpunkterJson, upprepning, dagarJson, intervalDagar, aktiv, skapad FROM recept"""
                )
                db.execSQL("DROP TABLE recept")
                db.execSQL("ALTER TABLE recept_new RENAME TO recept")

                db.execSQL(
                    """CREATE TABLE favoriter_new (
                        id TEXT NOT NULL PRIMARY KEY,
                        namn TEXT NOT NULL,
                        dos TEXT NOT NULL,
                        enhet TEXT NOT NULL,
                        tidpunkt TEXT NOT NULL,
                        minTidMellan INTEGER NOT NULL,
                        dispenseringsTid TEXT NOT NULL DEFAULT '',
                        maxDoserPerDag INTEGER NOT NULL DEFAULT 0,
                        isFavorite INTEGER NOT NULL DEFAULT 0
                    )"""
                )
                db.execSQL(
                    """INSERT INTO favoriter_new (id, namn, dos, enhet, tidpunkt, minTidMellan, dispenseringsTid, maxDoserPerDag, isFavorite)
                       SELECT id, namn, dos, enhet, tidpunkt, minTidMellan, dispenseringsTid, maxDoserPerDag, isFavorite FROM favoriter"""
                )
                db.execSQL("DROP TABLE favoriter")
                db.execSQL("ALTER TABLE favoriter_new RENAME TO favoriter")
            }
        }

        val MIGRATIONS = arrayOf(
            MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7,
        )
    }
}
