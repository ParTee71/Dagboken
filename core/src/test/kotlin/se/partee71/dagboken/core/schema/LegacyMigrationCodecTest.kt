package se.partee71.dagboken.core.schema

import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Instant
import org.junit.Test
import se.partee71.dagboken.core.model.LegacyMigration
import se.partee71.dagboken.core.model.LegacySource

/** Markören `users/{uid}.legacyMigration` (OMB-2): rundtur, tolerans och att fixturens värde läses. */
class LegacyMigrationCodecTest {
    private val sample = LegacyMigration(
        completedAt = Instant.fromEpochSeconds(1_790_000_000),
        source = LegacySource.JSON,
        sourceCreatedAt = Instant.fromEpochSeconds(1_780_000_000),
        appVersion = "4.0.0",
        counts = mapOf("doses" to 12, "settings" to 1),
    )

    @Test
    fun `provet har icke-default i varje fält och går runt genom codecen, även med okända fält och Long-tal`() {
        assertEveryFieldDiffersFromDefault(sample, LegacyMigration())
        assertEquals(sample, LegacyMigrationCodec.decode(LegacyMigrationCodec.encode(sample)))
        val stored = asDoc(LegacyMigrationCodec.encode(sample)) + ("nytt" to 1) + ("counts" to mapOf("doses" to 12L, "settings" to 1L, "x" to "inte tal"))
        assertEquals(sample, LegacyMigrationCodec.decode(stored))
        assertEquals(setOf("completedAt", "source", "sourceCreatedAt", "appVersion", "counts"), asDoc(LegacyMigrationCodec.encode(sample)).keys)
    }

    @Test
    fun `saknat, null eller fel typ är ingen markör - ett tomt objekt är en markör med defaults`() {
        assertNull(LegacyMigrationCodec.decode(null))
        assertNull(LegacyMigrationCodec.decode("room"))
        assertNull(LegacyMigrationCodec.encode(null))
        assertEquals(LegacyMigration(), LegacyMigrationCodec.decode(emptyMap<String, Any?>()))
        assertEquals(LegacyMigration(), LegacyMigrationCodec.decode(mapOf("source" to "okänd")))
    }

    @Test
    fun `fixturens användardokument bär en markör med varje fält satt`() {
        val user = ExportFormat.decode(File("../tools/db/test/fixtures/user.json").readText()).first { it.path == "users/uid-test" }
        val marker = checkNotNull(LegacyMigrationCodec.decode(user.data[LegacyMigrationCodec.FIELD]))
        assertEveryFieldDiffersFromDefault(marker, LegacyMigration())
        assertEquals(LegacySource.JSON, marker.source)
        assertEquals(mapOf("settings" to 1, "doses" to 2), marker.counts)
        assertEquals(emptyList(), DocumentRules.validate(CollectionNames.USERS, user.data))
    }
}
