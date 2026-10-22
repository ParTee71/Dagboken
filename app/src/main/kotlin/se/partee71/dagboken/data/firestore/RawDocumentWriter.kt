package se.partee71.dagboken.data.firestore

import se.partee71.dagboken.core.schema.ExportFormat

/**
 * Skriver råa dokument (`ExportFormat.Document`, sökväg + data med `Instant` för tidsstämplar) som de
 * är – importen (BCK-6, BCK-14) skriver filens dokument, aldrig via codecarna. Enda Firestore-koden för
 * det ligger i `data/firestore` (skill firestore-data-layer).
 */
interface RawDocumentWriter {
    /**
     * Skriver [documents] atomärt i **en** batch (högst `FirestoreCollection.MAX_BATCH` skrivningar) med
     * merge på samma dokument-id:n, så att en omkörning ger samma dokument utan dubbletter (DAT-13), och
     * väntar in serverns kvitto. Nekar rules ett dokument skrivs inget i batchen (`PermissionDenied`);
     * utan nät `DataError.Offline`; slut på kvot `DataError.QuotaExceeded`.
     */
    suspend fun writeBatch(documents: List<ExportFormat.Document>): Result<Unit>

    /**
     * Raderar [paths] atomärt i **en** batch (högst `FirestoreCollection.MAX_BATCH`) och väntar in serverns kvitto.
     * Ett dokument som redan saknas är inget fel.
     */
    suspend fun deleteBatch(paths: List<String>): Result<Unit>
}
