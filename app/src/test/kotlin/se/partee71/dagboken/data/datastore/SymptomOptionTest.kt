package se.partee71.dagboken.data.datastore

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

class SymptomOptionTest {

    @Test fun `default options are all non-favorite`() {
        DEFAULT_SYMPTOM_OPTIONS.forEach {
            assertEquals(false, it.isFavorite)
        }
    }

    @Test fun `serialization round-trips correctly`() {
        val options = listOf(
            SymptomOption("Alpha", isFavorite = true),
            SymptomOption("Beta",  isFavorite = false),
        )
        val decoded = Json.decodeFromString<List<SymptomOption>>(Json.encodeToString(options))
        assertEquals(options, decoded)
    }

    @Test fun `old string list migrates to symptom options without favorite`() {
        val oldJson  = """["Huvudvärk","Trötthet","Yrsel"]"""
        val migrated = Json.decodeFromString<List<String>>(oldJson).map { SymptomOption(it) }
        assertEquals(
            listOf(SymptomOption("Huvudvärk"), SymptomOption("Trötthet"), SymptomOption("Yrsel")),
            migrated,
        )
    }

    @Test fun `migration strategy prefers new format when valid`() {
        val newJson = Json.encodeToString(listOf(SymptomOption("Foo", isFavorite = true)))
        val result  = runCatching { Json.decodeFromString<List<SymptomOption>>(newJson) }.getOrNull()
        assertEquals(listOf(SymptomOption("Foo", isFavorite = true)), result)
    }

    @Test fun `migration strategy falls back for old format`() {
        val oldJson = """["Foo","Bar"]"""
        val asNew   = runCatching { Json.decodeFromString<List<SymptomOption>>(oldJson) }.getOrNull()
        assertEquals(null, asNew)  // old format not parseable as new
        val asOld   = Json.decodeFromString<List<String>>(oldJson)
        assertEquals(listOf("Foo", "Bar"), asOld)
    }

    @Test fun `sorting puts favorites first then alphabetical within each group`() {
        val options = listOf(
            SymptomOption("Yrsel",     isFavorite = false),
            SymptomOption("Trötthet",  isFavorite = true),
            SymptomOption("Huvudvärk", isFavorite = true),
            SymptomOption("Smärta",    isFavorite = false),
        )
        val sorted = options.sortedWith(
            compareByDescending<SymptomOption> { it.isFavorite }.thenBy { it.name }
        )
        assertEquals(
            listOf(
                SymptomOption("Huvudvärk", isFavorite = true),
                SymptomOption("Trötthet",  isFavorite = true),
                SymptomOption("Smärta",    isFavorite = false),
                SymptomOption("Yrsel",     isFavorite = false),
            ),
            sorted,
        )
    }
}
