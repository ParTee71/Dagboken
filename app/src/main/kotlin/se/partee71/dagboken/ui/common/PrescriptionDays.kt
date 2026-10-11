package se.partee71.dagboken.ui.common

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import se.partee71.dagboken.data.common.DataError
import se.partee71.dagboken.data.common.suspendRunCatching
import se.partee71.dagboken.data.repository.PrescriptionRepository

/**
 * REC-8, REC-5, REC-10: receptens städning (`PrescriptionRepository.tidyUp`) för varje dag flödet ger – när
 * en vy med recepten visas och igen vid midnatt (Mediciner, Idag). Följer flödets prenumeration, så att
 * dagklockan inte tickar när vyn inte visas. Offline gör städningen ingenting (repositoryt svarar utan fel);
 * ett annat fel – t.ex. slut på molnets kvot – går till [onFailure], så att vyn kan visa det. Nästa dag eller
 * visning försöker igen. Ett kast är ett programfel som inte får fälla vyn: det fångas och visas inte. Körs i
 * [scope] (ViewModelns), så att flödet inte väntar på den.
 */
fun Flow<LocalDate>.tidyingUpEachDay(
    prescriptions: PrescriptionRepository,
    scope: CoroutineScope,
    onFailure: (Failure) -> Unit = {},
): Flow<LocalDate> =
    onEach { day ->
        scope.launch {
            failureOfAttempt { prescriptions.tidyUp(day) }?.let(onFailure)
        }
    }

/**
 * Utfallet av ett bakgrundsförsök mot recepten ([tidyingUpEachDay], Idags `ensureDay`): felet som ska visas, eller
 * `null`. Ett kast fångas och visas inte (ett programfel får inte fälla vyn); offline svarar repositoryt utan fel.
 */
suspend fun failureOfAttempt(action: suspend () -> Result<Unit>): Failure? =
    suspendRunCatching({ DataError.Unknown }) { action() }.getOrNull()?.failureOrNull()
