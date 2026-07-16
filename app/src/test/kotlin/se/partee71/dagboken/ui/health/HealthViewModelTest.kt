package se.partee71.dagboken.ui.health

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import se.partee71.dagboken.data.repository.HealthAvailability
import se.partee71.dagboken.data.repository.HealthConnectRepository
import se.partee71.dagboken.domain.model.HealthData
import java.time.Duration

@OptIn(ExperimentalCoroutinesApi::class)
class HealthViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    private class FakeHealthRepo(
        var availability: HealthAvailability = HealthAvailability.AVAILABLE,
        var granted: Boolean = true,
        var data: HealthData = HealthData(steps = 100, heartRateAvg = 60, sleepDuration = Duration.ofHours(7)),
        var throwOnRead: Boolean = false,
    ) : HealthConnectRepository {
        override val permissions: Set<String> = setOf("read_steps", "read_hr", "read_sleep")
        override fun availability() = availability
        override suspend fun hasAllPermissions() = granted
        override suspend fun readToday(): HealthData =
            if (throwOnRead) throw RuntimeException("boom") else data
    }

    @Before fun setUp() { Dispatchers.setMain(testDispatcher) }
    @After fun tearDown() { Dispatchers.resetMain() }

    @Test fun `not installed maps to Unavailable(updateRequired=false)`() = runTest(testDispatcher) {
        val vm = HealthViewModel(FakeHealthRepo(availability = HealthAvailability.NOT_INSTALLED))
        assertEquals(HealthUiState.Unavailable(updateRequired = false), vm.state.value)
    }

    @Test fun `update required maps to Unavailable(updateRequired=true)`() = runTest(testDispatcher) {
        val vm = HealthViewModel(FakeHealthRepo(availability = HealthAvailability.UPDATE_REQUIRED))
        assertEquals(HealthUiState.Unavailable(updateRequired = true), vm.state.value)
    }

    @Test fun `available but no permissions maps to PermissionsRequired`() = runTest(testDispatcher) {
        val vm = HealthViewModel(FakeHealthRepo(granted = false))
        assertEquals(HealthUiState.PermissionsRequired, vm.state.value)
    }

    @Test fun `available and granted loads data`() = runTest(testDispatcher) {
        val data = HealthData(steps = 4200, heartRateAvg = 72, sleepDuration = Duration.ofMinutes(450))
        val vm = HealthViewModel(FakeHealthRepo(data = data))
        assertEquals(HealthUiState.Data(data), vm.state.value)
    }

    @Test fun `read failure maps to Error`() = runTest(testDispatcher) {
        val vm = HealthViewModel(FakeHealthRepo(throwOnRead = true))
        assertEquals(HealthUiState.Error, vm.state.value)
    }

    @Test fun `refresh re-evaluates after permissions granted`() = runTest(testDispatcher) {
        val repo = FakeHealthRepo(granted = false)
        val vm = HealthViewModel(repo)
        assertEquals(HealthUiState.PermissionsRequired, vm.state.value)

        repo.granted = true
        vm.refresh()

        assertTrue(vm.state.value is HealthUiState.Data)
    }

    @Test fun `exposes repository permissions for the launcher`() {
        val repo = FakeHealthRepo()
        val vm = HealthViewModel(repo)
        assertEquals(repo.permissions, vm.permissions)
    }
}
