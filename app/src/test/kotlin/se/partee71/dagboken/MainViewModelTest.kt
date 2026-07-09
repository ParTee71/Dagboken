package se.partee71.dagboken

import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import se.partee71.dagboken.data.datastore.PreferencesRepository
import se.partee71.dagboken.ui.navigation.Screen

@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private lateinit var viewModel: MainViewModel

    @Before fun setUp() {
        Dispatchers.setMain(testDispatcher)
        val prefs = mockk<PreferencesRepository>(relaxed = true) {
            every { themeMode } returns flowOf("auto")
            every { themeLightStart } returns flowOf(7)
            every { themeDarkStart } returns flowOf(21)
            every { dynamicColor } returns flowOf(true)
            every { migrationDone } returns flowOf(true)
        }
        viewModel = MainViewModel(prefs)
    }

    @After fun tearDown() { Dispatchers.resetMain() }

    @Test fun `pendingNavRoute is null initially`() {
        assertNull(viewModel.pendingNavRoute.value)
    }

    @Test fun `setPendingNavRoute stores the new bottom-nav route for Idag`() = runTest(testDispatcher) {
        viewModel.setPendingNavRoute(Screen.Idag.route)
        assertEquals(Screen.Idag.route, viewModel.pendingNavRoute.value)
    }

    @Test fun `clearPendingNavRoute resets it to null`() = runTest(testDispatcher) {
        viewModel.setPendingNavRoute(Screen.Idag.route)
        viewModel.clearPendingNavRoute()
        assertNull(viewModel.pendingNavRoute.value)
    }
}
