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
import se.partee71.dagboken.data.room.entities.AktivitetEntity
import se.partee71.dagboken.data.room.entities.FavoritEntity
import se.partee71.dagboken.data.room.entities.HandelseEntity
import se.partee71.dagboken.data.room.entities.MedicinEntity
import se.partee71.dagboken.data.room.entities.NoteEntity
import se.partee71.dagboken.data.room.entities.ReceptEntity

@Database(
    entities = [
        AktivitetEntity::class,
        MedicinEntity::class,
        ReceptEntity::class,
        FavoritEntity::class,
        HandelseEntity::class,
        NoteEntity::class,
    ],
    version = 4,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun aktivitetDao(): AktivitetDao
    abstract fun medicinDao(): MedicinDao
    abstract fun receptDao(): ReceptDao
    abstract fun favoritDao(): FavoritDao
    abstract fun handelseDao(): HandelseDao
    abstract fun noteDao(): NoteDao

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

        val MIGRATIONS = arrayOf(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
    }
}
