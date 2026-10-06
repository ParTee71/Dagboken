package se.partee71.dagboken.core.schema

/**
 * Översätter en modell till och från ett Firestore-dokument (skill data-safety-backup).
 *
 * - [encode] skriver **alla** fält codecen känner till, även de utan värde (`null`), så att
 *   en skrivning med merge tömmer det användaren tömt men lämnar okända fält orörda.
 * - [decode] tål saknade fält (default), `null` (som saknat) och okända fält (ignoreras).
 * - Byggs enbart av fälthjälparna i `Fields.kt` – ingen egen parsning.
 */
interface DocCodec<T> {
    fun encode(value: T): Map<String, Any?>

    fun decode(id: String, map: Map<String, Any?>): T
}

/** Codec för en samling poster med ett datumfält ([dateField], `yyyy-MM-dd`) – läsningen per dag eller period. */
interface DatedCodec<T> : DocCodec<T> {
    val dateField: String
}

/**
 * Codec för ett nästlat värde utan eget dokument-ID, t.ex. `schedule` och `rule`. Tar emot
 * fältets råa värde – även ett som inte är en map – så att ett okänt format kan skrivas
 * tillbaka oförändrat i stället för att ersättas.
 */
interface ValueCodec<T> {
    fun encode(value: T): Any?

    fun decode(raw: Any?): T
}
