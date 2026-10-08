package se.partee71.dagboken.data.legacy

import android.accounts.Account
import android.app.PendingIntent
import android.content.Context
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.Scope
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import se.partee71.dagboken.data.auth.AuthRepository
import se.partee71.dagboken.data.common.DataError
import se.partee71.dagboken.di.IoDispatcher

/** En 3.x-backup i Drive:s `appDataFolder` (`dagboken-backup-<yyyyMMdd-HHmm>.json`). Bara id, namn och tid – aldrig innehåll. */
data class DriveBackupFile(val id: String, val name: String, val createdTime: String)

/** Utfallet av en läsning ur Drive (BCK-14, OMB-5). */
sealed interface DriveRead {
    /** Den senaste backupen: filens text (läses av `ImportFile`) och dess metadata. */
    data class Found(val file: DriveBackupFile, val text: String) : DriveRead

    /** Ingen 3.x-backup i `appDataFolder` för kontot (papperskorgade räknas inte). */
    data object NoBackup : DriveRead

    /**
     * Användaren måste ge Dagboken läsrätt till `appDataFolder` (`DRIVE_APPDATA`): skärmen startar [consent] och anropar
     * läsningen igen när svaret är ja. Begärs bara här, vid en import – aldrig vid start.
     */
    data class NeedsConsent(val consent: PendingIntent) : DriveRead

    /** Utan nät `Offline`, utan konto `NotSignedIn`, nekad åtkomst `PermissionDenied`, annars `Unknown`. */
    data class Failed(val error: DataError) : DriveRead
}

/** 3.x:s Drive-backuper, **bara läsning** (ingen upload – Drive-backupen är pensionerad, ADR-001 beslut 4). */
interface DriveBackups {
    /** Den senaste backupen ([DriveRead.Found]) – eller varför den inte gick att läsa. */
    suspend fun downloadLatestBackup(): DriveRead
}

/**
 * Drive via REST (`HttpURLConnection`) med en åtkomsttoken från Identity `AuthorizationClient` – inget Drive-bibliotek
 * (CI-budget). Porterad från 3.x `DriveBackupRepository` (`listBackups`, `downloadLatestBackup`, samma fråga och
 * ordning); upload och gallring är borttagna. Tokenen lever bara i anropet och lagras aldrig; inget loggas (NFR-13).
 * 3.x:s filer syns för samma OAuth-klient, dvs. samma `applicationId` och signering.
 */
class DriveRestBackups @Inject constructor(
    @ApplicationContext private val context: Context,
    private val auth: AuthRepository,
    @IoDispatcher private val dispatcher: CoroutineDispatcher,
) : DriveBackups {
    override suspend fun downloadLatestBackup(): DriveRead = withContext(dispatcher) {
        val email = auth.authState.first()?.email ?: return@withContext DriveRead.Failed(DataError.NotSignedIn)
        val token = when (val authorized = authorize(email)) {
            is Authorized.Token -> authorized.value
            is Authorized.Consent -> return@withContext DriveRead.NeedsConsent(authorized.intent)
            is Authorized.Failed -> return@withContext DriveRead.Failed(authorized.error)
        }
        try {
            val latest = listBackups(token).firstOrNull() ?: return@withContext DriveRead.NoBackup
            DriveRead.Found(latest, get(token, "$FILES/${latest.id}?alt=media"))
        } catch (e: CancellationException) {
            throw e
        } catch (e: HttpError) {
            DriveRead.Failed(if (e.code == HTTP_UNAUTHORIZED || e.code == HTTP_FORBIDDEN) DataError.PermissionDenied else DataError.Unknown)
        } catch (_: IOException) {
            DriveRead.Failed(DataError.Offline)
        } catch (_: Exception) {
            DriveRead.Failed(DataError.Unknown)
        }
    }

    /** Backuperna, nyast först (3.x: `createdTime desc`, papperskorgen utesluten). */
    private fun listBackups(token: String): List<DriveBackupFile> {
        val query = listOf(
            "spaces" to "appDataFolder",
            "q" to BACKUP_QUERY,
            "orderBy" to "createdTime desc",
            "fields" to "files(id,name,createdTime)",
        ).joinToString("&") { (key, value) -> "$key=${URLEncoder.encode(value, Charsets.UTF_8.name())}" }
        val files = Json.parseToJsonElement(get(token, "$FILES?$query")).jsonObject["files"]?.jsonArray.orEmpty()
        return files.map { element ->
            val file = element.jsonObject
            DriveBackupFile(
                id = file.getValue("id").jsonPrimitive.content,
                name = file["name"]?.jsonPrimitive?.content.orEmpty(),
                createdTime = file["createdTime"]?.jsonPrimitive?.content.orEmpty(),
            )
        }
    }

    /** GET med Bearer-token; svaret som UTF-8. Ett HTTP-fel ger [HttpError] med bara statuskoden. */
    private fun get(token: String, url: String): String {
        val connection = URL(url).openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = TIMEOUT_MS
            connection.readTimeout = TIMEOUT_MS
            connection.setRequestProperty("Authorization", "Bearer $token")
            val code = connection.responseCode
            if (code !in 200..299) throw HttpError(code)
            return connection.inputStream.use { it.reader(Charsets.UTF_8).readText() }
        } finally {
            connection.disconnect()
        }
    }

    private suspend fun authorize(email: String): Authorized = try {
        val request = AuthorizationRequest.builder()
            .setRequestedScopes(listOf(Scope(DRIVE_APPDATA)))
            .setAccount(Account(email, ACCOUNT_TYPE))
            .build()
        val result = Identity.getAuthorizationClient(context).authorize(request).await()
        val consent = result.pendingIntent
        val token = result.accessToken
        when {
            result.hasResolution() && consent != null -> Authorized.Consent(consent)
            token != null -> Authorized.Token(token)
            else -> Authorized.Failed(DataError.PermissionDenied)
        }
    } catch (e: CancellationException) {
        throw e
    } catch (_: IOException) {
        Authorized.Failed(DataError.Offline)
    } catch (_: Exception) {
        Authorized.Failed(DataError.Unknown)
    }

    private sealed interface Authorized {
        class Token(val value: String) : Authorized

        class Consent(val intent: PendingIntent) : Authorized

        class Failed(val error: DataError) : Authorized
    }

    private class HttpError(val code: Int) : IOException("HTTP $code")

    private companion object {
        /** `DriveScopes.DRIVE_APPDATA` utan Drive-biblioteket. */
        const val DRIVE_APPDATA = "https://www.googleapis.com/auth/drive.appdata"
        const val ACCOUNT_TYPE = "com.google"
        const val FILES = "https://www.googleapis.com/drive/v3/files"

        /** Som 3.x: papperskorgade filer ligger kvar i appDataFolder och matchar annars namnfrågan. */
        const val BACKUP_QUERY = "name contains 'dagboken-backup-' and trashed = false"
        const val TIMEOUT_MS = 30_000
        const val HTTP_UNAUTHORIZED = 401
        const val HTTP_FORBIDDEN = 403
    }
}
