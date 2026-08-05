package se.partee71.dagboken.ui.hantera

import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import se.partee71.dagboken.data.datastore.DEFAULT_PERIOD_REMINDER_TIME
import se.partee71.dagboken.data.datastore.DEFAULT_SCREENING_EVENTS
import se.partee71.dagboken.data.datastore.ScreeningEventConfig
import se.partee71.dagboken.data.datastore.SymptomOption
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
import se.partee71.dagboken.data.repository.AktiviteterRepository
import se.partee71.dagboken.data.repository.HandelserRepository
import se.partee71.dagboken.data.repository.MedicinerRepository
import se.partee71.dagboken.data.repository.SjukdomarRepository
import se.partee71.dagboken.domain.model.Favorit
import se.partee71.dagboken.domain.model.Sex
import se.partee71.dagboken.notifications.AlarmScheduler

@OptIn(ExperimentalCoroutinesApi::class)
class HanteraViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    private lateinit var prefs: PreferencesRepository
    private lateinit var authRepo: FirebaseAuthRepository
    private lateinit var alarmScheduler: AlarmScheduler
    private lateinit var medicinerRepo: MedicinerRepository

    private val aktivitetOptionsFlow = MutableStateFlow(listOf(SymptomOption("Promenad"), SymptomOption("Jobb")))
    private val symptomOptionsFlow   = MutableStateFlow(listOf(SymptomOption("Huvudvärk")))
    private val handelseTypOptionsFlow = MutableStateFlow(listOf(SymptomOption("Yrsel")))
    private val medicinFavoriterFlow = MutableStateFlow<List<Favorit>>(emptyList())
    private val birthYearFlow = MutableStateFlow<Int?>(null)
    private val sexFlow = MutableStateFlow(Sex.EJ_ANGIVET)

    private lateinit var aktiviteterRepo: AktiviteterRepository
    private lateinit var handelserRepo: HandelserRepository
    private lateinit var sjukdomarRepo: SjukdomarRepository
    private lateinit var viewModel: HanteraViewModel

    @Before fun setUp() {
        Dispatchers.setMain(testDispatcher)
        prefs = mockk(relaxed = true) {
            every { isDarkTheme } returns flowOf(true)
            every { dynamicColor } returns flowOf(true)
            every { themeMode } returns flowOf("auto")
            every { themeLightStart } returns flowOf(7)
            every { themeDarkStart } returns flowOf(21)
            every { medsNotificationsEnabled } returns flowOf(false)
            every { screeningEventConfigs } returns flowOf(DEFAULT_SCREENING_EVENTS)
            every { periodReminderTime } returns flowOf(DEFAULT_PERIOD_REMINDER_TIME)
            every { aktivitetOptions } returns aktivitetOptionsFlow
            every { symptomOptions } returns symptomOptionsFlow
            every { handelseTypOptions } returns handelseTypOptionsFlow
            every { birthYear } returns birthYearFlow
            every { sex } returns sexFlow
        }
        authRepo = mockk(relaxed = true) {
            every { authStateFlow } returns MutableStateFlow(null)
        }
        alarmScheduler = mockk(relaxed = true)
        medicinerRepo = mockk(relaxed = true) {
            every { allFavoriter } returns medicinFavoriterFlow
        }
        aktiviteterRepo = mockk(relaxed = true)
        handelserRepo   = mockk(relaxed = true)
        sjukdomarRepo   = mockk(relaxed = true)
        viewModel = HanteraViewModel(prefs, authRepo, alarmScheduler, medicinerRepo, aktiviteterRepo, handelserRepo, sjukdomarRepo)
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
        val saved = slot<List<SymptomOption>>()
        coVerify { prefs.setAktivitetOptions(capture(saved)) }
        assertTrue(saved.captured.any { it.name == "Simning" })
        assertEquals("", viewModel.state.value.newAktivitetOption)
    }

    @Test fun `deleteAktivitetOption saves list without removed option`() = runTest {
        viewModel.deleteAktivitetOption("Promenad")
        val saved = slot<List<SymptomOption>>()
        coVerify { prefs.setAktivitetOptions(capture(saved)) }
        assertFalse(saved.captured.any { it.name == "Promenad" })
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
        val saved = slot<List<SymptomOption>>()
        coVerify { prefs.setSymptomOptions(capture(saved)) }
        assertTrue(saved.captured.any { it.name == "Illamående" })
        assertEquals("", viewModel.state.value.newSymptomOption)
    }

    // ─── addHandelseTypOption ─────────────────────────────────────────────────

    @Test fun `addHandelseTypOption ignores blank input`() = runTest {
        viewModel.setNewHandelseTypOption("   ")
        viewModel.addHandelseTypOption()
        coVerify(exactly = 0) { prefs.setHandelseTypOptions(any()) }
    }

    @Test fun `addHandelseTypOption ignores duplicate value`() = runTest {
        viewModel.setNewHandelseTypOption("Yrsel")
        viewModel.addHandelseTypOption()
        coVerify(exactly = 0) { prefs.setHandelseTypOptions(any()) }
    }

    @Test fun `addHandelseTypOption saves new option and clears input field`() = runTest {
        viewModel.setNewHandelseTypOption("Andnöd")
        viewModel.addHandelseTypOption()
        val saved = slot<List<SymptomOption>>()
        coVerify { prefs.setHandelseTypOptions(capture(saved)) }
        assertTrue(saved.captured.any { it.name == "Andnöd" })
        assertEquals("", viewModel.state.value.newHandelseTypOption)
    }

    @Test fun `deleteHandelseTypOption saves list without removed option`() = runTest {
        viewModel.deleteHandelseTypOption("Yrsel")
        val saved = slot<List<SymptomOption>>()
        coVerify { prefs.setHandelseTypOptions(capture(saved)) }
        assertFalse(saved.captured.any { it.name == "Yrsel" })
    }

    @Test fun `toggleHandelseTypFavorite flips isFavorite for matching option`() = runTest {
        viewModel.toggleHandelseTypFavorite("Yrsel")
        val saved = slot<List<SymptomOption>>()
        coVerify { prefs.setHandelseTypOptions(capture(saved)) }
        assertTrue(saved.captured.single { it.name == "Yrsel" }.isFavorite)
    }

    @Test fun `renameHandelseTypOption ignores duplicate target name`() = runTest {
        handelseTypOptionsFlow.value = listOf(SymptomOption("Yrsel"), SymptomOption("Andnöd"))
        viewModel = HanteraViewModel(prefs, authRepo, alarmScheduler, medicinerRepo, aktiviteterRepo, handelserRepo, sjukdomarRepo)
        viewModel.renameHandelseTypOption("Yrsel", "Andnöd")
        coVerify(exactly = 0) { prefs.setHandelseTypOptions(any()) }
    }

    @Test fun `renameHandelseTypOption saves list with renamed option`() = runTest {
        viewModel.renameHandelseTypOption("Yrsel", "Hjärtklappning")
        val saved = slot<List<SymptomOption>>()
        coVerify { prefs.setHandelseTypOptions(capture(saved)) }
        assertTrue(saved.captured.any { it.name == "Hjärtklappning" })
        assertFalse(saved.captured.any { it.name == "Yrsel" })
    }

    // ─── notification toggles and rescheduling ────────────────────────────────

    @Test fun `toggleMedsNotifications calls rescheduleAll`() = runTest {
        viewModel.toggleMedsNotifications()
        coVerify { alarmScheduler.rescheduleAll() }
    }

    @Test fun `toggleScreeningEvent calls rescheduleAll`() = runTest {
        viewModel.toggleScreeningEvent(0)
        coVerify { alarmScheduler.rescheduleAll() }
    }

    @Test fun `setScreeningEventTime reschedules when event is enabled`() = runTest {
        val enabledConfigs = DEFAULT_SCREENING_EVENTS.toMutableList()
            .also { it[0] = ScreeningEventConfig(enabled = true, time = "08:00") }
        every { prefs.screeningEventConfigs } returns flowOf(enabledConfigs)
        viewModel = HanteraViewModel(prefs, authRepo, alarmScheduler, medicinerRepo, aktiviteterRepo, handelserRepo, sjukdomarRepo)

        viewModel.setScreeningEventTime(0, "09:30")
        coVerify { alarmScheduler.rescheduleAll() }
    }

    // ─── periodpåminnelse (NOT-13) ────────────────────────────────────────────

    @Test fun `periodReminderTime defaults to nine`() = runTest {
        assertEquals(DEFAULT_PERIOD_REMINDER_TIME, viewModel.state.value.periodReminderTime)
    }

    @Test fun `setPeriodReminderTime persists and reschedules the alarm`() = runTest {
        viewModel.setPeriodReminderTime("07:15")

        coVerify { prefs.setPeriodReminderTime("07:15") }
    }

    // ─── Profil (HLS-11) ─────────────────────────────────────────────────────

    @Test fun `a plausible birth year is saved`() = runTest(testDispatcher) {
        viewModel.setBirthYear(1971)
        coVerify { prefs.setBirthYear(1971) }
    }

    @Test fun `an implausible birth year clears the value instead of storing nonsense`() = runTest(testDispatcher) {
        // Ett orimligt årtal skulle ge en ålder som tyst mäter sömnen mot fel norm.
        viewModel.setBirthYear(1200)
        coVerify { prefs.setBirthYear(null) }
    }

    @Test fun `clearing the birth year is passed through`() = runTest(testDispatcher) {
        viewModel.setBirthYear(null)
        coVerify { prefs.setBirthYear(null) }
    }

    @Test fun `sex is saved`() = runTest(testDispatcher) {
        viewModel.setSex(Sex.MAN)
        coVerify { prefs.setSex(Sex.MAN) }
    }

    @Test fun `profile values reach the ui state`() = runTest(testDispatcher) {
        birthYearFlow.value = 1971
        sexFlow.value = Sex.MAN
        assertEquals(1971, viewModel.state.value.birthYear)
        assertEquals(Sex.MAN, viewModel.state.value.sex)
        coVerify { alarmScheduler.schedulePeriodReminder("07:15") }
    }

    @Test fun `periodReminderTime from prefs reaches the ui state`() = runTest {
        every { prefs.periodReminderTime } returns flowOf("18:45")
        viewModel = HanteraViewModel(prefs, authRepo, alarmScheduler, medicinerRepo, aktiviteterRepo, handelserRepo, sjukdomarRepo)

        assertEquals("18:45", viewModel.state.value.periodReminderTime)
    }

    // ─── medicinFavoriter / toggleMedicinFavorite ─────────────────────────────

    private fun favorit(id: String = "f1", isFavorite: Boolean = false) = Favorit(
        id = id, namn = "Paracetamol", dos = "500", enhet = "mg", tidpunkt = "Vid behov",
        minTidMellan = 0, maxDoserPerDag = 0, isFavorite = isFavorite,
    )

    @Test fun `medicinFavoriter emits list from MedicinerRepository`() = runTest {
        medicinFavoriterFlow.value = listOf(favorit())
        assertEquals(listOf(favorit()), viewModel.medicinFavoriter.value)
    }

    @Test fun `toggleMedicinFavorite marks a non-favorite entry as favorite`() = runTest {
        viewModel.toggleMedicinFavorite(favorit(isFavorite = false))
        coVerify { medicinerRepo.setFavoritFavorite("f1", true) }
    }

    @Test fun `toggleMedicinFavorite unmarks an already-favorite entry`() = runTest {
        viewModel.toggleMedicinFavorite(favorit(isFavorite = true))
        coVerify { medicinerRepo.setFavoritFavorite("f1", false) }
    }

    // ─── namnbyte migrerar loggad data (HAN-9) ────────────────────────────────

    @Test fun `renameAktivitetOption also renames already logged aktiviteter`() = runTest {
        viewModel.renameAktivitetOption("Promenad", "Långpromenad")
        coVerify { aktiviteterRepo.renameAktivitet("Promenad", "Långpromenad") }
    }

    @Test fun `renameSymptomOption renames the symptom in both aktiviteter and incheckningar`() = runTest {
        viewModel.renameSymptomOption("Huvudvärk", "Migrän")
        coVerify { aktiviteterRepo.renameSymptom("Huvudvärk", "Migrän") }
        coVerify { sjukdomarRepo.renameSymptom("Huvudvärk", "Migrän") }
    }

    @Test fun `renameHandelseTypOption also renames already logged handelser`() = runTest {
        viewModel.renameHandelseTypOption("Yrsel", "Ostadighet")
        coVerify { handelserRepo.renameTyp("Yrsel", "Ostadighet") }
    }

    @Test fun `a rejected rename does not touch logged data`() = runTest {
        // Samma namn som redan finns i listan avvisas.
        viewModel.renameAktivitetOption("Promenad", "Jobb")
        coVerify(exactly = 0) { aktiviteterRepo.renameAktivitet(any(), any()) }
    }

    // ─── behörighetsläge för påminnelser (NOT-16) ─────────────────────────────

    @Test fun `refreshPermissionState reports blocked notifications`() = runTest {
        every { alarmScheduler.canPostNotifications() } returns false
        every { alarmScheduler.canScheduleExactAlarms() } returns true

        viewModel.refreshPermissionState()

        assertTrue(viewModel.state.value.notificationsBlocked)
        assertFalse(viewModel.state.value.exactAlarmsBlocked)
    }

    @Test fun `refreshPermissionState reports blocked exact alarms`() = runTest {
        every { alarmScheduler.canPostNotifications() } returns true
        every { alarmScheduler.canScheduleExactAlarms() } returns false

        viewModel.refreshPermissionState()

        assertFalse(viewModel.state.value.notificationsBlocked)
        assertTrue(viewModel.state.value.exactAlarmsBlocked)
    }

    @Test fun `refreshPermissionState reports nothing blocked when both are granted`() = runTest {
        every { alarmScheduler.canPostNotifications() } returns true
        every { alarmScheduler.canScheduleExactAlarms() } returns true

        viewModel.refreshPermissionState()

        assertFalse(viewModel.state.value.notificationsBlocked)
        assertFalse(viewModel.state.value.exactAlarmsBlocked)
    }
}
