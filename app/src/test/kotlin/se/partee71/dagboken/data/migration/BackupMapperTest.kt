package se.partee71.dagboken.data.migration

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupMapperTest {

    private fun backup(
        aktiviteter: List<AktivitetJson> = emptyList(),
        mediciner: List<MedicinJson> = emptyList(),
        recept: List<ReceptJson> = emptyList(),
        favoriter: List<FavoritJson> = emptyList(),
        handelser: List<HandelseJson> = emptyList(),
        notes: List<NoteJson> = emptyList(),
        sjukdomsepisoder: List<SjukdomsEpisodJson> = emptyList(),
        sjukdomsIncheckningar: List<SjukdomsIncheckningJson> = emptyList(),
    ) = BackupJson(
        aktiviteter = aktiviteter,
        mediciner = mediciner,
        medicinRecipes = recept,
        medicinFavoriter = favoriter,
        handelser = handelser,
        notes = notes,
        sjukdomsepisoder = sjukdomsepisoder,
        sjukdomsIncheckningar = sjukdomsIncheckningar,
    )

    // ─── aktiviteter ──────────────────────────────────────────────────────────

    @Test fun `toAktiviteter maps all fields`() {
        val json = backup(
            aktiviteter = listOf(AktivitetJson(
                id = "a1", timestamp = "2024-01-01T09:00:00.000Z",
                datum = "2024-01-01", tid = "09:00", aktivitet = "Promenad",
                energy = 5, stress = 3, somatiska = 1, symptom = "Nackvärk:2",
                aterhamtande = true, energitjuv = false, type = "aktivitet",
            ))
        )
        val result = BackupMapper.toAktiviteter(json)
        assertEquals(1, result.size)
        assertEquals("a1", result[0].id)
        assertEquals("Promenad", result[0].aktivitet)
        assertEquals(5, result[0].energy)
        assertTrue(result[0].aterhamtande)
        assertFalse(result[0].energitjuv)
        assertEquals("aktivitet", result[0].type)
    }

    @Test fun `toAktiviteter infers type for screening options when type is blank`() {
        val json = backup(
            aktiviteter = listOf(
                AktivitetJson(id = "s1", aktivitet = "Läggdags", type = ""),
                AktivitetJson(id = "a1", aktivitet = "Promenad",  type = ""),
            )
        )
        val result = BackupMapper.toAktiviteter(json)
        assertEquals("screening", result.find { it.id == "s1" }!!.type)
        assertEquals("aktivitet", result.find { it.id == "a1" }!!.type)
    }

    @Test fun `toAktiviteter returns empty for empty backup`() {
        assertTrue(BackupMapper.toAktiviteter(backup()).isEmpty())
    }

    // ─── mediciner ────────────────────────────────────────────────────────────

    @Test fun `toMediciner maps all fields`() {
        val json = backup(
            mediciner = listOf(MedicinJson(
                id = "m1", timestamp = "2024-01-01T09:00:00.000Z",
                datum = "2024-01-01", tid = "09:00", namn = "Ibuprofen",
                dos = "400", enhet = "mg", tidpunkt = "Morgon",
                tagen = true, anteckning = "test", receptId = "r1", skipped = false,
            ))
        )
        val result = BackupMapper.toMediciner(json)
        assertEquals(1, result.size)
        assertEquals("m1", result[0].id)
        assertEquals("Ibuprofen", result[0].namn)
        assertEquals("r1", result[0].receptId)
        assertTrue(result[0].tagen)
    }

    // ─── recept ───────────────────────────────────────────────────────────────

    @Test fun `toRecept maps modern format with tidpunkter list`() {
        val json = backup(
            recept = listOf(ReceptJson(
                id = "r1", namn = "Metformin", dos = "500", enhet = "mg",
                tidpunkter = listOf("Morgon", "Kväll"), upprepning = "dagligen",
                aktiv = true, skapad = "2024-01-01",
            ))
        )
        val result = BackupMapper.toRecept(json)
        assertEquals(listOf("Morgon", "Kväll"), result[0].tidpunkter)
    }

    @Test fun `toRecept handles v1 format with single tidpunkt field`() {
        val json = backup(
            recept = listOf(ReceptJson(
                id = "r1", namn = "Metformin", dos = "500", enhet = "mg",
                tidpunkter = emptyList(), tidpunkt = "Morgon",
                upprepning = "dagligen", aktiv = true, skapad = "2024-01-01",
            ))
        )
        val result = BackupMapper.toRecept(json)
        assertEquals(listOf("Morgon"), result[0].tidpunkter)
    }

    @Test fun `toRecept falls back to Morgon when both tidpunkt fields are empty`() {
        val json = backup(
            recept = listOf(ReceptJson(
                id = "r1", namn = "Metformin", dos = "500", enhet = "mg",
                tidpunkter = emptyList(), tidpunkt = null,
                upprepning = "dagligen", aktiv = true, skapad = "2024-01-01",
            ))
        )
        val result = BackupMapper.toRecept(json)
        assertEquals(listOf("Morgon"), result[0].tidpunkter)
    }

    @Test fun `toRecept preserves anpassad dagar`() {
        val json = backup(
            recept = listOf(ReceptJson(
                id = "r1", namn = "Yoga", dos = "1", enhet = "st",
                tidpunkter = listOf("Morgon"), upprepning = "anpassad",
                dagar = listOf(0, 2, 4), aktiv = true, skapad = "2024-01-01",
            ))
        )
        val result = BackupMapper.toRecept(json)
        assertEquals(listOf(0, 2, 4), result[0].dagar)
        assertEquals("anpassad", result[0].upprepning)
    }

    // ─── favoriter ────────────────────────────────────────────────────────────

    @Test fun `toFavoriter maps all fields`() {
        val json = backup(
            favoriter = listOf(FavoritJson(
                id = "f1", namn = "Ibuprofen", dos = "400", enhet = "mg",
                tidpunkt = "Vid behov", anteckning = "", minTidMellan = 4,
                dispenseringsTid = "", maxDoserPerDag = 2, isFavorite = true,
            ))
        )
        val result = BackupMapper.toFavoriter(json)
        assertEquals(1, result.size)
        assertEquals("f1", result[0].id)
        assertEquals(4, result[0].minTidMellan)
        assertEquals(2, result[0].maxDoserPerDag)
        assertEquals(true, result[0].isFavorite)
    }

    // ─── händelser ────────────────────────────────────────────────────────────

    @Test fun `toHandelser maps all fields`() {
        val json = backup(
            handelser = listOf(HandelseJson(
                id = "h1", timestamp = "2024-01-01T14:00:00.000Z",
                datum = "2024-01-01", tid = "14:00", typ = "huvudvärk",
                svarighetsgrad = 7, varaktighetMinuter = 120,
                triggers = "[\"stress\"]", atgarder = "[\"vila\"]",
            ))
        )
        val result = BackupMapper.toHandelser(json)
        assertEquals(1, result.size)
        with(result[0]) {
            assertEquals("h1", id)
            assertEquals("2024-01-01T14:00:00.000Z", timestamp)
            assertEquals("2024-01-01", datum)
            assertEquals("14:00", tid)
            assertEquals("huvudvärk", typ)
            assertEquals(7, svarighetsgrad)
            assertEquals(120, varaktighetMinuter)
            assertEquals("[\"stress\"]", triggers)
            assertEquals("[\"vila\"]", atgarder)
        }
    }

    @Test fun `toHandelser returns empty for empty backup`() {
        assertTrue(BackupMapper.toHandelser(backup()).isEmpty())
    }

    // ─── notes ────────────────────────────────────────────────────────────────

    @Test fun `toNotes maps all fields`() {
        val json = backup(
            notes = listOf(NoteJson(target = "ACTIVITY", entityId = "a1", text = "Bra dag"))
        )
        val result = BackupMapper.toNotes(json)
        assertEquals(1, result.size)
        assertEquals("ACTIVITY", result[0].target)
        assertEquals("a1", result[0].entityId)
        assertEquals("Bra dag", result[0].text)
    }

    @Test fun `toNotes filters out entries with blank text`() {
        val json = backup(
            notes = listOf(
                NoteJson(target = "ACTIVITY", entityId = "a1", text = ""),
                NoteJson(target = "ACTIVITY", entityId = "a2", text = "  "),
                NoteJson(target = "ACTIVITY", entityId = "a3", text = "Giltig"),
            )
        )
        val result = BackupMapper.toNotes(json)
        assertEquals(1, result.size)
        assertEquals("a3", result[0].entityId)
    }

    @Test fun `toNotes filters out entries with blank target or entityId`() {
        val json = backup(
            notes = listOf(
                NoteJson(target = "", entityId = "a1", text = "Text"),
                NoteJson(target = "ACTIVITY", entityId = "", text = "Text"),
                NoteJson(target = "ACTIVITY", entityId = "a3", text = "Giltig"),
            )
        )
        val result = BackupMapper.toNotes(json)
        assertEquals(1, result.size)
    }

    @Test fun `toNotes returns empty for empty backup`() {
        assertTrue(BackupMapper.toNotes(backup()).isEmpty())
    }

    @Test fun `toNotes synthesizes MEDICATION RECEPT FAVORIT EVENT notes from legacy anteckning columns`() {
        val json = backup(
            mediciner = listOf(MedicinJson(id = "m1", anteckning = "Tas med mat")),
            recept = listOf(ReceptJson(id = "r1", anteckning = "Kväll bäst")),
            favoriter = listOf(FavoritJson(id = "f1", anteckning = "Max 3/dag")),
            handelser = listOf(HandelseJson(id = "h1", anteckning = "Kom efter möte")),
        )
        val result = BackupMapper.toNotes(json)
        assertEquals(4, result.size)
        assertEquals("Tas med mat", result.find { it.target == "MEDICATION" && it.entityId == "m1" }?.text)
        assertEquals("Kväll bäst", result.find { it.target == "RECEPT" && it.entityId == "r1" }?.text)
        assertEquals("Max 3/dag", result.find { it.target == "FAVORIT" && it.entityId == "f1" }?.text)
        assertEquals("Kom efter möte", result.find { it.target == "EVENT" && it.entityId == "h1" }?.text)
    }

    @Test fun `toNotes ignores blank legacy anteckning columns`() {
        val json = backup(mediciner = listOf(MedicinJson(id = "m1", anteckning = "  ")))
        assertTrue(BackupMapper.toNotes(json).isEmpty())
    }

    @Test fun `toNotes prefers an explicit notes entry over the legacy anteckning column for the same row`() {
        val json = backup(
            mediciner = listOf(MedicinJson(id = "m1", anteckning = "Gammal (kolumn)")),
            notes = listOf(NoteJson(target = "MEDICATION", entityId = "m1", text = "Ny (notes-tabell)")),
        )
        val result = BackupMapper.toNotes(json)
        assertEquals(1, result.size)
        assertEquals("Ny (notes-tabell)", result[0].text)
    }

    // ─── sjukdomar ────────────────────────────────────────────────────────────

    @Test fun `toSjukdomsEpisoder maps all fields`() {
        val json = backup(
            sjukdomsepisoder = listOf(SjukdomsEpisodJson(
                id = "e1", typ = "migrän", startDatum = "2024-01-01",
                slutDatum = "2024-01-03", anteckning = "Svår period",
                timestamp = 1_700_000_000_000L,
            ))
        )
        val result = BackupMapper.toSjukdomsEpisoder(json)
        assertEquals(1, result.size)
        with(result[0]) {
            assertEquals("e1", id)
            assertEquals("migrän", typ)
            assertEquals("2024-01-01", startDatum)
            assertEquals("2024-01-03", slutDatum)
            assertEquals("Svår period", anteckning)
            assertEquals(1_700_000_000_000L, timestamp)
        }
    }

    @Test fun `toSjukdomsEpisoder falls back to non-zero timestamp for legacy backup`() {
        val json = backup(
            sjukdomsepisoder = listOf(SjukdomsEpisodJson(id = "e1", typ = "migrän"))
        )
        // timestamp defaults to 0 in v1 backups; mapper must substitute a real time
        assertTrue(BackupMapper.toSjukdomsEpisoder(json)[0].timestamp != 0L)
    }

    @Test fun `toSjukdomsEpisoder returns empty for empty backup`() {
        assertTrue(BackupMapper.toSjukdomsEpisoder(backup()).isEmpty())
    }

    @Test fun `toSjukdomsIncheckningar maps all fields`() {
        val json = backup(
            sjukdomsIncheckningar = listOf(SjukdomsIncheckningJson(
                id = "i1", episodId = "e1", datum = "2024-01-01", tid = "10:00",
                svarighetsgrad = 8, symptom = "Yrsel:3", somatiska = 2,
                anteckning = "Tog medicin", timestamp = 1_700_000_000_000L,
            ))
        )
        val result = BackupMapper.toSjukdomsIncheckningar(json)
        assertEquals(1, result.size)
        with(result[0]) {
            assertEquals("i1", id)
            assertEquals("e1", episodId)
            assertEquals("2024-01-01", datum)
            assertEquals("10:00", tid)
            assertEquals(8, svarighetsgrad)
            assertEquals("Yrsel:3", symptom)
            assertEquals(2, somatiska)
            assertEquals("Tog medicin", anteckning)
            assertEquals(1_700_000_000_000L, timestamp)
        }
    }

    @Test fun `toSjukdomsIncheckningar falls back to non-zero timestamp for legacy backup`() {
        val json = backup(
            sjukdomsIncheckningar = listOf(SjukdomsIncheckningJson(id = "i1", episodId = "e1"))
        )
        assertTrue(BackupMapper.toSjukdomsIncheckningar(json)[0].timestamp != 0L)
    }

    @Test fun `toSjukdomsIncheckningar returns empty for empty backup`() {
        assertTrue(BackupMapper.toSjukdomsIncheckningar(backup()).isEmpty())
    }

    // ─── handelseTypOptions ───────────────────────────────────────────────────

    @Test fun `handelseTypOptions round-trips through BackupJson with isFavorite preserved`() {
        val json = backup().copy(
            handelseTypOptions = listOf(
                SymptomOptionBackup("Yrsel", isFavorite = true),
                SymptomOptionBackup("Andnöd", isFavorite = false),
            ),
        )
        val encoded = Json.encodeToString(json)
        val decoded = Json.decodeFromString<BackupJson>(encoded)
        assertEquals(json.handelseTypOptions, decoded.handelseTypOptions)
    }

    @Test fun `handelseTypOptions is null for a backup that predates the field`() {
        assertEquals(null, backup().handelseTypOptions)
    }
}
