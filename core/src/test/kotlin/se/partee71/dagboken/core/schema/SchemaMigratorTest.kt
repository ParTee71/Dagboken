package se.partee71.dagboken.core.schema

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SchemaMigratorTest {

    private val doc: Doc = mapOf("name" to "Promenad", "okänt" to listOf(1, 2))

    @Test
    fun `nuvarande version migreras till sig själv`() {
        assertEquals(doc, SchemaMigrator.migrate(Schema.CURRENT_VERSION, "options", doc))
    }

    @Test
    fun `varje version under den nuvarande har ett steg`() {
        for (version in Schema.FIRST_VERSION until Schema.CURRENT_VERSION) {
            assertTrue(version in SchemaMigrator.steps, "migreringssteg saknas för version $version")
        }
    }

    @Test
    fun `version 1 är den första – inga steg och inget att stämpla`() {
        assertEquals(1, Schema.CURRENT_VERSION)
        assertEquals(emptyMap(), SchemaMigrator.steps)
        assertFalse(SchemaMigrator.canStamp(Schema.CURRENT_VERSION))
        assertTrue(SchemaMigrator.canStamp(0), "ett trasigt värde stämplas om med den nuvarande versionen")
    }

    @Test
    fun `nyare data än appen migreras aldrig och markeras som nyare`() {
        assertTrue(Schema.isNewerThanApp(Schema.CURRENT_VERSION + 1))
        assertFalse(Schema.isNewerThanApp(Schema.CURRENT_VERSION))
        assertFalse(SchemaMigrator.canStamp(Schema.CURRENT_VERSION + 1))
        assertFailsWith<IllegalArgumentException> {
            SchemaMigrator.migrate(Schema.CURRENT_VERSION + 1, "options", doc)
        }
    }

    @Test
    fun `en trasig version under den första läses som det första formatet`() {
        assertEquals(Schema.FIRST_VERSION, Schema.versionOf(0L))
        assertEquals(Schema.FIRST_VERSION, Schema.versionOf(-3))
        assertEquals(Schema.FIRST_VERSION, Schema.versionOf(null))
        assertEquals(Schema.FIRST_VERSION, Schema.versionOf("1"))
        assertEquals(Schema.CURRENT_VERSION, Schema.versionOf(Schema.CURRENT_VERSION.toLong()))
        assertEquals(Int.MAX_VALUE, Schema.versionOf(Long.MAX_VALUE), "för stort är nyare än appen, inte ett litet tal")
        assertEquals(doc, SchemaMigrator.migrate(0, "options", doc))
    }

    @Test
    fun `ett steg som inte ändrar data lämnar dokumentet orört`() {
        assertEquals(doc, SchemaMigrator.Step.UNCHANGED.migrate("options", doc))
    }
}
