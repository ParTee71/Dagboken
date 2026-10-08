package se.partee71.dagboken.core.legacy

import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.encodeToJsonElement
import org.junit.Test
import se.partee71.dagboken.core.legacy.threex.BackupJson as BackupJson3x

/**
 * Kopian av 3.x-datan som migreringsskärmen sparar (OMB-8) ska kunna läsas av 3.27.0:s egen import: filen parsas
 * här med 3.x:s egna dataklasser och serialiseringsinställningar (`threex/BackupJson3x.kt`, kopierade från branchen
 * `legacy`; `MigrationViewModel.importFromFile` läste filen som UTF-8-text och avkodade med
 * `Json { ignoreUnknownKeys = true }`). Fixturen med alla fält satta ger fältvis likhet efter parse.
 */
class Backup3xCompatibilityTest {

    /** 3.x `dagbokenJson()` (`di/AppModule.kt` på `legacy`). */
    private val json3x = Json { ignoreUnknownKeys = true }

    private fun assertReadableBy3x(backup: BackupJson, name: String) {
        val text = BackupJson.encode(backup)
        val bytes = text.toByteArray(Charsets.UTF_8)
        assertFalse(bytes.size >= 3 && bytes[0] == 0xEF.toByte() && bytes[1] == 0xBB.toByte() && bytes[2] == 0xBF.toByte(), "$name: ingen BOM")
        assertEquals(text, String(bytes, Charsets.UTF_8), "$name: UTF-8 utan förlust (å, ä, ö)")
        val parsed = json3x.decodeFromString<BackupJson3x>(text)
        // Fält för fält: det 3.x läste, skrivet igen med 3.x:s klasser, är exakt det filen innehåller.
        assertEquals(Json.parseToJsonElement(text), json3x.encodeToJsonElement(parsed), "$name: varje fält överlever 3.x:s parse")
        assertEquals(backup.version, parsed.version, name)
        assertEquals(backup.createdAt, parsed.createdAt, name)
        assertEquals(backup.aktiviteter.map { it.id }, parsed.aktiviteter.map { it.id }, name)
        assertEquals(backup.aktiviteterOptionsV2?.map { it.name to it.isFavorite }, parsed.aktiviteterOptionsV2?.map { it.name to it.isFavorite }, name)
        assertEquals(backup.notes.map { Triple(it.target, it.entityId, it.text) }, parsed.notes.map { Triple(it.target, it.entityId, it.text) }, name)
        assertEquals(backup.settings?.themeMode, parsed.settings?.themeMode, name)
        assertEquals(backup.medicinRecipes.flatMap { r -> r.dosperioder.map { it.id to it.slutDatum } }, parsed.medicinRecipes.flatMap { r -> r.dosperioder.map { it.id to it.slutDatum } }, name)
        assertEquals(backup.medNotificationConfigs?.map { it.tidpunkt }, parsed.medNotificationConfigs?.map { it.tidpunkt }, name)
    }

    @Test
    fun `kopian ur Room-raderna läses av 3x klasser och parser med varje fält intakt`() {
        val room = LegacyFixtures.room()
        val assembled = LegacyRoomAssembler.assemble(room.tables, room.preferences, room.createdAt).backup
        assertEquals(2, assembled.version, "formatversion v2")
        assertReadableBy3x(assembled, "room-v11")
        assertEquals(room.tables.mapValues { it.value.size }, LegacyRoomAssembler.tableCounts(assembled))
    }

    @Test
    fun `fixturerna med varje 3x-fält satt går samma väg - v2 och v1`() {
        for (name in listOf("backup-v2", "backup-v1")) assertReadableBy3x(LegacyFixtures.backup(name), name)
    }

    @Test
    fun `3x-klasserna i testet har exakt samma fält som konverterarens 3x-klasser - driver kopian ur led fäller testet`() {
        val ours = LegacyFixtures.BACKUP_CLASSES.associate { it.simpleName!! to LegacyFixtures.fields(it).map { f -> f.name }.toSet() }
        val theirs = listOf(
            BackupJson3x::class, se.partee71.dagboken.core.legacy.threex.SettingsBackup::class, se.partee71.dagboken.core.legacy.threex.AktivitetJson::class,
            se.partee71.dagboken.core.legacy.threex.MedicinJson::class, se.partee71.dagboken.core.legacy.threex.ReceptJson::class,
            se.partee71.dagboken.core.legacy.threex.DosperiodJson::class, se.partee71.dagboken.core.legacy.threex.FavoritJson::class,
            se.partee71.dagboken.core.legacy.threex.SjukdomsEpisodJson::class, se.partee71.dagboken.core.legacy.threex.SjukdomsIncheckningJson::class,
            se.partee71.dagboken.core.legacy.threex.HandelseJson::class, se.partee71.dagboken.core.legacy.threex.NoteJson::class,
            se.partee71.dagboken.core.legacy.threex.ScreeningEventConfigJson::class, se.partee71.dagboken.core.legacy.threex.MedNotificationConfigJson::class,
            se.partee71.dagboken.core.legacy.threex.SymptomOptionBackup::class,
        ).associate { it.simpleName!! to LegacyFixtures.fields(it).map { f -> f.name }.toSet() }
        assertEquals(theirs, ours)
        assertTrue(theirs.isNotEmpty())
    }
}
