package se.partee71.dagboken.core.legacy

import java.io.File
import java.lang.reflect.Modifier
import kotlin.reflect.KClass
import kotlin.test.assertEquals
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.serializer
import se.partee71.dagboken.core.schema.ExportFormat

/**
 * Konverterarens syntetiska 3.x-fixturer (`tools/db/test/fixtures/legacy/`, OMB-3) och reflektion över
 * 3.x-klasserna – delat av konverterar-, paritets-, fixtur- och legacy-läsartesterna.
 */
object LegacyFixtures {
    private val dir = File("../tools/db/test/fixtures/legacy")

    /** Användaren i de förväntade exporterna. */
    const val UID = "uid-legacy"

    fun text(name: String): String = dir.resolve(name).readText()

    fun backup(name: String): BackupJson = BackupJson.parse(text("$name.json"))

    /** Den förväntade 4.0-exporten för fixturen [name] (`backup-v2`, `backup-v1`). */
    fun expectedText(name: String): String = text("$name.expected.json")

    fun expected(name: String): List<ExportFormat.Document> = ExportFormat.decode(expectedText(name))

    /** 3.x-databasen i Room-form (`room-v11.json`): tabellrader som SQLite ger dem, DataStore-värden och `createdAt`. */
    class RoomFixture(val tables: Map<String, List<Row>>, val preferences: Map<String, Any?>, val createdAt: String)

    @Suppress("UNCHECKED_CAST")
    fun room(name: String = "room-v11"): RoomFixture {
        val root = Json.parseToJsonElement(text("$name.json")).jsonObject
        return RoomFixture(
            tables = ExportFormat.fromJson(root.getValue("tables")) as Map<String, List<Row>>,
            preferences = ExportFormat.fromJson(root.getValue("preferences")) as Map<String, Any?>,
            createdAt = root.getValue("createdAt").jsonPrimitive.content,
        )
    }

    /** Kolumnnamnen per tabell i Room-schemat v11 (`room-schema-11.json`, kopia av 3.x `11.json`). */
    fun schemaColumns(): Map<String, List<String>> =
        Json.parseToJsonElement(text("room-schema-11.json")).jsonObject.getValue("database").jsonObject.getValue("entities").let { entities ->
            entities.let { it as kotlinx.serialization.json.JsonArray }.associate { entity ->
                val obj = entity.jsonObject
                obj.getValue("tableName").jsonPrimitive.content to
                    (obj.getValue("fields") as kotlinx.serialization.json.JsonArray).map { it.jsonObject.getValue("columnName").jsonPrimitive.content }
            }
        }

    /**
     * Fält för fält per dokument mot den förväntade exporten [name] (ett tydligt fel per avvikelse), och
     * sedan hela filen tecken för tecken med [exportedAt] som `exportedAt`.
     */
    fun assertMatchesExpected(name: String, result: ConversionResult.Converted, exportedAt: kotlin.time.Instant) {
        val expected = expected(name).associateBy { it.path }
        val actual = result.documents.associateBy { it.path }
        assertEquals(expected.keys.sorted(), actual.keys.sorted(), "dokumentens sökvägar")
        for ((path, document) in expected) {
            val got = actual.getValue(path).data
            for (field in document.data.keys + got.keys) {
                assertEquals(ExportFormat.toJson(document.data[field]), ExportFormat.toJson(got[field]), "$path.$field")
            }
        }
        assertEquals(expectedText(name), result.exportJson(exportedAt))
    }

    /** Varje klass i 3.x `BackupJson` (branchen `legacy`, `data/migration/BackupJson.kt`). */
    val BACKUP_CLASSES: List<KClass<*>> = listOf(
        BackupJson::class, SettingsBackup::class, AktivitetJson::class, MedicinJson::class, ReceptJson::class, DosperiodJson::class,
        FavoritJson::class, SjukdomsEpisodJson::class, SjukdomsIncheckningJson::class, HandelseJson::class, NoteJson::class,
        ScreeningEventConfigJson::class, MedNotificationConfigJson::class, SymptomOptionBackup::class,
    )

    /** `Klass.fält` för varje 3.x-fält, i deklarationsordning – paritetstabellens vänsterkolumn. */
    val BACKUP_FIELDS: List<String> = BACKUP_CLASSES.flatMap { klass -> fields(klass).map { "${klass.simpleName}.${it.name}" } }

    /** Klassens fält (inte `Companion`). */
    fun fields(klass: KClass<*>) = klass.java.declaredFields.filterNot { Modifier.isStatic(it.modifiers) }.onEach { it.isAccessible = true }

    /** Instansen med alla standardvärden – som 3.x läste ett tomt objekt. */
    fun defaults(klass: KClass<*>): Any = Json.decodeFromString(serializer(klass.java), "{}")!!

    /** Alla instanser av 3.x-klasserna i [backup], per klass (även de i listor). */
    fun instances(backup: BackupJson): Map<KClass<*>, List<Any>> {
        val found = mutableMapOf<KClass<*>, MutableList<Any>>()
        fun visit(value: Any?) {
            when {
                value == null -> Unit
                value is List<*> -> value.forEach(::visit)
                value::class in BACKUP_CLASSES -> {
                    found.getOrPut(value::class) { mutableListOf() } += value
                    fields(value::class).forEach { visit(it.get(value)) }
                }
            }
        }
        visit(backup)
        return found
    }

    /** `Klass.fält` som har ett icke-default-värde i minst en instans i [backup]. */
    fun fieldsDifferingFromDefault(backup: BackupJson): Set<String> {
        val instances = instances(backup)
        return BACKUP_CLASSES.flatMap { klass ->
            val defaults = defaults(klass)
            instances[klass].orEmpty().flatMap { instance ->
                fields(klass).filter { it.get(instance) != it.get(defaults) }.map { "${klass.simpleName}.${it.name}" }
            }
        }.toSet()
    }
}
