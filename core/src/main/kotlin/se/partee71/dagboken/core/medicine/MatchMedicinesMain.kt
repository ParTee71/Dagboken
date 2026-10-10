package se.partee71.dagboken.core.medicine

import java.io.File
import java.io.PrintStream
import kotlin.system.exitProcess
import kotlin.time.Instant
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import se.partee71.dagboken.core.cli.CliArgs
import se.partee71.dagboken.core.schema.ExportFormat

/**
 * Recept och vid behov-mediciner får namn, styrka och form ur Läkemedelsverkets lista ([MedicineRename]),
 * från kommandoraden. Två steg på en `tools/db export`-fil; stdout får bara antal, aldrig namn (hälsodata).
 *
 * ```
 * ./gradlew :core:matchMedicines --args="--in export.json --catalog app/src/main/assets/medicines.tsv --suggest map.tsv"
 * ./gradlew :core:matchMedicines --args="--in export.json --map map.tsv --out renamed.json"
 * ```
 * `renamed.json` är en ändringsfil (`updates`) med bara de ändrade fälten per dokument och läses in med
 * `node tools/db/import.mjs --in renamed.json --update` (`--dry-run` först): andra fält står kvar och ett
 * dokument som raderats sedan exporten hoppas över. En vanlig import vägrar filen.
 * Exitkod 0 = filen skrevs; 1 = kartan eller exporten går inte att läsa, eller ett ändrat fält bryter mot rules;
 * 2 = fel argument, saknad infil eller en utfil som redan finns (skrivs över bara med `--force`).
 */
fun main(args: Array<String>) {
    exitProcess(MatchMedicinesCli.run(args, System.out, System.err))
}

object MatchMedicinesCli {
    const val USAGE = "Användning: --in <export.json> --catalog <medicines.tsv> --suggest <map.tsv> [--force]\n" +
        "        eller: --in <export.json> --map <map.tsv> --out <renamed.json> [--force]\n" +
        "renamed.json innehåller bara ändrade fält och läses in med tools/db/import.mjs --update."

    private val SUGGEST = setOf("--in", "--catalog", "--suggest")
    private val APPLY = setOf("--in", "--map", "--out")
    private const val FORCE = "--force"
    const val EXIT_OK = 0
    const val EXIT_FAILED = 1
    const val EXIT_USAGE = 2

    fun run(args: Array<String>, out: PrintStream, err: PrintStream): Int {
        val parsed = CliArgs.parse(args, SUGGEST + APPLY, setOf(FORCE))?.takeIf { it.values.keys == SUGGEST || it.values.keys == APPLY }
            ?: return usage(err)
        val suggesting = parsed.values.keys == SUGGEST
        val inputs = if (suggesting) listOf("--in", "--catalog") else listOf("--in", "--map")
        inputs.map(parsed::value).firstOrNull { !File(it).isFile }?.let {
            err.println("Fel: filen $it finns inte")
            return EXIT_USAGE
        }
        val output = File(parsed.value(if (suggesting) "--suggest" else "--out"))
        if (output.exists() && FORCE !in parsed.switches) {
            err.println("Fel: ${output.path} finns redan – skriv över med --force")
            return EXIT_USAGE
        }
        return try {
            val export = readExport(File(parsed.value("--in")))
            if (suggesting) suggest(export, File(parsed.value("--catalog")), output, out) else apply(export, File(parsed.value("--map")), output, out)
        } catch (e: IllegalArgumentException) {
            // Meddelandena nämner rad, fält och sökväg – aldrig innehåll.
            err.println("Fel: ${e.message}")
            EXIT_FAILED
        }
    }

    private fun usage(err: PrintStream): Int {
        err.println(USAGE)
        return EXIT_USAGE
    }

    private class Export(val exportedAt: Instant, val schemaVersion: Int, val documents: List<ExportFormat.Document>)

    private fun readExport(file: File): Export {
        val root = try {
            Json.parseToJsonElement(file.readText()).jsonObject
        } catch (e: kotlinx.serialization.SerializationException) {
            throw IllegalArgumentException("exporten är inte giltig JSON (innehållet visas inte)", e)
        } catch (e: IllegalArgumentException) {
            throw IllegalArgumentException("exporten är inte giltig JSON (innehållet visas inte)", e)
        }
        require(ExportFormat.DOCUMENTS in root) { "filen är ingen export från tools/db (documents saknas)" }
        val exportedAt = runCatching { Instant.parse(root.getValue("exportedAt").jsonPrimitive.content) }.getOrNull()
        val version = runCatching { root.getValue(ExportFormat.SCHEMA_VERSION).jsonPrimitive.int }.getOrNull()
        return Export(
            requireNotNull(exportedAt) { "exporten saknar giltig exportedAt" },
            requireNotNull(version) { "exporten saknar giltig schemaVersion" },
            ExportFormat.decode(root),
        )
    }

    private fun suggest(export: Export, catalogFile: File, output: File, out: PrintStream): Int {
        val rows = MedicineRename.suggest(export.documents, MedicineCatalog.parse(catalogFile.readText()))
        output.writeText(MedicineRename.renderMap(rows))
        out.println("Mediciner: ${rows.size}")
        out.println("  med förslag: ${rows.count { it.newName.isNotEmpty() }}")
        out.println("  utan träff: ${rows.count { it.newName.isEmpty() }}")
        out.println("  med omräknad dos: ${rows.count { it.newName.isNotEmpty() && (it.newDose != it.oldDose || it.newUnit != it.oldUnit) }}")
        out.println("Doser som berörs: ${rows.sumOf { it.doses }}")
        out.println("Skrev kartan till ${output.path}")
        return EXIT_OK
    }

    private fun apply(export: Export, mapFile: File, output: File, out: PrintStream): Int {
        val applied = MedicineRename.apply(export.documents, MedicineRename.parseMap(mapFile.readText()))
        output.writeText(ExportFormat.encode(export.exportedAt, export.schemaVersion, applied.documents, ExportFormat.UPDATES))
        out.println("Mediciner ändrade: ${applied.medicines}")
        out.println("Doser med nytt namn eller ny styrka: ${applied.doses}")
        out.println("Rader utan nytt namn: ${applied.skipped}")
        out.println("Inaktuella rader (medicinen saknas eller har ändrats): ${applied.stale}")
        out.println("Recept med orörd dos (går inte att räkna om exakt): ${applied.doseKept}")
        out.println("Doser utan koppling med tvetydigt namn (orörda): ${applied.ambiguousDoses}")
        out.println("Skrev ändringar i ${applied.documents.size} dokument till ${output.path}")
        out.println("Läs in med: node tools/db/import.mjs --in ${output.path} --update --dry-run (sedan utan --dry-run)")
        return EXIT_OK
    }
}
