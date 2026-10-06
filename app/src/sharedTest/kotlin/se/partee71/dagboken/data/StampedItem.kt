package se.partee71.dagboken.data

import kotlin.time.Instant
import se.partee71.dagboken.core.model.Identified
import se.partee71.dagboken.core.schema.DocCodec
import se.partee71.dagboken.core.schema.instant
import se.partee71.dagboken.core.schema.string

/**
 * Testmodellen med `updatedAt` – bara för att `CollectionContract` ska bevisa regeln för fältet mot
 * riktig Firestore, eftersom ingen av appens modeller har det ännu. Formad som `options` och lagrad
 * där (rules godtar den), med `createdAt` och `updatedAt` som `prepareForWrite` behandlar särskilt.
 * Får en riktig modell `updatedAt` byts den här mot den. Ett dokument med namnet [StampedItemCodec.UNREADABLE]
 * går inte att avkoda – så prövas att ett oläsbart dokument inte fäller en villkorad skrivning.
 */
data class StampedItem(
    override val id: String,
    val name: String = "",
    val kind: String = "activity",
    val createdAt: Instant? = null,
    val updatedAt: Instant? = null,
) : Identified

object StampedItemCodec : DocCodec<StampedItem> {
    const val NAME = "name"
    const val KIND = "kind"

    /** Namnet som gör dokumentet oläsbart (som data från en app som inte finns ännu). */
    const val UNREADABLE = "OLÄSBAR"

    override fun encode(value: StampedItem): Map<String, Any?> = mapOf(
        NAME to value.name,
        KIND to value.kind,
        "createdAt" to value.createdAt,
        "updatedAt" to value.updatedAt,
    )

    override fun decode(id: String, map: Map<String, Any?>): StampedItem = if (map[NAME] == UNREADABLE) error("Oläsbart testdokument") else StampedItem(
        id = id,
        name = map.string(NAME),
        kind = map.string(KIND, "activity"),
        createdAt = map.instant("createdAt"),
        updatedAt = map.instant("updatedAt"),
    )
}
