package se.partee71.dagboken.core.schema

import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Instant
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Test
import se.partee71.dagboken.core.schema.DocumentRules.Check

/**
 * [DocumentRules] är `firestore.rules` i Kotlin: fält för fält per samling, konstanterna, enum-listorna
 * och datum-/klockslagsmönstren läses ur rules och jämförs. Driver tabellen eller rules ifrån varandra
 * blir testet rött, så konverterarens validering alltid motsvarar det servern nekar (OMB-3).
 */
class DocumentRulesTest {

    private val rules = File("../firestore.rules").readText()

    /** `function namn(args) { kropp }` – kroppen till den klammerparentes som stänger funktionen (mönster som `{4}` är balanserade). */
    private val functions: Map<String, String> = buildMap {
        for (match in Regex("""function (\w+)\([^)]*\) \{""").findAll(rules)) {
            var depth = 1
            var end = match.range.last + 1
            while (depth > 0) {
                when (rules[end]) {
                    '{' -> depth++
                    '}' -> depth--
                }
                end++
            }
            put(match.groupValues[1], rules.substring(match.range.last + 1, end - 1))
        }
    }

    private fun rulesList(function: String): List<String> = Regex("'([^']*)'").findAll(functions.getValue(function)).map { it.groupValues[1] }.toList()

    private fun constant(function: String): Int = Regex("""return (\d+);""").find(functions.getValue(function))!!.groupValues[1].toInt()

    /** `nullOrX(d.get('f', null), args…)` → kontrollen för toppnivåfält. */
    private fun topLevelCheck(checker: String, args: List<String>): Check = when (checker) {
        "nullOrInt" -> Check.IntField
        "nullOrBool" -> Check.BoolField
        "nullOrTime" -> Check.TimestampField
        "nullOrShort" -> Check.ShortText
        "nullOrLong" -> Check.LongText
        "nullOrDate" -> Check.DateText
        "nullOrClock" -> Check.ClockText
        "nullOrRange" -> Check.Range(args[0].toInt()..args[1].toInt())
        "nullOrMin" -> Check.Min(args[0].toInt())
        "nullOrIn" -> Check.OneOf(rulesList(args[0].removeSuffix("()")))
        else -> error("okänd kontroll $checker")
    }

    /** `isX(d.f)` → kontrollen för nästlade fält. */
    private fun nestedCheck(function: String, args: List<String>): Check = when (function) {
        "isSymptoms" -> Check.ListOf(DocumentRules.SYMPTOM, max = constant("maxSymptoms"))
        "isBoosts" -> Check.ListOf(DocumentRules.BOOST, max = constant("maxBoosts"))
        "isSchedule" -> Check.Nested(DocumentRules.SCHEDULE)
        "isPeriod" -> Check.Nested(DocumentRules.PERIOD)
        "isTheme" -> Check.Nested(DocumentRules.THEME)
        "isReminders" -> Check.Nested(DocumentRules.REMINDERS)
        "isProfile" -> Check.Nested(DocumentRules.PROFILE)
        "isLegacy" -> Check.Nested(DocumentRules.LEGACY)
        "isEnumList" -> Check.EnumList(rulesList(args[0].removeSuffix("()")), args[1].toInt())
        else -> error("okänd kontroll $function")
    }

    /** Fältkontrollerna i en `valid…`-funktion, inklusive de den ärver från validEntry/validPost. */
    private fun fieldsOf(function: String): Map<String, Check> {
        val body = functions.getValue(function)
        val inherited = Regex("""valid(Entry|Post)\(d, w\)""").find(body)?.let { fieldsOf("valid${it.groupValues[1]}") }.orEmpty()
        val simple = Regex("""\(!\('(\w+)' in w\) \|\| (nullOr\w+)\(d\.get\('\1', null\)((?:, [^,()]+(?:\(\))?)*)\)\)""").findAll(body)
            .associate { m -> m.groupValues[1] to topLevelCheck(m.groupValues[2], m.groupValues[3].split(", ").filter { it.isNotBlank() }) }
        val nested = Regex("""\(!\('(\w+)' in w\) \|\| d\.get\('\1', null\) == null \|\| (\w+)\(d\.\1((?:, [^,()]+(?:\(\))?)*)\)\)""").findAll(body)
            .associate { m -> m.groupValues[1] to nestedCheck(m.groupValues[2], m.groupValues[3].split(", ").filter { it.isNotBlank() }) }
        return inherited + simple + nested
    }

    /** Samling → `valid…`-funktionen dess match-regel anropar. */
    private val validators: Map<String, String> =
        Regex("""match /(\w+)/\{\w+\} \{[^{}]*?(valid\w+)\(request\.resource\.data\)""").findAll(rules).associate { it.groupValues[1] to it.groupValues[2] } - "users"

    @Test
    fun `varje samling i rules har samma fältkontroller som DocumentRules, och tvärtom`() {
        assertEquals(CollectionNames.USER_COLLECTIONS.toSet() + CollectionNames.CHECKINS, validators.keys)
        for ((collection, function) in validators) {
            assertEquals(fieldsOf(function), DocumentRules.FIELDS.getValue(collection), "$collection ($function)")
        }
        assertEquals(validators.keys + CollectionNames.USERS, DocumentRules.FIELDS.keys)
    }

    @Test
    fun `användardokumentet - schemaVersion heltal från 1 och createdAt tidsstämpel`() {
        val body = functions.getValue("validUser")
        assertTrue(Regex("""d\.schemaVersion is int\s*&& d\.schemaVersion >= 1""").containsMatchIn(body))
        assertTrue("nullOrTime(d.get('createdAt', null))" in body)
        assertTrue("(!('legacyMigration' in w) || (noLegacyMigrationYet() && isLegacyMigration(d.legacyMigration)))" in body, "markören sätts bara när den saknas")
        assertEquals(
            mapOf("schemaVersion" to Check.Min(1), "createdAt" to Check.TimestampField, "legacyMigration" to Check.Nested(DocumentRules.LEGACY_MIGRATION)),
            DocumentRules.FIELDS.getValue(CollectionNames.USERS),
        )
        val marker = functions.getValue("isLegacyMigration")
        assertTrue("m.get('completedAt', null) is timestamp && m.completedAt == request.time" in marker, "serverns tid, aldrig klientens")
        assertTrue("m.get('source', null) in legacySources()" in marker)
        assertTrue("nullOrTime(m.get('sourceCreatedAt', null)) && nullOrShort(m.get('appVersion', null))" in marker)
        assertTrue("m.keys().hasOnly(['completedAt', 'source', 'sourceCreatedAt', 'appVersion', 'counts'])" in marker, "exakt markörens fält")
        assertTrue(DocumentRules.LEGACY_MIGRATION.closed)
        assertTrue("isCounts(m.counts)" in marker)
        val counts = functions.getValue("isCounts")
        assertTrue("c.keys().hasOnly(countCollections())" in counts)
        assertEquals(DocumentRules.COUNT_COLLECTIONS, rulesList("countCollections"))
        for (name in DocumentRules.COUNT_COLLECTIONS) assertTrue("(!('$name' in c) || c.$name is int)" in counts, name)
        assertEquals(
            listOf(
                DocumentRules.Violation("legacyMigration.counts.doses", "fel typ: String (väntat heltal)"),
                DocumentRules.Violation("legacyMigration.counts.okänd", "okänt fält"),
                DocumentRules.Violation("legacyMigration.extra", "okänt fält"),
            ),
            DocumentRules.validate(
                CollectionNames.USERS,
                mapOf("schemaVersion" to 1, "legacyMigration" to mapOf("completedAt" to kotlin.time.Instant.fromEpochSeconds(1), "source" to "room", "counts" to mapOf("doses" to "4", "okänd" to 1), "extra" to 1)),
            ),
        )
        assertEquals(setOf("completedAt", "source"), DocumentRules.LEGACY_MIGRATION.required)
        assertEquals(rulesList("legacySources"), (DocumentRules.LEGACY_MIGRATION.fields.getValue("source") as Check.OneOf).values)
        assertEquals(
            listOf(DocumentRules.Violation("legacyMigration.completedAt", "saknas"), DocumentRules.Violation("legacyMigration.counts", "fel typ: Int (väntat objekt)")),
            DocumentRules.validate(CollectionNames.USERS, mapOf("schemaVersion" to 1, "legacyMigration" to mapOf("source" to "room", "counts" to 3))),
        )
        assertEquals(listOf(DocumentRules.Violation("schemaVersion", "saknas")), DocumentRules.validate(CollectionNames.USERS, emptyMap()))
        assertEquals(listOf(DocumentRules.Violation("schemaVersion", "under 1: 0")), DocumentRules.validate(CollectionNames.USERS, mapOf("schemaVersion" to 0)))
    }

    @Test
    fun `fältträdet per samling är codecens fält med underträd för nästlade objekt`() {
        val settings = DocumentRules.fieldTree(CollectionNames.SETTINGS)
        assertEquals(DocumentRules.FIELDS.getValue(CollectionNames.SETTINGS).keys, settings.fields.keys)
        assertEquals(DocumentRules.THEME.fields.keys, settings.fields.getValue("theme")!!.fields.keys)
        assertEquals(DocumentRules.REMINDERS.fields.keys, settings.fields.getValue("reminders")!!.fields.keys)
        assertEquals(null, settings.fields.getValue("reminders")!!.fields.getValue("medSlots"), "listor jämförs hela")
        val doses = DocumentRules.fieldTree(CollectionNames.DOSES)
        assertEquals(DocumentRules.FIELDS.getValue(CollectionNames.DOSES).keys, doses.fields.keys)
        assertTrue(doses.fields.values.all { it == null })
        assertEquals(null, DocumentRules.fieldTree(CollectionNames.USERS).fields.getValue("legacyMigration")!!.fields.getValue("counts"))
    }

    @Test
    fun `konstanterna och mönstren är rules`() {
        assertEquals(constant("maxFields"), DocumentRules.MAX_FIELDS)
        assertEquals(constant("maxSymptoms"), DocumentRules.MAX_SYMPTOMS)
        assertEquals(constant("maxBoosts"), DocumentRules.MAX_BOOSTS)
        assertEquals(constant("maxShort"), TextLimits.SHORT)
        assertEquals(constant("maxLong"), TextLimits.LONG)
        assertEquals(DocumentRules.DATE.pattern, Regex("""v\.matches\('([^']+)'\)""").find(functions.getValue("isDate"))!!.groupValues[1])
        assertEquals(DocumentRules.CLOCK.pattern, Regex("""v\.matches\('([^']+)'\)""").find(functions.getValue("isClock"))!!.groupValues[1])
    }

    @Test
    fun `de nästlade objekten är rules - symptom, doshöjning, schema, period, tema, påminnelser, profil, legacy`() {
        val symptom = functions.getValue("isSymptom")
        assertTrue("s.optionId is string" in symptom && "(s.score == null || (s.score is int && s.score >= ${DocumentRules.SCORE.first} && s.score <= ${DocumentRules.SCORE.last}))" in symptom)
        assertTrue("(s.customText == null || s.customText is string)" in symptom)
        assertEquals(setOf("optionId"), DocumentRules.SYMPTOM.required)
        assertEquals(setOf("score"), DocumentRules.SYMPTOM.nullable, "score läses utan get i rules: nyckeln krävs, null godtas")
        val boost = functions.getValue("isBoost")
        assertTrue("b.id is string" in boost && "(b.start == null || isDate(b.start))" in boost && "(b.end == null || isDate(b.end))" in boost && "b.dose is string && b.unit is string" in boost)
        assertEquals(setOf("id", "dose", "unit"), DocumentRules.BOOST.required)
        val schedule = functions.getValue("isSchedule")
        assertTrue("m.repeat in repeats()" in schedule && "isEnumList(m.days, [${DocumentRules.WEEKDAYS.joinToString(", ")}], ${DocumentRules.WEEKDAYS.count()})" in schedule && "atLeast(m.intervalDays, 0)" in schedule)
        assertEquals(rulesList("repeats"), (DocumentRules.SCHEDULE.fields.getValue("repeat") as Check.OneOf).values)
        val period = functions.getValue("isPeriod")
        assertTrue("isDate(m.start)" in period && "isDate(m.end)" in period)
        val theme = functions.getValue("isTheme")
        assertTrue("m.mode in themeModes()" in theme && "inRange(m.lightStartHour, ${DocumentRules.HOUR.first}, ${DocumentRules.HOUR.last})" in theme && "inRange(m.darkStartHour, 0, 23)" in theme && "m.isDarkTheme is bool" in theme)
        assertEquals(rulesList("themeModes"), (DocumentRules.THEME.fields.getValue("mode") as Check.OneOf).values)
        val reminder = functions.getValue("isReminder")
        assertTrue("r.get(key, null) in values && r.get('enabled', null) is bool && isClock(r.get('time', null))" in reminder)
        val medSlots = (DocumentRules.REMINDERS.fields.getValue("medSlots") as Check.ListOf)
        val occasions = (DocumentRules.REMINDERS.fields.getValue("screeningOccasions") as Check.ListOf)
        assertTrue("l.size() == ${medSlots.exact}" in functions.getValue("isMedSlots") && "isReminder(l[0], 'slot', scheduledSlots())" in functions.getValue("isMedSlots"))
        assertTrue("l.size() == ${occasions.exact}" in functions.getValue("isOccasions") && "isReminder(l[0], 'occasion', occasions())" in functions.getValue("isOccasions"))
        assertEquals(rulesList("scheduledSlots"), (medSlots.shape.fields.getValue("slot") as Check.OneOf).values)
        assertEquals(rulesList("occasions"), (occasions.shape.fields.getValue("occasion") as Check.OneOf).values)
        assertEquals(setOf("slot", "enabled", "time"), medSlots.shape.required)
        val reminders = functions.getValue("isReminders")
        assertTrue("m.medsEnabled is bool" in reminders && "isClock(m.periodReminderTime)" in reminders)
        val profile = functions.getValue("isProfile")
        assertTrue("m.birthYear is int" in profile && "m.sex in sexes()" in profile)
        assertEquals(rulesList("sexes"), (DocumentRules.PROFILE.fields.getValue("sex") as Check.OneOf).values)
        val legacy = functions.getValue("isLegacy")
        assertTrue("nullOrBool(m.get('dynamicColor', null))" in legacy && "nullOrLong(m.get('sheetsConfig', null))" in legacy)
    }

    @Test
    fun `tools-db-fixturen (alla samlingar, varje fält) godtas - samma dokument som rules-testet skriver`() {
        for (doc in ExportFormat.decode(File("../tools/db/test/fixtures/user.json").readText())) {
            assertEquals(emptyList(), DocumentRules.validate(CollectionNames.collectionOf(doc.path), doc.data), doc.path)
        }
    }

    @Test
    fun `brott rapporteras med fältväg och skäl utan innehållet`() {
        val activity = Samples.entry(CollectionNames.ACTIVITIES).encoded()
        fun violations(vararg changes: Pair<String, Any?>) = DocumentRules.validate(CollectionNames.ACTIVITIES, activity + changes)
        val longNote = violations("note" to "HEMLIGT ".repeat(700)).single()
        assertEquals(DocumentRules.Violation("note", "för lång text: 5600 tecken (högst ${TextLimits.LONG})"), longNote)
        assertFalse("HEMLIGT" in longNote.reason)
        assertEquals(listOf(DocumentRules.Violation("energy", "utanför intervallet -10..10: 11")), violations("energy" to 11))
        assertEquals(listOf(DocumentRules.Violation("energy", "fel typ: Double (väntat heltal)")), violations("energy" to 5.5))
        assertEquals(listOf(DocumentRules.Violation("minutes", "under 0: -1")), violations("minutes" to -1))
        assertEquals(listOf(DocumentRules.Violation("date", "ogiltigt datum")), violations("date" to "2026-02-30"), "strängare än rules mönster: dagen finns inte")
        assertEquals(listOf(DocumentRules.Violation("time", "ogiltigt klockslag")), violations("time" to "9:00"))
        assertEquals(listOf(DocumentRules.Violation("createdAt", "fel typ: String (väntat tidsstämpel)")), violations("createdAt" to "2026-01-01"))
        assertEquals(listOf(DocumentRules.Violation("recovering", "fel typ: Int (väntat sant/falskt)")), violations("recovering" to 1))
        val symptoms = List(51) { mapOf("optionId" to "s", "score" to 1, "customText" to null) }
        assertEquals(listOf(DocumentRules.Violation("symptoms", "för många element: 51 (högst 50)")), violations("symptoms" to symptoms))
        assertEquals(listOf(DocumentRules.Violation("symptoms[1].score", "utanför intervallet 0..10: 11")), violations("symptoms" to symptoms.take(1) + mapOf("optionId" to "s", "score" to 11, "customText" to null)))
        assertEquals(listOf(DocumentRules.Violation("symptoms[0].optionId", "saknas")), violations("symptoms" to listOf(mapOf("score" to 1))))
        assertEquals(listOf(DocumentRules.Violation("symptoms[0]", "fel typ: String (väntat objekt)")), violations("symptoms" to listOf("huvudvärk")))
        assertEquals(listOf(DocumentRules.Violation("symptoms", "fel typ: String (väntat lista)")), violations("symptoms" to "huvudvärk"))
        assertEquals(emptyList(), violations("note" to null, "framtidaFalt" to mapOf("a" to 1)), "null och okända fält är tillåtna")
        val many = (1..41).associate { "f$it" to it }
        assertEquals(listOf(DocumentRules.Violation("", "för många fält: 41 (högst 40)")), DocumentRules.validate(CollectionNames.DOSES, many))
    }

    @Test
    fun `inställningar och recept - nästlade brott och enum-värden`() {
        val settings = Samples.entry(CollectionNames.SETTINGS).encoded()
        fun settingsWith(group: String, change: Pair<String, Any?>) = DocumentRules.validate(CollectionNames.SETTINGS, settings + (group to asDoc(settings[group]) + change))
        assertEquals(listOf(DocumentRules.Violation("theme.mode", "okänt värde (5 tecken)")), settingsWith("theme", "mode" to "sepia"))
        assertEquals(listOf(DocumentRules.Violation("theme.lightStartHour", "utanför intervallet 0..23: 24")), settingsWith("theme", "lightStartHour" to 24))
        assertEquals(listOf(DocumentRules.Violation("reminders.medSlots", "fel antal element: 5 (ska vara 6)")), settingsWith("reminders", "medSlots" to asDoc(settings["reminders"]).docs("medSlots").take(5)))
        val rows = asDoc(settings["reminders"]).docs("screeningOccasions").mapIndexed { i, row -> if (i == 2) row - "time" else row }
        assertEquals(listOf(DocumentRules.Violation("reminders.screeningOccasions[2].time", "saknas")), settingsWith("reminders", "screeningOccasions" to rows))
        assertEquals(listOf(DocumentRules.Violation("legacy.sheetsConfig", "för lång text: 5001 tecken (högst 5000)")), settingsWith("legacy", "sheetsConfig" to "x".repeat(5001)))
        assertEquals(listOf(DocumentRules.Violation("profile", "fel typ: String (väntat objekt)")), DocumentRules.validate(CollectionNames.SETTINGS, settings + ("profile" to "kvinna")))

        val prescription = Samples.entry(CollectionNames.PRESCRIPTIONS).encoded()
        fun prescriptionWith(vararg changes: Pair<String, Any?>) = DocumentRules.validate(CollectionNames.PRESCRIPTIONS, prescription + changes)
        assertEquals(listOf(DocumentRules.Violation("slots[1]", "okänt värde (6 tecken)")), prescriptionWith("slots" to listOf("morning", "brunch")))
        assertEquals(listOf(DocumentRules.Violation("slots", "för många element: 8 (högst 7)")), prescriptionWith("slots" to List(8) { "morning" }))
        assertEquals(listOf(DocumentRules.Violation("schedule.days[0]", "utanför intervallet 1..7: 0")), prescriptionWith("schedule" to asDoc(prescription["schedule"]) + ("days" to listOf(0))))
        assertEquals(listOf(DocumentRules.Violation("schedule.repeat", "okänt värde (7 tecken)")), prescriptionWith("schedule" to asDoc(prescription["schedule"]) + ("repeat" to "monthly")))
        assertEquals(listOf(DocumentRules.Violation("boosts[0].end", "ogiltigt datum")), prescriptionWith("boosts" to listOf(mapOf("id" to "b", "start" to null, "end" to "igår", "dose" to "1", "unit" to "mg"))))
        assertEquals(listOf(DocumentRules.Violation("boosts[0].dose", "saknas")), prescriptionWith("boosts" to listOf(mapOf("id" to "b", "unit" to "mg"))))
        assertEquals(emptyList(), prescriptionWith("createdAt" to Instant.parse("2026-01-01T00:00:00Z"), "schedule" to null, "period" to null))
    }

    @Test
    fun `dokument-id som Firestore godtar - samma fixtur som hasValidIds i tools-db (fixtures ids_json)`() {
        val ids = Json.parseToJsonElement(File("../tools/db/test/fixtures/ids.json").readText()).jsonObject
        fun texts(key: String) = ids.getValue(key).jsonArray.map { element ->
            if (element is JsonObject) element.getValue("text").jsonPrimitive.content.repeat(element.getValue("times").jsonPrimitive.int) else element.jsonPrimitive.content
        }
        val valid = texts("valid")
        val invalid = texts("invalid")
        assertTrue(valid.size >= 5 && invalid.size >= 5)
        for (id in valid) assertTrue(DocumentRules.isValidId(id), id)
        for (id in invalid) assertFalse(DocumentRules.isValidId(id), id)
    }

    @Test
    fun `alla brott rapporteras - även flera i samma nästlade objekt och i flera listelement`() {
        val activity = Samples.entry(CollectionNames.ACTIVITIES).encoded()
        val symptoms = listOf(mapOf("optionId" to "s", "score" to 11, "customText" to 3), mapOf("score" to 1.5, "customText" to null))
        assertEquals(
            listOf(
                DocumentRules.Violation("symptoms[0].score", "utanför intervallet 0..10: 11"),
                DocumentRules.Violation("symptoms[0].customText", "fel typ: Int (väntat text)"),
                DocumentRules.Violation("symptoms[1].optionId", "saknas"),
                DocumentRules.Violation("symptoms[1].score", "fel typ: Double (väntat heltal)"),
            ),
            DocumentRules.validate(CollectionNames.ACTIVITIES, activity + ("symptoms" to symptoms)),
        )
        val settings = Samples.entry(CollectionNames.SETTINGS).encoded()
        assertEquals(
            listOf(DocumentRules.Violation("theme.mode", "okänt värde (5 tecken)"), DocumentRules.Violation("theme.darkStartHour", "utanför intervallet 0..23: 24")),
            DocumentRules.validate(CollectionNames.SETTINGS, settings + ("theme" to asDoc(settings["theme"]) + mapOf("mode" to "sepia", "darkStartHour" to 24))),
        )
    }
}
