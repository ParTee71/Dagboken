package se.partee71.dagboken.data.legacy

import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.security.MessageDigest
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import se.partee71.dagboken.core.legacy.LegacyRoomSchema
import se.partee71.dagboken.core.legacy.Row
import se.partee71.dagboken.di.IoDispatcher

/** Vad Room-filen gav. */
sealed interface LegacyRoomRead {
    /**
     * Alla rader per tabell ([LegacyRoomSchema.TABLES]), när filen senast skrevs (epok-ms, filen eller dess WAL) och
     * [fingerprint] – kontrollsumman över `.db` och `-wal` som kopian av 3.x-datan binds till (OMB-8).
     */
    data class Tables(val tables: Map<String, List<Row>>, val lastModifiedMillis: Long, val fingerprint: String) : LegacyRoomRead

    /** `PRAGMA user_version` är inte 11 – en äldre eller okänd 3.x; inget läses. */
    data class WrongVersion(val found: Int) : LegacyRoomRead

    /** Ingen Room-fil på enheten. */
    data object Missing : LegacyRoomRead
}

/** 3.x:s Room-fil på enheten, bara läsning (OMB-2). Ett läsfel kastas (filen trasig eller låst). */
interface LegacyRoomSource {
    /** Om filen finns – startkontrollen (NAV-6). */
    fun exists(): Boolean

    suspend fun read(): LegacyRoomRead

    /** Kontrollsumman över `.db` och `-wal` just nu (`null` utan fil) – ändras den efter kopian krävs en ny kopia (OMB-8). */
    suspend fun fingerprint(): String?
}

/**
 * Läser `dagboken.db` med `SQLiteDatabase.OPEN_READONLY` – inget Room-beroende, ingen ändring av filen:
 * en skrivskyddad anslutning byter aldrig journalläge, skapar inga tabeller och checkpointar inte WAL-filen
 * när den stängs, men läser de rader som ligger i WAL (3.x-processen dog utan checkpoint). Raderna lämnas
 * råa (TEXT som `String`, INTEGER som `Long`, NULL som `null`) till `LegacyRoomAssembler` i `:core`.
 */
class LegacyRoomReader @Inject constructor(
    @ApplicationContext private val context: Context,
    @IoDispatcher private val dispatcher: CoroutineDispatcher,
) : LegacyRoomSource {
    private val file: File get() = context.getDatabasePath(LegacyRoomSchema.DATABASE_NAME)

    override fun exists(): Boolean = file.exists()

    override suspend fun read(): LegacyRoomRead = withContext(dispatcher) {
        val db = file
        if (!db.exists()) return@withContext LegacyRoomRead.Missing
        SQLiteDatabase.openDatabase(db.path, null, SQLiteDatabase.OPEN_READONLY or SQLiteDatabase.NO_LOCALIZED_COLLATORS).use { sqlite ->
            val version = sqlite.version // PRAGMA user_version
            if (version != LegacyRoomSchema.DATABASE_VERSION) return@withContext LegacyRoomRead.WrongVersion(version)
            val tables = LegacyRoomSchema.TABLES.associateWith { table ->
                // Tabellnamnen är konstanter i :core, aldrig användarens.
                sqlite.rawQuery("SELECT * FROM `$table`", null).use(::rows)
            }
            LegacyRoomRead.Tables(tables, maxOf(db.lastModified(), File(db.path + WAL_SUFFIX).lastModified()), fingerprintOf(db))
        }
    }

    override suspend fun fingerprint(): String? = withContext(dispatcher) { file.takeIf { it.exists() }?.let(::fingerprintOf) }

    /** SHA-256 över `.db` följt av `-wal` (om den finns) – inte `-shm`, som är ett flyktigt index läsaren själv får bygga om. */
    private fun fingerprintOf(db: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(FINGERPRINT_BUFFER)
        for (part in listOf(db, File(db.path + WAL_SUFFIX)).filter { it.exists() }) {
            part.inputStream().use { stream ->
                while (true) {
                    val read = stream.read(buffer)
                    if (read < 0) break
                    digest.update(buffer, 0, read)
                }
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun rows(cursor: Cursor): List<Row> = buildList {
        while (cursor.moveToNext()) add((0 until cursor.columnCount).associate { cursor.getColumnName(it) to value(cursor, it) })
    }

    private fun value(cursor: Cursor, index: Int): Any? = when (cursor.getType(index)) {
        Cursor.FIELD_TYPE_NULL -> null
        Cursor.FIELD_TYPE_INTEGER -> cursor.getLong(index)
        Cursor.FIELD_TYPE_FLOAT -> cursor.getDouble(index)
        Cursor.FIELD_TYPE_BLOB -> cursor.getBlob(index)
        else -> cursor.getString(index)
    }

    companion object {
        /** SQLites write-ahead-logg bredvid databasfilen. */
        const val WAL_SUFFIX = "-wal"

        /** Strömmas i bitar – en databas på tiotals MB läses aldrig in i minnet. */
        private const val FINGERPRINT_BUFFER = 64 * 1024
    }
}
