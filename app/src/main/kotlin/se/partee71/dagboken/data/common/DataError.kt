package se.partee71.dagboken.data.common

import kotlin.coroutines.cancellation.CancellationException

/**
 * Alla fel från datalagret, mappade en gång (Firestore i `data/firestore`, inloggning i
 * `AuthRepository`). UI visar dem via `DataError.toMessage()` i `ui/common`.
 */
sealed class DataError : Exception() {
    /** Ingen anslutning, och det som efterfrågas finns inte i cachen. */
    data object Offline : DataError()

    /** Security rules nekade – t.ex. en annan användares data. */
    data object PermissionDenied : DataError()

    /** Användaren eller systemet avbröt. */
    data object Cancelled : DataError()

    /** Användarens data har ett nyare format än appen: läsbart men skrivskyddat. */
    data object UpdateRequired : DataError()

    /** Firebase godtog inte Google-inloggningen (t.ex. ogiltig eller återkallad token). */
    data object SignInRejected : DataError()

    /** Ingen inloggad användare – samlingarna under `users/{uid}` finns inte utan uid. */
    data object NotSignedIn : DataError()

    /** Det som efterfrågas finns inte (längre) – t.ex. raderat på en annan enhet. Ett nytt försök hjälper inte. */
    data object NotFound : DataError()

    data object Unknown : DataError()
}

/** Felet i ett misslyckat [Result], eller `null` vid lyckat. */
fun Result<*>.dataError(): DataError? = exceptionOrNull()?.let { it as? DataError ?: DataError.Unknown }

/**
 * Kör [block] och fångar fel som [DataError] via [mapError] – den enda felhanteringen runt
 * Firestore och inloggning. Avbrott av coroutinen släpps igenom.
 */
suspend fun <T> suspendRunCatching(mapError: (Throwable) -> DataError, block: suspend () -> T): Result<T> =
    try {
        Result.success(block())
    } catch (e: CancellationException) {
        throw e
    } catch (e: DataError) {
        Result.failure(e)
    } catch (e: Throwable) {
        Result.failure(mapError(e))
    }
