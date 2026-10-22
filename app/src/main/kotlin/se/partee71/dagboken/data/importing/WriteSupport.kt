package se.partee71.dagboken.data.importing

import java.security.MessageDigest
import se.partee71.dagboken.core.schema.Doc
import se.partee71.dagboken.core.schema.ExportFormat
import se.partee71.dagboken.core.schema.asDoc
import se.partee71.dagboken.data.common.DataError
import se.partee71.dagboken.data.common.suspendRunCatching
import se.partee71.dagboken.data.firestore.firestoreError

// Det importen (BCK-6) behöver när råa dokument skrivs och verifieras mot servern: räkningen per entitet,
// likheten för ett ifyllt dokument, felkoden och felhanteringen.

/** Det lagrade dokumentet begränsat till [template]s nycklar, rekursivt – det som ska hasha lika med det som skrevs med merge. */
internal fun project(stored: Doc, template: Doc): Doc = template.mapNotNull { (field, value) ->
    if (!stored.containsKey(field)) return@mapNotNull null
    val got = stored[field]
    field to if (value is Map<*, *> && got is Map<*, *>) project(asDoc(got), asDoc(value)) else got
}.toMap()

/**
 * Om [written] (skrivet med merge) finns i [stored] på servern: varje skrivet fält har samma värde, i den kanoniska
 * formen ([hashOf]); fält bara på servern rör inte svaret.
 */
internal fun landed(stored: Doc, written: Doc): Boolean = hashOf(project(stored, written)) == hashOf(written)

/**
 * **Den enda** likheten i importen: SHA-256 över den kanoniska formen – samma dokument ger samma hash oavsett
 * nyckelordning (även i mappar inne i listor: symptom, doshöjningar, påminnelserader, som Firestore annars ger i
 * godtycklig ordning); ett fält som försvinner ändrar den. Tal och tidsstämplar normaliseras av exportformatet.
 */
internal fun hashOf(doc: Doc): String = hex(MessageDigest.getInstance("SHA-256").digest(ExportFormat.toJson(canonical(doc)).toString().toByteArray(Charsets.UTF_8)))

private val HEX = "0123456789abcdef".toCharArray()

/** Små hexsiffror utan formatering per byte – tusentals hashar per import. */
private fun hex(bytes: ByteArray): String {
    val out = CharArray(bytes.size * 2)
    for ((i, b) in bytes.withIndex()) {
        val v = b.toInt() and 0xff
        out[i * 2] = HEX[v ushr 4]
        out[i * 2 + 1] = HEX[v and 0x0f]
    }
    return String(out)
}

/** Nycklarna sorterade rekursivt, också i mappar inne i listor. */
private fun canonical(doc: Doc): Doc = doc.keys.sorted().associateWith { canonicalValue(doc[it]) }

private fun canonicalValue(value: Any?): Any? = when (value) {
    is Map<*, *> -> canonical(asDoc(value))
    is List<*> -> value.map(::canonicalValue)
    else -> value
}

/** Felkoden som visas med ett fel i skrivningen (`PERMISSION_DENIED`, `RESOURCE_EXHAUSTED`, `UNAVAILABLE` …) – aldrig innehåll. */
internal fun codeOf(error: DataError): String = when (error) {
    DataError.PermissionDenied -> "PERMISSION_DENIED"
    DataError.QuotaExceeded -> "RESOURCE_EXHAUSTED"
    DataError.Offline -> "UNAVAILABLE"
    DataError.NotSignedIn -> "UNAUTHENTICATED"
    DataError.UpdateRequired -> "UPDATE_REQUIRED"
    DataError.Cancelled -> "CANCELLED"
    DataError.NotFound -> "NOT_FOUND"
    DataError.SignInRejected, DataError.Unknown -> "UNKNOWN"
}

/** Antal per entitet under skrivningen, i "före"-ordningen. */
internal class Tally(private val before: Map<String, Int>) {
    /** Lika på servern redan före skrivningen. */
    val after = Counter()

    /** Skrivna i den här körningen och verifierade lika – separat från [written], så att inget räknas två gånger. */
    val afterWritten = Counter()
    val written = Counter()
    val mismatched = Counter()

    /** Lika på servern per entitet, i "före"-ordningen med 0 där inget är lika. */
    fun after() = before.keys.associateWith { after[it] + afterWritten[it] }

    /** Det som var klart när skrivningen stannade: lika på servern och skrivet med kvitto. */
    fun doneSoFar() = before.keys.associateWith { after[it] + written[it] }

    fun mismatched() = mismatched.sparse()

    /** Klara per entitet: lika och skrivna hittills. */
    fun progress(): Map<String, Int> = before.keys.associateWith { after[it] + written[it] }

    /** Bara entiteter med något att rapportera, i "före"-ordningen – tom map = inget. */
    private fun Counter.sparse(): Map<String, Int> = before.keys.filter { this[it] > 0 }.associateWith { this[it] }
}

internal class Counter {
    private val counts = linkedMapOf<String, Int>()

    operator fun get(key: String): Int = counts[key] ?: 0

    fun add(key: String) {
        counts.merge(key, 1, Int::plus)
    }

    fun addAll(keys: Collection<String>) = keys.forEach(::add)

    fun isEmpty() = counts.isEmpty()
}

/** En läsning från servern med Firestores fel mappade (`Offline`, `PermissionDenied` …). */
internal suspend fun <T> fromServer(block: suspend () -> T): Result<T> = suspendRunCatching(::firestoreError) { block() }
