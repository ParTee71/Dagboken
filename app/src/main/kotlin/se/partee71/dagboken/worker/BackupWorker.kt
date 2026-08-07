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
import se.partee71.dagboken.data.migration.BackupAssembler
import se.partee71.dagboken.data.migration.DriveBackupRepository
import se.partee71.dagboken.data.migration.DriveResult
import se.partee71.dagboken.data.migration.SettingsBackup
import se.partee71.dagboken.data.repository.AktiviteterRepository
import se.partee71.dagboken.data.repository.HandelserRepository
import se.partee71.dagboken.data.repository.MedicinerRepository
import se.partee71.dagboken.data.repository.NoteRepository
import se.partee71.dagboken.data.repository.SjukdomarRepository
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
            val aktivitetOptions   = prefs.aktivitetOptions.first()
            val symptomOptions     = prefs.symptomOptions.first()

            val backup = BackupAssembler.assemble(
                createdAt             = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME),
                aktiviteter           = aktiviteterRepo.all.first(),
                mediciner             = medicinerRepo.allMediciner.first(),
                recept                = medicinerRepo.allRecept.first(),
                favoriter             = medicinerRepo.allFavoriter.first(),
                handelser             = handelserRepo.all.first(),
                episoder              = sjukdomarRepo.all.first(),
                // En fråga för alla incheckningar i stället för en per episod.
                incheckningar         = sjukdomarRepo.allIncheckningar.first(),
                notes                 = noteRepo.getAll(),
                aktivitetOptions      = aktivitetOptions,
                symptomOptions        = symptomOptions,
                handelseTypOptions    = prefs.handelseTypOptions.first(),
                screeningEventConfigs = prefs.screeningEventConfigs.first(),
                medNotificationConfigs = prefs.medNotificationConfigs.first(),
                sheetsConfig          = prefs.sheetsConfig.first(),
                periodReminderTime    = prefs.periodReminderTime.first(),
                settings              = SettingsBackup(
                    medsNotificationsEnabled = prefs.medsNotificationsEnabled.first(),
                    themeMode                = prefs.themeMode.first(),
                    themeLightStart          = prefs.themeLightStart.first(),
                    themeDarkStart           = prefs.themeDarkStart.first(),
                    isDarkTheme              = prefs.isDarkTheme.first(),
                    dynamicColor             = prefs.dynamicColor.first(),
                    birthYear                = prefs.birthYear.first(),
                    sex                      = prefs.sex.first().storageKey,
                ),
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
}
