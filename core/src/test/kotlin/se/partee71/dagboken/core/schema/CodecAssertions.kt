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

private fun persistedFields(value: Any) =
    value::class.java.declaredFields
        .filterNot { Modifier.isStatic(it.modifiers) || it.name == "id" }
        .onEach { it.isAccessible = true }

private fun persistedFieldNames(value: Any) = persistedFields(value).map { it.name }.toSet()
