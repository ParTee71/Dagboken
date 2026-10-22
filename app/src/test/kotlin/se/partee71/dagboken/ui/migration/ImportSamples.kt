package se.partee71.dagboken.ui.migration

import se.partee71.dagboken.core.legacy.ImportFormat
import se.partee71.dagboken.core.model.LegacySource

/** Påhittade lägen för importens tester och skärmdumpar – samma siffror som mockupen (avsnitt 16). */
object ImportSamples {
    /** Antal per samling i `CollectionNames`-ordning; inställningarna och listorna blir en rad. */
    val before: Map<String, Int> = linkedMapOf(
        "settings" to 1, "options" to 24, "prescriptions" to 9, "prnMedicines" to 4, "doses" to 3904,
        "screenings" to 1248, "activities" to 612, "events" to 187, "illnessEpisodes" to 21, "checkins" to 143
    )

    val total: Int = before.values.sum()

    /** Halvvägs: allt före doserna klart, doserna delvis, resten inte påbörjat. */
    val halfway: Map<String, Int> = before.mapValues { (collection, count) ->
        when (collection) {
            "settings", "options", "screenings", "activities", "events" -> count
            "doses" -> 2140
            else -> 0
        }
    }

    /** Importens granskning: en 3.x-backup v2 från Drive med en varning. */
    val importReview = ImportStage.Review(
        LegacySource.DRIVE,
        ImportFormat.Legacy(2),
        before,
        listOf("3 anteckning(ar) med target ACTIVITY utan sin post – kan inte placeras"),
        replaced = 12
    )

    val importStopped = ImportStage.Stopped(listOf(ReportLine("prescriptions", "periodDays", "värde utanför tillåtet intervall (högst 1096)", 1)))
}
