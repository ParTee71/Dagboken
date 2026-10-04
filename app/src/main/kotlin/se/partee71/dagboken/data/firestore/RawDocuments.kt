package se.partee71.dagboken.data.firestore

import se.partee71.dagboken.core.schema.Doc

/**
 * Dokument som de ligger lagrade, med tidsstämplar som `Instant` – för exporten, som ska ta med
 * allt, även fält som appen inte känner till, och därför aldrig går via codecarna.
 */
interface RawDocuments {
    /** Väntar tills enhetens egna skrivningar nått servern, så att de kommer med i det som läses. */
    suspend fun awaitPendingWrites()

    /** Dokumentet på [path], eller `null` om det inte finns. */
    suspend fun document(path: String): Doc?

    /** Dokumenten i samlingen [path], per ID. */
    suspend fun collection(path: String): Map<String, Doc>
}
