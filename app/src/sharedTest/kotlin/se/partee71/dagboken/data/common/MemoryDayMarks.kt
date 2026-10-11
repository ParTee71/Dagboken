package se.partee71.dagboken.data.common

import kotlinx.datetime.LocalDate

/** [DayMarks] i minnet: en instans = en enhets lagring (dela den mellan grindar för att efterlikna processdöd). Delas av JVM- och instrumenttesterna. */
class MemoryDayMarks : DayMarks {
    val marks = mutableMapOf<String, LocalDate>()

    /** Lagringen går inte att läsa eller skriva (som en trasig DataStore-fil). */
    var fails = false

    override suspend fun get(key: String): LocalDate? {
        if (fails) throw java.io.IOException("dagboken_device gick inte att nå")
        return marks[key]
    }

    override suspend fun set(key: String, day: LocalDate) {
        if (fails) throw java.io.IOException("dagboken_device gick inte att nå")
        marks[key] = day
    }

    override suspend fun remove(key: String) {
        if (fails) throw java.io.IOException("dagboken_device gick inte att nå")
        marks.remove(key)
    }

    override suspend fun removeStartingWith(prefix: String) {
        if (fails) throw java.io.IOException("dagboken_device gick inte att nå")
        marks.keys.removeAll { it.startsWith(prefix) }
    }
}
