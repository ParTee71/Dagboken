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
import se.partee71.dagboken.data.migration.MedicinJson
import se.partee71.dagboken.data.migration.ReceptJson
import se.partee71.dagboken.data.repository.AktiviteterRepository
import se.partee71.dagboken.data.repository.MedicinerRepository
import se.partee71.dagboken.domain.model.Aktivitet
import se.partee71.dagboken.domain.model.Favorit
import se.partee71.dagboken.domain.model.Medicin
import se.partee71.dagboken.domain.model.Recept
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

@HiltWorker
class BackupWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val aktiviteterRepo: AktiviteterRepository,
    private val medicinerRepo: MedicinerRepository,
    private val driveRepo: DriveBackupRepository,
    private val authRepo: FirebaseAuthRepository,
    private val prefs: PreferencesRepository,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        if (authRepo.currentUser == null) return Result.success()

        return try {
            val backup = BackupJson(
                version            = 1,
                createdAt          = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME),
                aktiviteter        = aktiviteterRepo.all.first().map { it.toJson() },
                mediciner          = medicinerRepo.allMediciner.first().map { it.toJson() },
                medicinRecipes     = medicinerRepo.allRecept.first().map { it.toJson() },
                medicinFavoriter   = medicinerRepo.allFavoriter.first().map { it.toJson() },
                aktiviteterOptions = prefs.aktivitetOptions.first(),
                symptomOptions     = prefs.symptomOptions.first(),
            )

            when (driveRepo.uploadBackup(backup)) {
                is DriveResult.Success            -> { prefs.setBackupNeedsAuth(false); Result.success() }
                is DriveResult.NeedsAuthorization -> { prefs.setBackupNeedsAuth(true); Result.success() }
                is DriveResult.NoAccount          -> Result.success()
                is DriveResult.NoBackupFound      -> Result.success()
                is DriveResult.Error              -> Result.retry()
            }
        } catch (_: Exception) {
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
}
