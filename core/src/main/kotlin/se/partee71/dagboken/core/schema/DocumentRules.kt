package se.partee71.dagboken.core.schema

import kotlin.time.Instant
import se.partee71.dagboken.core.model.DoseStatus
import se.partee71.dagboken.core.model.LegacySource
import se.partee71.dagboken.core.model.MedicineForm
import se.partee71.dagboken.core.model.Occasion
import se.partee71.dagboken.core.model.OptionKind
import se.partee71.dagboken.core.model.Repeat
import se.partee71.dagboken.core.model.Sex
import se.partee71.dagboken.core.model.Slot
import se.partee71.dagboken.core.model.ThemeMode
import se.partee71.dagboken.core.model.WireEnum

/**
 * Gränserna och fältkontrollerna i `firestore.rules`, på **ett** ställe i `:core` (ARKITEKTUR.md →
 * Migrering, punkt 1; OMB-3): textgränserna ([TextLimits]), heltalsintervallen, listtaken, enum-listorna
 * och datum-/klockslagsmönstren, per samling och fält precis som `valid…`-funktionerna i rules.
 * `DocumentRulesTest` läser rules och kräver att tabellen här är densamma.
 *
 * [validate] kontrollerar ett kodat dokument som rules skulle göra vid `create` (alla fält skrivs):
 * saknat fält eller `null` är tillåtet, okända fält får finnas. Konverteraren kör den på varje
 * genererat dokument och stoppar med en rapport – `tools/db import.mjs` skriver förbi rules, så utan den
 * kunde data importeras som appen sedan inte kan spara om. Felen nämner fält och värdets längd eller
 * tal, aldrig textinnehållet (hälsodata).
 */
object DocumentRules {
    /** Högst så många fält på toppnivå i ett dokument (`maxFields()`). */
    const val MAX_FIELDS = 40

    /** Högst så många symptom på en post (`maxSymptoms()`). */
    const val MAX_SYMPTOMS = 50

    /** Högst så många doshöjningar på ett recept (`maxBoosts()`). */
    const val MAX_BOOSTS = 50

    /** Stress, svårighetsgrad, måendets energi och symptompoäng. */
    val SCORE: IntRange = 0..10

    /** Aktivitetens energi (AKT-4). */
    val ACTIVITY_ENERGY: IntRange = -10..10

    /** Timme på dygnet (temats ljus-/mörkerstart). */
    val HOUR: IntRange = 0..23

    /** Veckodag som ISO-nummer (`schedule.days`). */
    val WEEKDAYS: IntRange = 1..7

    /** Datum `yyyy-MM-dd` och klockslag `HH:mm` som text (DAT-2) – samma mönster som `isDate`/`isClock` i rules. */
    val DATE = Regex("^[0-9]{4}-(0[1-9]|1[0-2])-(0[1-9]|[12][0-9]|3[01])$")
    val CLOCK = Regex("^([01][0-9]|2[0-3]):[0-5][0-9]$")

    /** En kontroll av ett fälts värde; `null` (saknat) godtas av alla utom där [Shape.required] säger annat. */
    sealed interface Check {
        /** Heltal (Firestore `int`). */
        data object IntField : Check

        data object BoolField : Check

        /** Tidsstämpel ([Instant] i `:core`, `timestamp` i Firestore). */
        data object TimestampField : Check

        /** Text utan längdgräns (element i listor, som rules bara typkontrollerar). */
        data object AnyText : Check

        /** Text upp till [TextLimits.SHORT] (`nullOrShort`). */
        data object ShortText : Check

        /** Text upp till [TextLimits.LONG] (`nullOrLong`). */
        data object LongText : Check

        data object DateText : Check

        data object ClockText : Check

        /** Heltal inom [range] (`nullOrRange`). */
        data class Range(val range: IntRange) : Check

        /** Heltal från [min] (`nullOrMin`). */
        data class Min(val min: Int) : Check

        /** Ett av de lagrade enum-namnen (`nullOrIn`). */
        data class OneOf(val values: List<String>) : Check

        /** Lista av heltal i [range], högst [max] (`isEnumList` över tal). */
        data class IntList(val range: IntRange, val max: Int) : Check

        /** Lista av enum-namn, högst [max] (`isEnumList`). */
        data class EnumList(val values: List<String>, val max: Int) : Check

        /** Nästlat objekt (`isTheme`, `isSchedule` …). */
        data class Nested(val shape: Shape) : Check

        /** Ett objekt med bara nycklarna i [keys] (`keys().hasOnly`), var och en ett heltal när den finns – markörens `counts`. */
        data class IntMap(val keys: List<String>) : Check

        /** Lista av nästlade objekt, högst [max] eller exakt [exact] element. */
        data class ListOf(val shape: Shape, val max: Int = Int.MAX_VALUE, val exact: Int? = null) : Check
    }

    /**
     * Ett nästlat objekts fält; [required] måste finnas med rätt typ (rules läser dem utan `none`), [nullable] måste
     * finnas som nyckel men får vara `null` (rules läser dem utan `get`, `s.score == null || …`). [closed] = inga andra
     * nycklar får finnas (`keys().hasOnly`) – markören, som bara appen skriver.
     */
    data class Shape(
        val fields: Map<String, Check>,
        val required: Set<String> = emptySet(),
        val closed: Boolean = false,
        val nullable: Set<String> = emptySet(),
    )

    private fun wires(values: List<WireEnum>) = values.map { it.wire }

    /** `score` får vara `null` (utan poäng, från 3.x) men nyckeln måste finnas – rules läser den utan `get`. */
    val SYMPTOM = Shape(
        mapOf("optionId" to Check.AnyText, "score" to Check.Range(SCORE), "customText" to Check.AnyText),
        required = setOf("optionId"),
        nullable = setOf("score"),
    )
    val BOOST = Shape(
        mapOf("id" to Check.AnyText, "start" to Check.DateText, "end" to Check.DateText, "dose" to Check.AnyText, "unit" to Check.AnyText),
        required = setOf("id", "dose", "unit"),
    )
    val SCHEDULE = Shape(
        mapOf("repeat" to Check.OneOf(wires(Repeat.entries)), "days" to Check.IntList(WEEKDAYS, WEEKDAYS.count()), "intervalDays" to Check.Min(0)),
    )
    val PERIOD = Shape(mapOf("start" to Check.DateText, "end" to Check.DateText))
    val THEME = Shape(
        mapOf(
            "mode" to Check.OneOf(wires(ThemeMode.entries)),
            "lightStartHour" to Check.Range(HOUR),
            "darkStartHour" to Check.Range(HOUR),
            "isDarkTheme" to Check.BoolField,
        ),
    )
    private fun reminderRow(key: String, values: List<WireEnum>) = Shape(
        mapOf(key to Check.OneOf(wires(values)), "enabled" to Check.BoolField, "time" to Check.ClockText),
        required = setOf(key, "enabled", "time"),
    )
    val REMINDERS = Shape(
        mapOf(
            "medsEnabled" to Check.BoolField,
            "medSlots" to Check.ListOf(reminderRow("slot", Slot.SCHEDULED), exact = Slot.SCHEDULED.size),
            "screeningOccasions" to Check.ListOf(reminderRow("occasion", Occasion.entries), exact = Occasion.entries.size),
            "periodReminderTime" to Check.ClockText,
        ),
    )
    val PROFILE = Shape(mapOf("birthYear" to Check.IntField, "sex" to Check.OneOf(wires(Sex.entries))))
    val LEGACY = Shape(mapOf("dynamicColor" to Check.BoolField, "sheetsConfig" to Check.LongText))

    /** Samlingarna som migreringsmarkörens `counts` får räkna (`countCollections()`): användarens samlingar och undersamlingen. */
    val COUNT_COLLECTIONS: List<String> = CollectionNames.USER_COLLECTIONS + CollectionNames.CHECKINS

    /** Migreringsmarkören på användardokumentet (`isLegacyMigration`, OMB-2): exakt dessa fält, tid och källa krävs. */
    val LEGACY_MIGRATION = Shape(
        mapOf(
            "completedAt" to Check.TimestampField,
            "source" to Check.OneOf(wires(LegacySource.entries)),
            "sourceCreatedAt" to Check.TimestampField,
            "appVersion" to Check.ShortText,
            "counts" to Check.IntMap(COUNT_COLLECTIONS),
        ),
        required = setOf("completedAt", "source"),
        closed = true,
    )

    /** Gemensamt för alla dokument under användaren (`validEntry`): anteckningen och namnet. */
    val ENTRY: Map<String, Check> = mapOf("note" to Check.LongText, "name" to Check.ShortText)

    /** Dagbokens poster (`validPost`): dag, klockslag och när de skapades. */
    val POST: Map<String, Check> = ENTRY + mapOf("date" to Check.DateText, "time" to Check.ClockText, "createdAt" to Check.TimestampField)

    /** Fältkontrollerna per samling – samma fält som `valid…`-funktionerna i rules och codecarna. */
    val FIELDS: Map<String, Map<String, Check>> = mapOf(
        CollectionNames.USERS to mapOf(
            "schemaVersion" to Check.Min(1),
            "createdAt" to Check.TimestampField,
            "legacyMigration" to Check.Nested(LEGACY_MIGRATION),
        ),
        CollectionNames.SETTINGS to ENTRY + mapOf(
            "theme" to Check.Nested(THEME),
            "reminders" to Check.Nested(REMINDERS),
            "profile" to Check.Nested(PROFILE),
            "legacy" to Check.Nested(LEGACY),
        ),
        CollectionNames.OPTIONS to ENTRY + mapOf(
            "kind" to Check.OneOf(wires(OptionKind.entries)),
            "favorite" to Check.BoolField,
            "sortOrder" to Check.IntField,
            "archived" to Check.BoolField,
        ),
        CollectionNames.PRESCRIPTIONS to ENTRY + mapOf(
            STRENGTH to Check.ShortText,
            FORM to Check.OneOf(wires(MedicineForm.entries)),
            "dose" to Check.ShortText,
            "unit" to Check.ShortText,
            "slots" to Check.EnumList(wires(Slot.entries), Slot.entries.size),
            "schedule" to Check.Nested(SCHEDULE),
            "period" to Check.Nested(PERIOD),
            "boosts" to Check.ListOf(BOOST, max = MAX_BOOSTS),
            "active" to Check.BoolField,
            "createdAt" to Check.TimestampField,
        ),
        CollectionNames.PRN_MEDICINES to ENTRY + mapOf(
            STRENGTH to Check.ShortText,
            FORM to Check.OneOf(wires(MedicineForm.entries)),
            "dose" to Check.ShortText,
            "unit" to Check.ShortText,
            "slot" to Check.OneOf(wires(Slot.entries)),
            "minHoursBetween" to Check.Min(0),
            "dispensingTime" to Check.ShortText,
            "maxPerDay" to Check.Min(0),
            "favorite" to Check.BoolField,
        ),
        CollectionNames.DOSES to ENTRY + mapOf(
            "date" to Check.DateText,
            "slot" to Check.OneOf(wires(Slot.entries)),
            STRENGTH to Check.ShortText,
            "dose" to Check.ShortText,
            "unit" to Check.ShortText,
            "status" to Check.OneOf(wires(DoseStatus.entries)),
            "plannedTime" to Check.ClockText,
            "takenAt" to Check.TimestampField,
            "prescriptionId" to Check.ShortText,
            "prnId" to Check.ShortText,
            "createdAt" to Check.TimestampField,
        ),
        CollectionNames.SCREENINGS to POST + mapOf(
            "occasion" to Check.OneOf(wires(Occasion.entries)),
            "customText" to Check.ShortText,
            "energy" to Check.Range(SCORE),
            "stress" to Check.Range(SCORE),
            "symptoms" to Check.ListOf(SYMPTOM, max = MAX_SYMPTOMS),
            "legacySomatic" to Check.Min(0),
        ),
        CollectionNames.ACTIVITIES to POST + mapOf(
            "optionId" to Check.ShortText,
            "customText" to Check.ShortText,
            "energy" to Check.Range(ACTIVITY_ENERGY),
            "stress" to Check.Range(SCORE),
            "symptoms" to Check.ListOf(SYMPTOM, max = MAX_SYMPTOMS),
            "legacySomatic" to Check.Min(0),
            "recovering" to Check.BoolField,
            "drain" to Check.BoolField,
            "minutes" to Check.Min(0),
        ),
        CollectionNames.EVENTS to POST + mapOf(
            "optionId" to Check.ShortText,
            "severity" to Check.Range(SCORE),
            "durationMinutes" to Check.Min(0),
            "triggers" to Check.LongText,
            "actions" to Check.LongText,
        ),
        CollectionNames.ILLNESS_EPISODES to ENTRY + mapOf(
            "type" to Check.ShortText,
            "start" to Check.DateText,
            "end" to Check.DateText,
            "createdAt" to Check.TimestampField,
        ),
        CollectionNames.CHECKINS to POST + mapOf(
            "severity" to Check.Range(SCORE),
            "symptoms" to Check.ListOf(SYMPTOM, max = MAX_SYMPTOMS),
            "legacySomatic" to Check.Min(0),
        ),
    )

    /**
     * Fälten en samlings codec kan skriva, som träd: en nyckel per fält, med underträd för nästlade objekt
     * (`theme`, `reminders`, …) och `null` för värden som jämförs hela (text, tal, listor, markörens `counts`). Den fasta
     * nyckelmängden som migreringens hashar och jämförelser begränsas till (OMB-7) – inte dokumentets egna nycklar, så
     * att ett fält som tömts i 3.x (nyckeln försvinner) syns som en ändring.
     */
    data class FieldTree(val fields: Map<String, FieldTree?>)

    fun fieldTree(collection: String): FieldTree = treeOf(requireNotNull(FIELDS[collection]) { "okänd samling $collection" })

    private fun treeOf(fields: Map<String, Check>): FieldTree = FieldTree(fields.mapValues { (_, check) -> (check as? Check.Nested)?.let { treeOf(it.shape.fields) } })

    /** Fält som rules kräver även på toppnivå (läses utan `none`): användardokumentets version. */
    private val REQUIRED: Map<String, Set<String>> = mapOf(CollectionNames.USERS to setOf("schemaVersion"))

    /** Ett brott mot rules: fältvägen (`symptoms[3].score`) och skälet – aldrig textinnehållet. */
    data class Violation(val field: String, val reason: String)

    /**
     * Om [id] går att använda som Firestore-dokument-id: inte tomt, inte `.`/`..`, inte reserverat
     * `__…__`, inget `/` och högst 1 500 byte – samma kontroll som `hasValidIds` i tools/db gör före en import
     * (båda körs mot `tools/db/test/fixtures/ids.json`).
     */
    fun isValidId(id: String): Boolean =
        id.isNotEmpty() && id != "." && id != ".." && !RESERVED_ID.matches(id) && '/' !in id && id.toByteArray(Charsets.UTF_8).size <= MAX_ID_BYTES

    /** **Alla** brott i [doc] mot [collection]s regler (även i nästlade objekt och varje listelement), i fältordning; tom lista = rules godtar dokumentet vid `create`. */
    fun validate(collection: String, doc: Doc): List<Violation> {
        val fields = requireNotNull(FIELDS[collection]) { "okänd samling $collection" }
        val violations = mutableListOf<Violation>()
        if (doc.size > MAX_FIELDS) violations += Violation("", "för många fält: ${doc.size} (högst $MAX_FIELDS)")
        violations += check(Shape(fields, REQUIRED[collection].orEmpty()), doc, prefix = "")
        return violations
    }

    private fun check(shape: Shape, doc: Doc, prefix: String): List<Violation> = shape.fields.flatMap { (key, check) ->
        val value = doc[key]
        when {
            value != null -> check(check, value, "$prefix$key")
            key in shape.required -> listOf(Violation("$prefix$key", "saknas"))
            key in shape.nullable && !doc.containsKey(key) -> listOf(Violation("$prefix$key", "saknas"))
            else -> emptyList()
        }
    } + if (shape.closed) (doc.keys - shape.fields.keys).map { Violation("$prefix$it", "okänt fält") } else emptyList()

    /** Alla brott i [value] (aldrig `null`) mot [check]; nästlade fel får sin egen fältväg (`symptoms[3].score`). */
    private fun check(check: Check, value: Any, path: String): List<Violation> {
        val reason: String? = when (check) {
            Check.IntField -> whole(value)
            Check.BoolField -> if (value is Boolean) null else type(value, "sant/falskt")
            Check.TimestampField -> if (value is Instant) null else type(value, "tidsstämpel")
            Check.AnyText -> if (value is String) null else type(value, "text")
            Check.ShortText -> text(value, TextLimits.SHORT)
            Check.LongText -> text(value, TextLimits.LONG)
            Check.DateText -> if (value is String) (if (parseDate(value) == null) "ogiltigt datum" else null) else type(value, "datum")
            Check.ClockText -> if (value is String) (if (parseClock(value) == null) "ogiltigt klockslag" else null) else type(value, "klockslag")
            is Check.Range -> whole(value) ?: (value as Number).toLong().let { if (it in check.range) null else "utanför intervallet ${check.range}: $it" }
            is Check.Min -> whole(value) ?: (value as Number).toLong().let { if (it >= check.min) null else "under ${check.min}: $it" }
            is Check.OneOf -> if (value is String) (if (value in check.values) null else "okänt värde (${value.length} tecken)") else type(value, "enum")
            is Check.IntList -> list(value, check.max) ?: return elements(value, path) { i, v -> check(Check.Range(check.range), v, "$path[$i]") }
            is Check.EnumList -> list(value, check.max) ?: return elements(value, path) { i, v -> check(Check.OneOf(check.values), v, "$path[$i]") }
            is Check.Nested -> if (value is Map<*, *>) return check(check.shape, asDoc(value), "$path.") else type(value, "objekt")
            is Check.IntMap -> if (value is Map<*, *>) return check(Shape(check.keys.associateWith { Check.IntField }, closed = true), asDoc(value), "$path.") else type(value, "objekt")
            is Check.ListOf -> list(value, check.max, check.exact) ?: return elements(value, path) { i, element ->
                if (element is Map<*, *>) check(check.shape, asDoc(element), "$path[$i].") else listOf(Violation("$path[$i]", type(element, "objekt")))
            }
        }
        return listOfNotNull(reason?.let { Violation(path, it) })
    }

    /** Brotten i varje element; ett `null`-element saknas (rules läser elementen utan `none`). */
    private fun elements(list: Any, path: String, check: (Int, Any) -> List<Violation>): List<Violation> =
        (list as List<*>).withIndex().flatMap { (i, element) -> if (element == null) listOf(Violation("$path[$i]", "saknas")) else check(i, element) }

    private fun whole(value: Any): String? = if (value is Int || value is Long) null else type(value, "heltal")

    private fun text(value: Any, limit: Int): String? = when {
        value !is String -> type(value, "text")
        value.length > limit -> "för lång text: ${value.length} tecken (högst $limit)"
        else -> null
    }

    private fun list(value: Any, max: Int, exact: Int? = null): String? = when {
        value !is List<*> -> type(value, "lista")
        exact != null && value.size != exact -> "fel antal element: ${value.size} (ska vara $exact)"
        value.size > max -> "för många element: ${value.size} (högst $max)"
        else -> null
    }

    private fun type(value: Any?, expected: String) = "fel typ: ${value?.let { it::class.simpleName } ?: "null"} (väntat $expected)"

    private val RESERVED_ID = Regex("^__.*__$")
    private const val MAX_ID_BYTES = 1500
}
