package se.partee71.dagboken.ui.common

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import se.partee71.dagboken.core.model.Archivable
import se.partee71.dagboken.data.common.DataError

/** Tillståndet för varje listskärm – samma fyra lägen överallt (`EntityListScreen`). */
sealed interface ListUiState<out T> {
    data object Loading : ListUiState<Nothing>

    data object Empty : ListUiState<Nothing>

    data class Content<T>(val items: List<T>) : ListUiState<T>

    data class Error(val error: DataError) : ListUiState<Nothing>
}

/**
 * Som [asListUiState], men börjar om från [Loading][ListUiState.Loading] varje gång [retry]
 * emitterar – "Försök igen" i `EntityListScreen`.
 */
@OptIn(ExperimentalCoroutinesApi::class)
fun <T> Flow<List<T>>.asListUiState(retry: Flow<Unit>): Flow<ListUiState<T>> =
    retry.onStart { emit(Unit) }.flatMapLatest { asListUiState() }

/** Den enda översättningen från en datalista till [ListUiState]. */
fun <T> Flow<List<T>>.asListUiState(): Flow<ListUiState<T>> =
    map { items -> items.toListUiState() }
        .onStart { emit(ListUiState.Loading) }
        .catch { emit(ListUiState.Error(it as? DataError ?: DataError.Unknown)) }

/** En läst lista som tillstånd: tom → [ListUiState.Empty], annars innehåll – för en skärm som själv håller laddning och fel (Dagbok). */
fun <T> List<T>.toListUiState(): ListUiState<T> = if (isEmpty()) ListUiState.Empty else ListUiState.Content(this)

/** Händelser som alla listskärmar har gemensamt; skärmens egna händelser läggs i en egen typ. */
sealed interface ListEvent {
    data object Retry : ListEvent
}

/**
 * Listtillståndet för en ViewModel: [state] från [source] och [onEvent] för [ListEvent].
 * Delas av alla listskärmars ViewModels, så att laddning, tomt och fel beter sig likadant.
 */
class ListLoader<T>(source: Flow<List<T>>, scope: CoroutineScope) {
    private val retries = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    /**
     * Utan prenumerant i [STOP_TIMEOUT_MILLIS] stoppas flödet och tillståndet går tillbaka till
     * laddning: listan från förra gången (kanske en annan användares efter kontobyte) visas aldrig
     * ens en bildruta när skärmen kommer tillbaka.
     */
    val state: StateFlow<ListUiState<T>> = source.asListUiState(retries).stateIn(
        scope,
        SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS, replayExpirationMillis = 0),
        ListUiState.Loading,
    )

    fun onEvent(event: ListEvent) {
        when (event) {
            ListEvent.Retry -> retries.tryEmit(Unit)
        }
    }
}

/** Tillståndet för en detaljskärm (`EntityDetailScreen`): laddning, fel eller innehåll. */
sealed interface DetailUiState<out T> {
    data object Loading : DetailUiState<Nothing>

    data class Content<T>(val value: T) : DetailUiState<T>

    data class Error(val error: DataError) : DetailUiState<Nothing>
}

/**
 * Den enda översättningen från ett dokument till [DetailUiState]; börjar om från laddning när
 * [retry] emitterar. Ett dokument som inte finns (raderat, eller en annans) är ett fel.
 */
@OptIn(ExperimentalCoroutinesApi::class)
fun <T : Any> Flow<T?>.asDetailUiState(retry: Flow<Unit>): Flow<DetailUiState<T>> =
    retry.onStart { emit(Unit) }.flatMapLatest {
        map<T?, DetailUiState<T>> { value -> value?.let { DetailUiState.Content(it) } ?: DetailUiState.Error(DataError.NotFound) }
            .onStart { emit(DetailUiState.Loading) }
            .catch { emit(DetailUiState.Error(it as? DataError ?: DataError.Unknown)) }
    }

/** Detaljtillståndet för en ViewModel, med "Försök igen" – som [ListLoader] för listor. */
class DetailLoader<T : Any>(source: Flow<T?>, scope: CoroutineScope) {
    private val retries = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    val state: StateFlow<DetailUiState<T>> = source.asDetailUiState(retries).stateIn(
        scope,
        SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS, replayExpirationMillis = 0),
        DetailUiState.Loading,
    )

    fun retry() {
        retries.tryEmit(Unit)
    }
}

/** Arkiverade dokument visas inte i listorna förrän "Visa arkiverade" finns för samlingen. */
fun <T : Archivable> Flow<List<T>>.activeOnly(): Flow<List<T>> = map { items -> items.filterNot { it.archived } }

/** Hur länge ett flöde lever vidare utan prenumerant – överlever en rotation. */
const val STOP_TIMEOUT_MILLIS = 5_000L
