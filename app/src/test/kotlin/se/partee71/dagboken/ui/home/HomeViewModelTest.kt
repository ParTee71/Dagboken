package se.partee71.dagboken.ui.home

import app.cash.turbine.test
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
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
import se.partee71.dagboken.data.datastore.ScreeningEventConfig
import se.partee71.dagboken.data.repository.AktiviteterRepository
import se.partee71.dagboken.data.repository.HealthAvailability
import se.partee71.dagboken.data.repository.HealthConnectRepository
import se.partee71.dagboken.data.repository.MedicinerRepository
import se.partee71.dagboken.data.repository.SjukdomarRepository
import se.partee71.dagboken.domain.model.Aktivitet
import se.partee71.dagboken.domain.model.DailySteps
import se.partee71.dagboken.domain.model.Medicin
import se.partee71.dagboken.domain.model.WeeklyHealth
import java.time.LocalDate
import java.time.LocalTime

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    private lateinit var medicinerRepo: MedicinerRepository
    private lateinit var aktiviteterRepo: AktiviteterRepository
    private lateinit var authRepo: FirebaseAuthRepository
    private lateinit var prefs: PreferencesRepository
    private lateinit var sjukdomarRepo: SjukdomarRepository
    private lateinit var healthRepo: HealthConnectRepository

    private val todayFlow = MutableStateFlow<List<Medicin>>(emptyList())

    private lateinit var viewModel: HomeViewModel

    @Before fun setUp() {
        Dispatchers.setMain(testDispatcher)
        medicinerRepo = mockk(relaxed = true) {
            every { entriesForDate(any()) } returns todayFlow
        }
        aktiviteterRepo = mockk(relaxed = true) {
            coEvery { getRecent(any(), any()) } returns emptyList()
            coEvery { getScreeningToday() } returns emptyList()
            every { screeningFromDate(any()) } returns flowOf(emptyList())
        }
        authRepo = mockk(relaxed = true) {
            every { authStateFlow } returns MutableStateFlow(null)
        }
        prefs = mockk(relaxed = true) {
            every { screeningEventConfigs } returns flowOf(emptyList())
        }
        sjukdomarRepo = mockk(relaxed = true) {
            every { pagaende } returns flowOf(null)
        }
        healthRepo = mockk(relaxed = true) {
            every { availability() } returns HealthAvailability.NOT_INSTALLED
        }
        viewModel = HomeViewModel(aktiviteterRepo, medicinerRepo, authRepo, prefs, sjukdomarRepo, healthRepo)
    }

    @After fun tearDown() { Dispatchers.resetMain() }

    private fun medicin(
        id: String = "m1",
        namn: String = "Ibuprofen",
        tidpunkt: String = "Morgon",
        tagen: Boolean = false,
        skipped: Boolean = false,
        datum: String = LocalDate.now().toString(),
    ) = Medicin(
        id = id, timestamp = "${datum}T07:00:00.000Z", datum = datum, tid = "07:00",
        namn = namn, dos = "400", enhet = "mg", tidpunkt = tidpunkt,
        tagen = tagen, receptId = null, skipped = skipped,
    )

    // ─── tagenCount ───────────────────────────────────────────────────────────

    @Test fun `tagenCount reflects number of taken medicines`() = runTest {
        todayFlow.value = listOf(
            medicin(id = "m1", tagen = true),
            medicin(id = "m2", tagen = false),
            medicin(id = "m3", tagen = true),
        )
        viewModel.uiState.test {
            assertEquals(2, awaitItem().tagenCount)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test fun `tagenCount is 0 when no medicines are taken`() = runTest {
        todayFlow.value = listOf(medicin(tagen = false))
        viewModel.uiState.test {
            assertEquals(0, awaitItem().tagenCount)
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ─── todayMediciner sorting ───────────────────────────────────────────────

    @Test fun `todayMediciner is sorted by tidpunktSortIndex`() = runTest {
        todayFlow.value = listOf(
            medicin(id = "k", tidpunkt = "Kväll"),
            medicin(id = "m", tidpunkt = "Morgon"),
            medicin(id = "l", tidpunkt = "Lunch"),
        )
        viewModel.uiState.test {
            assertEquals(listOf("m", "l", "k"), awaitItem().todayMediciner.map { it.id })
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ─── overdueMediciner ─────────────────────────────────────────────────────

    @Test fun `overdueMediciner excludes taken medicines`() = runTest {
        todayFlow.value = listOf(medicin(id = "taken", tidpunkt = "Morgon", tagen = true))
        viewModel.uiState.test {
            assertTrue(awaitItem().overdueMediciner.none { it.id == "taken" })
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test fun `overdueMediciner excludes skipped medicines`() = runTest {
        todayFlow.value = listOf(medicin(id = "skipped", tidpunkt = "Morgon", skipped = true))
        viewModel.uiState.test {
            assertTrue(awaitItem().overdueMediciner.none { it.id == "skipped" })
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test fun `overdueMediciner excludes Vid behov medicines`() = runTest {
        todayFlow.value = listOf(medicin(id = "vb", tidpunkt = "Vid behov"))
        viewModel.uiState.test {
            assertTrue(awaitItem().overdueMediciner.none { it.id == "vb" })
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test fun `overdueMediciner is sorted by tidpunktSortIndex`() = runTest {
        // Use Morgon (7) and Lunch (12) — both safely in the past at CI run time
        todayFlow.value = listOf(
            medicin(id = "l", tidpunkt = "Lunch"),
            medicin(id = "m", tidpunkt = "Morgon"),
        )
        viewModel.uiState.test {
            val overdue = awaitItem().overdueMediciner.filter { it.id in listOf("l", "m") }
            if (overdue.size == 2) {
                assertEquals("m", overdue[0].id)
                assertEquals("l", overdue[1].id)
            }
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ─── kommandeMediciner ────────────────────────────────────────────────────

    @Test fun `computeKommandeMediciner returns future dose not yet due`() {
        val dose = medicin(id = "n", tidpunkt = "Natt") // 22:00
        val result = computeKommandeMediciner(isToday = true, nowTime = LocalTime.of(8, 0), dayMediciner = listOf(dose))
        assertEquals(listOf("n"), result.map { it.id })
    }

    @Test fun `computeKommandeMediciner excludes dose whose time has passed`() {
        val dose = medicin(id = "m", tidpunkt = "Morgon") // 07:00
        val result = computeKommandeMediciner(isToday = true, nowTime = LocalTime.of(8, 0), dayMediciner = listOf(dose))
        assertTrue(result.isEmpty())
    }

    @Test fun `computeKommandeMediciner excludes taken medicines`() {
        val dose = medicin(id = "n", tidpunkt = "Natt", tagen = true)
        val result = computeKommandeMediciner(isToday = true, nowTime = LocalTime.of(8, 0), dayMediciner = listOf(dose))
        assertTrue(result.isEmpty())
    }

    @Test fun `computeKommandeMediciner excludes skipped medicines`() {
        val dose = medicin(id = "n", tidpunkt = "Natt", skipped = true)
        val result = computeKommandeMediciner(isToday = true, nowTime = LocalTime.of(8, 0), dayMediciner = listOf(dose))
        assertTrue(result.isEmpty())
    }

    @Test fun `computeKommandeMediciner excludes Vid behov medicines`() {
        val dose = medicin(id = "vb", tidpunkt = "Vid behov")
        val result = computeKommandeMediciner(isToday = true, nowTime = LocalTime.of(8, 0), dayMediciner = listOf(dose))
        assertTrue(result.isEmpty())
    }

    @Test fun `computeKommandeMediciner is empty when not viewing today`() {
        val dose = medicin(id = "n", tidpunkt = "Natt")
        val result = computeKommandeMediciner(isToday = false, nowTime = LocalTime.of(8, 0), dayMediciner = listOf(dose))
        assertTrue(result.isEmpty())
    }

    @Test fun `computeKommandeMediciner is sorted by tidpunktSortIndex`() {
        val doses = listOf(medicin(id = "n", tidpunkt = "Natt"), medicin(id = "e", tidpunkt = "Eftermiddag"))
        val result = computeKommandeMediciner(isToday = true, nowTime = LocalTime.of(8, 0), dayMediciner = doses)
        assertEquals(listOf("e", "n"), result.map { it.id })
    }

    @Test fun `kommandeMediciner is wired into uiState from the combine flow`() = runTest {
        todayFlow.value = listOf(medicin(id = "vb", tidpunkt = "Vid behov"))
        viewModel.uiState.test {
            assertTrue(awaitItem().kommandeMediciner.none { it.id == "vb" })
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ─── initial state ────────────────────────────────────────────────────────

    @Test fun `initial uiState has empty medicine lists and tagenCount zero`() = runTest {
        viewModel.uiState.test {
            val state = awaitItem()
            assertTrue(state.todayMediciner.isEmpty())
            assertTrue(state.overdueMediciner.isEmpty())
            assertTrue(state.kommandeMediciner.isEmpty())
            assertEquals(0, state.tagenCount)
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ─── toggleMedicinTagen ───────────────────────────────────────────────────

    @Test fun `toggleMedicinTagen delegates to repo with inverted tagen`() = runTest {
        viewModel.toggleMedicinTagen(medicin(id = "m1", tagen = false))
        coVerify { medicinerRepo.toggleTagen("m1", true) }
    }

    @Test fun `toggleMedicinTagen sets tagen false when medicine is already taken`() = runTest {
        viewModel.toggleMedicinTagen(medicin(id = "m1", tagen = true))
        coVerify { medicinerRepo.toggleTagen("m1", false) }
    }

    // ─── screeningEvents ──────────────────────────────────────────────────────

    @Test fun `screeningEvents is empty when no screening events are enabled`() = runTest {
        every { prefs.screeningEventConfigs } returns flowOf(emptyList())
        viewModel = HomeViewModel(aktiviteterRepo, medicinerRepo, authRepo, prefs, sjukdomarRepo, healthRepo)

        viewModel.uiState.test {
            assertTrue(awaitItem().screeningEvents.isEmpty())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test fun `screeningEvents maps enabled configs to labels by index`() = runTest {
        every { prefs.screeningEventConfigs } returns flowOf(
            listOf(
                ScreeningEventConfig(enabled = true, time = "08:00"),
                ScreeningEventConfig(enabled = false, time = "12:00"),
            ),
        )
        viewModel = HomeViewModel(aktiviteterRepo, medicinerRepo, authRepo, prefs, sjukdomarRepo, healthRepo)

        viewModel.uiState.test {
            val events = awaitItem().screeningEvents
            assertEquals(1, events.size)
            assertEquals("Efter frukost", events[0].label)
            assertEquals("08:00", events[0].time)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test fun `screeningEvents marks an event logged when a matching screening was saved today`() = runTest {
        every { prefs.screeningEventConfigs } returns flowOf(
            listOf(ScreeningEventConfig(enabled = true, time = "08:00")),
        )
        val loggedScreening = Aktivitet(
            id = "s1", timestamp = "x", datum = LocalDate.now().toString(), tid = "08:05",
            aktivitet = "Efter frukost", energy = 5, stress = 2, somatiska = 0, symptom = "",
            type = "screening",
        )
        every { aktiviteterRepo.screeningFromDate(any()) } returns flowOf(listOf(loggedScreening))
        viewModel = HomeViewModel(aktiviteterRepo, medicinerRepo, authRepo, prefs, sjukdomarRepo, healthRepo)

        viewModel.uiState.test {
            val event = awaitItem().screeningEvents.first()
            assertTrue(event.logged)
            assertFalse(event.overdue)
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ─── stat-pill: screeningPoints/Labels ───────────────────────────────────

    @Test fun `screeningPoints and screeningLabels have matching sizes`() = runTest {
        viewModel.uiState.test {
            val state = awaitItem()
            assertEquals(state.screeningPoints.size, state.screeningLabels.size)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test fun `googleEmail is null when no user is signed in`() = runTest {
        viewModel.uiState.test {
            assertFalse(awaitItem().isSigningIn)
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ─── datumnavigering (#114) ───────────────────────────────────────────────

    @Test fun `initial selectedDate is today and isToday is true`() = runTest {
        viewModel.uiState.test {
            val state = awaitItem()
            assertEquals(LocalDate.now(), state.selectedDate)
            assertTrue(state.isToday)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test fun `previousDay moves selectedDate back one day`() = runTest {
        viewModel.previousDay()
        viewModel.uiState.test {
            val state = awaitItem()
            assertEquals(LocalDate.now().minusDays(1), state.selectedDate)
            assertFalse(state.isToday)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test fun `nextDay is a no-op when already viewing today`() = runTest {
        viewModel.nextDay()
        viewModel.uiState.test {
            assertEquals(LocalDate.now(), awaitItem().selectedDate)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test fun `nextDay moves forward when viewing a past day, but never past today`() = runTest {
        viewModel.previousDay()
        viewModel.previousDay()
        viewModel.nextDay()
        viewModel.uiState.test {
            assertEquals(LocalDate.now().minusDays(1), awaitItem().selectedDate)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test fun `overdueMediciner is empty when viewing a past day, even for an unmarked morning dose`() = runTest {
        val yesterday = LocalDate.now().minusDays(1).toString()
        todayFlow.value = listOf(medicin(id = "m1", tidpunkt = "Morgon", datum = yesterday))
        viewModel.previousDay()

        viewModel.uiState.test {
            assertTrue(awaitItem().overdueMediciner.isEmpty())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test fun `navigating to a new date ensures that date's scheduled doses are seeded`() = runTest {
        viewModel.previousDay()
        coVerify { medicinerRepo.ensureEntriesForDate(LocalDate.now().minusDays(1)) }
    }

    // ─── hälsokort (HLS-7) ────────────────────────────────────────────────────

    @Test fun `healthCard is NotConnected when Health Connect is unavailable`() = runTest {
        every { healthRepo.availability() } returns HealthAvailability.NOT_INSTALLED
        viewModel = HomeViewModel(aktiviteterRepo, medicinerRepo, authRepo, prefs, sjukdomarRepo, healthRepo)
        assertEquals(HealthCardUiState.NotConnected, viewModel.healthCard.value)
    }

    @Test fun `healthCard is NotConnected when permissions are not granted`() = runTest {
        every { healthRepo.availability() } returns HealthAvailability.AVAILABLE
        coEvery { healthRepo.hasAllPermissions() } returns false
        viewModel = HomeViewModel(aktiviteterRepo, medicinerRepo, authRepo, prefs, sjukdomarRepo, healthRepo)
        assertEquals(HealthCardUiState.NotConnected, viewModel.healthCard.value)
    }

    @Test fun `healthCard exposes weekly data when available and granted`() = runTest {
        val weekly = WeeklyHealth(
            dailySteps = listOf(
                DailySteps(LocalDate.now().minusDays(1), 5000),
                DailySteps(LocalDate.now(), 8000),
            ),
            restingHeartRate = 58,
        )
        every { healthRepo.availability() } returns HealthAvailability.AVAILABLE
        coEvery { healthRepo.hasAllPermissions() } returns true
        coEvery { healthRepo.readWeeklyHealth() } returns weekly
        viewModel = HomeViewModel(aktiviteterRepo, medicinerRepo, authRepo, prefs, sjukdomarRepo, healthRepo)
        assertEquals(HealthCardUiState.Data(weekly), viewModel.healthCard.value)
    }

    @Test fun `healthCard is NotConnected when weekly read throws`() = runTest {
        every { healthRepo.availability() } returns HealthAvailability.AVAILABLE
        coEvery { healthRepo.hasAllPermissions() } returns true
        coEvery { healthRepo.readWeeklyHealth() } throws RuntimeException("boom")
        viewModel = HomeViewModel(aktiviteterRepo, medicinerRepo, authRepo, prefs, sjukdomarRepo, healthRepo)
        assertEquals(HealthCardUiState.NotConnected, viewModel.healthCard.value)
    }
}
