package se.partee71.dagboken.data.common

import kotlin.time.Duration

/**
 * Den lokala databascachen (AUTH-6): utloggningen tömmer den så att nästa konto börjar med en tom
 * cache. Firestore-implementationen ligger i `data/firestore`; tester använder en fejk.
 */
interface LocalCacheCleaner {
    /**
     * Väntar högst [timeout] på att alla lokala skrivningar når servern. `false` = något är
     * fortfarande osynkat – då får cachen inte tömmas, annars tappas data (regel 1).
     */
    suspend fun awaitPendingWrites(timeout: Duration): Boolean

    /** Stänger databasen, tömmer cachen på enheten och öppnar en ny, tom databas. */
    suspend fun clear(): Result<Unit>
}
