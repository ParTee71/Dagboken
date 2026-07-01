package se.partee71.dagboken.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first
import se.partee71.dagboken.data.auth.FirebaseAuthRepository
import se.partee71.dagboken.data.datastore.PreferencesRepository
import se.partee71.dagboken.data.migration.AktivitetJson
import se.partee71.dagboken.data.migration.BackupJson
import se.partee71.dagboken.data.migration.DriveBackupRepository
import se.partee71.dagboken.data.migration.DriveResult
import se.partee71.dagboken.data.migration.FavoritJson
import se.partee71.dagboken.data.migration.HandelseJson
import se.partee71.dagboken.data.migration.MedicinJson
import se.partee71.dagboken.data.migration.NoteJson
import se.partee71.dagboken.data.migration.ReceptJson
import se.partee71.dagboken.data.migration.ScreeningEventConfigJson
import se.partee71.dagboken.data.migration.SjukdomsEpisodJson
import se.partee71.dagboken.data.migration.SjukdomsIncheckningJson
import se.partee71.dagboken.data.migration.SymptomOptionBackup
import se.partee71.dagboken.data.repository.AktiviteterRepository
import se.partee71.dagboken.data.repository.HandelserRepository
import se.partee71.dagboken.data.repository.MedicinerRepository
import se.partee71.dagboken.data.repository.NoteRepository
import se.partee71.dagboken.data.repository.SjukdomarRepository
import se.partee71.dagboken.data.room.entities.NoteEntity
import se.partee71.dagboken.domain.model.Aktivitet
import se.partee71.dagboken.domain.model.Favorit
import se.partee71.dagboken.domain.model.Handelse
import se.partee71.dagboken.domain.model.Medicin
import se.partee71.dagboken.domain.model.Recept
import se.partee71.dagboken.domain.model.SjukdomsEpisod
import se.partee71.dagboken.domain.model.SjukdomsIncheckning
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

@HiltWorker
class BackupWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val aktiviteterRepo: AktiviteterRepository,
    private val medicinerRepo: MedicinerRepository,
    private val sjukdomarRepo: SjukdomarRepository,
    private val handelserRepo: HandelserRepository,
    private val noteRepo: NoteRepository,
    private val driveRepo: DriveBackupRepository,
    private val authRepo: FirebaseAuthRepository,
    private val prefs: PreferencesRepository,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        if (authRepo.currentUser == null) return Result.success()

        return try {
            val episoder      = sjukdomarRepo.all.first()
            val incheckningar = episoder.flatMap { ep ->
                sjukdomarRepo.incheckningarForEpisod(ep.id).first()
            }

            val backup = BackupJson(
                version               = 1,
                createdAt             = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME),
                aktiviteter           = aktiviteterRepo.all.first().map { it.toJson() },
                mediciner             = medicinerRepo.allMediciner.first().map { it.toJson() },
                medicinRecipes        = medicinerRepo.allRecept.first().map { it.toJson() },
                medicinFavoriter      = medicinerRepo.allFavoriter.first().map { it.toJson() },
                aktiviteterOptions    = prefs.aktivitetOptions.first().map { it.name },
                symptomOptions        = prefs.symptomOptions.first().map { it.name },
                aktiviteterOptionsV2  = prefs.aktivitetOptions.first().map { SymptomOptionBackup(it.name, it.isFavorite) },
                symptomOptionsV2      = prefs.symptomOptions.first().map { SymptomOptionBackup(it.name, it.isFavorite) },
                sjukdomsepisoder      = episoder.map { it.toJson() },
                sjukdomsIncheckningar = incheckningar.map { it.toJson() },
                handelser             = handelserRepo.all.first().map { it.toJson() },
                notes                 = noteRepo.getAll().map { it.toJson() },
                screeningEventConfigs = prefs.screeningEventConfigs.first().map { ScreeningEventConfigJson(it.enabled, it.time) },
                sheetsConfig          = prefs.sheetsConfig.first().takeIf { it.isNotBlank() },
                handelseTypOptions    = prefs.handelseTypOptions.first().map { SymptomOptionBackup(it.name, it.isFavorite) },
            )

            when (driveRepo.uploadBackup(backup)) {
                is DriveResult.Success            -> { prefs.setBackupNeedsAuth(false); Result.success() }
                is DriveResult.NeedsAuthorization -> { prefs.setBackupNeedsAuth(true); Result.success() }
                is DriveResult.NoAccount          -> Result.success()
                is DriveResult.NoBackupFound      -> Result.success()
                is DriveResult.Error              -> Result.retry()
            }
        } catch (e: SecurityException) {
            // Permanent: user revoked Drive permission — surface via flag and stop retrying
            prefs.setBackupNeedsAuth(true)
            Result.failure()
        } catch (_: Exception) {
            // Transient (network, IO) — retry
            Result.retry()
        }
    }

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
        anteckning = anteckning,
        receptId   = receptId,
        skipped    = skipped,
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
        anteckning    = anteckning,
        aktiv         = aktiv,
        skapad        = skapad,
    )

    private fun Favorit.toJson() = FavoritJson(
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

    private fun SjukdomsEpisod.toJson() = SjukdomsEpisodJson(
        id         = id,
        typ        = typ,
        startDatum = startDatum,
        slutDatum  = slutDatum,
        anteckning = anteckning,
        timestamp  = timestamp,
    )

    private fun NoteEntity.toJson() = NoteJson(
        target   = target,
        entityId = entityId,
        text     = text,
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
        anteckning         = anteckning,
    )

    private fun SjukdomsIncheckning.toJson() = SjukdomsIncheckningJson(
        id             = id,
        episodId       = episodId,
        datum          = datum,
        tid            = tid,
        svarighetsgrad = svarighetsgrad,
        symptom        = symptom,
        somatiska      = somatiska,
        anteckning     = anteckning,
        timestamp      = timestamp,
    )
}
