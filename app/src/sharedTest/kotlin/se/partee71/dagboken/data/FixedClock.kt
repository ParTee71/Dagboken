package se.partee71.dagboken.data

import kotlin.time.Clock
import kotlin.time.Instant

/**
 * Klocka som visar samma tid tills testet själv flyttar den ([instant]) – tester är deterministiska
 * (skill testing-strategy).
 */
class FixedClock(var instant: Instant = Instant.fromEpochSeconds(1_790_000_000)) : Clock {
    override fun now(): Instant = instant
}
