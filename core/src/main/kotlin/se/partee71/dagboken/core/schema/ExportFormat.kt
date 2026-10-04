package se.partee71.dagboken.core.schema

import java.math.BigDecimal
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import kotlin.math.abs
import kotlin.time.Instant
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.JsonUnquotedLiteral
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/**
 * Exportfilens format – samma som `tools/db export` (`tools/db/lib/serialize.mjs` och
 * `backup.mjs`), så att appens export kan läsas in med `tools/db import` (SET-5, BCK-1):
 * `{ exportedAt, schemaVersion, documents: [{ path, data }] }`. En tidsstämpel blir
 * `{ "__ts": "…Z" }` med nio decimaler; en egen map vars enda nyckel är `__ts` eller `__map`
 * skyddas som `{ "__map": … }`. Ett decimaltal med heltalsvärde skrivs som heltal, som i
 * JavaScript. Värden som JSON inte kan bära exakt stoppar exporten; felmeddelandena innehåller
 * aldrig värdena (hälsodata).
 */
object ExportFormat {
    data class Document(val path: String, val data: Doc)

    /** Hela filen, indragen med två mellanslag som `tools/db export`. */
    fun encode(exportedAt: Instant, schemaVersion: Int, documents: List<Document>): String =
        PRETTY.encodeToString(
            JsonElement.serializer(),
            buildJsonObject {
                put("exportedAt", millis(exportedAt))
                put("schemaVersion", schemaVersion)
                put("documents", buildJsonArray { documents.forEach { add(document(it)) } })
            },
        )

    /** Dokumenten i en exportfil. */
    fun decode(text: String): List<Document> =
        Json.parseToJsonElement(text).jsonObject.getValue("documents").jsonArray.map { element ->
            val doc = element.jsonObject
            @Suppress("UNCHECKED_CAST")
            Document(doc.getValue("path").jsonPrimitive.content, fromJson(doc.getValue("data")) as Doc)
        }

    /** Ett dokument; ett fel får dokumentets sökväg, aldrig värdet. */
    fun document(document: Document): JsonObject = try {
        buildJsonObject {
            put("path", document.path)
            put("data", toJson(document.data))
        }
    } catch (e: IllegalArgumentException) {
        throw IllegalArgumentException("${document.path}: ${e.message}")
    }

    /** Firestore-data (med [Instant] för tidsstämplar) → JSON. Okända typer stoppar exporten. */
    fun toJson(value: Any?): JsonElement = when (value) {
        null -> JsonNull
        is String -> JsonPrimitive(value)
        is Boolean -> JsonPrimitive(value)
        is Int, is Long -> wholeNumber((value as Number).toLong())
        is Float, is Double -> decimal((value as Number).toDouble())
        is Instant -> buildJsonObject { put(TS, nanos(value)) }
        is List<*> -> JsonArray(value.map(::toJson))
        is Map<*, *> -> {
            val json = JsonObject(value.entries.associate { (key, item) -> key.toString() to toJson(item) })
            if (value.size == 1 && value.keys.single() in WRAPPED) JsonObject(mapOf(MAP to json)) else json
        }
        else -> throw IllegalArgumentException("värdetypen ${value::class.simpleName} stöds inte")
    }

    /** JSON → Firestore-data, motsatsen till [toJson]. */
    fun fromJson(element: JsonElement): Any? = when (element) {
        JsonNull -> null
        is JsonPrimitive -> when {
            element.isString -> element.content
            element.booleanOrNull != null -> element.booleanOrNull
            element.content.any { it == '.' || it == 'e' || it == 'E' } -> element.content.toDouble()
            else -> element.content.toLong()
        }
        is JsonArray -> element.map(::fromJson)
        is JsonObject -> {
            val only = element.keys.singleOrNull()
            when {
                only == TS -> parseNanos(element.getValue(TS).jsonPrimitive.content)
                only == MAP && element.getValue(MAP) is JsonObject -> element.getValue(MAP).jsonObject.mapValues { fromJson(it.value) }
                else -> element.mapValues { fromJson(it.value) }
            }
        }
    }

    private fun wholeNumber(value: Long): JsonPrimitive {
        require(value in -MAX_SAFE..MAX_SAFE) { "ett tal kan inte exporteras exakt" }
        return JsonPrimitive(value)
    }

    /**
     * Som `toJson` i tools/db: ett heltalsvärde utanför 2^53 stoppar (`toLong` mättar, så
     * [wholeNumber] fångar det), och decimaltal skrivs som JavaScript skriver dem – utan exponent,
     * utom under 10^-6 (`1e-7`).
     */
    @OptIn(ExperimentalSerializationApi::class)
    private fun decimal(value: Double): JsonPrimitive {
        require(value.isFinite()) { "ett tal kan inte exporteras exakt" }
        if (value == Math.rint(value)) return wholeNumber(value.toLong())
        val text = if (abs(value) < JS_EXPONENT_BELOW) {
            val (mantissa, exponent) = value.toString().split('E')
            "${mantissa.removeSuffix(".0")}e$exponent"
        } else {
            BigDecimal(value.toString()).stripTrailingZeros().toPlainString()
        }
        return JsonUnquotedLiteral(text)
    }

    /** `2026-09-01T08:15:30.123456000Z` – alla nio decimaler, som `tsToIso` i tools/db. */
    private fun nanos(value: Instant): String = "${seconds(value)}.${value.nanosecondsOfSecond.toString().padStart(9, '0')}Z"

    /** `2026-10-02T10:00:00.123Z` – som JavaScripts `toISOString()`. */
    private fun millis(value: Instant): String = "${seconds(value)}.${(value.nanosecondsOfSecond / NANOS_PER_MILLI).toString().padStart(3, '0')}Z"

    private fun seconds(value: Instant): String =
        SECONDS.format(java.time.Instant.ofEpochSecond(value.epochSeconds).atOffset(ZoneOffset.UTC))

    private fun parseNanos(text: String): Instant {
        val match = requireNotNull(TIMESTAMP.matchEntire(text)) { "ogiltig tidsstämpel" }
        val whole = java.time.Instant.parse("${match.groupValues[1]}Z")
        return Instant.fromEpochSeconds(whole.epochSecond, match.groupValues[2].padEnd(9, '0').toInt())
    }

    private const val TS = "__ts"
    private const val MAP = "__map"
    private val WRAPPED = setOf(TS, MAP)

    /** Största heltal som JavaScript (och därmed tools/db) bär exakt: 2^53 − 1. */
    private const val MAX_SAFE = 9_007_199_254_740_991L
    private const val NANOS_PER_MILLI = 1_000_000
    private const val JS_EXPONENT_BELOW = 1e-6
    private val SECONDS = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss")
    private val TIMESTAMP = Regex("""^(.{19})(?:\.(\d{1,9}))?Z$""")

    @OptIn(ExperimentalSerializationApi::class)
    private val PRETTY = Json {
        prettyPrint = true
        prettyPrintIndent = "  "
    }
}
