package se.partee71.dagboken.data.common

import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.retryWhen

/**
 * Börjar om flödet med växande paus ([first], dubblas upp till [max]) när [retryOn] godtar felet,
 * så att ett tillfälligt fel aldrig avslutar ett flöde som appen följer. [beforeRetry] kan sända
 * ett ersättningsvärde innan pausen. Andra fel släpps igenom.
 */
fun <T> Flow<T>.retryWithBackoff(
    first: Duration,
    max: Duration,
    retryOn: (Throwable) -> Boolean,
    beforeRetry: suspend FlowCollector<T>.(Throwable) -> Unit = {},
): Flow<T> = retryWhen { cause, attempt ->
    if (!retryOn(cause)) return@retryWhen false
    beforeRetry(cause)
    delay(backoff(first, max, attempt))
    true
}

/** Pausen före försök nummer [attempt] (från 0). */
internal fun backoff(first: Duration, max: Duration, attempt: Long): Duration =
    (first * (1L shl attempt.coerceAtMost(MAX_DOUBLINGS).toInt()).toDouble()).coerceAtMost(max)

private const val MAX_DOUBLINGS = 16L

/**
 * Första pausen innan ett försök utan nät görs om, och den längsta – kort nog att det kommer snart efter att nätet kommit
 * tillbaka: historikläget (NFR-24) och receptens försök på Idag och Mediciner (HEM-10, REC-8).
 */
val OFFLINE_RETRY_FIRST = 30.seconds
val OFFLINE_RETRY_MAX = 1.minutes

/**
 * Kör [block] och gör om det med växande paus ([OFFLINE_RETRY_FIRST] upp till [OFFLINE_RETRY_MAX]) så länge [retryOn]
 * godtar utfallet – [onRetry] före varje paus. Ger det första utfall som inte görs om; avbryts med anroparen.
 */
suspend fun <R> retryWithBackoffWhile(retryOn: (R) -> Boolean, onRetry: (R) -> Unit = {}, block: suspend () -> R): R {
    var attempt = 0L
    while (true) {
        val result = block()
        if (!retryOn(result)) return result
        onRetry(result)
        delay(backoff(OFFLINE_RETRY_FIRST, OFFLINE_RETRY_MAX, attempt++))
    }
}

/**
 * För ett tillägg på en skärm (en sammanfattning, en räknare): vid fel visas [fallback]
 * och flödet försöker igen efter en växande paus, så att tillägget kommer tillbaka när felet gått
 * över – i stället för att hela skärmen visar fel eller tillägget försvinner för gott. Pausen
 * börjar om när något lästs igen. Ett bestående fel (nekad, kräver uppdatering, utloggad,
 * finns inte) försöks inte igen: [fallback] står kvar.
 */
fun <T> Flow<T>.withFallback(fallback: T): Flow<T> = flow {
    var failures = 0L
    emitAll(
        onEach { failures = 0 }
            .retryWhen { cause, _ ->
                emit(fallback)
                if (cause in LASTING) return@retryWhen false
                delay(backoff(FALLBACK_FIRST_RETRY, FALLBACK_MAX_RETRY, failures++))
                true
            }
            .catch { },
    )
}

/** Fel som inte går över av sig själva medan skärmen är öppen. */
private val LASTING = setOf(DataError.PermissionDenied, DataError.UpdateRequired, DataError.NotSignedIn, DataError.NotFound)

private val FALLBACK_FIRST_RETRY = 2.seconds
private val FALLBACK_MAX_RETRY = 60.seconds
