package se.partee71.dagboken.data.migration

import se.partee71.dagboken.data.datastore.ScreeningEventConfig
import se.partee71.dagboken.data.datastore.SymptomOption
import se.partee71.dagboken.data.room.entities.NoteEntity
import se.partee71.dagboken.domain.model.Aktivitet
import se.partee71.dagboken.domain.model.Dosperiod
import se.partee71.dagboken.domain.model.Favorit
import se.partee71.dagboken.domain.model.Handelse
import se.partee71.dagboken.domain.model.Medicin
import se.partee71.dagboken.domain.model.Recept
import se.partee71.dagboken.domain.model.SjukdomsEpisod
import se.partee71.dagboken.domain.model.SjukdomsIncheckning

/**
 * Domän → [BackupJson] (exportsidan av regel 1). Motstycket till [BackupMapper], som
 * gör vägen tillbaka.
 *
 * Ligger som en ren funktion utanför [se.partee71.dagboken.worker.BackupWorker] just
 * för att kunna rundturstestas ihop med [BackupMapper]: så länge mappningen låg inbäddad
 * i workern gick den inte att testa utan WorkManager, och ett nytt persisterat fält kunde
 * glömmas bort här utan att något test blev rött.
 */
object BackupAssembler {

    /** Aktuell formatversion. Höjs när formatet ändras på ett sätt läsaren behöver veta om. */
    const val BACKUP_FORMAT_VERSION = 2

    @Suppress("LongParameterList")
    fun assemble(
        createdAt: String,
        aktiviteter: List<Aktivitet>,
        mediciner: List<Medicin>,
        recept: List<Recept>,
        favoriter: List<Favorit>,
        handelser: List<Handelse>,
        episoder: List<SjukdomsEpisod>,
        incheckningar: List<SjukdomsIncheckning>,
        notes: List<NoteEntity>,
        aktivitetOptions: List<SymptomOption>,
        symptomOptions: List<SymptomOption>,
        handelseTypOptions: List<SymptomOption>,
        screeningEventConfigs: List<ScreeningEventConfig>,
        sheetsConfig: String,
        periodReminderTime: String,
        settings: SettingsBackup,
    ): BackupJson = BackupJson(
        version               = BACKUP_FORMAT_VERSION,
        createdAt             = createdAt,
        aktiviteter           = aktiviteter.map { it.toJson() },
        mediciner             = mediciner.map { it.toJson() },
        medicinRecipes        = recept.map { it.toJson() },
        medicinFavoriter      = favoriter.map { it.toJson() },
        // V1-listorna skrivs fortfarande så att en äldre appversion kan läsa backupen.
        aktiviteterOptions    = aktivitetOptions.map { it.name },
        symptomOptions        = symptomOptions.map { it.name },
        aktiviteterOptionsV2  = aktivitetOptions.map { SymptomOptionBackup(it.name, it.isFavorite) },
        symptomOptionsV2      = symptomOptions.map { SymptomOptionBackup(it.name, it.isFavorite) },
        sjukdomsepisoder      = episoder.map { it.toJson() },
        sjukdomsIncheckningar = incheckningar.map { it.toJson() },
        handelser             = handelser.map { it.toJson() },
        notes                 = notes.map { it.toJson() },
        screeningEventConfigs = screeningEventConfigs.map { ScreeningEventConfigJson(it.enabled, it.time) },
        sheetsConfig          = sheetsConfig.takeIf { it.isNotBlank() },
        handelseTypOptions    = handelseTypOptions.map { SymptomOptionBackup(it.name, it.isFavorite) },
        periodReminderTime    = periodReminderTime,
        settings              = settings,
    )

    private fun Aktivitet.toJson() = AktivitetJson(
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
        type         = type,
        spentTime    = spentTime,
    )

    private fun Medicin.toJson() = MedicinJson(
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
        tagenTid   = tagenTid,
    )

    private fun Recept.toJson() = ReceptJson(
        id            = id,
        namn          = namn,
        dos           = dos,
        enhet         = enhet,
        tidpunkter    = tidpunkter,
        upprepning    = upprepning,
        dagar         = dagar,
        intervalDagar = intervalDagar,
        aktiv         = aktiv,
        skapad        = skapad,
        startDatum    = startDatum,
        slutDatum     = slutDatum,
        dosperioder   = dosperioder.map { it.toJson() },
    )

    private fun Dosperiod.toJson() = DosperiodJson(
        id         = id,
        startDatum = startDatum,
        slutDatum  = slutDatum,
        dos        = dos,
        enhet      = enhet,
    )

    private fun Favorit.toJson() = FavoritJson(
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

    private fun SjukdomsEpisod.toJson() = SjukdomsEpisodJson(
        id         = id,
        typ        = typ,
        startDatum = startDatum,
        slutDatum  = slutDatum,
        timestamp  = timestamp,
    )

    private fun SjukdomsIncheckning.toJson() = SjukdomsIncheckningJson(
        id             = id,
        episodId       = episodId,
        datum          = datum,
        tid            = tid,
        svarighetsgrad = svarighetsgrad,
        symptom        = symptom,
        somatiska      = somatiska,
        timestamp      = timestamp,
    )

    private fun Handelse.toJson() = HandelseJson(
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

    private fun NoteEntity.toJson() = NoteJson(
        target   = target,
        entityId = entityId,
        text     = text,
    )
}
