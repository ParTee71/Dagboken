package se.partee71.dagboken.ui.common

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import se.partee71.dagboken.data.common.DataError
import se.partee71.dagboken.data.common.retryWithBackoffWhile
import se.partee71.dagboken.data.common.suspendRunCatching
import se.partee71.dagboken.data.repository.PrescriptionRepository
import se.partee71.dagboken.ui.common.AppErrors
import se.partee71.ui.common.UiError
import se.partee71.ui.common.uiErrorOrNull

/**
 * REC-8, REC-5, REC-10, HEM-10: receptens städning (`PrescriptionRepository.tidyUp`) för varje dag flödet ger – när
 * en vy med recepten visas och igen vid midnatt (Mediciner, Idag). Följer flödets prenumeration, så att dagklockan
 * inte tickar när vyn inte visas. Varje prenumeration anropar `tidyUp`, men repositoryts dagsgrind (#304) kör
 * städningen mot servern högst en gång per dag, enhet och konto efter en lyckad körning – flikbesök och vyer som
 * visas samtidigt kostar inga nya serverläsningar. Offline gör städningen ingenting (repositoryt svarar utan fel, och
 * nästa visning med nät kör); ett annat fel – t.ex. slut på molnets kvot – går till [onFailure], så att vyn kan visa
 * det, och nästa visning eller dag försöker igen. Ett kast är ett programfel som inte får fälla vyn: det fångas och
 * visas inte. Körs i [scope] (ViewModelns), så att flödet inte väntar på den.
 */
fun Flow<LocalDate>.tidyingUpEachDay(
    prescriptions: PrescriptionRepository,
    scope: CoroutineScope,
    onFailure: (UiError) -> Unit = {}
): Flow<LocalDate> = onEach { day ->
    scope.launch {
        failureOfAttempt { prescriptions.tidyUp(day) }?.let(onFailure)
    }
}

/**
 * HEM-10: ett bakgrundsförsök ([attempt], Idags `ensureDay`) för varje värde flödet ger, utan att flödet väntar på det.
 * Försöket körs i [scope] (ViewModelns), så att ett påbörjat försök alltid avslutas och dess utfall ([onResult]: felet
 * som ska visas, eller `null`) alltid ges – utom när ett nyare försök för samma värde startat, så att ett äldre, långsamt
 * försök aldrig skriver över ett nyare. Utan nät ([DataError.Offline]) anropas [onOffline] och försöket görs om med
 * växande paus (`retryWithBackoffWhile`) – men bara så länge flödet samlas in (vyn visas) och inget nytt värde kommit;
 * annars gör nästa visning eller värde om det. Ett kast fångas ([failureOfAttempt]). [onOffline] och [onResult] anropas
 * på [scope]s och insamlingens tråd (huvudtråden), som räknar försöken.
 */
@OptIn(ExperimentalCoroutinesApi::class)
fun <T> Flow<T>.attemptingEach(
    scope: CoroutineScope,
    onOffline: (T) -> Unit,
    onResult: (T, UiError?) -> Unit,
    attempt: suspend (T) -> Result<Unit>
): Flow<T> = channelFlow {
    val latest = HashMap<T, Long>()
    var started = 0L
    collectLatest { value ->
        send(value)
        retryWithBackoffWhile(retryOn = { failure: UiError? -> failure?.cause == DataError.Offline }, onRetry = { onOffline(value) }) {
            val number = ++started
            latest[value] = number
            scope.async {
                failureOfAttempt { attempt(value) }.also { failure ->
                    if (failure?.cause != DataError.Offline && latest[value] == number) onResult(value, failure)
                }
            }.await()
        }
    }
}

/**
 * Utfallet av ett bakgrundsförsök mot recepten ([tidyingUpEachDay], [attemptingEach]): felet som ska visas, eller
 * `null`. Ett kast fångas och visas inte (ett programfel får inte fälla vyn); offline svarar städningen utan fel, och
 * `ensureDay` med [DataError.Offline] (som [attemptingEach] gör om).
 */
suspend fun failureOfAttempt(action: suspend () -> Result<Unit>): UiError? = suspendRunCatching { action() }.getOrNull()?.uiErrorOrNull(AppErrors)
