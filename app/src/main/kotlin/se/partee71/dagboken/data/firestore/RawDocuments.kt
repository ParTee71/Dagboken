package se.partee71.dagboken.data.firestore

import se.partee71.dagboken.core.schema.Doc

/**
 * Dokument som de ligger lagrade, med tidsstämplar som `Instant` – för exporten, som ska ta med
 * allt, även fält som appen inte känner till, och därför aldrig går via codecarna. Alltid från
 * servern; utan nät kastas Firestores fel (anroparen mappar med `firestoreError`).
 */
interface RawDocuments {
    /** Väntar tills enhetens egna skrivningar nått servern, så att de kommer med i det som läses. */
    suspend fun awaitPendingWrites()

    /** Dokumentet på [path], eller `null` om det inte finns. */
    suspend fun document(path: String): Doc?

    /** Dokumenten i samlingen [path], per ID. */
    suspend fun collection(path: String): Map<String, Doc>

    /** Om samlingen [path] har minst ett dokument – en fråga med `limit(1)`, aldrig hela samlingen (fallbacken, OMB-5). */
    suspend fun hasDocuments(path: String): Boolean

    /**
     * Dokumenten på [paths] som finns på servern, per sökväg – lästa direkt på id i grupper om högst
     * [ID_GROUP] (`documentId() in …`) och parallellt, aldrig hela samlingar. Migreringens läge före
     * skrivningen och verifieringen efter den (OMB-2, OMB-7).
     */
    suspend fun documents(paths: Collection<String>): Map<String, Doc>

    companion object {
        /** Firestores gräns för antal värden i en `in`-fråga. */
        const val ID_GROUP = 30
    }
}
