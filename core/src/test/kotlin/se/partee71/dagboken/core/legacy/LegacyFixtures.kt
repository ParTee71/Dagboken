package se.partee71.dagboken.core.legacy

import java.io.File
import java.lang.reflect.Modifier
import kotlin.reflect.KClass
import kotlinx.serialization.json.Json
import kotlinx.serialization.serializer
import se.partee71.dagboken.core.schema.ExportFormat

/**
 * Konverterarens syntetiska 3.x-fixturer (`tools/db/test/fixtures/legacy/`, OMB-3) och reflektion över
 * 3.x-klasserna – delat av konverterar-, paritets- och fixturtesterna.
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
}
