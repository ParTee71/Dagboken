package se.partee71.dagboken.data.migration

import android.accounts.Account
import android.app.PendingIntent
import android.content.Context
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.AuthorizationResult
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.Scope
import com.google.api.client.googleapis.json.GoogleJsonResponseException
import com.google.api.client.http.ByteArrayContent
import com.google.api.client.http.HttpRequestInitializer
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

private sealed class AuthorizeResult {
    data class Token(val accessToken: String) : AuthorizeResult()
    data class NeedsAuthorization(val pendingIntent: PendingIntent) : AuthorizeResult()
    data class Error(val message: String) : AuthorizeResult()
}

@Singleton
class DriveBackupRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val authRepo: FirebaseAuthRepository,
) {
    private val json = Json { ignoreUnknownKeys = true }
    private val BACKUP_PREFIX = "dagboken-backup-"
    private val APP_NAME = "Dagboken"

    private suspend fun authorizeDrive(): AuthorizeResult {
        val accountHint = authRepo.currentUser?.email?.let { Account(it, "com.google") }
        val request = AuthorizationRequest.builder()
            .setRequestedScopes(listOf(Scope(DriveScopes.DRIVE_APPDATA)))
            .apply { accountHint?.let { setAccount(it) } }
            .build()

        return suspendCancellableCoroutine { cont ->
            Identity.getAuthorizationClient(context)
                .authorize(request)
                .addOnSuccessListener { result: AuthorizationResult ->
                    when {
                        result.hasResolution() ->
                            cont.resume(AuthorizeResult.NeedsAuthorization(result.pendingIntent!!))
                        result.accessToken != null ->
                            cont.resume(AuthorizeResult.Token(result.accessToken!!))
                        else ->
                            cont.resume(AuthorizeResult.Error("Ingen åtkomsttoken returnerades"))
                    }
                }
                .addOnFailureListener { e ->
                    cont.resume(AuthorizeResult.Error(e.message ?: "Auktorisering misslyckades"))
                }
        }
    }

    private fun driveServiceFromToken(accessToken: String): Drive {
        val requestInitializer = HttpRequestInitializer { request ->
            request.headers.authorization = "Bearer $accessToken"
        }
        return Drive.Builder(NetHttpTransport(), GsonFactory.getDefaultInstance(), requestInitializer)
            .setApplicationName(APP_NAME)
            .build()
    }

    private fun jsonErrorMessage(e: GoogleJsonResponseException): String {
        val reason = e.details?.errors?.firstOrNull()?.reason ?: "?"
        return "Drive ${e.statusCode} ($reason): ${e.details?.errors?.firstOrNull()?.message ?: e.message}"
    }

    private suspend fun <T> withDrive(block: (Drive) -> DriveResult<T>): DriveResult<T> {
        if (authRepo.currentUser == null) return DriveResult.NoAccount

        return withContext(Dispatchers.IO) {
            when (val auth = authorizeDrive()) {
                is AuthorizeResult.NeedsAuthorization -> DriveResult.NeedsAuthorization(auth.pendingIntent)
                is AuthorizeResult.Error -> DriveResult.Error(auth.message)
                is AuthorizeResult.Token -> try {
                    block(driveServiceFromToken(auth.accessToken))
                } catch (e: GoogleJsonResponseException) {
                    DriveResult.Error(jsonErrorMessage(e))
                } catch (e: Exception) {
                    DriveResult.Error(e.message ?: "Okänt fel")
                }
            }
        }
    }

    suspend fun listBackups(): DriveResult<List<DriveBackupFile>> = withDrive { drive ->
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
    }

    suspend fun downloadLatestBackup(): DriveResult<BackupJson> = withDrive { drive ->
        val files = drive.files().list()
            .setSpaces("appDataFolder")
            .setQ("name contains '$BACKUP_PREFIX'")
            .setOrderBy("createdTime desc")
            .setPageSize(1)
            .setFields("files(id,name)")
            .execute()
            .files ?: emptyList()

        if (files.isEmpty()) return@withDrive DriveResult.NoBackupFound

        val content = drive.files().get(files.first().id)
            .executeMediaAsInputStream()
            .bufferedReader()
            .readText()

        DriveResult.Success(json.decodeFromString<BackupJson>(content))
    }

    suspend fun uploadBackup(backupJson: BackupJson): DriveResult<String> = withDrive { drive ->
        val timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmm"))
        val content = json.encodeToString(BackupJson.serializer(), backupJson)

        val metadata = File().apply {
            name    = "$BACKUP_PREFIX$timestamp.json"
            parents = listOf("appDataFolder")
        }
        val media = ByteArrayContent("application/json", content.toByteArray())
        val file  = drive.files().create(metadata, media).setFields("id").execute()

        pruneOldBackups(drive)
        DriveResult.Success(file.id)
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
