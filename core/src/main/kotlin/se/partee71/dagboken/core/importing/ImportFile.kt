package se.partee71.dagboken.core.importing

import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull
import se.partee71.dagboken.core.schema.CollectionNames
import se.partee71.dagboken.core.schema.DocumentRules
import se.partee71.dagboken.core.schema.ExportFormat
import se.partee71.dagboken.core.schema.Schema
import se.partee71.dagboken.core.schema.SchemaMigrator

/**
 * Något i en importfil som hindrar importen (BCK-6): dokumentets sökväg under användaren (`doses/{id}`), fältet och
 * skälet – värdets längd, talet eller enum-namnet, aldrig textinnehållet (hälsodata).
 */
data class Problem(val path: String, val field: String, val reason: String) {
    override fun toString() = "$path · $field: $reason"
}

/** Utfallet av [ImportFile.read]: alla dokument, ett stopp med alla fel, eller en fil som inte är någon export. */
sealed interface ImportFileResult {
    /**
     * Klart att granska och skriva: [documents] under det inloggade kontot, utan `users/{uid}` (det finns sedan
     * inloggningen), sorterade på sökväg (en episod före sina incheckningar), i appens format. [schemaVersion] = filens
     * dataformat. [counts] = antal per samling i `CollectionNames.ALL`-ordning – granskningens och skrivningens "före".
     */
    data class Ready(val schemaVersion: Int, val documents: List<ExportFormat.Document>) : ImportFileResult {
        val counts: Map<String, Int> get() = CollectionNames.countsOf(documents.map { it.path })
    }

    /** Något i filen klarar inte rules eller saknar plats: [problems] listar **alla** fel – ingenting skrivs. */
    data class Stopped(val schemaVersion: Int, val problems: List<Problem>) : ImportFileResult

    /** Filen är ingen 4.x-export (eller inte JSON alls). Innehållet visas aldrig. */
    data object NotABackup : ImportFileResult
}

/**
 * Importens enda läsare av en fil (BCK-6): en 4.x-export i `tools/db export`-formatet (appens egen export, BCK-13, eller
 * veckobackupen) ger dokumenten för det inloggade kontot [uid] – eller ett stopp – innan något skrivs.
 *
 * Varje dokument ska ligga under **en** användare i en känd samling med giltiga och unika id:n och klara [DocumentRules]
 * (= `firestore.rules`); en incheckning kräver sin episod i filen (rules `existsAfter`). Filens `schemaVersion`
 * (användardokumentets, annars filens) får inte vara nyare än appens; en äldre lyfts med [SchemaMigrator]. Sökvägarna
 * flyttas till [uid] (exporten kan komma från ett annat konto). Användardokumentet skrivs aldrig – det bär kontots egen
 * version, `createdAt` och migreringsmarkören. Okända fält följer med (BCK-9). En 3.x-backup (`BackupJson`) är ingen
 * export: sedan 4.9.0 importeras den inte (BCK-14 borttaget).
 *
 * Felen nämner samlingar, id:n, fält och skäl – aldrig innehåll; alternativ anges som plats i filen (`options#3`),
 * eftersom deras id bär namnet.
 */
object ImportFile {
    fun read(text: String, uid: String): ImportFileResult {
        val root = try {
            Json.parseToJsonElement(text) as? JsonObject
        } catch (_: SerializationException) {
            null
        } ?: return ImportFileResult.NotABackup
        if (ExportFormat.DOCUMENTS !in root) return ImportFileResult.NotABackup
        val documents = try {
            ExportFormat.decode(root)
        } catch (_: Exception) {
            // Fel form (ingen lista, data som inte är ett objekt, trasig tidsstämpel): inte en export – meddelandena citerar innehåll.
            return ImportFileResult.NotABackup
        }
        return ExportRead(documents, root, uid).run()
    }
}

/** En 4.x-export som läses in i kontot [uid]: kontroll av hela filen först, sedan dokumenten – eller alla fel. */
private class ExportRead(private val documents: List<ExportFormat.Document>, private val root: JsonObject, private val uid: String) {
    private val problems = mutableListOf<Problem>()

    fun run(): ImportFileResult {
        val users = documents.mapNotNull { segments(it.path).takeIf { s -> s.size >= 2 && s[0] == CollectionNames.USERS }?.get(1) }.toSet()
        if (users.size > 1) problem(CollectionNames.USERS, "uid", "filen har ${users.size} användare – importera en export av en användare")
        val userDoc = documents.firstOrNull { segments(it.path).size == 2 && segments(it.path)[0] == CollectionNames.USERS }
        val version = Schema.versionOf(userDoc?.data?.get(ExportFormat.SCHEMA_VERSION) ?: (root[ExportFormat.SCHEMA_VERSION] as? JsonPrimitive)?.intOrNull)
        if (Schema.isNewerThanApp(version)) {
            problem(CollectionNames.USERS, ExportFormat.SCHEMA_VERSION, "nyare format ($version) än appen förstår (${Schema.CURRENT_VERSION}) – uppdatera appen")
            return ImportFileResult.Stopped(version, problems.toList())
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
        return if (problems.isNotEmpty()) ImportFileResult.Stopped(version, problems.toList()) else ImportFileResult.Ready(version, result.toList())
    }

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

    /** Ett id i rapporten: som det står när det är giltigt, annars bara längden – ett ogiltigt id kan bära innehåll. */
    private fun idLabel(id: String): String = if (DocumentRules.isValidId(id)) id else "(ogiltigt id, ${id.length} tecken)"

    private fun problem(path: String, field: String, reason: String) {
        problems += Problem(path, field, reason)
    }
}
