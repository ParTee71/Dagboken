package se.kodhast.dagboken.data.auth

import javax.inject.Inject
import kotlin.time.Duration.Companion.seconds
import se.kodhast.dagboken.data.common.DataError
import se.kodhast.dagboken.data.common.HistoryCoverage
import se.kodhast.dagboken.data.common.LocalCacheCleaner
import se.kodhast.dagboken.data.common.suspendRunCatching
import se.kodhast.ui.account.ProfilePhotoCache

/**
 * Utloggningen (AUTH-2, AUTH-6): Firebase-sessionen och Credential Managers inloggning
 * ([AuthRepository.signOut]), sedan tas profilfotot bort från enheten ([ProfilePhotoCache], AUTH-3) och den lokala
 * cachen töms så att nästa konto börjar tomt.
 *
 * Skrivningar som ännu inte nått servern finns bara i cachen. De får en kort stund att synka
 * före utloggningen (efteråt nekar servern dem); hinner de inte – t.ex. utan nät – behålls
 * cachen, och Firestore synkar dem när samma konto loggar in igen (regel 1: ingen data tappas).
 *
 * Historiklägets täckning (NFR-24) beskriver cachen och glöms före tömningen – en anteckning som överlevde cachen skulle
 * få historiken att läsa en tom cache som om den vore komplett – och igen efteråt, ifall en hämtning hann anteckna under
 * tiden. Cachen töms **alltid** (integriteten går före: ingen hälsodata blir kvar efter utloggningen), och utfallet är
 * tömningens: att glömskan misslyckas är inget fel för användaren, som är utloggad (ingenting loggas). Är `dagboken_device`
 * oläslig räknas ingen täckning ändå. Kvarstående risk: kan filen läsas men inte skrivas står täckningen kvar mot en tom
 * cache, och äldre dagar saknas tills täckningen hämtas om efter 7 dagar (självläker).
 */
class SignOutUseCase @Inject constructor(
    private val auth: AuthRepository,
    private val cache: LocalCacheCleaner,
    private val coverage: HistoryCoverage,
    private val photos: ProfilePhotoCache
) {
    suspend operator fun invoke(): Result<Unit> {
        val synced = cache.awaitPendingWrites(SYNC_TIMEOUT)
        val signedOut = auth.signOut()
        if (!signedOut.isSuccess) return signedOut
        // Profilfotot är ingen data som väntar på servern: det tas alltid bort (AUTH-3); ett fel här är inget för användaren.
        photos.clear()
        if (!synced) return signedOut
        forget()
        val cleared = cache.clear()
        forget()
        return cleared
    }

    private suspend fun forget(): Result<Unit> = suspendRunCatching { coverage.forgetAll() }

    private companion object {
        /** Kvitton från servern brukar komma på under en sekund; utloggningen ska inte dröja. */
        val SYNC_TIMEOUT = 3.seconds
    }
}
