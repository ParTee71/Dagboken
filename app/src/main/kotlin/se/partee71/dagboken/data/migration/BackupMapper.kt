package se.partee71.dagboken.data.migration

import se.partee71.dagboken.data.room.entities.NoteEntity
import se.partee71.dagboken.domain.model.Aktivitet
import se.partee71.dagboken.domain.model.Favorit
import se.partee71.dagboken.domain.model.Handelse
import se.partee71.dagboken.domain.model.Medicin
import se.partee71.dagboken.domain.model.Recept
import se.partee71.dagboken.domain.model.SjukdomsEpisod
import se.partee71.dagboken.domain.model.SjukdomsIncheckning

object BackupMapper {

    fun toAktiviteter(json: BackupJson): List<Aktivitet> = json.aktiviteter.map { it.toDomain() }

    fun toMediciner(json: BackupJson): List<Medicin> = json.mediciner.map { it.toDomain() }

    fun toRecept(json: BackupJson): List<Recept> = json.medicinRecipes.map { it.toDomain() }

    fun toFavoriter(json: BackupJson): List<Favorit> = json.medicinFavoriter.map { it.toDomain() }

    fun toSjukdomsEpisoder(json: BackupJson): List<SjukdomsEpisod> =
        json.sjukdomsepisoder.map { it.toDomain() }

    fun toSjukdomsIncheckningar(json: BackupJson): List<SjukdomsIncheckning> =
        json.sjukdomsIncheckningar.map { it.toDomain() }

    fun toHandelser(json: BackupJson): List<Handelse> = json.handelser.map { it.toDomain() }

    // Legacy backups (pre notes-table migration) carried Medicin/Recept/Favorit anteckning as a
    // per-row column instead of a `notes` entry. Synthesize those into notes on import so
    // restoring an old backup on the current schema doesn't lose them. An explicit `notes`
    // entry for the same (target, entityId) always wins over the legacy column value.
    fun toNotes(json: BackupJson): List<NoteEntity> {
        val explicit = json.notes
            .filter { it.target.isNotBlank() && it.entityId.isNotBlank() && it.text.isNotBlank() }
            .map { NoteEntity(target = it.target, entityId = it.entityId, text = it.text) }
        val explicitKeys = explicit.map { it.target to it.entityId }.toSet()

        val legacy = buildList {
            json.mediciner.forEach { m ->
                if (m.anteckning.isNotBlank() && ("MEDICATION" to m.id) !in explicitKeys) {
                    add(NoteEntity(target = "MEDICATION", entityId = m.id, text = m.anteckning))
                }
            }
            json.medicinRecipes.forEach { r ->
                if (r.anteckning.isNotBlank() && ("RECEPT" to r.id) !in explicitKeys) {
                    add(NoteEntity(target = "RECEPT", entityId = r.id, text = r.anteckning))
                }
            }
            json.medicinFavoriter.forEach { f ->
                if (f.anteckning.isNotBlank() && ("FAVORIT" to f.id) !in explicitKeys) {
                    add(NoteEntity(target = "FAVORIT", entityId = f.id, text = f.anteckning))
                }
            }
            json.handelser.forEach { h ->
                if (h.anteckning.isNotBlank() && ("EVENT" to h.id) !in explicitKeys) {
                    add(NoteEntity(target = "EVENT", entityId = h.id, text = h.anteckning))
                }
            }
        }

        return explicit + legacy
    }

    private fun AktivitetJson.toDomain() = Aktivitet(
        id           = id,
        timestamp    = timestamp,
        datum        = datum,
        tid          = tid,
        aktivitet    = aktivitet,
        energy       = energy,
        stress       = stress,
        somatiska    = somatiska,
        symptom      = symptom,
        aterhamtande = aterhamtande,
        energitjuv   = energitjuv,
        type         = type.ifBlank { inferType(aktivitet) },
        spentTime    = spentTime,
    )

    private fun MedicinJson.toDomain() = Medicin(
        id         = id,
        timestamp  = timestamp,
        datum      = datum,
        tid        = tid,
        namn       = namn,
        dos        = dos,
        enhet      = enhet,
        tidpunkt   = tidpunkt,
        tagen      = tagen,
        receptId   = receptId,
        skipped    = skipped,
    )

    private fun ReceptJson.toDomain(): Recept {
        // v1 backups had a single `tidpunkt` string instead of `tidpunkter` list
        val resolvedTidpunkter = tidpunkter.ifEmpty {
            tidpunkt?.let { listOf(it) } ?: listOf("Morgon")
        }
        return Recept(
            id           = id,
            namn         = namn,
            dos          = dos,
            enhet        = enhet,
            tidpunkter   = resolvedTidpunkter,
            upprepning   = upprepning,
            dagar        = dagar,
            intervalDagar = intervalDagar,
            aktiv        = aktiv,
            skapad       = skapad,
        )
    }

    private fun FavoritJson.toDomain() = Favorit(
        id               = id,
        namn             = namn,
        dos              = dos,
        enhet            = enhet,
        tidpunkt         = tidpunkt,
        minTidMellan     = minTidMellan,
        dispenseringsTid = dispenseringsTid,
        maxDoserPerDag   = maxDoserPerDag,
        isFavorite       = isFavorite,
    )

    private fun SjukdomsEpisodJson.toDomain() = SjukdomsEpisod(
        id         = id,
        typ        = typ,
        startDatum = startDatum,
        slutDatum  = slutDatum,
        anteckning = anteckning,
        // v1 backups didn't carry timestamp (0) — fall back to now so ordering stays sane
        timestamp  = timestamp.takeIf { it != 0L } ?: System.currentTimeMillis(),
    )

    private fun SjukdomsIncheckningJson.toDomain() = SjukdomsIncheckning(
        id             = id,
        episodId       = episodId,
        datum          = datum,
        tid            = tid,
        svarighetsgrad = svarighetsgrad,
        symptom        = symptom,
        somatiska      = somatiska,
        anteckning     = anteckning,
        timestamp      = timestamp.takeIf { it != 0L } ?: System.currentTimeMillis(),
    )

    private fun HandelseJson.toDomain() = Handelse(
        id                 = id,
        timestamp          = timestamp,
        datum              = datum,
        tid                = tid,
        typ                = typ,
        svarighetsgrad     = svarighetsgrad,
        varaktighetMinuter = varaktighetMinuter,
        triggers           = triggers,
        atgarder           = atgarder,
    )

    // Mirrors migrateAktiviteterTypes from src/storage/aktiviteter.ts
    private val SCREENING_OPTIONS = setOf("Efter frukost", "Lunch", "Kvällsmat", "Läggdags")
    private fun inferType(aktivitet: String): String =
        if (aktivitet in SCREENING_OPTIONS) "screening" else "aktivitet"
}
