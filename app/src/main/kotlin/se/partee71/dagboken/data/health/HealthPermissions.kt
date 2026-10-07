package se.partee71.dagboken.data.health

import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import se.partee71.dagboken.core.engine.health.OptionalHealthMetric

/**
 * Behörighetsflödet mot Health Connect (HLS-3, HLS-4, HLS-14) som port: skärmarna skickar bara en händelse
 * ("Ge åtkomst", "Installera"/"Uppdatera"), och porten äger aktivitetens launcher och Health Connects
 * behörighetsnamn. Bunden till [HealthPermissionsImpl] (Health Connect, #243); i test en fejk.
 */
interface HealthPermissions {
    /**
     * De **valfria** mått som saknar åtkomst (HLS-8, HLS-14), igen när det ändras – tomt när allt är beviljat och
     * `null` när behörighetsläget inte går att läsa: då visas ingen rad alls, hellre ingen varning än en gissning.
     */
    val missingOptional: Flow<Set<OptionalHealthMetric>?>

    /** Öppnar Health Connects samtyckesdialog för **hela** behörighetsuppsättningen (HLS-3, HLS-14). */
    fun requestAccess()

    /** Öppnar Health Connect för att installera eller uppdatera det (HLS-4). */
    fun openHealthConnect()
}

/**
 * Porten utan Health Connect, för test och förhandsvisningar: behörighetsläget går inte att läsa och åtgärderna gör
 * ingenting – klockan är ändå [HealthStatus.UNAVAILABLE] (`UnavailableHealthRepository`).
 */
class UnavailableHealthPermissions @Inject constructor() : HealthPermissions {
    override val missingOptional: Flow<Set<OptionalHealthMetric>?> = flowOf(null)

    override fun requestAccess() = Unit

    override fun openHealthConnect() = Unit
}
