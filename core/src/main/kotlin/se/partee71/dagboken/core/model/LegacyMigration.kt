package se.partee71.dagboken.core.model

import kotlin.time.Instant

/** Varifrån 3.x-datan kom (OMB-2, OMB-5): Room-filen på enheten, en Drive-backup eller en lokal JSON. */
enum class LegacySource(override val wire: String) : WireEnum {
    ROOM("room"),
    DRIVE("drive"),
    JSON("json"),
}

/**
 * Markören `users/{uid}.legacyMigration` (OMB-2, OMB-7): att 3.x-datan har migrerats in i kontot. Skrivs
 * **en** gång som sista steg efter verifieringen – rules låter den aldrig ändras eller tas bort – och
 * startkontrollen frågar servern efter den innan en migrering erbjuds, så att en omkörning aldrig
 * skriver över data som 4.0 hunnit ändra.
 */
data class LegacyMigration(
    /** När markören skrevs – serverns klocka (`serverTimestamp`); `null` bara i minnet innan den skrivits. */
    val completedAt: Instant? = null,
    val source: LegacySource = LegacySource.ROOM,
    /** 3.x-backupens eller Room-filens `createdAt` som ögonblick; `null` när den saknades. */
    val sourceCreatedAt: Instant? = null,
    /** Appens `versionName` när migreringen gjordes. */
    val appVersion: String = "",
    /** Antal dokument per samling som migrerades (rapportens "före"). */
    val counts: Map<String, Int> = emptyMap(),
)
