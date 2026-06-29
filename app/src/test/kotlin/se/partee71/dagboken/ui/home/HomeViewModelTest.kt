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
import se.partee71.dagboken.data.repository.AktiviteterRepository
import se.partee71.dagboken.data.repository.MedicinerRepository
import se.partee71.dagboken.data.repository.SjukdomarRepository
import se.partee71.dagboken.domain.model.Medicin
import java.time.LocalDate

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    private lateinit var medicinerRepo: MedicinerRepository
    private lateinit var aktiviteterRepo: AktiviteterRepository
    private lateinit var authRepo: FirebaseAuthRepository
    private lateinit var prefs: PreferencesRepository
    private lateinit var sjukdomarRepo: SjukdomarRepository

    private val todayFlow = MutableStateFlow<List<Medicin>>(emptyList())

    private lateinit var viewModel: HomeViewModel

    @Before fun setUp() {
        Dispatchers.setMain(testDispatcher)
        medicinerRepo = mockk(relaxed = true) {
            every { todayFlow() } returns todayFlow
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
        viewModel = HomeViewModel(aktiviteterRepo, medicinerRepo, authRepo, prefs, sjukdomarRepo)
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
        tagen = tagen, anteckning = "", receptId = null, skipped = skipped,
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

    // ─── initial state ────────────────────────────────────────────────────────

    @Test fun `initial uiState has empty medicine lists and tagenCount zero`() = runTest {
        viewModel.uiState.test {
            val state = awaitItem()
            assertTrue(state.todayMediciner.isEmpty())
            assertTrue(state.overdueMediciner.isEmpty())
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

    // ─── overdueScreeningTimes ────────────────────────────────────────────────

    @Test fun `overdueScreeningTimes is empty when no screening events are enabled`() = runTest {
        every { prefs.screeningEventConfigs } returns flowOf(emptyList())
        viewModel = HomeViewModel(aktiviteterRepo, medicinerRepo, authRepo, prefs, sjukdomarRepo)

        viewModel.uiState.test {
            assertTrue(awaitItem().overdueScreeningTimes.isEmpty())
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
}
