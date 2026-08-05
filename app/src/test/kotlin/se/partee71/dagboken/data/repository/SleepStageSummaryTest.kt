package se.partee71.dagboken.data.repository

import androidx.health.connect.client.records.SleepSessionRecord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration

/**
 * Enhetstest för [summarizeSleepStages] — nattens sömnstadier från Samsung Health
 * via Health Connect (HLS-8, §19).
 */
class SleepStageSummaryTest {

    private fun slice(stage: Int, minutes: Long) = StageSlice(stage, Duration.ofMinutes(minutes))

    @Test fun `no stages gives an empty summary`() {
        val stages = summarizeSleepStages(emptyList())
        assertTrue(stages.isEmpty)
        assertNull(stages.deep)
    }

    @Test fun `sums each stage category separately`() {
        val stages = summarizeSleepStages(
            listOf(
                slice(SleepSessionRecord.STAGE_TYPE_DEEP, 40),
                slice(SleepSessionRecord.STAGE_TYPE_DEEP, 35),
                slice(SleepSessionRecord.STAGE_TYPE_REM, 90),
                slice(SleepSessionRecord.STAGE_TYPE_LIGHT, 240),
                slice(SleepSessionRecord.STAGE_TYPE_AWAKE, 12),
            ),
        )
        assertEquals(Duration.ofMinutes(75), stages.deep)
        assertEquals(Duration.ofMinutes(90), stages.rem)
        assertEquals(Duration.ofMinutes(240), stages.light)
        assertEquals(Duration.ofMinutes(12), stages.awake)
    }

    @Test fun `unspecified sleeping counts as light sleep`() {
        // Samsung skriver STAGE_TYPE_SLEEPING när stadieindelningen saknas — den tiden
        // får inte tappas bort, annars ser natten tommare ut än den var.
        val stages = summarizeSleepStages(
            listOf(
                slice(SleepSessionRecord.STAGE_TYPE_LIGHT, 100),
                slice(SleepSessionRecord.STAGE_TYPE_SLEEPING, 60),
            ),
        )
        assertEquals(Duration.ofMinutes(160), stages.light)
    }

    @Test fun `awake in bed and out of bed count as awake`() {
        val stages = summarizeSleepStages(
            listOf(
                slice(SleepSessionRecord.STAGE_TYPE_AWAKE, 5),
                slice(SleepSessionRecord.STAGE_TYPE_AWAKE_IN_BED, 10),
                slice(SleepSessionRecord.STAGE_TYPE_OUT_OF_BED, 3),
            ),
        )
        assertEquals(Duration.ofMinutes(18), stages.awake)
    }

    @Test fun `unknown stages are ignored`() {
        val stages = summarizeSleepStages(listOf(slice(SleepSessionRecord.STAGE_TYPE_UNKNOWN, 30)))
        assertTrue(stages.isEmpty)
    }

    @Test fun `a category without time is null rather than zero`() {
        // Null gör att UI visar "—" i stället för "0 min" för ett stadium som saknas.
        val stages = summarizeSleepStages(listOf(slice(SleepSessionRecord.STAGE_TYPE_DEEP, 45)))
        assertEquals(Duration.ofMinutes(45), stages.deep)
        assertNull(stages.rem)
        assertNull(stages.light)
        assertNull(stages.awake)
    }
}
