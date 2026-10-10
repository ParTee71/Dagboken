package se.partee71.dagboken.data.common

import kotlinx.coroutines.flow.StateFlow

/** Appens enda synkläge: underlag för "synkas…"-indikatorn (NFR-22) och sena skrivfel. */
interface SyncStatus {
    /** Sant så länge någon skrivning väntar på att nå servern. */
    val syncing: StateFlow<Boolean>

    /**
     * Senaste skrivning som lades i cachen men sedan nekades av servern (Firestore har då
     * backat den lokala ändringen). Ligger kvar tills UI:t visat den och anropat [clearWriteError],
     * så att ett fel medan appen är i bakgrunden inte försvinner.
     */
    val lastWriteError: StateFlow<DataError?>

    fun clearWriteError()

    /**
     * Bakgrundsarbete utanför Firestores skrivkö – t.ex. dossynken efter att receptformuläret sparats,
     * som körs i appens livslånga scope: räknas som [syncing] medan det pågår, och ett fel (inte
     * `Offline`, som görs om nästa gång med nät) blir [lastWriteError] som en sen skrivning.
     */
    suspend fun trackWork(work: suspend () -> Result<Unit>)
}
