package se.partee71.dagboken.data.firestore

import com.google.firebase.Timestamp
import kotlin.time.Instant
import se.partee71.dagboken.core.schema.Doc

// Firestores egna typer stannar här: :core ser bara kotlin.time.Instant (skill firestore-data-layer).

/** Före skrivning: [Instant] → [Timestamp], rekursivt i nästlade maps och listor. */
fun toFirestore(doc: Doc): Doc = doc.mapValues { (_, value) -> toFirestoreValue(value) }

/** Efter läsning: [Timestamp] → [Instant], rekursivt. */
fun fromFirestore(doc: Doc): Doc = doc.mapValues { (_, value) -> fromFirestoreValue(value) }

private fun toFirestoreValue(value: Any?): Any? = when (value) {
    is Instant -> Timestamp(value.epochSeconds, value.nanosecondsOfSecond)
    is Map<*, *> -> value.mapValues { toFirestoreValue(it.value) }
    is List<*> -> value.map(::toFirestoreValue)
    else -> value
}

private fun fromFirestoreValue(value: Any?): Any? = when (value) {
    is Timestamp -> Instant.fromEpochSeconds(value.seconds, value.nanoseconds)
    is Map<*, *> -> value.mapValues { fromFirestoreValue(it.value) }
    is List<*> -> value.map(::fromFirestoreValue)
    else -> value
}
