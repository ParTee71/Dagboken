package se.partee71.dagboken.data.legacy

import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import se.partee71.dagboken.core.schema.DocumentRules

/**
 * Liggaren som fil (OMB-7): bara tillägg per batch, W- och V-rader med hash, läst i ordning så att sista raden per
 * sökväg vinner, trasig rad hoppas över, raderas vid bekräftelsen. Hashen inom samlingens fasta fältmängd.
 */
@RunWith(RobolectricTestRunner::class)
class FileMigrationLedgerTest {
    private val context = RuntimeEnvironment.getApplication()
    private val ledger = FileMigrationLedger(context, Dispatchers.Unconfined)
    private val file = File(File(context.filesDir, "legacy-migration"), "uid-a.ledger")

    private companion object {
        val H1 = "a".repeat(64)
        val H2 = "b".repeat(64)
        val W1 = "1".repeat(64)
        val W2 = "2".repeat(64)
        val W3 = "3".repeat(64)
        val WX = "4".repeat(64)
        val OLD = "5".repeat(64)
        val NEW = "6".repeat(64)
        val BAD = "7".repeat(64)
    }

    @Test
    fun `tillägg per batch, sista raden per sökväg vinner, per konto, och raderad vid bekräftelsen`() = runTest {
        assertEquals(Ledger.EMPTY, ledger.read("uid-a"))
        ledger.appendWritten("uid-a", mapOf("doses/d1" to W1, "doses/d2" to W2))
        ledger.appendVerified("uid-a", mapOf("doses/d1" to W1))
        ledger.appendWritten("uid-a", mapOf("options/o1" to W3))
        ledger.appendWritten("uid-b", mapOf("doses/x" to WX))
        assertEquals(Ledger(pending = mapOf("doses/d2" to W2, "options/o1" to W3), verified = mapOf("doses/d1" to W1)), ledger.read("uid-a"))
        assertEquals(Ledger(pending = mapOf("doses/x" to WX), verified = emptyMap()), ledger.read("uid-b"))
        assertEquals(4, file.readLines().size, "en rad per post, bara tillägg")
        ledger.delete("uid-a")
        assertFalse(file.exists())
        assertEquals(Ledger.EMPTY, ledger.read("uid-a"))
        assertEquals(mapOf("doses/x" to WX), ledger.read("uid-b").pending, "ett annat konto rörs inte")
    }

    @Test
    fun `V följd av W och sedan krasch - sökvägen är pending igen med den nya hashen`() = runTest {
        ledger.appendVerified("uid-a", mapOf("doses/d1" to OLD))
        // 3.x nyare: dokumentet skrevs om med kvitto (W med ny hash) men processen dog före verifieringen.
        ledger.appendWritten("uid-a", mapOf("doses/d1" to NEW))
        assertEquals(Ledger(pending = mapOf("doses/d1" to NEW), verified = emptyMap()), ledger.read("uid-a"))
        // Verifierad igen: V vinner.
        ledger.appendVerified("uid-a", mapOf("doses/d1" to NEW))
        assertEquals(Ledger(pending = emptyMap(), verified = mapOf("doses/d1" to NEW)), ledger.read("uid-a"))
    }

    @Test
    fun `P-raden läggs bredvid V eller M utan att ersätta dem, och tas bort av nästa W, V eller M`() = runTest {
        ledger.appendVerified("uid-a", mapOf("doses/d1" to OLD))
        ledger.appendMismatched("uid-a", mapOf("doses/d2" to BAD))
        ledger.appendPlanned("uid-a", mapOf("doses/d1" to NEW, "doses/d2" to NEW, "doses/d3" to NEW))
        assertEquals(
            Ledger(pending = emptyMap(), verified = mapOf("doses/d1" to OLD), mismatched = mapOf("doses/d2" to BAD), planned = mapOf("doses/d1" to NEW, "doses/d2" to NEW, "doses/d3" to NEW)),
            ledger.read("uid-a"),
        )
        ledger.appendWritten("uid-a", mapOf("doses/d1" to NEW))
        ledger.appendVerified("uid-a", mapOf("doses/d2" to NEW))
        ledger.appendMismatched("uid-a", mapOf("doses/d3" to "MISSING"))
        assertEquals(
            Ledger(pending = mapOf("doses/d1" to NEW), verified = mapOf("doses/d2" to NEW), mismatched = mapOf("doses/d3" to "MISSING"), planned = emptyMap()),
            ledger.read("uid-a"),
        )
    }

    @Test
    fun `X-raden stryker bara planen - en V-rad står kvar - och contains följer alla fyra tillstånden`() = runTest {
        ledger.appendPlanned("uid-a", mapOf("doses/d1" to W1, "doses/d2" to W2, "doses/d3" to W3))
        ledger.appendVerified("uid-a", mapOf("doses/d3" to OLD))
        ledger.appendCancelled("uid-a", listOf("doses/d1", "doses/d3"))
        val read = ledger.read("uid-a")
        assertEquals(Ledger(pending = emptyMap(), verified = mapOf("doses/d3" to OLD), planned = mapOf("doses/d2" to W2)), read)
        assertEquals(true, "doses/d2" in read)
        assertEquals(true, "doses/d3" in read)
        assertEquals(false, "doses/d1" in read)
        assertEquals(setOf("doses/d2", "doses/d3"), read.paths)
        assertEquals(true, read.started)
        ledger.appendCancelled("uid-a", listOf("doses/d2", "doses/d3"))
        assertEquals(Ledger(pending = emptyMap(), verified = mapOf("doses/d3" to OLD)), ledger.read("uid-a"))
    }

    @Test
    fun `M-raden gör sökvägen avvikande med serverns hash, och en W- eller V-rad efteråt tar över`() = runTest {
        ledger.appendWritten("uid-a", mapOf("doses/d1" to W1, "doses/d2" to W2))
        ledger.appendMismatched("uid-a", mapOf("doses/d1" to BAD))
        assertEquals(Ledger(pending = mapOf("doses/d2" to W2), verified = emptyMap(), mismatched = mapOf("doses/d1" to BAD)), ledger.read("uid-a"))
        ledger.appendWritten("uid-a", mapOf("doses/d1" to W1))
        ledger.appendVerified("uid-a", mapOf("doses/d1" to W1))
        assertEquals(Ledger(pending = mapOf("doses/d2" to W2), verified = mapOf("doses/d1" to W1)), ledger.read("uid-a"))
        assertEquals(5, file.readLines().size)
    }

    @Test
    fun `en avkortad sista rad hoppas över, nästa tillägg börjar på ny rad, och bara giltiga hashar räknas`() = runTest {
        ledger.appendVerified("uid-a", mapOf("doses/d1" to H1))
        file.appendText("V\tdoses/d2\t${H2.take(40)}")
        assertEquals(Ledger(pending = emptyMap(), verified = mapOf("doses/d1" to H1)), ledger.read("uid-a"), "avkortad hash räknas inte")
        ledger.appendWritten("uid-a", mapOf("doses/d3" to H2))
        assertEquals(Ledger(pending = mapOf("doses/d3" to H2), verified = mapOf("doses/d1" to H1)), ledger.read("uid-a"), "tillägget efter en avkortad rad läses")
        assertEquals(3, file.readLines().size)
        file.appendText("W\tdoses/d4\tinte-hex\nX\tdoses/d5\t$H1\nM\tdoses/d6\tMISSING\nW\t\t$H1\n")
        assertEquals(Ledger(pending = mapOf("doses/d3" to H2), verified = mapOf("doses/d1" to H1), mismatched = mapOf("doses/d6" to "MISSING")), ledger.read("uid-a"))
    }

    @Test
    fun `ett id med tab, radbrytning eller backslash blir en rad och läses tillbaka exakt`() = runTest {
        val odd = "doses/id\tmed\ttab\noch rad\\slash"
        ledger.appendWritten("uid-a", mapOf(odd to W1, "doses/vanlig" to W2))
        ledger.appendVerified("uid-a", mapOf(odd to W1))
        assertEquals(Ledger(pending = mapOf("doses/vanlig" to W2), verified = mapOf(odd to W1)), ledger.read("uid-a"))
        assertEquals(3, file.readLines().size, "en rad per post trots radbrytningen i id:t")
    }

    @Test
    fun `hashen är oberoende av nyckelordning - också i mappar inne i listor`() {
        val tree = DocumentRules.fieldTree("prescriptions")
        val a = mapOf("name" to "x", "boosts" to listOf(mapOf("from" to "2026-01-01", "to" to "2026-01-05", "dose" to "2")), "schedule" to mapOf("kind" to "daily", "days" to listOf(1, 2)))
        val b = mapOf("schedule" to mapOf("days" to listOf(1L, 2L), "kind" to "daily"), "boosts" to listOf(mapOf("dose" to "2", "to" to "2026-01-05", "from" to "2026-01-01")), "name" to "x")
        assertEquals(MigrationLedger.hashOf(a, tree), MigrationLedger.hashOf(b, tree))
        assertEquals(MigrationLedger.hashOf(a, null), MigrationLedger.hashOf(b, null), "även utan träd")
        assertNotEquals(MigrationLedger.hashOf(a, tree), MigrationLedger.hashOf(a + ("boosts" to listOf(mapOf("from" to "2026-01-01", "to" to "2026-01-05", "dose" to "3"))), tree))
        assertEquals(listOf("boosts", "name", "schedule"), MigrationLedger.canonical(a, tree).keys.toList(), "sorterad och begränsad")
    }

    @Test
    fun `hashen gäller bara samlingens fasta fältmängd - extra fält rör den inte, ett saknat fält ändrar den, tal normaliseras`() {
        val tree = DocumentRules.fieldTree("doses")
        val dose = mapOf("name" to "x", "status" to "taken", "note" to "n", "date" to "2026-01-01")
        assertEquals(MigrationLedger.hashOf(dose, tree), MigrationLedger.hashOf(dose + ("framtida" to true), tree), "fält utanför mängden räknas inte")
        assertEquals(MigrationLedger.hashOf(dose, tree), MigrationLedger.hashOf(dose.entries.reversed().associate { it.key to it.value }, tree), "nyckelordningen spelar ingen roll")
        assertNotEquals(MigrationLedger.hashOf(dose, tree), MigrationLedger.hashOf(dose - "note", tree), "ett tömt fält är en ändring")
        assertNotEquals(MigrationLedger.hashOf(dose, tree), MigrationLedger.hashOf(dose + ("note" to "m"), tree))
        val settings = DocumentRules.fieldTree("settings")
        val a = mapOf("theme" to mapOf("lightStartHour" to 7), "profile" to mapOf("birthYear" to 1985))
        val b = mapOf("theme" to mapOf("lightStartHour" to 7L), "profile" to mapOf("birthYear" to 1985L, "framtida" to 1))
        assertEquals(MigrationLedger.hashOf(a, settings), MigrationLedger.hashOf(b, settings), "Long och Int är samma tal; nästlade extra nycklar räknas inte")
        assertNotEquals(MigrationLedger.hashOf(a, settings), MigrationLedger.hashOf(mapOf("theme" to mapOf("lightStartHour" to 7)), settings), "en nästlad grupp som saknas är en ändring")
    }
}
