package se.partee71.dagboken.data.repository

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Enhetstest för vilka valfria mått som saknar åtkomst (HLS-14). Behörighetsnamnen skrivs
 * ut i klartext här i stället för att hämtas ur `HealthPermission` — funktionen är ren och
 * ska inte behöva SDK:t för att testas (regel 2).
 */
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
        val missing = missingMetrics(granted = setOf(distance, history), permissions = permissions)
        assertEquals(setOf(OptionalHealthMetric.EXERCISE), missing)
    }

    @Test fun `nothing is reported when every permission is granted`() {
        val missing = missingMetrics(setOf(exercise, distance, history), permissions)
        assertTrue(missing.isEmpty())
    }

    @Test fun `every metric is reported when nothing is granted`() {
        // Läget för den som gav samtycke innan de valfria behörigheterna fanns.
        assertEquals(permissions.keys, missingMetrics(granted = emptySet(), permissions = permissions))
    }

    @Test fun `kärnbehörigheter påverkar inte resultatet`() {
        // Bara de valfria måtten bedöms — kärnan avgörs av hasRequiredPermissions.
        val missing = missingMetrics(
            granted = setOf("android.permission.health.READ_STEPS", exercise, distance, history),
            permissions = permissions,
        )
        assertTrue(missing.isEmpty())
    }

    @Test fun `an empty permission map reports nothing`() {
        assertTrue(missingMetrics(granted = emptySet(), permissions = emptyMap()).isEmpty())
    }
}
