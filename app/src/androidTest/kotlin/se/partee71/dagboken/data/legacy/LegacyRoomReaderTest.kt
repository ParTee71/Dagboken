package se.partee71.dagboken.data.legacy

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.security.MessageDigest
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.job
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import se.partee71.dagboken.core.legacy.BackupJsonConverter
import se.partee71.dagboken.core.legacy.ConversionResult
import se.partee71.dagboken.core.legacy.LegacyRoomAssembler
import se.partee71.dagboken.core.legacy.LegacyRoomSchema
import se.partee71.dagboken.core.legacy.Row
import se.partee71.dagboken.core.schema.CollectionNames
import se.partee71.dagboken.core.schema.ExportFormat
import se.partee71.dagboken.testing.StuckTestTimeout

/**
 * Legacy-läsaren mot en **riktig** Room-fil i 3.x-schemat (OMB-2): databasen byggs av `room-schema-11.json`
 * (3.x:s egen `11.json`) i WAL-läge med raderna ur `room-v11.json`, så att allt ligger i WAL-filen utan
 * checkpoint – som efter att 3.x-processen dött. Läsaren läser raderna ur WAL, ändrar varken `.db` eller `-wal`
 * (kontrollsumma före och efter), och tillsammans med DataStore-filen ger raderna exakt konverterarens dokument
 * (`backup-v2.expected.json`). En annan `user_version` är ett eget utfall.
 */
@RunWith(AndroidJUnit4::class)
class LegacyRoomReaderTest {

    @get:Rule(order = StuckTestTimeout.OUTERMOST)
    val timeout = StuckTestTimeout.rule()

    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
    private val assets = InstrumentationRegistry.getInstrumentation().context.assets
    private val fixture = Json.parseToJsonElement(assets.open("room-v11.json").bufferedReader().readText()).jsonObject
    private val schema = Json.parseToJsonElement(assets.open("room-schema-11.json").bufferedReader().readText()).jsonObject.getValue("database").jsonObject
    private val dbFile: File get() = context.getDatabasePath(LegacyRoomSchema.DATABASE_NAME)
    private val walFile: File get() = File(dbFile.path + LegacyRoomReader.WAL_SUFFIX)
    private val prefsFile: File get() = context.preferencesDataStoreFile(LegacyRoomSchema.PREFERENCES_NAME)

    @Suppress("UNCHECKED_CAST")
    private val tables = ExportFormat.fromJson(fixture.getValue("tables")) as Map<String, List<Row>>

    @Suppress("UNCHECKED_CAST")
    private val preferences = ExportFormat.fromJson(fixture.getValue("preferences")) as Map<String, Any?>

    private fun test(block: suspend () -> Unit) = runBlocking { withTimeout(30_000) { block() } }

    @Before
    fun cleanFiles() = deleteFiles()

    @After
    fun deleteFiles() {
        // Testets filer får inte ligga kvar: appens startkontroll (NAV-6) skulle annars se en 3.x-databas.
        listOf(dbFile, walFile, File(dbFile.path + "-shm"), File(dbFile.path + "-journal"), prefsFile).forEach { it.delete() }
    }

    /** Bygger `dagboken.db` som 3.x: schemats `createSql`, `room_master_table`, `user_version` = [version], raderna i WAL. */
    private fun createRoomFile(version: Int = LegacyRoomSchema.DATABASE_VERSION) {
        dbFile.parentFile?.mkdirs()
        val snapshot = File(context.cacheDir, "snapshot").apply { deleteRecursively(); mkdirs() }
        SQLiteDatabase.openOrCreateDatabase(dbFile, null).use { db ->
            check(db.enableWriteAheadLogging()) { "WAL kunde inte slås på" }
            for (entity in schema.getValue("entities").jsonArray) {
                val table = entity.jsonObject.getValue("tableName").jsonPrimitive.content
                db.execSQL(entity.jsonObject.getValue("createSql").jsonPrimitive.content.replace("\${TABLE_NAME}", table))
                for (index in entity.jsonObject.getValue("indices").jsonArray) {
                    db.execSQL(index.jsonObject.getValue("createSql").jsonPrimitive.content.replace("\${TABLE_NAME}", table))
                }
            }
            for (sql in schema.getValue("setupQueries").jsonArray) db.execSQL(sql.jsonPrimitive.content)
            db.version = version
            db.beginTransaction()
            try {
                for ((table, rows) in tables) {
                    for (row in rows) {
                        val values = ContentValues()
                        for ((column, value) in row) {
                            when (value) {
                                null -> values.putNull(column)
                                is String -> values.put(column, value)
                                is Long -> values.put(column, value)
                                else -> error("oväntad fixturtyp ${value::class.simpleName}")
                            }
                        }
                        check(db.insertOrThrow(table, null, values) >= 0)
                    }
                }
                db.setTransactionSuccessful()
            } finally {
                db.endTransaction()
            }
            // Ögonblicksbild medan anslutningen är öppen: WAL-filen bär raderna, inte huvudfilen – som efter processdöd.
            for (file in listOf(dbFile, walFile)) file.copyTo(File(snapshot, file.name), overwrite = true)
        }
        // Stängningen checkpointade originalet; lägg tillbaka ögonblicksbilden utan -shm (byggs om vid öppning).
        for (file in listOf(dbFile, walFile)) File(snapshot, file.name).copyTo(file, overwrite = true)
        File(dbFile.path + "-shm").delete()
        check(walFile.length() > 0) { "WAL-filen ska bära raderna" }
    }

    private suspend fun createPrefsFile() {
        val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        try {
            val store = PreferenceDataStoreFactory.create(scope = scope) { prefsFile }
            store.edit { prefs ->
                for ((key, value) in preferences) {
                    when (value) {
                        is Boolean -> prefs[booleanPreferencesKey(key)] = value
                        is String -> prefs[stringPreferencesKey(key)] = value
                        is Long -> prefs[intPreferencesKey(key)] = value.toInt()
                        else -> error("oväntad fixturtyp ${value?.let { it::class.simpleName }}")
                    }
                }
            }
        } finally {
            scope.coroutineContext.job.cancelAndJoin()
        }
    }

    private fun checksum(file: File): String = MessageDigest.getInstance("SHA-256").digest(file.readBytes()).joinToString("") { "%02x".format(it) }

    private fun checksums() = mapOf("db" to checksum(dbFile), "wal" to checksum(walFile), "prefs" to checksum(prefsFile))

    @Test
    fun room_filen_och_datastore_filen_las_ur_wal_utan_att_andras_och_ger_konverterarens_dokument() = test {
        createRoomFile()
        createPrefsFile()
        val before = checksums()
        val reader = LegacyRoomReader(context, Dispatchers.IO)
        assertTrue(reader.exists())
        val read = assertIs<LegacyRoomRead.Tables>(reader.read())
        val prefs = LegacyPreferencesReader(context, Dispatchers.IO).read()
        assertEquals(before, checksums(), ".db, -wal och DataStore-filen är oförändrade")

        // Raderna som skrevs kommer tillbaka – de låg bara i WAL.
        assertEquals(tables.mapValues { it.value.size }, read.tables.mapValues { it.value.size })
        for ((table, rows) in tables) assertEquals(rows.toSet(), read.tables.getValue(table).toSet(), table)
        // DataStore ger Int där fixturen har Long; i övrigt samma nycklar och värden.
        assertEquals(preferences.mapValues { (_, v) -> if (v is Long) v.toInt() else v }, prefs)

        val assembly = LegacyRoomAssembler.assemble(read.tables, prefs, fixture.getValue("createdAt").jsonPrimitive.content)
        assertEquals(emptyList(), assembly.warnings)
        val result = assertIs<ConversionResult.Converted>(BackupJsonConverter.convert(assembly.backup, "uid-legacy"))
        val expected = ExportFormat.decode(assets.open("backup-v2.expected.json").bufferedReader().readText()).associateBy { it.path }
        assertEquals(expected.keys, result.documents.map { it.path }.toSet())
        for (document in result.documents) {
            val want = expected.getValue(document.path).data
            for (field in want.keys + document.data.keys) assertEquals(ExportFormat.toJson(want[field]), ExportFormat.toJson(document.data[field]), "${document.path}.$field")
        }
        assertEquals(expected.size - 1, result.documents.count { CollectionNames.collectionOf(it.path) != CollectionNames.USERS })
    }

    @Test
    fun en_annan_user_version_ar_ett_eget_utfall_och_laser_inget() = test {
        createRoomFile(version = 10)
        val before = checksums().filterKeys { it != "prefs" }
        assertEquals(LegacyRoomRead.WrongVersion(10), LegacyRoomReader(context, Dispatchers.IO).read())
        assertEquals(before, checksums().filterKeys { it != "prefs" })
    }

    @Test
    fun utan_filer_saknas_databasen_och_installningarna_ar_tomma() = test {
        val reader = LegacyRoomReader(context, Dispatchers.IO)
        assertFalse(reader.exists())
        assertEquals(LegacyRoomRead.Missing, reader.read())
        assertEquals(emptyMap(), LegacyPreferencesReader(context, Dispatchers.IO).read())
    }
}
