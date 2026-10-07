package se.partee71.dagboken.data.health

import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.metadata.Metadata
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.time.toKotlinInstant
import org.junit.Test
import se.partee71.dagboken.core.engine.health.HeartRateSample
import se.partee71.dagboken.core.engine.health.SleepStageSlice
import se.partee71.dagboken.core.engine.health.SleepStageType

/**
 * Källans översättning från Health Connects poster till `:core` (HLS-2, HLS-7, HLS-8): pulsproven plattas ut med
 * källan, sömnens `STAGE_TYPE_*` blir [SleepStageType] – en okänd kod ignoreras i stället för att fälla läsningen.
 */
class HealthConnectMappingTest {
    private val t0 = Instant.parse("2026-10-06T21:00:00Z")
    // dataOrigin sätts av Health Connect och går inte att välja i en post som appen bygger – testet följer postens värde.
    private val watch = Metadata.manualEntry()
    private val origin = watch.dataOrigin.packageName

    @Test
    fun `en pulspost blir ett prov per mätning, med postens källa`() {
        val record = HeartRateRecord(
            startTime = t0,
            startZoneOffset = null,
            endTime = t0.plusSeconds(120),
            endZoneOffset = null,
            samples = listOf(HeartRateRecord.Sample(t0, 58), HeartRateRecord.Sample(t0.plusSeconds(60), 61)),
            metadata = watch,
        )
        assertEquals(
            listOf(
                HeartRateSample(origin, t0.toKotlinInstant(), 58),
                HeartRateSample(origin, t0.plusSeconds(60).toKotlinInstant(), 61),
            ),
            heartRateSamples(record),
        )
    }

    @Test
    fun `sömnens stadier översätts kod för kod`() {
        val codes = mapOf(
            SleepSessionRecord.STAGE_TYPE_UNKNOWN to SleepStageType.UNKNOWN,
            SleepSessionRecord.STAGE_TYPE_AWAKE to SleepStageType.AWAKE,
            SleepSessionRecord.STAGE_TYPE_SLEEPING to SleepStageType.SLEEPING,
            SleepSessionRecord.STAGE_TYPE_OUT_OF_BED to SleepStageType.OUT_OF_BED,
            SleepSessionRecord.STAGE_TYPE_LIGHT to SleepStageType.LIGHT,
            SleepSessionRecord.STAGE_TYPE_DEEP to SleepStageType.DEEP,
            SleepSessionRecord.STAGE_TYPE_REM to SleepStageType.REM,
            SleepSessionRecord.STAGE_TYPE_AWAKE_IN_BED to SleepStageType.AWAKE_IN_BED,
            99 to SleepStageType.UNKNOWN,
        )
        codes.forEach { (code, type) -> assertEquals(type, sleepStageType(code), "kod $code") }
        assertEquals(SleepStageType.entries.toSet(), codes.values.toSet(), "varje domäntyp har en kod")
    }

    @Test
    fun `en sömnsession behåller tid, källa och stadier`() {
        val end = t0.plusSeconds(8 * 3600)
        val record = SleepSessionRecord(
            startTime = t0,
            startZoneOffset = null,
            endTime = end,
            endZoneOffset = null,
            stages = listOf(
                SleepSessionRecord.Stage(t0, t0.plusSeconds(3600), SleepSessionRecord.STAGE_TYPE_LIGHT),
                SleepSessionRecord.Stage(t0.plusSeconds(3600), t0.plusSeconds(7200), SleepSessionRecord.STAGE_TYPE_DEEP),
            ),
            metadata = watch,
        )
        val session = sleepSession(record)
        assertEquals(origin, session.origin)
        assertEquals(t0.toKotlinInstant(), session.start)
        assertEquals(end.toKotlinInstant(), session.end)
        assertEquals(
            listOf(
                SleepStageSlice(SleepStageType.LIGHT, t0.toKotlinInstant(), t0.plusSeconds(3600).toKotlinInstant()),
                SleepStageSlice(SleepStageType.DEEP, t0.plusSeconds(3600).toKotlinInstant(), t0.plusSeconds(7200).toKotlinInstant()),
            ),
            session.stages,
        )
    }
}
