package se.partee71.dagboken.data.importing

import java.io.File
import kotlin.time.Instant
import se.partee71.dagboken.core.schema.ExportFormat
import se.partee71.dagboken.core.schema.Schema
import se.partee71.dagboken.data.firestore.Paths

/**
 * Importens testdata: 4.0-fixturen `tools/db/test/fixtures/user.json` (syntetisk, varje fält satt) som en export från
 * ett annat konto, utan incheckningen vars episod saknar dokument (den kan inte skrivas genom rules).
 */
object ImportFixtures {
    private val repoRoot = File("").absoluteFile.let { if (it.name == "app") it.parentFile else it }

    /** Kontot i fixturen. */
    private const val FIXTURE_UID = "uid-test"

    /** Fixturens dokument, användardokumentet med, under `users/uid-test`. */
    val documents: List<ExportFormat.Document> = ExportFormat.decode(File(repoRoot, "tools/db/test/fixtures/user.json").readText())
        .filterNot { "/utan-dokument/" in it.path }

    /** Fixturen som en export i `tools/db export`-formatet – det användaren väljer i dokumentväljaren. */
    val exportText: String = ExportFormat.encode(Instant.parse("2026-10-07T19:14:00Z"), Schema.CURRENT_VERSION, documents)

    /** Det importen skriver i kontot [uid]: fixturens dokument flyttade dit, utan användardokumentet. */
    fun imported(uid: String): List<ExportFormat.Document> = documents.filterNot { it.path == Paths.user(FIXTURE_UID) }
        .map { ExportFormat.Document(it.path.replaceFirst(Paths.user(FIXTURE_UID), Paths.user(uid)), it.data) }
}
