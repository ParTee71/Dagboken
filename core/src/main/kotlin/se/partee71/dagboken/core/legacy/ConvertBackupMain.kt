package se.partee71.dagboken.core.legacy

import java.io.File
import java.io.PrintStream
import se.partee71.dagboken.core.schema.DocumentRules
import kotlin.system.exitProcess

/**
 * Grinden OMB-4 från kommandoraden: en 3.x-backupfil → en fil i `tools/db export`-format som
 * `node tools/db/import.mjs --in <fil> --dry-run` godtar. Rapporten (antal, varningar, stopp) skrivs
 * till stdout utan något innehåll ur backupen.
 *
 * ```
 * ./gradlew :core:convertLegacyBackup --args="--in <3.x-backup.json> --out <export.json> --user <uid> [--force]"
 * ```
 * Exitkod 0 = filen skrevs; 1 = stopp (rapporten säger varför, ingen fil skrivs); 2 = fel argument, ogiltigt
 * uid, saknad infil eller en utfil som redan finns (skrivs över bara med `--force`).
 */
fun main(args: Array<String>) {
    exitProcess(ConvertBackupCli.run(args, System.out, System.err))
}

object ConvertBackupCli {
    const val USAGE = "Användning: --in <3.x-backup.json> --out <export.json> --user <uid> [--force]"

    fun run(args: Array<String>, out: PrintStream, err: PrintStream): Int {
        val options = parse(args) ?: run {
            err.println(USAGE)
            return EXIT_USAGE
        }
        if (!DocumentRules.isValidId(options.uid)) {
            err.println("Fel: --user är inte ett giltigt dokument-id (${options.uid.length} tecken)")
            return EXIT_USAGE
        }
        val input = File(options.input)
        if (!input.isFile) {
            err.println("Fel: filen ${options.input} finns inte")
            return EXIT_USAGE
        }
        val output = File(options.output)
        if (output.exists() && !options.force) {
            err.println("Fel: ${options.output} finns redan – skriv över med --force")
            return EXIT_USAGE
        }
        val backup = try {
            BackupJson.parse(input.readText())
        } catch (e: IllegalArgumentException) {
            err.println("Fel: ${e.message}")
            return EXIT_STOPPED
        }
        val result = BackupJsonConverter.convert(backup, options.uid)
        out.print(result.report.render())
        return when (result) {
            is ConversionResult.Stopped -> EXIT_STOPPED
            is ConversionResult.Converted -> {
                output.writeText(result.exportJson(BackupJsonConverter.exportedAt(backup)))
                out.println("Skrev ${result.documents.size} dokument till ${options.output}")
                EXIT_OK
            }
        }
    }

    private class Options(val input: String, val output: String, val uid: String, val force: Boolean)

    /** `--in`, `--out`, `--user` obligatoriska, `--force` valfri; inget annat godtas. */
    private fun parse(args: Array<String>): Options? {
        val values = mutableMapOf<String, String>()
        var force = false
        var i = 0
        while (i < args.size) {
            if (args[i] == FORCE) {
                if (force) return null
                force = true
                i += 1
                continue
            }
            val flag = args[i].takeIf { it in FLAGS } ?: return null
            val value = args.getOrNull(i + 1)?.takeUnless { it.startsWith("--") } ?: return null
            if (values.put(flag, value) != null) return null
            i += 2
        }
        if (values.keys != FLAGS) return null
        return Options(values.getValue("--in"), values.getValue("--out"), values.getValue("--user"), force)
    }

    private val FLAGS = setOf("--in", "--out", "--user")
    private const val FORCE = "--force"
    const val EXIT_OK = 0
    const val EXIT_STOPPED = 1
    const val EXIT_USAGE = 2
}
