package se.partee71.dagboken.data.health

import kotlinx.coroutines.flow.MutableStateFlow
import se.partee71.dagboken.core.engine.health.OptionalHealthMetric

/**
 * Behörighetsflödet i test: [missingOptional] styrs av testet (`null` = läget går inte att läsa), och varje begäran
 * räknas – så att ett test kan visa att "Ge åtkomst" och "Installera" når porten och inget annat.
 */
class FakeHealthPermissions(missing: Set<OptionalHealthMetric>? = emptySet()) : HealthPermissions {
    override val missingOptional: MutableStateFlow<Set<OptionalHealthMetric>?> = MutableStateFlow(missing)

    var accessRequests = 0
        private set

    var healthConnectOpened = 0
        private set

    override fun requestAccess() {
        accessRequests++
    }

    override fun openHealthConnect() {
        healthConnectOpened++
    }
}
