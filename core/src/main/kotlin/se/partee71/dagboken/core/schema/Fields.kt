package se.partee71.dagboken.core.schema

import kotlin.time.Instant
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.isoDayNumber
import se.partee71.dagboken.core.model.WireEnum

// Delade fälthjälpare – det ENDA stället där ett dokuments värden tolkas och skrivs.
// Tolerans mot saknade, felaktiga och okända värden finns därmed på ett ställe (regel 1, 4).
// `null` och saknat fält behandlas likadant: default.

typealias Doc = Map<String, Any?>

fun Doc.string(key: String, default: String = ""): String = this[key] as? String ?: default

fun Doc.stringOrNull(key: String): String? = this[key] as? String

/** Firestore lämnar heltal som `Long`; allt numeriskt accepteras. */
fun Doc.int(key: String, default: Int = 0): Int = intOrNull(key) ?: default

fun Doc.intOrNull(key: String): Int? = (this[key] as? Number)?.toIntClamped()

/** Heltal där ett lagrat `null` betyder något (symptomets `score`, DAT-6): saknas eller trasigt → [default], `null` → `null`. */
fun Doc.intKeepingNull(key: String, default: Int = 0): Int? = if (containsKey(key) && this[key] == null) null else int(key, default)

/**
 * Som `toInt()`, men ett värde utanför Int-intervallet (t.ex. ett Long från ett annat verktyg)
 * begränsas till gränsen i stället för att slå runt till ett litet, rimligt tal.
 */
internal fun Number.toIntClamped(): Int = toLong().coerceIn(Int.MIN_VALUE.toLong(), Int.MAX_VALUE.toLong()).toInt()

fun Doc.bool(key: String, default: Boolean = false): Boolean = this[key] as? Boolean ?: default

/** Valfritt sant/falskt; saknat eller annan typ → `null`. */
fun Doc.boolOrNull(key: String): Boolean? = this[key] as? Boolean

/** Enum med lagrat namn ([WireEnum.wire]); okänt eller saknat → `null`. */
inline fun <reified E> wireValue(raw: Any?): E? where E : Enum<E>, E : WireEnum =
    (raw as? String)?.let { name -> enumValues<E>().firstOrNull { it.wire == name } }

/** Enum med lagrat namn; okänt eller saknat värde (t.ex. från en nyare app) → [default]. */
inline fun <reified E> Doc.wire(key: String, default: E): E where E : Enum<E>, E : WireEnum =
    wireValue<E>(this[key]) ?: default

inline fun <reified E> Doc.wireOrNull(key: String): E? where E : Enum<E>, E : WireEnum = wireValue<E>(this[key])

/** Lista av enum med lagrade namn, i lagrad ordning; okända värden hoppas över (se [unknownWires]). */
inline fun <reified E> Doc.wireList(key: String): List<E> where E : Enum<E>, E : WireEnum =
    (this[key] as? List<*>)?.mapNotNull { wireValue<E>(it) }.orEmpty()

/** De textvärden i listan [key] som inte är något känt [E] – för att skriva tillbaka dem oförändrade. */
inline fun <reified E> Doc.unknownWires(key: String): List<String> where E : Enum<E>, E : WireEnum =
    (this[key] as? List<*>)?.filterIsInstance<String>()?.filter { wireValue<E>(it) == null }.orEmpty()

/**
 * Fältets text när den inte är något känt [E] – ett enumvärde från en nyare app, som skrivs tillbaka
 * oförändrat (som [unknownWires] för listor). Känt, saknat eller annan typ → `null`.
 */
inline fun <reified E> Doc.unknownWire(key: String): String? where E : Enum<E>, E : WireEnum =
    (this[key] as? String)?.takeIf { wireValue<E>(it) == null }

/** Datum utan tid lagras som ISO-sträng (`yyyy-MM-dd`); ogiltigt → `null`. */
fun Doc.localDate(key: String): LocalDate? = (this[key] as? String)?.let(::parseDate)

/**
 * Klockslag lagras som `HH:mm`; ogiltigt, saknat eller i annat format (även från en nyare app)
 * → `null`, som skrivs vid nästa sparning (skill data-safety-backup).
 */
fun Doc.localTime(key: String): LocalTime? = (this[key] as? String)?.let(::parseClock)

/**
 * `yyyy-MM-dd` → datum; `null` om texten inte är ett giltigt datum (även `2024-02-30`, som rules
 * datummönster godtar men som inte finns). Enda datumtolkningen – codecs och konverteraren delar den.
 */
fun parseDate(text: String): LocalDate? =
    text.takeIf { DocumentRules.DATE.matches(it) }?.let { runCatching { LocalDate.parse(it) }.getOrNull() }

/** `HH:mm` → klockslag; `null` för allt annat (även `9:00` och `09:00:30`). Samma mönster som rules. */
fun parseClock(text: String): LocalTime? =
    text.takeIf { DocumentRules.CLOCK.matches(it) }?.let { LocalTime(it.take(2).toInt(), it.takeLast(2).toInt()) }

fun LocalTime?.encodeTime(): String? = this?.let { "%02d:%02d".format(java.util.Locale.ROOT, it.hour, it.minute) }

/** Tidpunkt; `:app` översätter Firestores `Timestamp` till [Instant] innan decode. */
fun Doc.instant(key: String): Instant? = this[key] as? Instant

fun Doc.stringList(key: String): List<String> = (this[key] as? List<*>)?.filterIsInstance<String>().orEmpty()

/** Veckodagar lagras som ISO-nummer (1 = måndag … 7 = söndag); ogiltiga tal hoppas över. */
fun Doc.weekdays(key: String): Set<DayOfWeek> =
    (this[key] as? List<*>)?.filterIsInstance<Number>()?.map { it.toIntClamped() }
        ?.filter { it in 1..7 }?.map { DayOfWeek(it) }?.toSet().orEmpty()

/** Lista av nästlade objekt, i lagrad ordning; element som inte är objekt hoppas över. */
fun Doc.docs(key: String): List<Doc> = (this[key] as? List<*>)?.filterIsInstance<Map<*, *>>()?.map(::asDoc).orEmpty()

/** Nästlat objekt; [codec] får det råa värdet och avgör vad saknat eller okänt betyder. */
fun <T> Doc.nested(key: String, codec: ValueCodec<T>): T = codec.decode(this[key])

/** Map från Firestore med okända nyckeltyper → [Doc]. */
fun asDoc(value: Any?): Doc =
    (value as? Map<*, *>)?.entries?.filter { it.key is String }?.associate { it.key as String to it.value }.orEmpty()

// Skrivning – samma format som läsningen ovan.

fun LocalDate?.encodeDate(): String? = this?.toString()

fun Set<DayOfWeek>.encodeWeekdays(): List<Int> = map { it.isoDayNumber }.sorted()

fun WireEnum?.encodeWire(): String? = this?.wire

/** Ett valfritt enumvärde: det kända, annars det okända lagrade värdet oförändrat ([unknownWire]). */
fun WireEnum?.encodeWire(unknown: String?): String? = this?.wire ?: unknown

fun List<WireEnum>.encodeWires(): List<String> = map { it.wire }
