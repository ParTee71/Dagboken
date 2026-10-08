package se.partee71.dagboken.data.firestore

import se.partee71.dagboken.core.schema.Doc
import se.partee71.dagboken.core.schema.ExportFormat

/**
 * Skriver råa dokument (`ExportFormat.Document`, sökväg + data med `Instant` för tidsstämplar) som de
 * är – migreringen från 3.x (OMB-2) skriver konverterarens dokument, aldrig via codecarna. Enda
 * Firestore-koden för det ligger i `data/firestore` (skill firestore-data-layer).
 */
interface RawDocumentWriter {
    /**
     * Skriver [documents] atomärt i **en** batch (högst `FirestoreCollection.MAX_BATCH` skrivningar) med
     * merge på samma dokument-id:n, så att en omkörning ger samma dokument utan dubbletter (OMB-7), och
     * väntar in serverns kvitto. Nekar rules ett dokument skrivs inget i batchen (`PermissionDenied`);
     * utan nät `DataError.Offline`; slut på kvot `DataError.QuotaExceeded`.
     */
    suspend fun writeBatch(documents: List<ExportFormat.Document>): Result<Unit>

    /**
     * Raderar [paths] atomärt i **en** batch (högst `FirestoreCollection.MAX_BATCH`) och väntar in serverns kvitto –
     * "Avbryt flytten" (OMB-7) tar bort exakt det migreringen skrev. Ett dokument som redan saknas är inget fel.
     */
    suspend fun deleteBatch(paths: List<String>): Result<Unit>

    /**
     * Sätter migreringsmarkören `users/{uid}.legacyMigration` (OMB-2, OMB-7) med merge – [marker] är
     * `LegacyMigrationCodec.encode` utan `completedAt`, som servern sätter (`serverTimestamp`). Rules
     * tillåter den en gång; finns den redan blir det `PermissionDenied`.
     */
    suspend fun markLegacyMigration(uid: String, marker: Doc): Result<Unit>

    companion object {
        /**
         * Värdet för ett fält som ska **tas bort** i en merge-skrivning (`FieldValue.delete()`), även nästlat – när
         * migreringen skriver om ett dokument som är dess eget och 3.x tömt ett fält (OMB-7). Firestores typ stannar i
         * `data/firestore`; anroparna använder den här markören.
         */
        val DELETE: Any = object {
            override fun toString() = "RawDocumentWriter.DELETE"
        }
    }
}
