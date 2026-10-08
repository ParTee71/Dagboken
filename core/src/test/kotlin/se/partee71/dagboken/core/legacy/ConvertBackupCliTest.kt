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
        val output = File(folder.root, "export.json")
        val run = run("--in", fixture, "--out", output.path, "--user", LegacyFixtures.UID)
        assertEquals(ConvertBackupCli.EXIT_OK, run.exit, run.err)
        assertEquals(LegacyFixtures.expectedText("backup-v2"), output.readText())
        assertTrue("Dokument per samling:" in run.out && "Varningar: 13" in run.out && "Skrev 35 dokument" in run.out, run.out)
        assertEquals("", run.err)
    }

    @Test
    fun `stopp ger exitkod 1, rapporten på stdout och ingen fil`() {
        val input = folder.newFile("stopp.json").apply { writeText("""{"version": 2, "mediciner": [{"id": "m1", "tidpunkt": "Brunch", "anteckning": "HEMLIGT"}]}""") }
        val output = File(folder.root, "export.json")
        val run = run("--in", input.path, "--out", output.path, "--user", "u")
        assertEquals(ConvertBackupCli.EXIT_STOPPED, run.exit)
        assertFalse(output.exists())
        assertTrue("Stopp: 1 fel" in run.out && "doses/m1 · tidpunkt: okänd tidpunkt okänt värde (6 tecken)" in run.out, run.out)
        assertFalse("HEMLIGT" in run.out + run.err)
        assertFalse("Brunch" in run.out + run.err, "råvärdet skrivs inte")
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

    @Test
    fun `ett uid som inte duger som dokument-id vägras`() {
        for (uid in listOf("", "a/b", "..", "__x__")) {
            val run = run("--in", fixture, "--out", File(folder.root, "x.json").path, "--user", uid)
            assertEquals(ConvertBackupCli.EXIT_USAGE, run.exit, uid)
            assertTrue("--user är inte ett giltigt dokument-id" in run.err, run.err)
        }
        assertFalse(File(folder.root, "x.json").exists())
    }

    @Test
    fun `en befintlig utfil skrivs bara över med --force`() {
        val output = folder.newFile("export.json").apply { writeText("gammalt innehåll") }
        val refused = run("--in", fixture, "--out", output.path, "--user", LegacyFixtures.UID)
        assertEquals(ConvertBackupCli.EXIT_USAGE, refused.exit)
        assertTrue("finns redan" in refused.err && "--force" in refused.err, refused.err)
        assertEquals("gammalt innehåll", output.readText())
        val forced = run("--force", "--in", fixture, "--out", output.path, "--user", LegacyFixtures.UID)
        assertEquals(ConvertBackupCli.EXIT_OK, forced.exit, forced.err)
        assertEquals(LegacyFixtures.expectedText("backup-v2"), output.readText())
        assertEquals(ConvertBackupCli.EXIT_USAGE, run("--force", "--force", "--in", fixture, "--out", output.path, "--user", "u").exit, "dubbel flagga")
    }
}
