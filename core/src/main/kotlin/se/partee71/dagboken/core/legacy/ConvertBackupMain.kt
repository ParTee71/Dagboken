package se.partee71.dagboken.core.legacy

import java.io.File
import java.io.PrintStream
import kotlin.system.exitProcess

/**
 * Grinden OMB-4 från kommandoraden: en 3.x-backupfil → en fil i `tools/db export`-format som
 * `node tools/db/import.mjs --in <fil> --dry-run` godtar. Rapporten (antal, varningar, stopp) skrivs
 * till stdout utan något innehåll ur backupen.
 *
 * ```
 * ./gradlew :core:convertLegacyBackup --args="--in <3.x-backup.json> --out <export.json> --user <uid>"
 * ```
 * Exitkod 0 = filen skrevs; 1 = stopp (rapporten säger varför, ingen fil skrivs); 2 = fel argument.
 */
fun main(args: Array<String>) {
    exitProcess(ConvertBackupCli.run(args, System.out, System.err))
}

object ConvertBackupCli {
    const val USAGE = "Användning: --in <3.x-backup.json> --out <export.json> --user <uid>"

    fun run(args: Array<String>, out: PrintStream, err: PrintStream): Int {
        val options = parse(args) ?: run {
            err.println(USAGE)
            return EXIT_USAGE
        }
        val input = File(options.input)
        if (!input.isFile) {
            err.println("Fel: filen ${options.input} finns inte")
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
                File(options.output).writeText(result.exportJson(BackupJsonConverter.exportedAt(backup)))
                out.println("Skrev ${result.documents.size} dokument till ${options.output}")
                EXIT_OK
            }
        }
    }

    private class Options(val input: String, val output: String, val uid: String)

    /** `--in`, `--out`, `--user`, alla obligatoriska; inget annat godtas. */
    private fun parse(args: Array<String>): Options? {
        val values = mutableMapOf<String, String>()
        var i = 0
        while (i < args.size) {
            val flag = args[i].takeIf { it in FLAGS } ?: return null
            val value = args.getOrNull(i + 1)?.takeUnless { it.startsWith("--") } ?: return null
            if (values.put(flag, value) != null) return null
            i += 2
        }
        if (values.keys != FLAGS) return null
        return Options(values.getValue("--in"), values.getValue("--out"), values.getValue("--user"))
    }

    private val FLAGS = setOf("--in", "--out", "--user")
    const val EXIT_OK = 0
    const val EXIT_STOPPED = 1
    const val EXIT_USAGE = 2
}
