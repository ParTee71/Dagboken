package se.partee71.dagboken.core.model

import kotlinx.datetime.LocalTime

/** Måendets fyra tillfällen (NOT-4, HEM-4). [defaultTime] är påminnelsens standardtid. */
enum class Occasion(override val wire: String, val defaultTime: LocalTime) : WireEnum {
    BREAKFAST("breakfast", LocalTime(8, 0)),
    LUNCH("lunch", LocalTime(12, 0)),
    DINNER("dinner", LocalTime(17, 0)),
    BEDTIME("bedtime", LocalTime(21, 0))
}
