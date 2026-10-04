package se.partee71.dagboken.data.user

import se.partee71.dagboken.core.schema.Doc

/**
 * Användardokumentet `users/{uid}` på **servern** – underlaget för första inloggningen. Till
 * skillnad från samlingarna (offline först) väntar operationen på servern: dokumentet ska finnas
 * där innan appen öppnas, och ett befintligt dokument får aldrig skrivas över.
 * Utan nät blir det [se.partee71.dagboken.data.common.DataError.Offline].
 */
interface UserDirectory {
    /**
     * Skapar `users/{uid}` med [initial] om dokumentet inte finns – atomärt, så att ett dokument
     * som redan finns (från en annan enhet) aldrig skrivs över. Ger dokumentet som det ligger på
     * servern efteråt: det befintliga, eller [initial].
     */
    suspend fun createIfMissing(uid: String, initial: Doc): Result<Doc>
}
