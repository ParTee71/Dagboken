package se.partee71.dagboken.ui.migration

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import se.partee71.dagboken.data.legacy.AccountCheck

/** Påhittade lägen för migreringsskärmens tester och skärmdumpar – samma siffror som mockupen (avsnitt 15). */
object MigrationSamples {
    /** Antal per samling i `CollectionNames`-ordning; inställningarna och listorna blir en rad. */
    val before: Map<String, Int> = linkedMapOf(
        "settings" to 1, "options" to 24, "prescriptions" to 9, "prnMedicines" to 4, "doses" to 3904,
        "screenings" to 1248, "activities" to 612, "events" to 187, "illnessEpisodes" to 21, "checkins" to 143,
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

    val saved = CopyStep.Saved("dagboken-3x-kopia-2026-10-07.json", LocalDateTime(LocalDate(2026, 10, 7), LocalTime(21, 14)), total)

    fun review(copy: CopyStep = CopyStep.Missing, accountCheck: AccountCheck = AccountCheck.SAME, accountConfirmed: Boolean = false) = MigrationStage.Review(
        counts = before,
        accountEmail = "anna.lind@example.com",
        accountCheck = accountCheck,
        accountConfirmed = accountConfirmed,
        warnings = listOf("3 anteckning(ar) med target ACTIVITY utan sin post – kan inte placeras"),
        copy = copy,
        copyFileName = "dagboken-3x-kopia-2026-10-07.json",
    )

    val stopped = MigrationStage.Stopped(listOf(ReportLine("prescriptions", "periodDays", "värde utanför tillåtet intervall (högst 1096)", 1)))
}
