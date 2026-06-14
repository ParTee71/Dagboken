package se.partee71.dagboken.data.migration

import android.app.PendingIntent
import android.content.Context
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.Scope
import com.google.api.client.http.ByteArrayContent
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.drive.Drive
import com.google.api.services.drive.DriveScopes
import com.google.api.services.drive.model.File
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import se.partee71.dagboken.data.auth.FirebaseAuthRepository
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

data class DriveBackupFile(val id: String, val name: String, val createdTime: String)

sealed class DriveResult<out T> {
    data class Success<T>(val value: T) : DriveResult<T>()
    data class Error(val message: String) : DriveResult<Nothing>()
    object NoAccount : DriveResult<Nothing>()
    object NoBackupFound : DriveResult<Nothing>()
    data class NeedsAuthorization(val pendingIntent: PendingIntent) : DriveResult<Nothing>()
}

private sealed class BuildResult {
    data class Ok(val drive: Drive) : BuildResult()
    data class Fail(val reason: DriveResult<Nothing>) : BuildResult()
}

@Singleton
class DriveBackupRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val authRepo: FirebaseAuthRepository,
) {
    private val json = Json { ignoreUnknownKeys = true }
    private val BACKUP_PREFIX = "dagboken-backup-"
    private val APP_NAME = "Dagboken"

    private suspend fun buildDriveService(): BuildResult {
        authRepo.currentUser ?: return BuildResult.Fail(DriveResult.NoAccount)

        val authRequest = AuthorizationRequest.builder()
            .setRequestedScopes(listOf(Scope(DriveScopes.DRIVE_APPDATA)))
            .build()

        return suspendCancellableCoroutine { cont ->
            Identity.getAuthorizationClient(context)
                .authorize(authRequest)
                .addOnSuccessListener { result ->
                    if (result.hasResolution()) {
                        cont.resume(BuildResult.Fail(DriveResult.NeedsAuthorization(result.pendingIntent!!)))
                    } else {
                        val token = result.accessToken
                        if (token == null) {
                            cont.resume(BuildResult.Fail(DriveResult.Error("No access token received")))
                        } else {
                            val drive = Drive.Builder(
                                NetHttpTransport(),
                                GsonFactory.getDefaultInstance(),
                            ) { request ->
                                request.headers.authorization = "Bearer $token"
                            }.setApplicationName(APP_NAME).build()
                            cont.resume(BuildResult.Ok(drive))
                        }
                    }
                }
                .addOnFailureListener { e ->
                    cont.resume(BuildResult.Fail(DriveResult.Error(e.message ?: "Authorization failed")))
                }
        }
    }

    suspend fun listBackups(): DriveResult<List<DriveBackupFile>> {
        return when (val build = buildDriveService()) {
            is BuildResult.Fail -> build.reason
            is BuildResult.Ok   -> withContext(Dispatchers.IO) {
                runCatching {
                    val files = build.drive.files().list()
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
        }
    }

    suspend fun downloadLatestBackup(): DriveResult<BackupJson> {
        return when (val build = buildDriveService()) {
            is BuildResult.Fail -> build.reason
            is BuildResult.Ok   -> withContext(Dispatchers.IO) {
                runCatching {
                    val drive = build.drive
                    val files = drive.files().list()
                        .setSpaces("appDataFolder")
                        .setQ("name contains '$BACKUP_PREFIX'")
                        .setOrderBy("createdTime desc")
                        .setPageSize(1)
                        .setFields("files(id,name)")
                        .execute()
                        .files ?: emptyList()

                    if (files.isEmpty()) return@withContext DriveResult.NoBackupFound

                    val content = drive.files().get(files.first().id)
                        .executeMediaAsInputStream()
                        .bufferedReader()
                        .readText()

                    DriveResult.Success(json.decodeFromString<BackupJson>(content))
                }.getOrElse { DriveResult.Error(it.message ?: "Download failed") }
            }
        }
    }

    suspend fun uploadBackup(backupJson: BackupJson): DriveResult<String> {
        return when (val build = buildDriveService()) {
            is BuildResult.Fail -> build.reason
            is BuildResult.Ok   -> withContext(Dispatchers.IO) {
                runCatching {
                    val drive = build.drive
                    val timestamp = LocalDateTime.now()
                        .format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmm"))
                    val content = json.encodeToString(BackupJson.serializer(), backupJson)

                    val metadata = File().apply {
                        name    = "$BACKUP_PREFIX$timestamp.json"
                        parents = listOf("appDataFolder")
                    }
                    val media = ByteArrayContent("application/json", content.toByteArray())
                    val file  = drive.files().create(metadata, media).setFields("id").execute()

                    pruneOldBackups(drive)
                    DriveResult.Success(file.id)
                }.getOrElse { DriveResult.Error(it.message ?: "Upload failed") }
            }
        }
    }

    private fun pruneOldBackups(drive: Drive) {
        runCatching {
            val files = drive.files().list()
                .setSpaces("appDataFolder")
                .setQ("name contains '$BACKUP_PREFIX'")
                .setOrderBy("createdTime desc")
                .setFields("files(id)")
                .execute()
                .files ?: return
            files.drop(5).forEach { drive.files().delete(it.id).execute() }
        }
    }
}
