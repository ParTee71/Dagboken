package se.partee71.dagboken.data.migration

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Verifies that BackupJson survives a full JSON encode → decode round-trip.
 * This catches cases where a new entity is added to the backup but its
 * @Serializable annotation or field mapping is broken.
 */
class BackupJsonSerializationTest {

    private val json = Json { ignoreUnknownKeys = true }

    private fun roundTrip(backup: BackupJson): BackupJson =
        json.decodeFromString(json.encodeToString(BackupJson.serializer(), backup))

    private fun fullBackup() = BackupJson(
        version   = 1,
        createdAt = "2026-01-15T09:00:00",
        aktiviteter = listOf(
            AktivitetJson(
                id = "a1", timestamp = "2026-01-15T09:00:00.000Z", datum = "2026-01-15",
                tid = "09:00", aktivitet = "Promenad", energy = 5, stress = 3,
                somatiska = 1, symptom = "Nackvärk:2", aterhamtande = true,
                energitjuv = false, type = "aktivitet", spentTime = 45,
            ),
        ),
        mediciner = listOf(
            MedicinJson(
                id = "m1", timestamp = "2026-01-15T07:00:00.000Z", datum = "2026-01-15",
                tid = "07:00", namn = "Metformin", dos = "500", enhet = "mg",
                tidpunkt = "Morgon", tagen = true, anteckning = "Med mat",
                receptId = "r1", skipped = false,
            ),
        ),
        medicinRecipes = listOf(
            ReceptJson(
                id = "r1", namn = "Vitamin D", dos = "1", enhet = "st",
                tidpunkter = listOf("Morgon", "Kväll"), upprepning = "dagligen",
                dagar = listOf(1, 3, 5), intervalDagar = 2, anteckning = "Med mat",
                aktiv = true, skapad = "2026-01-01",
            ),
        ),
        medicinFavoriter = listOf(
            FavoritJson(
                id = "f1", namn = "Paracetamol", dos = "500", enhet = "mg",
                tidpunkt = "Vid behov", anteckning = "", minTidMellan = 4,
                dispenseringsTid = "08:00", maxDoserPerDag = 3,
            ),
        ),
        aktiviteterOptionsV2 = listOf(
            SymptomOptionBackup("Promenad", isFavorite = true),
            SymptomOptionBackup("Träning", isFavorite = false),
        ),
        symptomOptionsV2 = listOf(
            SymptomOptionBackup("Trötthet", isFavorite = true),
        ),
        sjukdomsepisoder = listOf(
            SjukdomsEpisodJson(
                id = "e1", typ = "migrän", startDatum = "2026-01-10",
                slutDatum = "2026-01-12", anteckning = "Svår vecka",
            ),
        ),
        sjukdomsIncheckningar = listOf(
            SjukdomsIncheckningJson(
                id = "i1", episodId = "e1", datum = "2026-01-10", tid = "12:00",
                svarighetsgrad = 8, symptom = "Yrsel:3", somatiska = 2,
                anteckning = "Tog medicin",
            ),
        ),
        handelser = listOf(
            HandelseJson(
                id = "h1", timestamp = "2026-01-15T14:30:00.000Z", datum = "2026-01-15",
                tid = "14:30", typ = "huvudvärk", svarighetsgrad = 6,
                varaktighetMinuter = 90, triggers = "[\"stress\"]",
                atgarder = "[\"vila\"]", anteckning = "Kom efter möte",
            ),
        ),
        notes = listOf(
            NoteJson(target = "ACTIVITY", entityId = "a1", text = "Mådde bra"),
            NoteJson(target = "MEDICATION", entityId = "m1", text = "Tog med mat"),
        ),
        screeningEventConfigs = listOf(
            ScreeningEventConfigJson(enabled = true, time = "08:00"),
            ScreeningEventConfigJson(enabled = false, time = "21:00"),
        ),
        sheetsConfig = "https://docs.google.com/spreadsheets/d/abc123",
    )

    // ─── full round-trip ──────────────────────────────────────────────────────

    @Test fun `all fields survive json encode and decode`() {
        val result = roundTrip(fullBackup())

        assertEquals(1, result.aktiviteter.size)
        with(result.aktiviteter[0]) {
            assertEquals("a1", id)
            assertEquals("Promenad", aktivitet)
            assertEquals(5, energy)
            assertEquals(45, spentTime)
            assertTrue(aterhamtande)
        }

        assertEquals(1, result.mediciner.size)
        with(result.mediciner[0]) {
            assertEquals("m1", id)
            assertEquals("Metformin", namn)
            assertEquals("r1", receptId)
            assertTrue(tagen)
        }

        assertEquals(1, result.medicinRecipes.size)
        with(result.medicinRecipes[0]) {
            assertEquals("r1", id)
            assertEquals(listOf("Morgon", "Kväll"), tidpunkter)
            assertEquals(listOf(1, 3, 5), dagar)
        }

        assertEquals(1, result.medicinFavoriter.size)
        with(result.medicinFavoriter[0]) {
            assertEquals("f1", id)
            assertEquals(4, minTidMellan)
            assertEquals(3, maxDoserPerDag)
        }

        assertEquals(2, result.aktiviteterOptionsV2!!.size)
        assertTrue(result.aktiviteterOptionsV2!![0].isFavorite)

        assertEquals(1, result.sjukdomsepisoder.size)
        with(result.sjukdomsepisoder[0]) {
            assertEquals("e1", id)
            assertEquals("migrän", typ)
            assertEquals("2026-01-10", startDatum)
            assertEquals("2026-01-12", slutDatum)
            assertEquals("Svår vecka", anteckning)
        }

        assertEquals(1, result.sjukdomsIncheckningar.size)
        with(result.sjukdomsIncheckningar[0]) {
            assertEquals("i1", id)
            assertEquals("e1", episodId)
            assertEquals(8, svarighetsgrad)
            assertEquals("Yrsel:3", symptom)
        }

        assertEquals(1, result.handelser.size)
        with(result.handelser[0]) {
            assertEquals("h1", id)
            assertEquals("huvudvärk", typ)
            assertEquals(6, svarighetsgrad)
            assertEquals(90, varaktighetMinuter)
            assertEquals("[\"stress\"]", triggers)
        }

        assertEquals(2, result.notes.size)
        assertEquals("Mådde bra", result.notes.find { it.entityId == "a1" }!!.text)

        assertEquals(2, result.screeningEventConfigs!!.size)
        assertTrue(result.screeningEventConfigs!![0].enabled)
        assertEquals("08:00", result.screeningEventConfigs!![0].time)

        assertEquals("https://docs.google.com/spreadsheets/d/abc123", result.sheetsConfig)
    }

    // ─── backwards compatibility ──────────────────────────────────────────────

    @Test fun `old backup without handelser deserializes to empty list`() {
        val oldJson = """{"version":1,"createdAt":"2025-01-01","aktiviteter":[],"mediciner":[]}"""
        val result = json.decodeFromString<BackupJson>(oldJson)
        assertTrue(result.handelser.isEmpty())
    }

    @Test fun `old backup without notes deserializes to empty list`() {
        val oldJson = """{"version":1,"createdAt":"2025-01-01","aktiviteter":[],"mediciner":[]}"""
        val result = json.decodeFromString<BackupJson>(oldJson)
        assertTrue(result.notes.isEmpty())
    }

    @Test fun `old backup without screeningEventConfigs deserializes to null`() {
        val oldJson = """{"version":1,"createdAt":"2025-01-01","aktiviteter":[],"mediciner":[]}"""
        val result = json.decodeFromString<BackupJson>(oldJson)
        assertNull(result.screeningEventConfigs)
    }

    @Test fun `old backup without sheetsConfig deserializes to null`() {
        val oldJson = """{"version":1,"createdAt":"2025-01-01","aktiviteter":[],"mediciner":[]}"""
        val result = json.decodeFromString<BackupJson>(oldJson)
        assertNull(result.sheetsConfig)
    }

    @Test fun `backup with unknown extra fields deserializes without error`() {
        val futureJson = """{"version":99,"createdAt":"2030-01-01","aktiviteter":[],"mediciner":[],"unknownField":"ignored"}"""
        val result = json.decodeFromString<BackupJson>(futureJson)
        assertTrue(result.aktiviteter.isEmpty())
    }
}
