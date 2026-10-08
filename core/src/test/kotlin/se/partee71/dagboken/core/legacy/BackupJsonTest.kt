package se.partee71.dagboken.core.legacy

import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.Test

/**
 * 3.x-klasserna i `:core` läser en backup som 3.x gjorde, och v2-fixturen sätter **varje** 3.x-fält
 * (OMB-3): ett fält som bara har sitt standardvärde skulle kunna tappas i konverteringen utan att
 * jämförelsen mot den förväntade exporten märker det.
 */
class BackupJsonTest {

    private val v2 = LegacyFixtures.backup("backup-v2")

    @Test
    fun `varje fält i varje 3x-klass har ett icke-default-värde i minst en post i v2-fixturen`() {
        val instances = LegacyFixtures.instances(v2)
        assertEquals(LegacyFixtures.BACKUP_CLASSES.toSet(), instances.keys, "varje 3.x-klass förekommer i fixturen")
        for (klass in LegacyFixtures.BACKUP_CLASSES) {
            val defaults = LegacyFixtures.defaults(klass)
            val differing = instances.getValue(klass).flatMap { instance ->
                LegacyFixtures.fields(klass).filter { it.get(instance) != it.get(defaults) }.map { it.name }
            }.toSet()
            assertEquals(LegacyFixtures.fields(klass).map { it.name }.toSet(), differing, "${klass.simpleName}: fält med bara standardvärdet i fixturen")
        }
    }

    @Test
    fun `backupfilens egna fält är alla satta i v2-fixturen`() {
        val defaults = LegacyFixtures.defaults(BackupJson::class)
        for (field in LegacyFixtures.fields(BackupJson::class)) {
            assertTrue(field.get(v2) != field.get(defaults), "BackupJson.${field.name} har standardvärdet")
        }
        assertEquals(2, v2.version)
    }

    @Test
    fun `v1-fixturen är formatversion 1 utan v2-fälten`() {
        val v1 = LegacyFixtures.backup("backup-v1")
        assertEquals(1, v1.version)
        assertNull(v1.settings)
        assertNull(v1.aktiviteterOptionsV2)
        assertNull(v1.screeningEventConfigs)
        assertNull(v1.medNotificationConfigs)
        assertNull(v1.handelseTypOptions)
        assertEquals("Morgon", v1.medicinRecipes.single().tidpunkt)
        assertEquals(emptyList(), v1.medicinRecipes.single().tidpunkter)
    }

    @Test
    fun `okända fält ignoreras och null i ett fält utan null-stöd blir standardvärdet, som 3x`() {
        val backup = BackupJson.parse("""{"version": 2, "nyttFalt": {"a": 1}, "aktiviteter": [{"id": "a1", "okant": true, "spentTime": null, "symptom": null}]}""")
        assertEquals(2, backup.version)
        assertEquals(AktivitetJson(id = "a1"), backup.aktiviteter.single())
    }

    @Test
    fun `saknade fält får 3x standardvärden`() {
        val recept = BackupJson.parse("""{"medicinRecipes": [{"id": "r1"}]}""").medicinRecipes.single()
        assertEquals("dagligen", recept.upprepning)
        assertEquals(2, recept.intervalDagar)
        assertTrue(recept.aktiv)
        assertEquals(4, BackupJson.parse("""{"medicinFavoriter": [{"id": "f1"}]}""").medicinFavoriter.single().minTidMellan)
        assertEquals("aktivitet", BackupJson.parse("""{"aktiviteter": [{"id": "a1"}]}""").aktiviteter.single().type)
    }

    @Test
    fun `encode skriver en 3x-backup som 3x parser läser tillbaka oförändrad - kopian på migreringsskärmen (OMB-7)`() {
        for (name in listOf("backup-v2", "backup-v1")) {
            val backup = LegacyFixtures.backup(name)
            val text = BackupJson.encode(backup)
            assertEquals(backup, BackupJson.parse(text), name)
            // Som 3.x (`encodeDefaults` av): ett standardvärde skrivs inte – v1:s version 1 utelämnas, v2:s version 2 står först.
            if (backup.version != 1) assertTrue(text.startsWith("{\"version\":${backup.version}"), text.take(20))
        }
        val room = LegacyFixtures.room()
        val assembled = LegacyRoomAssembler.assemble(room.tables, room.preferences, room.createdAt).backup
        assertEquals(assembled, BackupJson.parse(BackupJson.encode(assembled)))
    }

    @Test
    fun `en fil som inte är en backup stoppar utan att visa innehållet`() {
        val error = assertFailsWith<IllegalArgumentException> { BackupJson.parse("""{"aktiviteter": "HEMLIGT-INNEHÅLL"}""") }
        assertFalse(error.message.orEmpty().contains("HEMLIGT"))
        assertFailsWith<IllegalArgumentException> { BackupJson.parse("inte json HEMLIGT") }.also { assertFalse(it.message.orEmpty().contains("HEMLIGT")) }
    }
}
