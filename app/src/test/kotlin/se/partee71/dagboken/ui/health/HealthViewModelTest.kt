package se.partee71.dagboken.ui.health

import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import se.partee71.dagboken.data.datastore.PreferencesRepository
import se.partee71.dagboken.data.repository.HealthAvailability
import se.partee71.dagboken.data.repository.HealthConnectRepository
import se.partee71.dagboken.domain.model.HealthData
import se.partee71.dagboken.domain.model.Sex
import se.partee71.dagboken.domain.model.SleepMeasurements
import se.partee71.dagboken.domain.model.WeeklyHealth
import java.time.Duration

@OptIn(ExperimentalCoroutinesApi::class)
class HealthViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    private class FakeHealthRepo(
        var availability: HealthAvailability = HealthAvailability.AVAILABLE,
        var granted: Boolean = true,
        var data: HealthData = HealthData(steps = 100, heartRateAvg = 60, sleepDuration = Duration.ofHours(7)),
        var throwOnRead: Boolean = false,
        var sleep: SleepMeasurements? = null,
    ) : HealthConnectRepository {
        override val requiredPermissions: Set<String> = setOf("read_steps", "read_hr", "read_sleep")
        override val permissions: Set<String> = requiredPermissions + setOf("read_exercise", "read_spo2")
        override fun availability() = availability
        override suspend fun hasRequiredPermissions() = granted
        override suspend fun readToday(): HealthData =
            if (throwOnRead) throw RuntimeException("boom") else data
        override suspend fun readWeeklyHealth() =
            if (throwOnRead) throw RuntimeException("boom") else WeeklyHealth()
        override suspend fun readHealthRange(days: Int) =
            if (throwOnRead) throw RuntimeException("boom") else WeeklyHealth()
        override suspend fun readSleepMeasurements(nights: Int) =
            if (throwOnRead) throw RuntimeException("boom") else sleep
    }

    /** Profil med angivet födelseår/kön — sömnkvaliteten kräver en ålder (HLS-10). */
    private fun fakePrefs(birthYear: Int? = 1971, sex: Sex = Sex.MAN): PreferencesRepository =
        mockk(relaxed = true) {
            every { this@mockk.birthYear } returns flowOf(birthYear)
            every { this@mockk.sex } returns flowOf(sex)
        }

    @Before fun setUp() { Dispatchers.setMain(testDispatcher) }
    @After fun tearDown() { Dispatchers.resetMain() }

    @Test fun `not installed maps to Unavailable(updateRequired=false)`() = runTest(testDispatcher) {
        val vm = HealthViewModel(FakeHealthRepo(availability = HealthAvailability.NOT_INSTALLED), fakePrefs())
        assertEquals(HealthUiState.Unavailable(updateRequired = false), vm.state.value)
    }

    @Test fun `update required maps to Unavailable(updateRequired=true)`() = runTest(testDispatcher) {
        val vm = HealthViewModel(FakeHealthRepo(availability = HealthAvailability.UPDATE_REQUIRED), fakePrefs())
        assertEquals(HealthUiState.Unavailable(updateRequired = true), vm.state.value)
    }

    @Test fun `available but no permissions maps to PermissionsRequired`() = runTest(testDispatcher) {
        val vm = HealthViewModel(FakeHealthRepo(granted = false), fakePrefs())
        assertEquals(HealthUiState.PermissionsRequired, vm.state.value)
    }

    @Test fun `available and granted loads data`() = runTest(testDispatcher) {
        val data = HealthData(steps = 4200, heartRateAvg = 72, sleepDuration = Duration.ofMinutes(450))
        val vm = HealthViewModel(FakeHealthRepo(data = data), fakePrefs())
        assertEquals(HealthUiState.Data(data), vm.state.value)
    }

    @Test fun `read failure maps to Error`() = runTest(testDispatcher) {
        val vm = HealthViewModel(FakeHealthRepo(throwOnRead = true), fakePrefs())
        assertEquals(HealthUiState.Error, vm.state.value)
    }

    @Test fun `refresh re-evaluates after permissions granted`() = runTest(testDispatcher) {
        val repo = FakeHealthRepo(granted = false)
        val vm = HealthViewModel(repo, fakePrefs())
        assertEquals(HealthUiState.PermissionsRequired, vm.state.value)

        repo.granted = true
        vm.refresh()

        assertTrue(vm.state.value is HealthUiState.Data)
    }

    @Test fun `exposes repository permissions for the launcher`() {
        val repo = FakeHealthRepo()
        val vm = HealthViewModel(repo, fakePrefs())
        assertEquals(repo.permissions, vm.permissions)
    }

    @Test fun `the launcher asks for the optional permissions too, not just the required ones`() {
        // HLS-8: de valfria typerna måste ingå i behörighetsdialogen, annars kan
        // användaren aldrig ge åtkomst till träning, sträcka, syremättnad m.m.
        val repo = FakeHealthRepo()
        val vm = HealthViewModel(repo, fakePrefs())
        assertTrue(vm.permissions.containsAll(repo.requiredPermissions))
        assertTrue(vm.permissions.size > repo.requiredPermissions.size)
    }

    // ─── HLS-10/HLS-11: sömnkvalitet och profilberoendet ─────────────────────

    @Test fun `sleep quality is scored when a birth year is set`() = runTest(testDispatcher) {
        val repo = FakeHealthRepo(
            sleep = SleepMeasurements(
                timeInBed = Duration.ofHours(8),
                awake = Duration.ofMinutes(25),
                deep = Duration.ofMinutes(70),
                rem = Duration.ofMinutes(100),
                midpointSdMinutes = 20.0,
            ),
        )
        val vm = HealthViewModel(repo, fakePrefs(birthYear = 1971, sex = Sex.MAN))

        val state = vm.state.value as HealthUiState.Data
        assertEquals(false, state.birthYearMissing)
        assertTrue("Förväntade en sömnkvalitetspoäng", state.sleepQuality != null)
    }

    @Test fun `without a birth year the screen asks for it instead of scoring`() = runTest(testDispatcher) {
        // Poängen är åldersjusterad — utan ålder vore den mätt mot fel norm.
        val repo = FakeHealthRepo(sleep = SleepMeasurements(timeInBed = Duration.ofHours(8)))
        val vm = HealthViewModel(repo, fakePrefs(birthYear = null))

        val state = vm.state.value as HealthUiState.Data
        assertTrue(state.birthYearMissing)
        assertEquals(null, state.sleepQuality)
    }

    @Test fun `a night without sleep data leaves the rest of the screen intact`() = runTest(testDispatcher) {
        val vm = HealthViewModel(FakeHealthRepo(sleep = null), fakePrefs())

        val state = vm.state.value as HealthUiState.Data
        assertEquals(null, state.sleepQuality)
        assertEquals(false, state.birthYearMissing)
    }

    @Test fun `only the required permissions gate the screen`() {
        // Nekad valfri behörighet får inte låsa skärmen i behörighetsläge — repot
        // svarar utifrån kärnbehörigheterna, och skärmen visar data ändå.
        val vm = HealthViewModel(FakeHealthRepo(granted = true), fakePrefs())
        assertTrue(vm.state.value is HealthUiState.Data)
    }
}
