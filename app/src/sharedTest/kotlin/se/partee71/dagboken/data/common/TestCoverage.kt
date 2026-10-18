package se.partee71.dagboken.data.common

import kotlin.time.Clock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.datetime.TimeZone
import se.partee71.dagboken.data.FixedClock

/**
 * [HistoryCoverage] i test – en gång för JVM- och instrumenttesterna: dagar i UTC och en egen scope (som appens
 * `@ApplicationScope`) där hämtningen startar direkt (`Unconfined`), så att en fejkad hämtning är klar när anropet återvänder.
 */
fun testCoverage(marks: DayMarks = MemoryDayMarks(), clock: Clock = FixedClock()): HistoryCoverage = HistoryCoverage(marks, clock, { TimeZone.UTC }, CoroutineScope(SupervisorJob() + Dispatchers.Unconfined))
