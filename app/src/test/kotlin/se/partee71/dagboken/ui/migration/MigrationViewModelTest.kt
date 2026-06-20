package se.partee71.dagboken.ui.migration

import android.content.Context
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import androidx.room.withTransaction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import se.partee71.dagboken.data.auth.FirebaseAuthRepository
import se.partee71.dagboken.data.datastore.PreferencesRepository
import se.partee71.dagboken.data.migration.AktivitetJson
import se.partee71.dagboken.data.migration.BackupJson
import se.partee71.dagboken.data.migration.DriveBackupFile
import se.partee71.dagboken.data.migration.DriveBackupRepository
import se.partee71.dagboken.data.migration.DriveResult
import se.partee71.dagboken.data.repository.AktiviteterRepository
import se.partee71.dagboken.data.repository.MedicinerRepository
import se.partee71.dagboken.data.room.AppDatabase

/**
 * Notes on test design:
 * UnconfinedTestDispatcher runs viewModelScope coroutines synchronously, so StateFlow
 * conflates intermediate states. We check state.value AFTER the call completes rather
 * than collecting intermediate emissions via Turbine.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MigrationViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    private val context   = mockk<Context>(relaxed = true)
    private val db        = mockk<AppDatabase>(relaxed = true)
    private val driveRepo = mockk<DriveBackupRepository>(relaxed = true)
    private val authRepo  = mockk<FirebaseAuthRepository>(relaxed = true)
    private val aktivRepo = mockk<AktiviteterRepository>(relaxed = true)
    private val medicRepo = mockk<MedicinerRepository>(relaxed = true)
    private val prefs     = mockk<PreferencesRepository>(relaxed = true)

    private lateinit var viewModel: MigrationViewModel

    @Before fun setUp() {
        Dispatchers.setMain(testDispatcher)
        every { authRepo.authStateFlow } returns MutableStateFlow(null)
        every { prefs.migrationDone } returns flowOf(false)
        // Make withTransaction execute its block (it's a static Room KTX extension)
        mockkStatic("androidx.room.RoomDatabaseKt")
        coEvery { db.withTransaction<Unit>(any()) } coAnswers {
            secondArg<suspend () -> Unit>().invoke()
        }
        viewModel = MigrationViewModel(context, db, driveRepo, authRepo, aktivRepo, medicRepo, prefs)
    }

    @After fun tearDown() {
        unmockkStatic("androidx.room.RoomDatabaseKt")
        Dispatchers.resetMain()
    }

    private fun emptyBackup() = BackupJson()

    private fun backupWith(aktiviteter: Int = 1) = BackupJson(
        aktiviteter = List(aktiviteter) {
            AktivitetJson(id = "a$it", datum = "2026-01-15", type = "aktivitet")
        },
    )

    // ─── initial state ────────────────────────────────────────────────────────

    @Test fun `initial state is Idle`() {
        assertTrue(viewModel.state.value is MigrationState.Idle)
    }

    // ─── startMigration – NoAccount ───────────────────────────────────────────

    @Test fun `startMigration lands on NoAccountSignedIn when drive returns NoAccount`() = runTest {
        coEvery { driveRepo.listBackups() } returns DriveResult.NoAccount
        viewModel.startMigration()
        assertTrue(viewModel.state.value is MigrationState.NoAccountSignedIn)
    }

    // ─── startMigration – NoBackupFound ───────────────────────────────────────

    @Test fun `startMigration lands on NoBackupFound when drive returns NoBackupFound`() = runTest {
        coEvery { driveRepo.listBackups() } returns DriveResult.NoBackupFound
        viewModel.startMigration()
        assertTrue(viewModel.state.value is MigrationState.NoBackupFound)
    }

    @Test fun `startMigration lands on NoBackupFound when backup list is empty`() = runTest {
        coEvery { driveRepo.listBackups() } returns DriveResult.Success(emptyList())
        viewModel.startMigration()
        assertTrue(viewModel.state.value is MigrationState.NoBackupFound)
    }

    // ─── startMigration – NeedsAuthorization ─────────────────────────────────

    @Test fun `startMigration lands on NeedsAuthorization when auth is needed`() = runTest {
        val pi = mockk<android.app.PendingIntent>(relaxed = true)
        coEvery { driveRepo.listBackups() } returns DriveResult.NeedsAuthorization(pi)
        viewModel.startMigration()
        assertTrue(viewModel.state.value is MigrationState.NeedsAuthorization)
    }

    // ─── startMigration – Error ───────────────────────────────────────────────

    @Test fun `startMigration lands on Error when drive returns error`() = runTest {
        coEvery { driveRepo.listBackups() } returns DriveResult.Error("network failure")
        viewModel.startMigration()
        val state = viewModel.state.value
        assertTrue(state is MigrationState.Error)
        assertTrue((state as MigrationState.Error).message.contains("network failure"))
    }

    // ─── startMigration – Done ────────────────────────────────────────────────

    @Test fun `startMigration lands on Done after successful import`() = runTest {
        val file = DriveBackupFile("id1", "backup.json", "2026-01-15T00:00:00")
        coEvery { driveRepo.listBackups() } returns DriveResult.Success(listOf(file))
        coEvery { driveRepo.downloadLatestBackup() } returns DriveResult.Success(emptyBackup())
        viewModel.startMigration()
        assertTrue(viewModel.state.value is MigrationState.Done)
    }

    @Test fun `Done state reports correct aktiviteter count`() = runTest {
        val file = DriveBackupFile("id1", "backup.json", "2026-01-15T00:00:00")
        coEvery { driveRepo.listBackups() } returns DriveResult.Success(listOf(file))
        coEvery { driveRepo.downloadLatestBackup() } returns DriveResult.Success(backupWith(aktiviteter = 3))
        viewModel.startMigration()
        assertEquals(3, (viewModel.state.value as MigrationState.Done).aktiviteter)
    }

    // ─── setMigrationDone on success ─────────────────────────────────────────

    @Test fun `successful import calls setMigrationDone`() = runTest {
        val file = DriveBackupFile("id1", "backup.json", "2026-01-15T00:00:00")
        coEvery { driveRepo.listBackups() } returns DriveResult.Success(listOf(file))
        coEvery { driveRepo.downloadLatestBackup() } returns DriveResult.Success(emptyBackup())
        viewModel.startMigration()
        coVerify { prefs.setMigrationDone(true) }
    }

    // ─── skipMigration ────────────────────────────────────────────────────────

    @Test fun `skipMigration calls setMigrationDone`() = runTest {
        viewModel.skipMigration()
        coVerify { prefs.setMigrationDone(true) }
    }
}
