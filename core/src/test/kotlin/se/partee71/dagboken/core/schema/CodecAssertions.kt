package se.partee71.dagboken.core.schema

import java.lang.reflect.Modifier
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import se.partee71.dagboken.core.model.Identified

// Delade asserts för codecs (skill data-safety-backup) – varje codec-test anropar dessa i
// stället för egna asserts.

/** Modell → map → modell ger samma modell. */
fun <T : Identified> assertCodecRoundTrip(codec: DocCodec<T>, sample: T) {
    assertEquals(sample, codec.decode(sample.id, codec.encode(sample)))
}

/** Ett tomt (äldre eller ofullständigt) dokument ger modellens defaults, utan krasch. */
fun <T : Identified> assertToleratesMissingFields(codec: DocCodec<T>, defaults: T) {
    assertEquals(defaults, codec.decode(defaults.id, emptyMap()))
    assertEquals(defaults, codec.decode(defaults.id, codec.encode(defaults).mapValues { null }))
}

/** Fält från en nyare appversion ignoreras vid läsning. */
fun <T : Identified> assertIgnoresUnknownFields(codec: DocCodec<T>, sample: T) {
    val newer = codec.encode(sample) + mapOf("faltFranNyareApp" to "x", "nastlatNytt" to mapOf("a" to 1))
    assertEquals(sample, codec.decode(sample.id, newer))
}

/**
 * [DocCodec.encode] skriver varje fält modellen har (utom `id`), även de som är `null` – annars
 * tömmer en merge-skrivning inte det användaren tömt, och ett glömt fält tappas vid backup.
 */
fun <T : Identified> assertEncodesAllFields(codec: DocCodec<T>, sample: T) {
    val encoded = codec.encode(sample)
    assertEquals(persistedFieldNames(sample), encoded.keys, "encode ska skriva exakt modellens fält")
}

/** Varje fält i [sample] har ett annat värde än i [defaults], så att rundturen bevisar varje fält. */
fun <T : Any> assertEveryFieldDiffersFromDefault(sample: T, defaults: T) {
    for (field in persistedFields(sample)) {
        assertNotEquals(field.get(defaults), field.get(sample), "provet ska ha ett icke-default-värde i `${field.name}`")
    }
}

/** Namnen på fälten där [value] har ett annat värde än [defaults]. */
fun fieldsDifferingFromDefault(value: Any, defaults: Any): Set<String> =
    persistedFields(value).filter { it.get(value) != it.get(defaults) }.map { it.name }.toSet()

/** Modellens persisterade fält (alla utom `id`). */
fun persistedFieldNames(value: Any): Set<String> = persistedFields(value).map { it.name }.toSet()

/** Hela kontraktet för en dokument-codec: [sample] med icke-default-värden, [defaults] = modellens defaults. */
fun <T : Identified> assertCodecContract(codec: DocCodec<T>, sample: T, defaults: T) {
    assertEveryFieldDiffersFromDefault(sample, defaults)
    assertCodecRoundTrip(codec, sample)
    assertToleratesMissingFields(codec, defaults)
    assertIgnoresUnknownFields(codec, sample)
    assertEncodesAllFields(codec, sample)
    assertEncodesAllFields(codec, defaults)
}

/**
 * Hela kontraktet för en nästlad codec med varianter. [variants] är par av (prov med
 * icke-default i varje fält, variantens defaults); alla kända varianter skriver samma fält.
 */
fun <T : Any> assertVariantCodecContract(codec: ValueCodec<T>, variants: List<Pair<T, T>>) {
    val encoded = variants.map { (sample, _) -> asDoc(codec.encode(sample)) }
    assertTrue(encoded.all { it.keys == encoded.first().keys }, "alla varianter ska skriva samma fält: ${encoded.map { it.keys }}")
    for ((sample, defaults) in variants) {
        assertEveryFieldDiffersFromDefault(sample, defaults)
        assertEquals(sample, codec.decode(codec.encode(sample)))
        assertEquals(sample, codec.decode(asDoc(codec.encode(sample)) + ("faltFranNyareApp" to 1)))
    }
}

/**
 * Fältvägarna i ett kodat dokument, även de nästlade: `theme.mode`, `boosts[].unit` (element i en
 * lista av objekt). Samma notation som paritetstabellen i ARKITEKTUR.md → Datamodell.
 */
fun fieldPaths(doc: Doc, prefix: String = ""): Set<String> = doc.flatMap { (key, value) ->
    val path = prefix + key
    when {
        value is Map<*, *> -> fieldPaths(asDoc(value), "$path.") + path
        value is List<*> && value.any { it is Map<*, *> } ->
            value.filterIsInstance<Map<*, *>>().flatMap { fieldPaths(asDoc(it), "$path[].") } + path
        else -> listOf(path)
    }
}.toSet()

/** Fältvägar utan värde – `null`, tom text eller tom lista – på alla nivåer. */
fun emptyFieldPaths(doc: Doc, prefix: String = ""): Set<String> = doc.flatMap { (key, value) ->
    val path = prefix + key
    when (value) {
        null -> listOf(path)
        is String -> if (value.isEmpty()) listOf(path) else emptyList()
        is Map<*, *> -> emptyFieldPaths(asDoc(value), "$path.")
        is List<*> -> if (value.isEmpty()) listOf(path) else value.filterIsInstance<Map<*, *>>().flatMap { emptyFieldPaths(asDoc(it), "$path[].") }
        else -> emptyList()
    }
}.toSet()

/** Varje fält i [doc] har ett värde, även i nästlade objekt och listor (prov och fixtur, OMB-3). */
fun assertEveryFieldSet(doc: Doc, what: String) {
    assertEquals(emptySet(), emptyFieldPaths(doc), "$what ska ha ett värde i varje fält")
}

private fun persistedFields(value: Any) =
    value::class.java.declaredFields
        .filterNot { Modifier.isStatic(it.modifiers) || it.name == "id" }
        .onEach { it.isAccessible = true }
