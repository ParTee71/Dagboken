package se.partee71.dagboken.ui.migration

import android.app.PendingIntent
import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.room.withTransaction
import kotlinx.serialization.json.Json
import se.partee71.dagboken.data.auth.FirebaseAuthRepository
import se.partee71.dagboken.data.datastore.PreferencesRepository
import se.partee71.dagboken.data.datastore.ScreeningEventConfig
import se.partee71.dagboken.data.datastore.SymptomOption
import se.partee71.dagboken.data.migration.BackupJson
import se.partee71.dagboken.data.migration.BackupMapper
import se.partee71.dagboken.data.migration.DriveBackupRepository
import se.partee71.dagboken.data.migration.DriveResult
import se.partee71.dagboken.data.repository.AktiviteterRepository
import se.partee71.dagboken.data.repository.HandelserRepository
import se.partee71.dagboken.data.repository.MedicinerRepository
import se.partee71.dagboken.data.repository.NoteRepository
import se.partee71.dagboken.data.repository.SjukdomarRepository
import se.partee71.dagboken.data.room.entities.NoteEntity
import se.partee71.dagboken.data.room.AppDatabase
import se.partee71.dagboken.notifications.AlarmScheduler
import javax.inject.Inject

sealed class MigrationState {
    object Idle : MigrationState()
    object CheckingDrive : MigrationState()
    object NoBackupFound : MigrationState()
    object NoAccountSignedIn : MigrationState()
    object Downloading : MigrationState()
    data class NeedsAuthorization(val pendingIntent: PendingIntent) : MigrationState()
    data class Importing(val progress: Float) : MigrationState()
    data class Done(val aktiviteter: Int, val mediciner: Int) : MigrationState()
    data class Error(val message: String) : MigrationState()
}

@HiltViewModel
class MigrationViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val db: AppDatabase,
    private val driveRepo: DriveBackupRepository,
    private val authRepo: FirebaseAuthRepository,
    private val aktiviteterRepo: AktiviteterRepository,
    private val medicinerRepo: MedicinerRepository,
    private val sjukdomarRepo: SjukdomarRepository,
    private val handelserRepo: HandelserRepository,
    private val noteRepo: NoteRepository,
    private val prefs: PreferencesRepository,
    private val alarmScheduler: AlarmScheduler,
    private val json: Json,
) : ViewModel() {

    private val _state = MutableStateFlow<MigrationState>(MigrationState.Idle)
    val state: StateFlow<MigrationState> = _state.asStateFlow()

    fun startMigration() {
        viewModelScope.launch {
            _state.value = MigrationState.CheckingDrive

            when (val listResult = driveRepo.listBackups()) {
                is DriveResult.NoAccount           -> _state.value = MigrationState.NoAccountSignedIn
                is DriveResult.NoBackupFound       -> _state.value = MigrationState.NoBackupFound
                is DriveResult.NeedsAuthorization  -> _state.value = MigrationState.NeedsAuthorization(listResult.pendingIntent)
                is DriveResult.Error               -> _state.value = MigrationState.Error(listResult.message)
                is DriveResult.Success -> {
                    if (listResult.value.isEmpty()) {
                        _state.value = MigrationState.NoBackupFound
                        return@launch
                    }
                    _state.value = MigrationState.Downloading

                    when (val dlResult = driveRepo.downloadLatestBackup()) {
                        is DriveResult.Error              -> _state.value = MigrationState.Error(dlResult.message)
                        is DriveResult.NoBackupFound      -> _state.value = MigrationState.NoBackupFound
                        is DriveResult.NoAccount          -> _state.value = MigrationState.NoAccountSignedIn
                        is DriveResult.NeedsAuthorization -> _state.value = MigrationState.NeedsAuthorization(dlResult.pendingIntent)
                        is DriveResult.Success            -> doImport(dlResult.value)
                    }
                }
            }
        }
    }

    fun signInAndMigrate(activityContext: Context) {
        viewModelScope.launch {
            val result = authRepo.signInWithGoogle(activityContext)
            if (result.isSuccess) {
                startMigration()
            } else {
                val msg = result.exceptionOrNull()?.message
                if (msg != null && !msg.contains("cancel", ignoreCase = true)) {
                    _state.value = MigrationState.Error(msg)
                } else {
                    _state.value = MigrationState.Idle
                }
            }
        }
    }

    fun importFromFile(uri: Uri) {
        viewModelScope.launch {
            _state.value = MigrationState.Importing(0f)
            try {
                val content = withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)
                        ?.bufferedReader()?.readText()
                        ?: error("Kunde inte läsa filen")
                }
                doImport(json.decodeFromString<BackupJson>(content))
            } catch (e: Exception) {
                _state.value = MigrationState.Error(e.message ?: "Fel vid filimport")
            }
        }
    }

    fun skipMigration() {
        viewModelScope.launch { prefs.setMigrationDone(true) }
    }

    private suspend fun doImport(backup: BackupJson) {
        _state.value = MigrationState.Importing(0f)
        val aktiviteter       = BackupMapper.toAktiviteter(backup)
        val mediciner         = BackupMapper.toMediciner(backup)
        val recept            = BackupMapper.toRecept(backup)
        val favoriter         = BackupMapper.toFavoriter(backup)
        val sjukdomsEpisoder  = BackupMapper.toSjukdomsEpisoder(backup)
        val sjukdomsIncheckningar = BackupMapper.toSjukdomsIncheckningar(backup)
        val handelser         = BackupMapper.toHandelser(backup)
        val notes             = BackupMapper.toNotes(backup)

        // Allt i en transaktion — sjukdomsdatan låg tidigare utanför, så ett fel där
        // lämnade en halvfärdig återställning bakom sig med resten redan committad.
        db.withTransaction {
            aktiviteterRepo.importAll(aktiviteter)
            medicinerRepo.importMediciner(mediciner)
            medicinerRepo.importRecept(recept)
            medicinerRepo.importFavoriter(favoriter)
            handelserRepo.importAll(handelser)
            noteRepo.importAll(notes)
            sjukdomarRepo.importEpisoder(sjukdomsEpisoder)
            sjukdomarRepo.importIncheckningar(sjukdomsIncheckningar)
        }

        val aktivitetOpts = backup.aktiviteterOptionsV2?.map { SymptomOption(it.name, it.isFavorite) }
            ?: backup.aktiviteterOptions?.map { SymptomOption(it) }
        aktivitetOpts?.let { prefs.setAktivitetOptions(it) }

        val symptomOpts = backup.symptomOptionsV2?.map { SymptomOption(it.name, it.isFavorite) }
            ?: backup.symptomOptions?.map { SymptomOption(it) }
        symptomOpts?.let { prefs.setSymptomOptions(it) }

        backup.handelseTypOptions
            ?.map { SymptomOption(it.name, it.isFavorite) }
            ?.let { prefs.setHandelseTypOptions(it) }

        backup.screeningEventConfigs
            ?.map { ScreeningEventConfig(it.enabled, it.time) }
            ?.let { prefs.setScreeningEventConfigs(it) }

        backup.sheetsConfig?.let { prefs.setSheetsConfig(it) }

        backup.periodReminderTime
            ?.takeIf { it.isNotBlank() }
            ?.let { prefs.setPeriodReminderTime(it) }

        // Appinställningar (BCK-10). Varje fält är null i äldre backupar och lämnas då
        // orört, så en gammal backup aldrig nollställer något användaren redan ställt in.
        backup.settings?.let { settings ->
            settings.medsNotificationsEnabled?.let { prefs.setMedsNotificationsEnabled(it) }
            settings.themeMode?.takeIf { it.isNotBlank() }?.let { prefs.setThemeMode(it) }
            settings.themeLightStart?.let { prefs.setThemeLightStart(it) }
            settings.themeDarkStart?.let { prefs.setThemeDarkStart(it) }
            settings.isDarkTheme?.let { prefs.setDarkTheme(it) }
            settings.dynamicColor?.let { prefs.setDynamicColor(it) }
        }

        // Larmen sattes vid appstart, innan de återställda tiderna fanns — utan detta
        // fick de återställda påminnelserna effekt först vid nästa processtart (NOT-15).
        alarmScheduler.rescheduleAll()

        _state.value = MigrationState.Importing(1f)
        prefs.setMigrationDone(true)
        _state.value = MigrationState.Done(aktiviteter.size, mediciner.size)
    }
}
