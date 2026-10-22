package se.partee71.dagboken.core.model

import kotlin.time.Instant

/** Varifrån 3.x-datan kom: Room-filen på enheten (bara i markörer från 4.0–4.7), en Drive-backup eller en lokal JSON. */
enum class LegacySource(override val wire: String) : WireEnum {
    ROOM("room"),
    DRIVE("drive"),
    JSON("json")
}

/**
 * Markören `users/{uid}.legacyMigration`: att 3.x-datan har migrerats in i kontot. 4.0–4.7 skrev den **en** gång som
 * sista steg i migreringen på enheten (~~OMB-2~~, borttagen i 4.8.0); rules låter den aldrig ändras eller tas bort.
 * Ingen kod skriver den längre, men en befintlig markör är persisterad data och läses, godtas och exporteras som förut
 * (regel 1).
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
    val counts: Map<String, Int> = emptyMap()
)
