package se.partee71.dagboken.data.auth

import android.content.Context
import kotlinx.coroutines.flow.Flow

/**
 * Den inloggade användaren – bara det appen behöver. Namn och e-post visas i inställningsarket
 * (Konto, NAV-9) men loggas eller sparas aldrig.
 */
data class AuthUser(val uid: String, val name: String? = null, val email: String? = null)

/** Inloggning med Google (skill firebase-auth). Alla fel kommer som `DataError`, mappade en gång. */
interface AuthRepository {
    /** Aktuell användare vid varje ändring; `null` = utloggad. */
    val authState: Flow<AuthUser?>

    /** Visar Googles kontoväljare – kräver aktivitetens kontext. Avbrott ger `DataError.Cancelled`. */
    suspend fun signInWithGoogle(activityContext: Context): Result<AuthUser>

    /** Loggar ut ur Firebase och rensar Credential Managers sparade inloggning (AUTH-3). */
    suspend fun signOut(): Result<Unit>
}
