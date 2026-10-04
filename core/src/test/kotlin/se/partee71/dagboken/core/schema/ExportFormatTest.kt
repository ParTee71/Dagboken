package se.partee71.dagboken.core.schema

import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.time.Instant
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import org.junit.Test

/** Appens export skrivs i samma format som `tools/db export` (SET-5, BCK-1). */
class ExportFormatTest {

    /**
     * Alla fälttyper i exportformatet (text, tal, decimaltal, bool, null, lista, nästlad map,
     * tidsstämpel, skyddad `__ts`-map). Ersätts av tools/db:s egen testdata när den finns i
     * Dagboken (etapp 2), så att appen och verktyget bevisligen läser samma fil.
     */
    private val fixture = """
        {
          "exportedAt": "2026-10-04T10:00:00.000Z",
          "schemaVersion": 1,
          "documents": [
            {
              "path": "users/uid-test",
              "data": {
                "schemaVersion": 1,
                "createdAt": { "__ts": "2026-09-01T08:15:30.123456000Z" },
                "framtidaFält": { "okänd": true, "andel": 0.25, "lista": [1, "två", null] }
              }
            },
            {
              "path": "users/uid-test/options/promenad",
              "data": {
                "kind": "activity",
                "name": "Promenad",
                "favorite": false,
                "sortOrder": 3,
                "note": null,
                "skyddad": { "__map": { "__ts": "inte en tid" } },
                "liten": 1.5e-7
              }
            }
          ]
        }
    """.trimIndent()

    @Test
    fun `varje dokument i fixturen kommer tillbaka exakt likadant`() {
        val documents = Json.parseToJsonElement(fixture).jsonObject.getValue("documents").jsonArray
        val roundTripped = ExportFormat.decode(fixture).map { ExportFormat.document(it) }
        assertEquals(documents.toList(), roundTripped)
    }

    @Test
    fun `filen har tools-db-exportens fält och indrag`() {
        val text = ExportFormat.encode(
            Instant.parse("2026-10-02T10:00:00.123456Z"),
            2,
            listOf(ExportFormat.Document("users/u1", mapOf("name" to "Testperson"))),
        )
        assertEquals(
            """
            {
              "exportedAt": "2026-10-02T10:00:00.123Z",
              "schemaVersion": 2,
              "documents": [
                {
                  "path": "users/u1",
                  "data": {
                    "name": "Testperson"
                  }
                }
              ]
            }
            """.trimIndent(),
            text,
        )
        assertEquals(listOf(ExportFormat.Document("users/u1", mapOf("name" to "Testperson"))), ExportFormat.decode(text))
    }

    @Test
    fun `tidsstämplar får nio decimaler och en egen map med __ts skyddas`() {
        val created = Instant.parse("2026-09-01T08:15:30.123456Z")
        assertEquals("""{"__ts":"2026-09-01T08:15:30.123456000Z"}""", ExportFormat.toJson(created).toString())
        val tricky = mapOf("__ts" to "inte en tid")
        assertEquals("""{"__map":{"__ts":"inte en tid"}}""", ExportFormat.toJson(tricky).toString())
        assertEquals(tricky, ExportFormat.fromJson(ExportFormat.toJson(tricky)))
        assertEquals(created, ExportFormat.fromJson(ExportFormat.toJson(created)))
    }

    @Test
    fun `decimaltal med heltalsvärde skrivs som heltal, som i JavaScript`() {
        assertEquals("2", ExportFormat.toJson(2.0).toString())
        assertEquals("0.25", ExportFormat.toJson(0.25).toString())
        assertEquals("0.0001", ExportFormat.toJson(0.0001).toString(), "utan exponent, som JavaScript")
        assertEquals("1e-7", ExportFormat.toJson(1e-7).toString(), "med exponent under 10^-6, som JavaScript")
        assertEquals("1.5e-7", ExportFormat.toJson(1.5e-7).toString())
        assertEquals(1.5e-7, ExportFormat.fromJson(ExportFormat.toJson(1.5e-7)))
        assertEquals(0.0001, ExportFormat.fromJson(ExportFormat.toJson(0.0001)))
    }

    @Test
    fun `det som JSON inte kan bära exakt stoppar exporten utan att visa värdet`() {
        val tooBig = assertFailsWith<IllegalArgumentException> { ExportFormat.document(ExportFormat.Document("users/u1", mapOf("n" to 9_007_199_254_740_992L))) }
        assertEquals("users/u1: ett tal kan inte exporteras exakt", tooBig.message)
        assertFailsWith<IllegalArgumentException> { ExportFormat.toJson(Double.NaN) }
        assertFailsWith<IllegalArgumentException>("ett heltalsvärde över 2^53 som decimaltal") { ExportFormat.toJson(1e16) }
        val unknown = assertFailsWith<IllegalArgumentException> { ExportFormat.toJson(mapOf("bild" to ByteArray(2))) }
        assertFalse(unknown.message.orEmpty().contains("[B@"))
    }
}
