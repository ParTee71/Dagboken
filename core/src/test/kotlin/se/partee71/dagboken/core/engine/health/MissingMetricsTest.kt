package se.partee71.dagboken.core.engine.health

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Vilka valfria mått som saknar åtkomst (HLS-14). Portad från 3.x `MissingMetricsTest`; strängarna i klartext. */
class MissingMetricsTest {

    private val exercise = "android.permission.health.READ_EXERCISE"
    private val distance = "android.permission.health.READ_DISTANCE"
    private val history = "android.permission.health.READ_HEALTH_DATA_HISTORY"

    private val permissions = mapOf(
        OptionalHealthMetric.EXERCISE to exercise,
        OptionalHealthMetric.DISTANCE to distance,
        OptionalHealthMetric.HISTORY to history,
    )

    @Test fun `a metric whose permission is missing is reported`() {
        assertEquals(setOf(OptionalHealthMetric.EXERCISE), missingMetrics(granted = setOf(distance, history), permissions = permissions))
    }

    @Test fun `nothing is reported when every permission is granted`() {
        assertTrue(missingMetrics(setOf(exercise, distance, history), permissions).isEmpty())
    }

    @Test fun `every metric is reported when nothing is granted`() {
        // Läget för den som gav samtycke innan de valfria behörigheterna fanns.
        assertEquals(permissions.keys, missingMetrics(granted = emptySet(), permissions = permissions))
    }

    @Test fun `core permissions do not affect the result`() {
        val granted = setOf("android.permission.health.READ_STEPS", exercise, distance, history)
        assertTrue(missingMetrics(granted = granted, permissions = permissions).isEmpty())
    }

    @Test fun `an empty permission map reports nothing`() {
        assertTrue(missingMetrics(granted = emptySet(), permissions = emptyMap()).isEmpty())
    }
}
