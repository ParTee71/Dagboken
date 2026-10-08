package se.partee71.dagboken.data.legacy

import kotlin.coroutines.cancellation.CancellationException
import se.partee71.dagboken.core.schema.Doc
import se.partee71.dagboken.core.schema.asDoc
import se.partee71.dagboken.data.common.DataError
import se.partee71.dagboken.data.common.suspendRunCatching
import se.partee71.dagboken.data.firestore.firestoreError

// Det migreringen (OMB-2, OMB-7) och importen (BCK-14, OMB-5) delar när råa dokument skrivs och verifieras mot servern:
// räkningen per entitet, likheten för ett ifyllt dokument och felhanteringen. En implementation, två användare.

/** Det lagrade dokumentet begränsat till [template]s nycklar, rekursivt – det som ska hasha lika med det som skrevs med merge. */
internal fun project(stored: Doc, template: Doc): Doc = template.mapNotNull { (field, value) ->
    if (!stored.containsKey(field)) return@mapNotNull null
    val got = stored[field]
    field to if (value is Map<*, *> && got is Map<*, *>) project(asDoc(got), asDoc(value)) else got
}.toMap()

/**
 * Om [written] (skrivet med merge) finns i [stored] på servern: varje skrivet fält har samma värde, i den kanoniska
 * formen ([MigrationLedger.hashOf]); fält bara på servern rör inte svaret.
 */
internal fun landed(stored: Doc, written: Doc): Boolean =
    MigrationLedger.hashOf(project(stored, written), null) == MigrationLedger.hashOf(written, null)

/** Antal per entitet under skrivningen, i "före"-ordningen. */
internal class Tally(private val before: Map<String, Int>) {
    /** Lika på servern redan före skrivningen. */
    val after = Counter()

    /** Skrivna i den här körningen och verifierade lika – separat från [written], så att inget räknas två gånger. */
    val afterWritten = Counter()
    val existing = Counter()
    val written = Counter()
    val mismatched = Counter()

    /** Lika på servern per entitet, i "före"-ordningen med 0 där inget är lika. */
    fun after() = before.keys.associateWith { after[it] + afterWritten[it] }

    /** Det som var klart när skrivningen stannade: lika på servern och skrivet med kvitto. */
    fun doneSoFar() = before.keys.associateWith { after[it] + written[it] }

    fun existing() = existing.sparse()

    fun mismatched() = mismatched.sparse()

    /** Klara per entitet: lika, befintliga och skrivna hittills. */
    fun progress(): Map<String, Int> = before.keys.associateWith { after[it] + existing[it] + written[it] }

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

internal fun Throwable.asDataError(): DataError = this as? DataError ?: DataError.Unknown

/** Lokala fel (fil, DataStore, liggaren) blir `null`; ett avbrott släpps igenom. */
internal inline fun <T> readOrNull(block: () -> T): T? = try {
    block()
} catch (e: CancellationException) {
    throw e
} catch (_: Exception) {
    null
}
