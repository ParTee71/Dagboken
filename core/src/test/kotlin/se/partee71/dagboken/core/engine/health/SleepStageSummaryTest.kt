package se.partee71.dagboken.core.engine.health

import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Nattens sömnstadier (HLS-8). Portad från 3.x `SleepStageSummaryTest`. */
class SleepStageSummaryTest {

    private val t = Instant.parse("2026-03-10T00:00:00Z")
    private fun slice(type: SleepStageType, minutes: Int) = SleepStageSlice(type, t, t + minutes.minutes)

    @Test fun `no stages gives an empty summary`() {
        val stages = summarizeSleepStages(emptyList())
        assertTrue(stages.isEmpty)
        assertNull(stages.deep)
    }

    @Test fun `sums each stage category separately`() {
        val stages = summarizeSleepStages(
            listOf(
                slice(SleepStageType.DEEP, 40),
                slice(SleepStageType.DEEP, 35),
                slice(SleepStageType.REM, 90),
                slice(SleepStageType.LIGHT, 240),
                slice(SleepStageType.AWAKE, 12),
            ),
        )
        assertEquals(75.minutes, stages.deep)
        assertEquals(90.minutes, stages.rem)
        assertEquals(240.minutes, stages.light)
        assertEquals(12.minutes, stages.awake)
    }

    @Test fun `unspecified sleeping counts as light sleep`() {
        val stages = summarizeSleepStages(listOf(slice(SleepStageType.LIGHT, 100), slice(SleepStageType.SLEEPING, 60)))
        assertEquals(160.minutes, stages.light)
    }

    @Test fun `awake in bed and out of bed count as awake`() {
        val stages = summarizeSleepStages(
            listOf(slice(SleepStageType.AWAKE, 5), slice(SleepStageType.AWAKE_IN_BED, 10), slice(SleepStageType.OUT_OF_BED, 3)),
        )
        assertEquals(18.minutes, stages.awake)
    }

    @Test fun `unknown stages are ignored`() {
        assertTrue(summarizeSleepStages(listOf(slice(SleepStageType.UNKNOWN, 30))).isEmpty)
    }

    @Test fun `a category without time is null rather than zero`() {
        val stages = summarizeSleepStages(listOf(slice(SleepStageType.DEEP, 45)))
        assertEquals(45.minutes, stages.deep)
        assertNull(stages.rem)
        assertNull(stages.light)
        assertNull(stages.awake)
    }
}
