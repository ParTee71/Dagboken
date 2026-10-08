package se.partee71.dagboken.data.legacy

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.RandomAccessFile
import java.security.MessageDigest
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import se.partee71.dagboken.core.schema.Doc
import se.partee71.dagboken.core.schema.DocumentRules
import se.partee71.dagboken.core.schema.ExportFormat
import se.partee71.dagboken.core.schema.asDoc
import se.partee71.dagboken.di.IoDispatcher

/**
 * Vad migreringen gjort för ett konto (OMB-7), sökvägar under `users/{uid}/` med en hash ([MigrationLedger.hashOf]):
 * [pending] – skrivna med kvitto, inte verifierade (hashen över det vi skrev); [verified] – verifierade (samma);
 * [mismatched] – avvikande i verifieringen (hashen över serverns felaktiga läge då, eller [MigrationLedger.MISSING] när
 * dokumentet saknades); [planned] – intentionen före batchens commit (hashen över det som skulle skrivas). Den sista
 * W-, V- eller M-raden för en sökväg gäller och tar bort en P-rad; en P-rad ersätter **inte** det tidigare tillståndet,
 * så en skrivning som inte landade lämnar V/M kvar att döma efter.
 */
data class Ledger(
    val pending: Map<String, String>,
    val verified: Map<String, String>,
    val mismatched: Map<String, String> = emptyMap(),
    val planned: Map<String, String> = emptyMap(),
) {
    /** Flytten har börjat: något är antecknat (P/W/V/M) – då finns dokument i kontot som migreringen skrivit eller tänkt skriva. */
    val started: Boolean get() = pending.isNotEmpty() || verified.isNotEmpty() || mismatched.isNotEmpty() || planned.isNotEmpty()

    /** Sökvägen är antecknad (P/W/V/M) – flytten har skrivit eller planerat den. */
    operator fun contains(path: String): Boolean = path in pending || path in verified || path in mismatched || path in planned

    /** Varje antecknad sökväg (utan ordning: planerade, skrivna, verifierade, avvikande) – det "Avbryt flytten" tar bort. */
    val paths: Set<String> get() = buildSet {
        addAll(planned.keys)
        addAll(pending.keys)
        addAll(verified.keys)
        addAll(mismatched.keys)
    }

    companion object {
        val EMPTY = Ledger(emptyMap(), emptyMap())
    }
}

/**
 * Migreringens liggare per konto – en egen appprivat fil som bara läggs till per batch, läses en gång per körning och
 * raderas när migreringen bekräftats. Inte DataStore: en växande mängd skulle skrivas om i sin helhet vid varje batch.
 * Ligger i samma privata lagring som Room-filen och delar öde med den. IO-fel kastas; anroparen gör dem till `Failed`.
 */
interface MigrationLedger {
    suspend fun read(uid: String): Ledger

    /** Planerat, **före** batchens commit: sökväg → hash över det som ska skrivas. Ersätter inte en tidigare V-/M-rad. */
    suspend fun appendPlanned(uid: String, hashes: Map<String, String>)

    /** Skrivna med serverns kvitto, ännu inte verifierade: sökväg → hash över det som skrevs. */
    suspend fun appendWritten(uid: String, hashes: Map<String, String>)

    /** Verifierade: sökväg → hash över det som ligger på servern inom samlingens fasta nyckelmängd. */
    suspend fun appendVerified(uid: String, hashes: Map<String, String>)

    /** Avvikande i verifieringen: sökväg → hash över serverns (felaktiga) läge då, eller [MISSING] – "Försök igen" skriver om så länge det läget står kvar. */
    suspend fun appendMismatched(uid: String, hashes: Map<String, String>)

    /** Stryker P-raderna (X-rad): en batch som servern avvisade definitivt har inte skrivit dem; en tidigare V-/M-rad (dokumentet är redan flyttens) står kvar. */
    suspend fun appendCancelled(uid: String, paths: Collection<String>)

    suspend fun delete(uid: String)

    companion object {
        /** M-radens "hash" när dokumentet saknades på servern vid verifieringen. */
        const val MISSING = "MISSING"

        /**
         * **Den enda** likheten i migreringen: hashen över [canonical] – samma dokument ger samma hash oavsett nyckelordning
         * (även i listelement) och fält utanför [tree]; ett fält som försvinner ändrar den. Tal och tidsstämplar
         * normaliseras av exportformatet. [tree] = `null` jämför hela dokumentet (kopian).
         */
        fun hashOf(doc: Doc, tree: DocumentRules.FieldTree?): String =
            hex(MessageDigest.getInstance("SHA-256").digest(ExportFormat.toJson(canonical(doc, tree)).toString().toByteArray(Charsets.UTF_8)))

        private val HEX = "0123456789abcdef".toCharArray()

        /** Små hexsiffror utan formatering per byte – tusentals hashar per körning. */
        private fun hex(bytes: ByteArray): String {
            val out = CharArray(bytes.size * 2)
            for ((i, b) in bytes.withIndex()) {
                val v = b.toInt() and 0xff
                out[i * 2] = HEX[v ushr 4]
                out[i * 2 + 1] = HEX[v and 0x0f]
            }
            return String(out)
        }

        /**
         * Den kanoniska formen: nycklarna sorterade rekursivt – också i mappar inne i listor (symptom, doshöjningar,
         * påminnelserader), som Firestore annars ger i godtycklig ordning – och, där [tree] finns, begränsade till
         * trädets nycklar (nästlat där trädet är det; listelement och fält utan underträd sorteras obegränsat).
         */
        fun canonical(doc: Doc, tree: DocumentRules.FieldTree?): Doc {
            val keys = (tree?.fields?.keys ?: doc.keys).filter { doc.containsKey(it) }.sorted()
            return keys.associateWith { key -> canonicalValue(doc[key], tree?.fields?.get(key)) }
        }

        private fun canonicalValue(value: Any?, tree: DocumentRules.FieldTree?): Any? = when (value) {
            is Map<*, *> -> canonical(asDoc(value), tree)
            is List<*> -> value.map { canonicalValue(it, null) }
            else -> value
        }
    }
}

/**
 * Textfil `files/legacy-migration/<uid>.ledger`, en post per rad: `P<TAB>sökväg<TAB>hash` (planerat, före commit),
 * `W<TAB>sökväg<TAB>hash`, `V<TAB>sökväg<TAB>hash`, `M<TAB>sökväg<TAB>hash|MISSING` respektive `X<TAB>sökväg<TAB>MISSING`
 * (planen struken), läst i ordning (sista W/V/M-raden per sökväg vinner; P läggs bredvid och tas bort av nästa W/V/M
 * eller X). Bara rader med giltigt
 * format räknas (hashen 64 hex eller `MISSING`); en avkortad rad hoppas över, och nästa tillägg börjar med en radbrytning
 * om filen inte slutar med en. Filen läggs bara till.
 */
class FileMigrationLedger @Inject constructor(
    @ApplicationContext private val context: Context,
    @IoDispatcher private val dispatcher: CoroutineDispatcher,
) : MigrationLedger {
    private fun file(uid: String) = File(File(context.filesDir, DIR), "$uid.ledger")

    override suspend fun read(uid: String): Ledger = withContext(dispatcher) {
        val f = file(uid)
        if (!f.exists()) return@withContext Ledger.EMPTY
        val pending = linkedMapOf<String, String>()
        val verified = linkedMapOf<String, String>()
        val mismatched = linkedMapOf<String, String>()
        val planned = linkedMapOf<String, String>()
        val states = listOf(pending, verified, mismatched)
        f.useLines { lines ->
            for (line in lines) {
                val parts = line.split('\t')
                if (parts.size != 3 || parts[1].isEmpty() || !validHash(parts[2])) continue
                val path = unescape(parts[1])
                when (parts[0]) {
                    PLANNED -> planned[path] = parts[2]
                    CANCELLED -> planned.remove(path)
                    else -> {
                        val target = when (parts[0]) {
                            WRITTEN -> pending
                            VERIFIED -> verified
                            MISMATCHED -> mismatched
                            else -> continue
                        }
                        states.forEach { it.remove(path) }
                        planned.remove(path)
                        target[path] = parts[2]
                    }
                }
            }
        }
        Ledger(pending, verified, mismatched, planned)
    }

    override suspend fun appendPlanned(uid: String, hashes: Map<String, String>) = append(uid, PLANNED, hashes)

    override suspend fun appendWritten(uid: String, hashes: Map<String, String>) = append(uid, WRITTEN, hashes)

    override suspend fun appendVerified(uid: String, hashes: Map<String, String>) = append(uid, VERIFIED, hashes)

    override suspend fun appendMismatched(uid: String, hashes: Map<String, String>) = append(uid, MISMATCHED, hashes)

    override suspend fun appendCancelled(uid: String, paths: Collection<String>) = append(uid, CANCELLED, paths.associateWith { MigrationLedger.MISSING })

    private suspend fun append(uid: String, kind: String, hashes: Map<String, String>) = append(uid, hashes.map { (path, hash) -> "$kind\t${escape(path)}\t$hash" })

    /** Ett id från 3.x kan innehålla vad som helst: tab, radbrytning och backslash escapas så att raden förblir en rad. */
    private fun escape(path: String): String = buildString(path.length) {
        for (c in path) when (c) {
            '\\' -> append("\\\\")
            '\t' -> append("\\t")
            '\n' -> append("\\n")
            '\r' -> append("\\r")
            else -> append(c)
        }
    }

    private fun unescape(text: String): String = buildString(text.length) {
        var i = 0
        while (i < text.length) {
            val c = text[i]
            if (c == '\\' && i + 1 < text.length) {
                when (text[i + 1]) {
                    't' -> append('\t')
                    'n' -> append('\n')
                    'r' -> append('\r')
                    else -> append(text[i + 1])
                }
                i += 2
            } else {
                append(c)
                i++
            }
        }
    }

    override suspend fun delete(uid: String) = withContext(dispatcher) {
        file(uid).delete()
        Unit
    }

    private suspend fun append(uid: String, lines: List<String>) = withContext(dispatcher) {
        if (lines.isEmpty()) return@withContext
        val f = file(uid)
        f.parentFile?.mkdirs()
        val lead = if (f.exists() && f.length() > 0 && !endsWithNewline(f)) "\n" else ""
        f.appendText(lines.joinToString("\n", prefix = lead, postfix = "\n"), Charsets.UTF_8)
    }

    private fun endsWithNewline(f: File): Boolean = RandomAccessFile(f, "r").use {
        it.seek(it.length() - 1)
        it.read() == '\n'.code
    }

    private fun validHash(hash: String): Boolean = hash == MigrationLedger.MISSING || HASH.matches(hash)

    private companion object {
        const val DIR = "legacy-migration"
        const val PLANNED = "P"
        const val WRITTEN = "W"
        const val VERIFIED = "V"
        const val MISMATCHED = "M"
        const val CANCELLED = "X"
        val HASH = Regex("[0-9a-f]{64}")
    }
}
