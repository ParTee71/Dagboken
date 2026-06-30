package se.partee71.dagboken.data.migration

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
        anteckning = anteckning,
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
            anteckning   = anteckning,
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
        anteckning       = anteckning,
        minTidMellan     = minTidMellan,
        dispenseringsTid = dispenseringsTid,
        maxDoserPerDag   = maxDoserPerDag,
    )

    private fun SjukdomsEpisodJson.toDomain() = SjukdomsEpisod(
        id         = id,
        typ        = typ,
        startDatum = startDatum,
        slutDatum  = slutDatum,
        anteckning = anteckning,
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
        anteckning         = anteckning,
    )

    // Mirrors migrateAktiviteterTypes from src/storage/aktiviteter.ts
    private val SCREENING_OPTIONS = setOf("Efter frukost", "Lunch", "Kvällsmat", "Läggdags")
    private fun inferType(aktivitet: String): String =
        if (aktivitet in SCREENING_OPTIONS) "screening" else "aktivitet"
}
