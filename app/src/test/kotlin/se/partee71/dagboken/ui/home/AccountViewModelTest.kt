package se.partee71.dagboken.ui.home

import app.cash.turbine.test
import com.google.firebase.auth.FirebaseUser
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import se.partee71.dagboken.data.auth.FirebaseAuthRepository

@OptIn(ExperimentalCoroutinesApi::class)
class AccountViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private val authFlow = MutableStateFlow<FirebaseUser?>(null)
    private lateinit var authRepo: FirebaseAuthRepository
    private lateinit var viewModel: AccountViewModel

    @Before fun setUp() {
        Dispatchers.setMain(testDispatcher)
        authRepo = mockk(relaxed = true) {
            every { authStateFlow } returns authFlow
            coEvery { signInWithGoogle(any()) } returns Result.success(mockk(relaxed = true))
        }
        viewModel = AccountViewModel(authRepo)
    }

    @After fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test fun `initial uiState has null user fields and isSigningIn false`() = runTest {
        viewModel.uiState.test {
            val state = awaitItem()
            assertNull(state.googleEmail)
            assertNull(state.googlePhotoUrl)
            assertNull(state.googleDisplayName)
            assertFalse(state.isSigningIn)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test fun `uiState maps signed-in user email and display name`() = runTest {
        val user: FirebaseUser = mockk(relaxed = true) {
            every { email } returns "alice@example.com"
            every { displayName } returns "Alice"
            every { photoUrl } returns null
        }
        viewModel.uiState.test {
            awaitItem() // initial AccountUiState (upstream emits same value — StateFlow deduplicates)
            authFlow.value = user
            val signedIn = awaitItem()
            assertEquals("alice@example.com", signedIn.googleEmail)
            assertEquals("Alice", signedIn.googleDisplayName)
            assertNull(signedIn.googlePhotoUrl)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test fun `signOut delegates clearCredentialState and signOut to repo`() = runTest {
        viewModel.signOut()
        coVerify { authRepo.clearCredentialState() }
        coVerify { authRepo.signOut() }
    }

    @Test fun `signIn delegates to repo signInWithGoogle`() = runTest {
        viewModel.signIn(mockk(relaxed = true))
        coVerify { authRepo.signInWithGoogle(any()) }
    }
}
