package se.partee71.dagboken.core.engine

import kotlin.time.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import se.partee71.dagboken.core.model.Identified

/**
 * Den gemensamma ordningen för poster med dag och klockslag, äldst först: på [date] och [time], sedan
 * skapandetiden [createdAt] och sist id:t, så att ordningen alltid är entydig. Ett saknat värde räknas som
 * äldst (en daterad post går före en odaterad). Används av [latestBy] och Dagbokens tidslinje (HIST-1).
 */
fun <T : Identified> chronological(date: (T) -> LocalDate?, time: (T) -> LocalTime?, createdAt: (T) -> Instant?): Comparator<T> =
    compareBy<T>({ date(it) }, { time(it) }, { createdAt(it) }, { it.id })

/**
 * Den senaste posten – i den gemensamma ordningen [chronological] (måendeloggar HEM-5, incheckningar
 * HEM-12). `null` för en tom lista.
 */
fun <T : Identified> List<T>.latestBy(date: (T) -> LocalDate?, time: (T) -> LocalTime?, createdAt: (T) -> Instant?): T? =
    maxWithOrNull(chronological(date, time, createdAt))
