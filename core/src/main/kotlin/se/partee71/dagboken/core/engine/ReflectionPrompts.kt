package se.partee71.dagboken.core.engine

import kotlinx.datetime.LocalDate

/**
 * NOT-21: Stundens hjälpfrågor – [COUNT] stycken, alla i nutid, en per dag. `:core` ger bara indexet; texterna
 * ligger i appens `strings.xml` (en array med [COUNT] element, i den här ordningen). Samma fråga hela dagen och
 * för varje tillfälle: indexet är dag i året modulo [COUNT], så det är deterministiskt och kräver inget lagrat tillstånd.
 */
object ReflectionPrompts {
    /** Antalet hjälpfrågor – appens array ska ha exakt så många. */
    const val COUNT = 30

    /** Alla index, i ordning. */
    val indices: IntRange = 0 until COUNT

    /** Dagens hjälpfråga för [date]: dag i året (1–366) modulo [COUNT]. */
    fun indexFor(date: LocalDate): Int = date.dayOfYear % COUNT
}
