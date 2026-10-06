package se.partee71.dagboken.ui.common

import app.cash.turbine.test
import kotlin.test.assertEquals
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import org.junit.Test

/** `hours`/`days`: byter vid hel timme och midnatt, och läser tidszonen vid varje omräkning. */
@OptIn(ExperimentalCoroutinesApi::class)
class ClockFlowsTest {

    /** 23:59:30 måndag 21 september i Stockholm (UTC+2); följer testets virtuella tid. */
    private fun TestScope.clock() = object : Clock {
        override fun now(): Instant = Instant.parse("2026-09-21T21:59:30Z") + testScheduler.currentTime.milliseconds
    }

    @Test
    fun `dagen byts vid midnatt och en ny tidszon gäller från nästa omräkning`() = runTest {
        var zone = TimeZone.of("Europe/Stockholm")
        clock().days { zone }.test {
            assertEquals(LocalDate(2026, 9, 21), awaitItem())
            advanceTimeBy(29.seconds)
            expectNoEvents()
            advanceTimeBy(2.seconds)
            assertEquals(LocalDate(2026, 9, 22), awaitItem())

            // Resan till New York (UTC−4): vid nästa Stockholmsmidnatt är det fortfarande den 22:a där,
            // och nästa byte räknas sedan till New Yorks midnatt.
            zone = TimeZone.of("America/New_York")
            advanceTimeBy(1.days)
            expectNoEvents()
            advanceTimeBy(6.hours + 1.seconds)
            assertEquals(LocalDate(2026, 9, 23), awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `timmen byts vid hel timme och följer en ny tidszon`() = runTest {
        var zone = TimeZone.of("Europe/Stockholm")
        clock().hours { zone }.test {
            assertEquals(23, awaitItem())
            advanceTimeBy(31.seconds)
            assertEquals(0, awaitItem())
            zone = TimeZone.UTC
            advanceTimeBy(1.hours)
            assertEquals(23, awaitItem(), "nästa timme räknas i den nya zonen (UTC 23, inte Stockholm 01)")
            cancelAndIgnoreRemainingEvents()
        }
    }
}
