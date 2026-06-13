package se.partee71.dagboken.data.migration

import android.content.Context
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.drive.Drive
import com.google.api.services.drive.DriveScopes
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

data class DriveBackupFile(val id: String, val name: String, val createdTime: String)

sealed class DriveResult<out T> {
    data class Success<T>(val value: T) : DriveResult<T>()
    data class Error(val message: String) : DriveResult<Nothing>()
    object NoAccount : DriveResult<Nothing>()
    object NoBackupFound : DriveResult<Nothing>()
}

@Singleton
class DriveBackupRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val json = Json { ignoreUnknownKeys = true }
    private val BACKUP_PREFIX = "dagboken-backup-"
    private val APP_NAME = "Dagboken"

    private fun buildDriveService(account: GoogleSignInAccount): Drive {
        val credential = GoogleAccountCredential
            .usingOAuth2(context, listOf(DriveScopes.DRIVE_APPDATA))
            .apply { selectedAccount = account.account }
        return Drive.Builder(NetHttpTransport(), GsonFactory.getDefaultInstance(), credential)
            .setApplicationName(APP_NAME)
            .build()
    }

    suspend fun listBackups(): DriveResult<List<DriveBackupFile>> = withContext(Dispatchers.IO) {
        val account = GoogleSignIn.getLastSignedInAccount(context)
            ?: return@withContext DriveResult.NoAccount
        runCatching {
            val drive = buildDriveService(account)
            val files = drive.files().list()
                .setSpaces("appDataFolder")
                .setQ("name contains '$BACKUP_PREFIX'")
                .setOrderBy("createdTime desc")
                .setFields("files(id,name,createdTime)")
                .execute()
                .files ?: emptyList()
            DriveResult.Success(files.map {
                DriveBackupFile(it.id, it.name, it.createdTime?.toString() ?: "")
            })
        }.getOrElse { DriveResult.Error(it.message ?: "Unknown error") }
    }

    suspend fun downloadLatestBackup(): DriveResult<BackupJson> = withContext(Dispatchers.IO) {
        val account = GoogleSignIn.getLastSignedInAccount(context)
            ?: return@withContext DriveResult.NoAccount

        runCatching {
            val drive = buildDriveService(account)
            val files = drive.files().list()
                .setSpaces("appDataFolder")
                .setQ("name contains '$BACKUP_PREFIX'")
                .setOrderBy("createdTime desc")
                .setPageSize(1)
                .setFields("files(id,name)")
                .execute()
                .files ?: emptyList()

            if (files.isEmpty()) return@withContext DriveResult.NoBackupFound

            val fileId = files.first().id
            val content = drive.files().get(fileId)
                .executeMediaAsInputStream()
                .bufferedReader()
                .readText()

            DriveResult.Success(json.decodeFromString<BackupJson>(content))
        }.getOrElse { DriveResult.Error(it.message ?: "Download failed") }
    }
}
