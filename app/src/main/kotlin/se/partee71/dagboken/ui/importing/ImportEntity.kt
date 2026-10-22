package se.partee71.dagboken.ui.importing

import androidx.annotation.StringRes
import java.util.Locale
import se.partee71.dagboken.R
import se.partee71.dagboken.core.schema.CollectionNames

/**
 * En rad i importens tabeller (BCK-6): en entitet med sitt svenska namn och de samlingar den räknar – bara antal,
 * aldrig innehåll. Ordningen är mockupens (avsnitt 15); inställningarna och alternativlistorna är en rad. Varje samling
 * som importen skriver har en rad (`ImportReportTest`).
 */
enum class ImportEntity(@param:StringRes val label: Int, val collections: List<String>) {
    SCREENINGS(R.string.import_entity_screenings, listOf(CollectionNames.SCREENINGS)),
    ACTIVITIES(R.string.import_entity_activities, listOf(CollectionNames.ACTIVITIES)),
    EVENTS(R.string.import_entity_events, listOf(CollectionNames.EVENTS)),
    DOSES(R.string.import_entity_doses, listOf(CollectionNames.DOSES)),
    PRESCRIPTIONS(R.string.import_entity_prescriptions, listOf(CollectionNames.PRESCRIPTIONS)),
    PRN_MEDICINES(R.string.import_entity_prn_medicines, listOf(CollectionNames.PRN_MEDICINES)),
    ILLNESS_EPISODES(R.string.import_entity_illness_episodes, listOf(CollectionNames.ILLNESS_EPISODES)),
    CHECKINS(R.string.import_entity_checkins, listOf(CollectionNames.CHECKINS)),
    SETTINGS(R.string.import_entity_settings, listOf(CollectionNames.SETTINGS, CollectionNames.OPTIONS))
    ;

    /** Antalet för raden ur antal per samling. */
    fun count(counts: Map<String, Int>): Int = collections.sumOf { counts[it] ?: 0 }

    companion object {
        /** Raden som samlingen [collection] räknas på, `null` för en samling utan rad. */
        fun of(collection: String): ImportEntity? = entries.firstOrNull { collection in it.collections }
    }
}

private val swedish = Locale.forLanguageTag("sv-SE")

/** "6 329" – ett antal med svensk tusentalsavgränsare. */
fun countText(count: Int): String = "%,d".format(swedish, count)
