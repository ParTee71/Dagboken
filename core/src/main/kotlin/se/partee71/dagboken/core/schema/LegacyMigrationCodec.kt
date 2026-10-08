package se.partee71.dagboken.core.schema

import se.partee71.dagboken.core.model.LegacyMigration
import se.partee71.dagboken.core.model.LegacySource

/**
 * `users/{uid}.legacyMigration` (OMB-2): nästlat objekt på användardokumentet, byggt av fälthjälparna.
 * `completedAt` sätts av servern (`FieldValue.serverTimestamp()` i `data/firestore`) – codecen skriver
 * värdet den har, `null` innan markören finns.
 */
object LegacyMigrationCodec : ValueCodec<LegacyMigration?> {
    /** Fältet på användardokumentet. */
    const val FIELD = "legacyMigration"
    const val COMPLETED_AT = "completedAt"
    const val SOURCE = "source"
    const val SOURCE_CREATED_AT = "sourceCreatedAt"
    const val APP_VERSION = "appVersion"
    const val COUNTS = "counts"

    override fun encode(value: LegacyMigration?): Any? = value?.let {
        mapOf(
            COMPLETED_AT to it.completedAt,
            SOURCE to it.source.encodeWire(),
            SOURCE_CREATED_AT to it.sourceCreatedAt,
            APP_VERSION to it.appVersion,
            COUNTS to it.counts,
        )
    }

    /** `null` eller något som inte är ett objekt = ingen markör. */
    override fun decode(raw: Any?): LegacyMigration? {
        if (raw !is Map<*, *>) return null
        val doc = asDoc(raw)
        return LegacyMigration(
            completedAt = doc.instant(COMPLETED_AT),
            source = doc.wire(SOURCE, LegacySource.ROOM),
            sourceCreatedAt = doc.instant(SOURCE_CREATED_AT),
            appVersion = doc.string(APP_VERSION),
            counts = asDoc(doc[COUNTS]).mapNotNull { (key, value) -> (value as? Number)?.let { key to it.toIntClamped() } }.toMap(),
        )
    }
}
