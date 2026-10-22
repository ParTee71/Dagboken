package se.partee71.dagboken.ui.common

import kotlin.test.assertEquals
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.Test
import se.partee71.ui.common.STOP_TIMEOUT_MILLIS

class ScreenSharingTest {
    @Test
    fun `standard glömmer värdet när skärmen stoppats, forgetWhenStopped = false behåller det`() = runTest(UnconfinedTestDispatcher()) {
        val source = MutableStateFlow("nu")
        val forgetting = source.stateIn(backgroundScope, screenSharing(), "start")
        val keeping = source.stateIn(backgroundScope, screenSharing(forgetWhenStopped = false), "start")
        val collectors = listOf(forgetting, keeping).map { it.launchIn(backgroundScope) }
        assertEquals("nu", forgetting.value)
        assertEquals("nu", keeping.value)

        collectors.forEach { it.cancel() }
        advanceTimeBy(STOP_TIMEOUT_MILLIS + 1)
        assertEquals("start", forgetting.value)
        assertEquals("nu", keeping.value)
    }
}
