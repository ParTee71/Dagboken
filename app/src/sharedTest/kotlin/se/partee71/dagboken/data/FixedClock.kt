package se.partee71.dagboken.data

import kotlin.time.Clock
import kotlin.time.Instant

/** Klocka som alltid visar samma tid – tester är deterministiska (skill testing-strategy). */
class FixedClock(val instant: Instant = Instant.fromEpochSeconds(1_790_000_000)) : Clock {
    override fun now(): Instant = instant
}
