package se.partee71.dagboken.ui.migration

import androidx.annotation.StringRes
import java.util.Locale
import se.partee71.dagboken.R
import se.partee71.dagboken.core.schema.CollectionNames

/**
 * En rad på migreringsskärmen (OMB-2): en entitet med sitt svenska namn och de samlingar den räknar – bara antal,
 * aldrig innehåll. Ordningen är skärmens (mockupen, avsnitt 15); inställningarna och alternativlistorna är en rad.
 * Varje samling som migreringen skriver har en rad (`MigrationEntityTest`).
 */
enum class MigrationEntity(@param:StringRes val label: Int, val collections: List<String>) {
    SCREENINGS(R.string.migration_entity_screenings, listOf(CollectionNames.SCREENINGS)),
    ACTIVITIES(R.string.migration_entity_activities, listOf(CollectionNames.ACTIVITIES)),
    EVENTS(R.string.migration_entity_events, listOf(CollectionNames.EVENTS)),
    DOSES(R.string.migration_entity_doses, listOf(CollectionNames.DOSES)),
    PRESCRIPTIONS(R.string.migration_entity_prescriptions, listOf(CollectionNames.PRESCRIPTIONS)),
    PRN_MEDICINES(R.string.migration_entity_prn_medicines, listOf(CollectionNames.PRN_MEDICINES)),
    ILLNESS_EPISODES(R.string.migration_entity_illness_episodes, listOf(CollectionNames.ILLNESS_EPISODES)),
    CHECKINS(R.string.migration_entity_checkins, listOf(CollectionNames.CHECKINS)),
    SETTINGS(R.string.migration_entity_settings, listOf(CollectionNames.SETTINGS, CollectionNames.OPTIONS)),
    ;

    /** Antalet för raden ur antal per samling. */
    fun count(counts: Map<String, Int>): Int = collections.sumOf { counts[it] ?: 0 }

    companion object {
        /** Raden som samlingen [collection] räknas på, `null` för en samling utan rad. */
        fun of(collection: String): MigrationEntity? = entries.firstOrNull { collection in it.collections }
    }
}

private val swedish = Locale.forLanguageTag("sv-SE")

/** "6 329" – ett antal med svensk tusentalsavgränsare. */
fun countText(count: Int): String = "%,d".format(swedish, count)
