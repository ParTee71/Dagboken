package se.partee71.dagboken.data.auth

import android.content.Context
import kotlinx.coroutines.flow.Flow

/**
 * Den inloggade användaren – bara det appen behöver. Namn, e-post och profilfotots adress
 * ([photoUrl]) visas i inställningsarket och avataren (AUTH-3, NAV-9) men hålls bara i minnet:
 * de loggas, sparas och cachas aldrig på disk.
 */
data class AuthUser(val uid: String, val name: String? = null, val email: String? = null, val photoUrl: String? = null) {
    /** Inga värden – uid, namn, e-post och foto får aldrig hamna i en logg eller ett felmeddelande (AUTH-3, NFR-13). */
    override fun toString(): String = "AuthUser(***)"
}

/** Inloggning med Google (skill firebase-auth). Alla fel kommer som `DataError`, mappade en gång. */
interface AuthRepository {
    /** Aktuell användare vid varje ändring; `null` = utloggad. */
    val authState: Flow<AuthUser?>

    /** Visar Googles kontoväljare – kräver aktivitetens kontext. Avbrott ger `DataError.Cancelled`. */
    suspend fun signInWithGoogle(activityContext: Context): Result<AuthUser>

    /**
     * Loggar ut ur Firebase och rensar Credential Managers sparade inloggning (AUTH-2). Appen loggar
     * ut via [SignOutUseCase], som dessutom tömmer den lokala cachen (AUTH-6).
     */
    suspend fun signOut(): Result<Unit>
}
