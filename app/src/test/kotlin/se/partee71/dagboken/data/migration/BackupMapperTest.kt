package se.partee71.dagboken.data.migration

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
    ) = BackupJson(
        aktiviteter = aktiviteter,
        mediciner = mediciner,
        medicinRecipes = recept,
        medicinFavoriter = favoriter,
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
                dispenseringsTid = "", maxDoserPerDag = 2,
            ))
        )
        val result = BackupMapper.toFavoriter(json)
        assertEquals(1, result.size)
        assertEquals("f1", result[0].id)
        assertEquals(4, result[0].minTidMellan)
        assertEquals(2, result[0].maxDoserPerDag)
    }
}
