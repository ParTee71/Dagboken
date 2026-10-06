package se.partee71.dagboken.data.common

import kotlin.time.Duration
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.withTimeoutOrNull

// De villkorade skrivningarnas regler (`EntityCollection.createIfAbsent`, `deleteIf`, `updateIf`) – en
// gång för `FirestoreCollection` och `FakeCollection`, så att de bevisligen beter sig lika.

/** Ett dokument som en villkorad skrivning läst: saknas, läsbart – eller finns men går inte att avkoda. */
sealed interface Stored<out T> {
    data object Missing : Stored<Nothing>

    data class Readable<T>(val value: T) : Stored<T>

    /** Finns, men går inte att avkoda (t.ex. från en nyare app): rörs aldrig, och fäller inget annat. */
    data object Unreadable : Stored<Nothing>
}

/** [Stored] för ett dokument som [exists]; ett fel i [decode] blir [Stored.Unreadable], inte ett fel. */
fun <T> storedOf(exists: Boolean, decode: () -> T?): Stored<T> = when {
    !exists -> Stored.Missing
    else -> runCatching(decode).getOrNull()?.let { Stored.Readable(it) } ?: Stored.Unreadable
}

/** `createIfAbsent`: bara det som bevisligen saknas skapas – ett oläsbart dokument finns. */
fun Stored<*>.mayCreate(): Boolean = this == Stored.Missing

/** `deleteIf`/`updateIf`: bara ett läsbart dokument som uppfyller [condition] ändras. */
fun <T> Stored<T>.satisfies(condition: (T) -> Boolean): Boolean = this is Stored.Readable && condition(value)

/**
 * Villkorade skrivningar som inte hann bekräftas inom väntetiden. De kan fortfarande committa på
 * servern, så nästa beslut som bygger på samma dokument väntar först in dem ([awaitAll]) – annars
 * kunde en sen radering landa efter en ny synk och ta dess doser.
 */
class PendingCommits {
    private val pending = mutableSetOf<Deferred<*>>()

    /**
     * Väntar på [commit] högst [wait]; `null` = inte klar i tid. En commit som inte är klar när
     * väntan slutar – efter tidsgränsen, ett fel eller ett avbrott av den som väntar – spåras tills den är klar,
     * och då anropas också [onUnfinished] (t.ex. så att ett sent fel syns i `SyncStatus`) – också vid avbrott.
     */
    suspend fun <R : Any> await(commit: Deferred<R>, wait: Duration, onUnfinished: () -> Unit = {}): R? {
        try {
            // En commit som blev klar precis när tidsgränsen löpte ut räknas som klar – inte som "inte skriven".
            return withTimeoutOrNull(wait) { commit.await() } ?: commit.completedOrNull()
        } finally {
            if (!commit.isCompleted) {
                synchronized(pending) { pending += commit }
                commit.invokeOnCompletion { synchronized(pending) { pending -= commit } }
                onUnfinished()
            }
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun <R : Any> Deferred<R>.completedOrNull(): R? =
        if (isCompleted && getCompletionExceptionOrNull() == null) getCompleted() else null

    /** Väntar in de spårade, högst [wait] totalt; `false` = någon är fortfarande inte klar. */
    suspend fun awaitAll(wait: Duration): Boolean {
        val current = synchronized(pending) { pending.toList() }
        return withTimeoutOrNull(wait) { current.forEach { it.join() } } != null
    }
}

/** `EntityCollection.move`: vad transaktionen gjorde, prövat mot serverns version av källan och målet. */
enum class MoveOutcome {
    Moved,

    /** Källan finns inte (raderad under tiden): ingenting skrivs, den återuppstår inte. */
    SourceMissing,

    /** Målet finns redan: ingenting skrivs, ett befintligt dokument skrivs aldrig över. */
    TargetExists;

    fun toResult(): Result<Unit> = when (this) {
        Moved -> Result.success(Unit)
        SourceMissing -> Result.failure(DataError.NotFound)
        TargetExists -> Result.failure(TargetExists())
    }
}

/**
 * `EntityCollection.move`: målet finns redan, så ingenting skrevs. Inget [DataError] – ett nej som anroparen
 * översätter till sitt eget fel (t.ex. `DoseSlotTaken`); visat som det är blir det "okänt fel" (`toFailure`).
 */
class TargetExists : Exception()

/** [MoveOutcome] för källan [source] (lagrad, `null` = finns inte) och om målet [targetExists]. */
fun moveOutcome(source: Map<String, Any?>?, targetExists: Boolean): MoveOutcome = when {
    source == null -> MoveOutcome.SourceMissing
    targetExists -> MoveOutcome.TargetExists
    else -> MoveOutcome.Moved
}

/**
 * Det flyttade dokumentet: det lagrade [stored] med alla fält (också okända), utan toppfälten i [remove] och
 * med [patch] (de ändrade fälten och `updatedAt`, `fieldsForUpdate`) ovanpå – samma regel som `update`.
 */
fun movedDocument(stored: Map<String, Any?>, patch: Map<String, Any?>, remove: Set<String>): Map<String, Any?> = stored - remove + patch
