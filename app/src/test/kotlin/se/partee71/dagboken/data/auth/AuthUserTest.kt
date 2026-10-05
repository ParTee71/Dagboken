package se.partee71.dagboken.data.auth

import kotlin.test.assertEquals
import kotlin.test.assertFalse
import org.junit.Test
import se.partee71.dagboken.ui.auth.AuthGate
import se.partee71.dagboken.ui.auth.AuthUiState

/** AUTH-3, NFR-13: uid, namn, e-post och foto kan aldrig hamna i en logg via `toString`. */
class AuthUserTest {

    private val anna = AuthUser("uid-anna", "Anna Berg", "anna.berg@exempel.se", "https://exempel.se/anna.jpg")

    @Test
    fun `toString visar inga värden`() {
        assertEquals("AuthUser(***)", anna.toString())
    }

    @Test
    fun `inte heller tillståndet som bär kontot läcker det`() {
        val text = AuthUiState(AuthGate.Ready, account = anna).toString()
        listOf("uid-anna", "Anna", "anna.berg@exempel.se", "https://exempel.se/anna.jpg").forEach { assertFalse(it in text, text) }
    }
}
