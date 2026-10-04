package se.partee71.dagboken.data.auth

import javax.inject.Inject
import kotlin.time.Duration.Companion.seconds
import se.partee71.dagboken.data.common.LocalCacheCleaner

/**
 * Utloggningen (AUTH-2, AUTH-6): Firebase-sessionen och Credential Managers inloggning
 * ([AuthRepository.signOut]), sedan töms den lokala cachen så att nästa konto börjar tomt.
 *
 * Skrivningar som ännu inte nått servern finns bara i cachen. De får en kort stund att synka
 * före utloggningen (efteråt nekar servern dem); hinner de inte – t.ex. utan nät – behålls
 * cachen, och Firestore synkar dem när samma konto loggar in igen (regel 1: ingen data tappas).
 */
class SignOutUseCase @Inject constructor(
    private val auth: AuthRepository,
    private val cache: LocalCacheCleaner,
) {
    suspend operator fun invoke(): Result<Unit> {
        val synced = cache.awaitPendingWrites(SYNC_TIMEOUT)
        val signedOut = auth.signOut()
        return if (signedOut.isSuccess && synced) cache.clear() else signedOut
    }

    private companion object {
        /** Kvitton från servern brukar komma på under en sekund; utloggningen ska inte dröja. */
        val SYNC_TIMEOUT = 3.seconds
    }
}
