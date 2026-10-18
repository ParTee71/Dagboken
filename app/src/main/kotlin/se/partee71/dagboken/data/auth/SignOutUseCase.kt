package se.partee71.dagboken.data.auth

import javax.inject.Inject
import kotlin.time.Duration.Companion.seconds
import se.partee71.dagboken.data.common.DataError
import se.partee71.dagboken.data.common.HistoryCoverage
import se.partee71.dagboken.data.common.LocalCacheCleaner
import se.partee71.dagboken.data.common.suspendRunCatching

/**
 * Utloggningen (AUTH-2, AUTH-6): Firebase-sessionen och Credential Managers inloggning
 * ([AuthRepository.signOut]), sedan töms den lokala cachen så att nästa konto börjar tomt.
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
    private val coverage: HistoryCoverage
) {
    suspend operator fun invoke(): Result<Unit> {
        val synced = cache.awaitPendingWrites(SYNC_TIMEOUT)
        val signedOut = auth.signOut()
        if (!signedOut.isSuccess || !synced) return signedOut
        forget()
        val cleared = cache.clear()
        forget()
        return cleared
    }

    private suspend fun forget(): Result<Unit> = suspendRunCatching({ DataError.Unknown }) { coverage.forgetAll() }

    private companion object {
        /** Kvitton från servern brukar komma på under en sekund; utloggningen ska inte dröja. */
        val SYNC_TIMEOUT = 3.seconds
    }
}
