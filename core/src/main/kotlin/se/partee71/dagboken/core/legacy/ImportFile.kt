package se.partee71.dagboken.core.legacy

import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import se.partee71.dagboken.core.schema.CollectionNames
import se.partee71.dagboken.core.schema.DocumentRules
import se.partee71.dagboken.core.schema.ExportFormat
import se.partee71.dagboken.core.schema.Schema
import se.partee71.dagboken.core.schema.SchemaMigrator

/** Vilken sorts fil importen läste (BCK-6, BCK-14). */
sealed interface ImportFormat {
    /** En 3.x-backup (`BackupJson`, Drive eller lokal JSON) i format [version] 1 eller 2. */
    data class Legacy(val version: Int) : ImportFormat

    /** En 4.0-export i `tools/db export`-formatet (appens egen export, BCK-13) med dataformatet [schemaVersion]. */
    data class Export(val schemaVersion: Int) : ImportFormat
}

/** Utfallet av [ImportFile.read]: alla dokument, ett stopp med rapport, eller en fil som inte är någon backup. */
sealed interface ImportFileResult {
    /**
     * Klart att granska och skriva: [documents] under det inloggade kontot, utan `users/{uid}` (det finns sedan
     * inloggningen), sorterade på sökväg (en episod före sina incheckningar). [counts] = antal per samling i
     * `CollectionNames.ALL`-ordning – granskningens och skrivningens "före".
     */
    data class Ready(val format: ImportFormat, val documents: List<ExportFormat.Document>, val report: ConversionReport) : ImportFileResult {
        val counts: Map<String, Int> get() = CollectionNames.countsOf(documents.map { it.path })
    }

    /** Något i filen ryms inte i 4.0 eller saknar plats: rapporten listar **alla** fel – ingenting skrivs (OMB-3). */
    data class Stopped(val format: ImportFormat, val report: ConversionReport) : ImportFileResult

    /** Filen är varken en 3.x-backup eller en 4.0-export (eller inte JSON alls). Innehållet visas aldrig. */
    data object NotABackup : ImportFileResult
}

/**
 * Importens enda läsare av en fil (BCK-6, BCK-14, OMB-5): känner igen formatet och ger dokumenten för det inloggade
 * kontot [uid] – eller ett stopp – innan något skrivs. Samma läsare för Drive-backupen, filen från dokumentväljaren och
 * båda importvägarna (första starten och Inställningar → Export och import).
 *
 * - **4.0-export** (har `documents`): varje dokument ska ligga under **en** användare i en känd samling med giltiga och
 *   unika id:n och klara [DocumentRules] (= `firestore.rules`); en incheckning kräver sin episod i filen (rules
 *   `existsAfter`). Filens `schemaVersion` (användardokumentets, annars filens) får inte vara nyare än appens; en äldre lyfts
 *   med [SchemaMigrator]. Sökvägarna flyttas till [uid] (exporten kan komma från ett annat konto). Användardokumentet
 *   skrivs aldrig – det bär kontots egen version, `createdAt` och migreringsmarkören. Okända fält följer med (BCK-9).
 * - **3.x-backup** (minst en av 3.x:s datalistor, [LEGACY_DATA_KEYS]): `BackupJson.parse` och den **enda**
 *   konverteraren [BackupJsonConverter] (OMB-3) – samma mappning som migreringen på enheten och grinden OMB-4.
 *
 * Rapporten nämner samlingar, id:n, fält och skäl – aldrig innehåll; alternativ anges som plats i filen
 * (`options#3`), eftersom deras id bär namnet.
 */
object ImportFile {
    fun read(text: String, uid: String): ImportFileResult {
        val root = try {
            Json.parseToJsonElement(text) as? JsonObject
        } catch (_: SerializationException) {
            null
        } ?: return ImportFileResult.NotABackup
        return when {
            ExportFormat.DOCUMENTS in root -> readExport(root, uid)
            LEGACY_DATA_KEYS.any { root[it] is JsonArray } -> readLegacy(text, uid)
            else -> ImportFileResult.NotABackup
        }
    }

    private fun readLegacy(text: String, uid: String): ImportFileResult {
        val backup = try {
            BackupJson.parse(text)
        } catch (_: IllegalArgumentException) {
            return ImportFileResult.NotABackup
        }
        val format = ImportFormat.Legacy(backup.version)
        return when (val result = BackupJsonConverter.convert(backup, uid)) {
            is ConversionResult.Stopped -> ImportFileResult.Stopped(format, result.report)
            is ConversionResult.Converted -> ImportFileResult.Ready(format, result.accountDocuments, result.report)
        }
    }

    private fun readExport(root: JsonObject, uid: String): ImportFileResult {
        val documents = try {
            ExportFormat.decode(root)
        } catch (_: Exception) {
            // Fel form (ingen lista, data som inte är ett objekt, trasig tidsstämpel): inte en export – meddelandena citerar innehåll.
            return ImportFileResult.NotABackup
        }
        return ExportRead(documents, root, uid).run()
    }

    /**
     * 3.x-backupens egna datalistor (poster och alternativ) – en fil är en 3.x-backup bara om minst en av dem är en lista.
     * `version`, `createdAt`, `notes` och `settings` är för allmänna för att känna igen formatet på (v1 skrev inte ens
     * `version`, standardvärdet), och 3.x utelämnade tomma listor, så ingen enskild nyckel finns alltid.
     */
    internal val LEGACY_DATA_KEYS: List<String> = listOf(
        "aktiviteter", "mediciner", "medicinRecipes", "medicinFavoriter", "sjukdomsepisoder", "sjukdomsIncheckningar", "handelser",
        "aktiviteterOptions", "symptomOptions", "aktiviteterOptionsV2", "symptomOptionsV2", "handelseTypOptions",
        "screeningEventConfigs", "medNotificationConfigs",
    )
}

/** En 4.0-export som läses in i kontot [uid]: kontroll av hela filen först, sedan dokumenten – eller alla fel. */
private class ExportRead(private val documents: List<ExportFormat.Document>, private val root: JsonObject, private val uid: String) {
    private val problems = mutableListOf<Problem>()

    fun run(): ImportFileResult {
        val users = documents.mapNotNull { segments(it.path).takeIf { s -> s.size >= 2 && s[0] == CollectionNames.USERS }?.get(1) }.toSet()
        if (users.size > 1) problem(CollectionNames.USERS, "uid", "filen har ${users.size} användare – importera en export av en användare")
        val userDoc = documents.firstOrNull { segments(it.path).size == 2 && segments(it.path)[0] == CollectionNames.USERS }
        val version = Schema.versionOf(userDoc?.data?.get(ExportFormat.SCHEMA_VERSION) ?: (root[ExportFormat.SCHEMA_VERSION] as? JsonPrimitive)?.intOrNull)
        val format = ImportFormat.Export(version)
        if (Schema.isNewerThanApp(version)) {
            problem(CollectionNames.USERS, ExportFormat.SCHEMA_VERSION, "nyare format ($version) än appen förstår (${Schema.CURRENT_VERSION}) – uppdatera appen")
            return ImportFileResult.Stopped(format, report(version, emptyList()))
        }

        val seen = HashSet<String>()
        val result = mutableListOf<ExportFormat.Document>()
        var optionIndex = 0
        for (document in documents) {
            val parts = segments(document.path)
            val collection = collectionOf(parts)
            val label = when (collection) {
                null -> "(okänd sökväg, ${parts.size} led)"
                CollectionNames.OPTIONS -> "${CollectionNames.OPTIONS}#${optionIndex++}"
                else -> label(parts)
            }
            when {
                collection == null -> problem(label, "path", "okänd samling – finns inte i 4.0")
                collection == CollectionNames.USERS -> Unit
                parts.any { !DocumentRules.isValidId(it) } -> problem(label, "id", "ogiltigt id")
                !seen.add(document.path) -> problem(label, "id", "dubblett: samma sökväg två gånger")
                else -> {
                    val data = SchemaMigrator.migrate(version, collection, document.data)
                    for (violation in DocumentRules.validate(collection, data)) problem(label, violation.field, violation.reason)
                    result += ExportFormat.Document(CollectionNames.user(uid) + document.path.removePrefix(CollectionNames.user(parts[1])), data)
                }
            }
        }
        val episodes = result.filter { CollectionNames.collectionOf(it.path) == CollectionNames.ILLNESS_EPISODES }.mapTo(HashSet()) { it.path }
        result.filter { CollectionNames.collectionOf(it.path) == CollectionNames.CHECKINS }
            .filterNot { it.path.substringBeforeLast('/').substringBeforeLast('/') in episodes }
            .forEach { problem(label(segments(it.path)), "episod", "episoden saknas i filen") }
        result.sortBy { it.path }
        val report = report(version, result)
        return if (report.stopped) ImportFileResult.Stopped(format, report) else ImportFileResult.Ready(format, result.toList(), report)
    }

    private fun report(version: Int, written: List<ExportFormat.Document>) = ConversionReport(
        formatVersion = version,
        createdAt = (root["exportedAt"] as? JsonPrimitive)?.contentOrNull.orEmpty(),
        counts = CollectionNames.countsOf(written.map { it.path }),
        warnings = emptyList(),
        problems = problems.toList(),
    )

    /** Samlingen för en sökväg under `users/{uid}` enligt Datamodell, eller `null` för en sökväg 4.0 inte har. */
    private fun collectionOf(parts: List<String>): String? = when {
        parts.isEmpty() || parts[0] != CollectionNames.USERS -> null
        parts.size == 2 -> CollectionNames.USERS
        parts.size == 4 && parts[2] in CollectionNames.USER_COLLECTIONS -> parts[2]
        parts.size == 6 && parts[2] == CollectionNames.ILLNESS_EPISODES && parts[4] == CollectionNames.CHECKINS -> CollectionNames.CHECKINS
        else -> null
    }

    private fun segments(path: String) = path.split('/')

    /** Sökvägen i rapporten utan `users/{uid}`: `doses/<id>`, `illnessEpisodes/<id>/checkins/<id>` – ogiltiga id:n som längd. */
    private fun label(parts: List<String>): String = parts.drop(2).chunked(2).joinToString("/") { (name, id) -> "$name/${idLabel(id)}" }

    private fun problem(path: String, field: String, reason: String) {
        problems += Problem(path, field, reason)
    }
}
