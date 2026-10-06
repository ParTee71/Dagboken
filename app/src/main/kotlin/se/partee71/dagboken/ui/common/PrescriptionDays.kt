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
 * dagklockan inte tickar när vyn inte visas. Offline gör städningen ingenting; ett fel här är inget
 * användaren kan åtgärda och visas inte – nästa dag eller visning försöker igen, och inget fel får fälla
 * vyn. Körs i [scope] (ViewModelns), så att flödet inte väntar på den.
 */
fun Flow<LocalDate>.tidyingUpEachDay(prescriptions: PrescriptionRepository, scope: CoroutineScope): Flow<LocalDate> =
    onEach { day -> scope.launch { suspendRunCatching({ DataError.Unknown }) { prescriptions.tidyUp(day) } } }
