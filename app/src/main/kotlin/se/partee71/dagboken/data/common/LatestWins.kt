package se.partee71.dagboken.data.common

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * "Senaste vinner" per nyckel – t.ex. dossynken per recept när reglaget växlas snabbt. Körningar för
 * samma nyckel går en i taget (två synkar skriver aldrig samtidigt), men en körning som hunnit bli
 * inaktuell – en senare har begärts – hoppas över innan den startar, och en pågående får veta det via
 * `isCurrent` och avslutar vid nästa steg. Ingen avbryts mitt i en skrivning: en avbruten väntan på
 * Firestore stoppar inte själva skrivningen, så två körningar kunde annars överlappa på servern.
 * Den som begär en körning väntar bara på låset för nyckeln, aldrig på andra nycklar.
 */
class LatestWins<K> {
    private class State {
        val lock = Mutex()
        var latest = 0L

        /** Körningar som begärts men inte avslutats – posten tas bort när den blir 0. */
        var waiting = 0
    }

    private val states = HashMap<K, State>()

    /**
     * Kör [block] för [key] när föregående körning för nyckeln är klar – eller ger [superseded] direkt
     * om en senare begäran hunnit komma. [block] får `isCurrent`, som blir `false` så fort en senare
     * begäran kommer.
     */
    suspend fun <R> run(key: K, superseded: R, block: suspend (isCurrent: () -> Boolean) -> R): R {
        val (state, generation) = synchronized(states) {
            val state = states.getOrPut(key, ::State)
            state.latest += 1
            state.waiting += 1
            state to state.latest
        }
        val isCurrent = { synchronized(states) { state.latest == generation } }
        try {
            return state.lock.withLock { if (isCurrent()) block(isCurrent) else superseded }
        } finally {
            synchronized(states) {
                state.waiting -= 1
                if (state.waiting == 0) states.remove(key)
            }
        }
    }

    /** Antal nycklar med en pågående eller väntande körning (för test: inga poster blir kvar). */
    internal val activeKeys: Int get() = synchronized(states) { states.size }
}
