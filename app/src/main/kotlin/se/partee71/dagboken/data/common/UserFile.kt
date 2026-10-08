package se.partee71.dagboken.data.common

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.IOException
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import se.partee71.dagboken.di.IoDispatcher

/**
 * En fil användaren valt i systemets dokumentväljare (SAF) – ingen lagringsbehörighet: kopian av 3.x-datan (OMB-8),
 * exporten (BCK-13) och filen som importeras (BCK-6). Skriv, läs, namn; aldrig något loggat.
 */
interface UserFile {
    /** Skriver [text] som UTF-8 utan BOM till [uri]; ett fel kastas. */
    suspend fun write(uri: Uri, text: String)

    /** Läser hela filen på [uri] som UTF-8 (kopians verifiering, importen); ett fel kastas. */
    suspend fun read(uri: Uri): String

    /** Filens namn som användaren ser det (`DISPLAY_NAME`), `null` om det inte går att läsa. */
    suspend fun displayName(uri: Uri): String?
}

class ContentResolverUserFile @Inject constructor(
    @ApplicationContext private val context: Context,
    @IoDispatcher private val dispatcher: CoroutineDispatcher,
) : UserFile {
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
