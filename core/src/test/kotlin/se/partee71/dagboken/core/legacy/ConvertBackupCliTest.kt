package se.partee71.dagboken.core.legacy

import java.io.ByteArrayOutputStream
import java.io.File
import java.io.PrintStream
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** Kommandoraden för grinden OMB-4: skriver exportfilen eller stoppar med rapporten, utan innehåll i utskriften. */
class ConvertBackupCliTest {

    @get:Rule
    val folder = TemporaryFolder()

    private class Run(val exit: Int, val out: String, val err: String)

    private fun run(vararg args: String): Run {
        val out = ByteArrayOutputStream()
        val err = ByteArrayOutputStream()
        val exit = ConvertBackupCli.run(arrayOf(*args), PrintStream(out, true, Charsets.UTF_8), PrintStream(err, true, Charsets.UTF_8))
        return Run(exit, out.toString(Charsets.UTF_8), err.toString(Charsets.UTF_8))
    }

    private val fixture = File("../tools/db/test/fixtures/legacy/backup-v2.json").path

    @Test
    fun `konverterar fixturen till exakt den förväntade exportfilen och skriver rapporten`() {
        val output = folder.newFile("export.json")
        val run = run("--in", fixture, "--out", output.path, "--user", LegacyFixtures.UID)
        assertEquals(ConvertBackupCli.EXIT_OK, run.exit, run.err)
        assertEquals(LegacyFixtures.expectedText("backup-v2"), output.readText())
        assertTrue("Dokument per samling:" in run.out && "Varningar: 6" in run.out && "Skrev 33 dokument" in run.out, run.out)
        assertEquals("", run.err)
    }

    @Test
    fun `stopp ger exitkod 1, rapporten på stdout och ingen fil`() {
        val input = folder.newFile("stopp.json").apply { writeText("""{"version": 2, "mediciner": [{"id": "m1", "tidpunkt": "Brunch", "anteckning": "HEMLIGT"}]}""") }
        val output = File(folder.root, "export.json")
        val run = run("--in", input.path, "--out", output.path, "--user", "u")
        assertEquals(ConvertBackupCli.EXIT_STOPPED, run.exit)
        assertFalse(output.exists())
        assertTrue("Stopp: 1 fel" in run.out && "doses/m1 · tidpunkt: okänd tidpunkt: Brunch" in run.out, run.out)
        assertFalse("HEMLIGT" in run.out + run.err)
    }

    @Test
    fun `en fil som inte är en backup ger exitkod 1 utan att visa innehållet`() {
        val input = folder.newFile("trasig.json").apply { writeText("HEMLIGT inte json") }
        val run = run("--in", input.path, "--out", File(folder.root, "x.json").path, "--user", "u")
        assertEquals(ConvertBackupCli.EXIT_STOPPED, run.exit)
        assertTrue(run.err.startsWith("Fel: Filen är inte en 3.x-backup"), run.err)
        assertFalse("HEMLIGT" in run.err)
    }

    @Test
    fun `fel argument eller saknad fil ger exitkod 2 och användningen`() {
        assertEquals(ConvertBackupCli.EXIT_USAGE, run().exit)
        assertEquals(ConvertBackupCli.EXIT_USAGE, run("--in", fixture).exit)
        assertEquals(ConvertBackupCli.EXIT_USAGE, run("--in", fixture, "--out", "x", "--user", "u", "--extra", "1").exit)
        assertEquals(ConvertBackupCli.EXIT_USAGE, run("--in", fixture, "--out", "--user", "u").exit)
        assertTrue(run("--in").err.contains(ConvertBackupCli.USAGE))
        val missing = run("--in", File(folder.root, "finns-inte.json").path, "--out", "x", "--user", "u")
        assertEquals(ConvertBackupCli.EXIT_USAGE, missing.exit)
        assertTrue("finns inte" in missing.err)
    }
}
