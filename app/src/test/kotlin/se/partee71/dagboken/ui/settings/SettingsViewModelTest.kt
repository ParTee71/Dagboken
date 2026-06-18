package se.partee71.dagboken.ui.settings

import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import se.partee71.dagboken.data.auth.FirebaseAuthRepository
import se.partee71.dagboken.data.datastore.PreferencesRepository
import se.partee71.dagboken.notifications.AlarmScheduler

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    private lateinit var prefs: PreferencesRepository
    private lateinit var authRepo: FirebaseAuthRepository
    private lateinit var alarmScheduler: AlarmScheduler

    private val aktivitetOptionsFlow = MutableStateFlow(listOf("Promenad", "Jobb"))
    private val symptomOptionsFlow   = MutableStateFlow(listOf("Huvudvärk"))

    private lateinit var viewModel: SettingsViewModel

    @Before fun setUp() {
        Dispatchers.setMain(testDispatcher)
        prefs = mockk(relaxed = true) {
            every { isDarkTheme } returns flowOf(true)
            every { dynamicColor } returns flowOf(true)
            every { themeMode } returns flowOf("auto")
            every { themeLightStart } returns flowOf(7)
            every { themeDarkStart } returns flowOf(21)
            every { medsNotificationsEnabled } returns flowOf(false)
            every { screeningNotificationsEnabled } returns flowOf(false)
            every { screeningReminderTimes } returns flowOf(listOf("08:00", "12:00", "16:00", "20:00"))
            every { aktivitetOptions } returns aktivitetOptionsFlow
            every { symptomOptions } returns symptomOptionsFlow
        }
        authRepo = mockk(relaxed = true) {
            every { authStateFlow } returns MutableStateFlow(null)
        }
        alarmScheduler = mockk(relaxed = true)
        viewModel = SettingsViewModel(prefs, authRepo, alarmScheduler)
    }

    @After fun tearDown() { Dispatchers.resetMain() }

    // ─── theme clamping ───────────────────────────────────────────────────────

    @Test fun `setThemeLightStart clamps value to one below dark start`() = runTest {
        // darkStart = 21; light must be ≤ 20
        viewModel.setThemeLightStart(25)
        coVerify { prefs.setThemeLightStart(20) }
    }

    @Test fun `setThemeLightStart does not clamp valid value`() = runTest {
        viewModel.setThemeLightStart(10)
        coVerify { prefs.setThemeLightStart(10) }
    }

    @Test fun `setThemeDarkStart clamps value to one above light start`() = runTest {
        // lightStart = 7; dark must be ≥ 8
        viewModel.setThemeDarkStart(3)
        coVerify { prefs.setThemeDarkStart(8) }
    }

    @Test fun `setThemeDarkStart does not clamp valid value`() = runTest {
        viewModel.setThemeDarkStart(22)
        coVerify { prefs.setThemeDarkStart(22) }
    }

    // ─── addAktivitetOption ───────────────────────────────────────────────────

    @Test fun `addAktivitetOption ignores blank input`() = runTest {
        viewModel.setNewAktivitetOption("   ")
        viewModel.addAktivitetOption()
        coVerify(exactly = 0) { prefs.setAktivitetOptions(any()) }
    }

    @Test fun `addAktivitetOption ignores duplicate value`() = runTest {
        viewModel.setNewAktivitetOption("Promenad")
        viewModel.addAktivitetOption()
        coVerify(exactly = 0) { prefs.setAktivitetOptions(any()) }
    }

    @Test fun `addAktivitetOption saves new option and clears input field`() = runTest {
        viewModel.setNewAktivitetOption("Simning")
        viewModel.addAktivitetOption()
        val saved = slot<List<String>>()
        coVerify { prefs.setAktivitetOptions(capture(saved)) }
        assertTrue(saved.captured.contains("Simning"))
        assertEquals("", viewModel.state.value.newAktivitetOption)
    }

    @Test fun `removeAktivitetOption saves list without removed option`() = runTest {
        viewModel.removeAktivitetOption("Promenad")
        val saved = slot<List<String>>()
        coVerify { prefs.setAktivitetOptions(capture(saved)) }
        assertFalse(saved.captured.contains("Promenad"))
    }

    // ─── addSymptomOption ─────────────────────────────────────────────────────

    @Test fun `addSymptomOption ignores blank input`() = runTest {
        viewModel.setNewSymptomOption("")
        viewModel.addSymptomOption()
        coVerify(exactly = 0) { prefs.setSymptomOptions(any()) }
    }

    @Test fun `addSymptomOption ignores duplicate value`() = runTest {
        viewModel.setNewSymptomOption("Huvudvärk")
        viewModel.addSymptomOption()
        coVerify(exactly = 0) { prefs.setSymptomOptions(any()) }
    }

    @Test fun `addSymptomOption saves new option and clears input field`() = runTest {
        viewModel.setNewSymptomOption("Illamående")
        viewModel.addSymptomOption()
        val saved = slot<List<String>>()
        coVerify { prefs.setSymptomOptions(capture(saved)) }
        assertTrue(saved.captured.contains("Illamående"))
        assertEquals("", viewModel.state.value.newSymptomOption)
    }

    // ─── notification toggles and rescheduling ────────────────────────────────

    @Test fun `toggleMedsNotifications calls rescheduleAll`() = runTest {
        viewModel.toggleMedsNotifications()
        coVerify { alarmScheduler.rescheduleAll() }
    }

    @Test fun `toggleScreeningNotifications calls rescheduleAll`() = runTest {
        viewModel.toggleScreeningNotifications()
        coVerify { alarmScheduler.rescheduleAll() }
    }

    @Test fun `setScreeningReminderTime updates time list and reschedules when enabled`() = runTest {
        every { prefs.screeningNotificationsEnabled } returns flowOf(true)
        viewModel = SettingsViewModel(prefs, authRepo, alarmScheduler)

        viewModel.setScreeningReminderTime(0, "09:30")
        coVerify { alarmScheduler.scheduleScreeningAlarms(any()) }
    }
}
