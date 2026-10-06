package se.partee71.dagboken.core.engine

import kotlin.time.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import se.partee71.dagboken.core.model.Identified

/**
 * Den senaste posten – en ordning för alla poster med dag och klockslag (måendeloggar HEM-5, incheckningar
 * HEM-12): på [date] och [time], sedan skapandetiden [createdAt] och sist id:t, så att valet alltid är
 * entydigt. Ett saknat värde räknas som äldst (en daterad post går före en odaterad). `null` för en tom lista.
 */
fun <T : Identified> List<T>.latestBy(date: (T) -> LocalDate?, time: (T) -> LocalTime?, createdAt: (T) -> Instant?): T? =
    maxWithOrNull(compareBy<T>({ date(it) }, { time(it) }, { createdAt(it) }, { it.id }))
