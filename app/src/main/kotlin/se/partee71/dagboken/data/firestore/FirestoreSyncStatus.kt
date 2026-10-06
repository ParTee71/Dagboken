package se.partee71.dagboken.data.firestore

import com.google.android.gms.tasks.Task
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import se.partee71.dagboken.data.common.DataError
import se.partee71.dagboken.data.common.SyncStatus
import se.partee71.dagboken.data.common.dataError
import se.partee71.dagboken.data.common.suspendRunCatching
import se.partee71.dagboken.di.ApplicationScope

/**
 * Räknar skrivningar som ännu inte nått servern. En Firestore-skrivning ligger i cachen direkt
 * men dess `Task` blir klar först när servern svarat – offline kan det dröja länge.
 */
@Singleton
class FirestoreSyncStatus @Inject constructor(@ApplicationScope scope: CoroutineScope) : SyncStatus {
    private val pending = MutableStateFlow(0)
    private val error = MutableStateFlow<DataError?>(null)

    override val syncing: StateFlow<Boolean> = pending.map { it > 0 }.stateIn(scope, SharingStarted.Eagerly, false)
    override val lastWriteError: StateFlow<DataError?> = error.asStateFlow()

    override fun clearWriteError() {
        error.value = null
    }

    override suspend fun trackWork(work: suspend () -> Result<Unit>) {
        pending.update { it + 1 }
        try {
            // Också ett kastat fel blir ett skrivfel – det får aldrig fälla appens scope.
            // Offline är inget fel här: arbetet görs om nästa gång med nät.
            suspendRunCatching(::firestoreError) { work().getOrThrow() }.dataError()
                ?.takeIf { it != DataError.Offline }
                ?.let { error.value = it }
        } finally {
            pending.update { it - 1 }
        }
    }

    /** Följer [task] tills servern svarat; ett fel sparas i [lastWriteError]. */
    fun track(task: Task<*>) {
        pending.update { it + 1 }
        task.addOnCompleteListener {
            pending.update { count -> count - 1 }
            it.exception?.let { failure -> error.value = firestoreError(failure) }
        }
    }
}
