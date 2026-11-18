package se.kodhast.dagboken.data.auth

import javax.inject.Inject
import kotlin.time.Clock
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn
import se.kodhast.dagboken.data.common.DailyGate
import se.kodhast.dagboken.data.common.UserScope
import se.kodhast.ui.account.NetworkStatus

/**
 * Hämtar om den inloggades profil från Firebase (AUTH-3) – så att en ny fotoadress når avataren – när appen startar
 * ansluten och när den blir ansluten igen, högst en gång per dag, enhet och konto efter en lyckad hämtning
 * ([DailyGate]). Utloggad eller utan nät görs ingenting; ett misslyckat försök görs om nästa gång appen blir ansluten.
 * Okontrollerat: om Firebase tar in ett utbytt Google-foto här eller först vid nästa inloggning.
 */
class ProfileCheckUseCase @Inject constructor(
    private val auth: AuthRepository,
    private val user: UserScope,
    private val network: NetworkStatus,
    private val daily: DailyGate,
    private val clock: Clock,
    private val zone: TimeZone
) {
    /** Lyssnar så länge anroparen lever (`AuthViewModel`). */
    @OptIn(ExperimentalCoroutinesApi::class)
    suspend operator fun invoke() {
        // Sessionens uid – samma som dagsgrinden läser – så att grinden aldrig ser en utloggad medan kontrollen körs.
        user.uid
            .flatMapLatest { uid -> if (uid == null) emptyFlow() else network.online.filter { it } }
            .collect { daily.runOncePerDay(NAME, clock.todayIn(zone)) { auth.reload() } }
    }

    private companion object {
        const val NAME = "profile_check"
    }
}
