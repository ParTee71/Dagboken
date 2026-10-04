package se.partee71.dagboken.data.common

import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import se.partee71.dagboken.core.schema.Schema

/**
 * Användardokumentets (`users/{uid}`) läge – gäller just [uid]. [exists] = `false` så länge
 * dokumentet inte finns (första inloggningen, innan `EnsureUserUseCase` skapat det); då gäller
 * [Schema.FIRST_VERSION].
 */
data class UserVersion(val uid: String, val version: Int, val exists: Boolean = true)

/** Den inloggade användaren som samlingarna läser och skriver under (`UserSession`). */
interface UserScope {
    /** Inloggad användares uid; `null` = utloggad. */
    val uid: StateFlow<String?>

    /** Inloggad användares version; `null` eller en annan användares version så länge den inte är känd. */
    val schemaVersion: StateFlow<UserVersion?>

    /** Användaren vars dokument senast inte gick att läsa (rules nekade), annars `null`. */
    val unreadable: StateFlow<String?>
}

/** Den kända versionen för den inloggade användaren, annars `null`. */
fun UserScope.currentVersion(): Int? =
    schemaVersion.value?.takeIf { it.uid == uid.value }?.version

/**
 * Varför en skrivning inte får göras, eller `null` om den får. **Stängd som standard:** så länge
 * användarens version är okänd väntar skrivningen en stund på den, och vägras sedan hellre än att
 * en äldre app skriver över nyare data. Ett användardokument som inte går att läsa ger
 * [DataError.PermissionDenied].
 */
suspend fun UserScope.writeBlocker(): DataError? {
    val id = uid.value ?: return null // samlingar som kräver inloggning kastar NotSignedIn själva
    val verdict = withTimeoutOrNull(VERSION_WAIT) {
        combine(schemaVersion, unreadable) { version, denied ->
            when {
                denied == id -> Verdict(DataError.PermissionDenied)
                version?.uid == id ->
                    Verdict(DataError.UpdateRequired.takeIf { Schema.isNewerThanApp(version.version) })
                else -> null
            }
        }.filterNotNull().first()
    } ?: return DataError.Offline
    return verdict.blocker
}

private class Verdict(val blocker: DataError?)

/** Hur länge en skrivning väntar på användarens version (från cachen tar det millisekunder). */
private val VERSION_WAIT = 5.seconds

/** Användardokumentets `schemaVersion` direkt ur dokumentet (utan codec). */
interface UserVersionSource {
    /**
     * Versionen som flöde; `null` = dokumentet finns inte (än). Ett dokument utan fältet har
     * [Schema.FIRST_VERSION]. Går dokumentet inte att läsa (rules nekar) avslutas flödet med
     * [DataError.PermissionDenied]; andra fel försöker källan själv igen.
     */
    fun schemaVersion(uid: String): Flow<Int?>

    /**
     * Stämplar användardokumentet med [version] utan att något annat fält skrivs om. Läggs i
     * cachen och synkas senare (offline först); ett fel rapporteras inte – nästa öppning försöker igen.
     */
    fun stamp(uid: String, version: Int)
}
