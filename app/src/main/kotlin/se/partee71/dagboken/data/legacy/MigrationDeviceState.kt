package se.partee71.dagboken.data.legacy

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import javax.inject.Inject
import kotlin.time.Instant
import kotlinx.coroutines.flow.first

/** Den sparade och verifierade kopian av 3.x-datan (OMB-8): vilken Room-fil den gäller, när, vart och hur mycket. */
data class CopyRecord(
    /** Room-filens kontrollsumma (`LegacyRoomRead.Tables.fingerprint`) när kopian togs. */
    val fingerprint: String,
    val savedAt: Instant,
    /** Filnamnet användaren valde (SAF `DISPLAY_NAME`), `null` när det inte gick att läsa. */
    val fileName: String?,
    /** Antal 3.x-poster (Room-rader) i kopian. */
    val total: Int,
    /** Kontrollens version ([VERSION]); en kopia sparad med en annan (äldre) kontroll gäller inte och måste sparas om. */
    val version: Int = VERSION,
) {
    companion object {
        /** 1 = `total` räknade 4.0-dokument (före kontrollen som 3.x-fil); 2 = `total` räknar 3.x-raderna. */
        const val VERSION = 2
    }
}

/** 3.x-sessionen som den såg ut vid första starten av 4.0: [uid] = inloggad i Firebase Auth då, `null` = ingen. */
data class LegacySession(val uid: String?)

/**
 * Enhetslokalt migreringsläge (TP-4) i 4.0:s egen DataStore-fil: flaggan per konto (uid) att 3.x-datan migrerats
 * dit, 3.x-sessionen (Firebase Auths konto vid första starten, före inloggningsgrinden), den verifierade kopian –
 * bunden till Room-filen, inte till kontot. Vad migreringen skrivit ligger i [MigrationLedger] (egen fil per konto).
 */
interface MigrationDeviceState {
    suspend fun isDone(uid: String): Boolean

    suspend fun markDone(uid: String)

    /** 3.x-sessionen; `null` = inte infångad (då kräver bekräftelsen att e-posten bekräftas uttryckligen). Sätts en gång. */
    suspend fun session(): LegacySession?

    suspend fun rememberSession(session: LegacySession)

    /** Senast verifierade kopian, `null` om ingen tagits (eller den rensats inför ett nytt försök). */
    suspend fun copy(): CopyRecord?

    suspend fun rememberCopy(record: CopyRecord)

    /** Glömmer kopian – före varje nytt försök, så att ett misslyckat försök aldrig lämnar ett gammalt "verifierad" kvar. */
    suspend fun clearCopy()

    /** Per installation: 3.x:s backupjobb i WorkManager är avbokat. Sätts först när avbokningen lyckats; tills dess görs ett nytt försök vid varje start. */
    suspend fun backupJobCancelled(): Boolean

    suspend fun markBackupJobCancelled()
}

class DataStoreMigrationState @Inject constructor(private val store: DataStore<Preferences>) : MigrationDeviceState {
    override suspend fun isDone(uid: String): Boolean = store.data.first()[doneKey(uid)] ?: false

    override suspend fun markDone(uid: String) {
        store.edit { it[doneKey(uid)] = true }
    }

    override suspend fun session(): LegacySession? {
        val prefs = store.data.first()
        if (!prefs.contains(SESSION_CAPTURED)) return null
        return LegacySession(prefs[SESSION_UID])
    }

    override suspend fun rememberSession(session: LegacySession) {
        store.edit {
            if (it.contains(SESSION_CAPTURED)) return@edit
            it[SESSION_CAPTURED] = true
            if (session.uid != null) it[SESSION_UID] = session.uid
        }
    }

    override suspend fun copy(): CopyRecord? {
        val prefs = store.data.first()
        return CopyRecord(
            fingerprint = prefs[COPY_FINGERPRINT] ?: return null,
            savedAt = Instant.fromEpochMilliseconds(prefs[COPY_SAVED_AT] ?: return null),
            fileName = prefs[COPY_FILE],
            total = prefs[COPY_TOTAL] ?: 0,
            // Saknad nyckel = sparad före versionsfältet.
            version = prefs[COPY_VERSION] ?: 1,
        )
    }

    override suspend fun rememberCopy(record: CopyRecord) {
        store.edit {
            it[COPY_FINGERPRINT] = record.fingerprint
            it[COPY_SAVED_AT] = record.savedAt.toEpochMilliseconds()
            if (record.fileName != null) it[COPY_FILE] = record.fileName else it.remove(COPY_FILE)
            it[COPY_TOTAL] = record.total
            it[COPY_VERSION] = record.version
        }
    }

    override suspend fun clearCopy() {
        store.edit { prefs -> listOf(COPY_FINGERPRINT, COPY_SAVED_AT, COPY_FILE, COPY_TOTAL, COPY_VERSION).forEach { prefs.remove(it) } }
    }

    override suspend fun backupJobCancelled(): Boolean = store.data.first()[BACKUP_JOB_CANCELLED] ?: false

    override suspend fun markBackupJobCancelled() {
        store.edit { it[BACKUP_JOB_CANCELLED] = true }
    }

    private fun doneKey(uid: String) = booleanPreferencesKey("$DONE_PREFIX$uid")

    private companion object {
        const val DONE_PREFIX = "legacy_migration_done_"
        val BACKUP_JOB_CANCELLED = booleanPreferencesKey("legacy_backup_job_cancelled")
        val SESSION_CAPTURED = booleanPreferencesKey("legacy_session_captured")
        val SESSION_UID = stringPreferencesKey("legacy_session_uid")
        val COPY_FINGERPRINT = stringPreferencesKey("legacy_copy_fingerprint")
        val COPY_SAVED_AT = longPreferencesKey("legacy_copy_saved_at")
        val COPY_FILE = stringPreferencesKey("legacy_copy_file")
        val COPY_TOTAL = intPreferencesKey("legacy_copy_total")
        val COPY_VERSION = intPreferencesKey("legacy_copy_version")
    }
}
