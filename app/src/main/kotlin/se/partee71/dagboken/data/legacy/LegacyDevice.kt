package se.partee71.dagboken.data.legacy

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.work.WorkManager
import androidx.work.await
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.IOException
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import se.partee71.dagboken.BuildConfig
import se.partee71.dagboken.core.legacy.LegacyRoomSchema
import se.partee71.dagboken.di.IoDispatcher

// Små enhetsberoenden runt migreringen (OMB-2), var och en bakom ett gränssnitt så att use caset testas mot fejkar.

/** Det 3.x lämnade kvar i WorkManager. */
interface LegacyWork {
    /** Avbokar 3.x:s periodiska Drive-backup (`dagboken_daily_backup`) – dess worker finns inte i 4.0 – och väntar in att det gick; ett fel kastas. */
    suspend fun cancelBackupJob()
}

class WorkManagerLegacyWork @Inject constructor(@ApplicationContext private val context: Context) : LegacyWork {
    override suspend fun cancelBackupJob() {
        WorkManager.getInstance(context).cancelUniqueWork(LegacyRoomSchema.BACKUP_WORK_NAME).await()
    }
}

/** Kopian av 3.x-datan i en fil användaren valt (SAF), före första skrivningen (OMB-8): skriv, läs tillbaka, namn. */
interface LegacyCopyFile {
    /** Skriver [text] som UTF-8 utan BOM till [uri]; ett fel kastas. */
    suspend fun write(uri: Uri, text: String)

    /** Läser tillbaka hela filen på [uri] som UTF-8 – verifieringen parsar det som 3.x gör; ett fel kastas. */
    suspend fun read(uri: Uri): String

    /** Filens namn som användaren ser det (`DISPLAY_NAME`), `null` om det inte går att läsa. */
    suspend fun displayName(uri: Uri): String?
}

class ContentResolverCopyFile @Inject constructor(
    @ApplicationContext private val context: Context,
    @IoDispatcher private val dispatcher: CoroutineDispatcher,
) : LegacyCopyFile {
    override suspend fun write(uri: Uri, text: String) = withContext(dispatcher) {
        // "wt": skriv över helt – en kortare kopia får inte lämna slutet av en äldre fil kvar.
        val stream = context.contentResolver.openOutputStream(uri, "wt") ?: throw IOException("filen gick inte att öppna")
        stream.writer(Charsets.UTF_8).use { it.write(text) }
    }

    override suspend fun read(uri: Uri): String = withContext(dispatcher) {
        val stream = context.contentResolver.openInputStream(uri) ?: throw IOException("filen gick inte att öppna")
        stream.reader(Charsets.UTF_8).use { it.readText() }
    }

    override suspend fun displayName(uri: Uri): String? = withContext(dispatcher) {
        runCatching {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(cursor.getColumnIndexOrThrow(OpenableColumns.DISPLAY_NAME)) else null
            }
        }.getOrNull()
    }
}

/** Appens `versionName` – skrivs i migreringsmarkören. */
class AppVersion @Inject constructor() {
    val name: String get() = BuildConfig.VERSION_NAME
}
