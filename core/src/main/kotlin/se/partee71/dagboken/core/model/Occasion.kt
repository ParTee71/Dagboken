package se.partee71.dagboken.core.model

import kotlinx.datetime.LocalTime

/**
 * Måendets fyra tillfällen (NOT-4, HEM-4). [legacyName] är namnet 3.x sparade som screeningens
 * `aktivitet` (`SCREENING_EVENT_LABELS`); [defaultTime] är påminnelsens standardtid.
 */
enum class Occasion(override val wire: String, val legacyName: String, val defaultTime: LocalTime) : WireEnum {
    BREAKFAST("breakfast", "Efter frukost", LocalTime(8, 0)),
    LUNCH("lunch", "Lunch", LocalTime(12, 0)),
    DINNER("dinner", "Kvällsmat", LocalTime(17, 0)),
    BEDTIME("bedtime", "Läggdags", LocalTime(21, 0)),
    ;

    companion object {
        /** 3.x-namnet → tillfället; okänt namn → `null`. */
        fun fromLegacyName(name: String): Occasion? = entries.firstOrNull { it.legacyName == name }

        /** Om [name] är ett av de fyra tillfällenas 3.x-namn (3.x `SCREENING_EVENT_LABELS`). */
        fun isLegacyName(name: String): Boolean = fromLegacyName(name) != null

        /**
         * Tillfället för en screening från 3.x, som inte hade fältet (DAT-12):
         * 1. Är [legacyName] (3.x-screeningens `aktivitet`) ett av de fyra tillfällenas namn gäller
         *    det – samma jämförelse som 3.x `isScreeningLoggedFor` gjorde.
         * 2. Annars tillfället vars påminnelsetid ([reminderTimes], som standard [defaultTime])
         *    ligger närmast [time] räknat runt dygnet; lika nära → det tidigare i dygnet.
         * 3. Utan klockslag: `null`.
         */
        fun derive(
            legacyName: String,
            time: LocalTime?,
            reminderTimes: Map<Occasion, LocalTime> = entries.associateWith { it.defaultTime },
        ): Occasion? {
            fromLegacyName(legacyName)?.let { return it }
            if (time == null) return null
            val minute = time.toSecondOfDay() / SECONDS_PER_MINUTE
            return entries.minWithOrNull(
                compareBy<Occasion> { circularDistance(minute, (reminderTimes[it] ?: it.defaultTime).toSecondOfDay() / SECONDS_PER_MINUTE) }
                    .thenBy { (reminderTimes[it] ?: it.defaultTime).toSecondOfDay() },
            )
        }

        private fun circularDistance(a: Int, b: Int): Int {
            val diff = kotlin.math.abs(a - b)
            return minOf(diff, MINUTES_PER_DAY - diff)
        }

        private const val SECONDS_PER_MINUTE = 60
        private const val MINUTES_PER_DAY = 24 * 60
    }
}
