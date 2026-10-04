package se.partee71.dagboken.ui.common

import app.cash.turbine.test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Test

class ShownAfterTest {

    private val source = MutableStateFlow(false)

    @Test
    fun `sant först efter fördröjningen, falskt direkt`() = runTest {
        source.shownAfter(2.seconds).test {
            assertEquals(false, awaitItem())
            source.value = true
            testScheduler.advanceTimeBy(1_999)
            expectNoEvents()
            testScheduler.advanceTimeBy(2)
            assertEquals(true, awaitItem())
            source.value = false
            assertEquals(false, awaitItem())
        }
    }

    @Test
    fun `något som går fort före fördröjningen syns aldrig`() = runTest {
        source.shownAfter(2.seconds).test {
            assertEquals(false, awaitItem())
            source.value = true
            testScheduler.advanceTimeBy(1_000)
            source.value = false
            testScheduler.advanceTimeBy(5_000)
            expectNoEvents()
        }
    }
}
