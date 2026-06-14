---
name: firebase-auth
description: Use when working with Firebase Authentication, Google Sign-In via Credential Manager, FirebaseUser, auth state listeners, or sign-out/credential clearing in this Android project. Covers the CredentialManager + GetSignInWithGoogleOption pattern (replaces deprecated GoogleSignIn).
---

# Firebase Auth — Credential Manager Pattern

This project uses **Firebase Auth + Android Credential Manager** (not the deprecated `GoogleSignIn` API).

## Architecture

```
UI (SettingsScreen)
  → SettingsViewModel.signIn(activityContext)
    → FirebaseAuthRepository.signInWithGoogle(activityContext): Result<FirebaseUser>
      → CredentialManager.getCredential() — shows Google account picker
      → GoogleIdTokenCredential.createFrom() — extracts ID token
      → FirebaseAuth.signInWithCredential() — exchanges token for Firebase session
```

## Key Classes

- **`FirebaseAuthRepository`** (`data/auth/`) — singleton, inject with `@Inject` constructor
- **`CredentialManager`** — created from `ApplicationContext` at repository construction time
- **`GetSignInWithGoogleOption`** — use this (not `GetGoogleIdOption`) — opens full-page Google account picker
- **`R.string.default_web_client_id`** — auto-generated from `google-services.json` by `google-services` plugin. Requires `client_type: 3` in `google-services.json`.

## `FirebaseAuthRepository` API

```kotlin
@Singleton
class FirebaseAuthRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    val currentUser: FirebaseUser? get() = auth.currentUser

    // Hot flow — emits on every auth state change (sign in, sign out, token refresh)
    val authStateFlow: Flow<FirebaseUser?> = callbackFlow {
        val listener = FirebaseAuth.AuthStateListener { trySend(it.currentUser) }
        auth.addAuthStateListener(listener)
        awaitClose { auth.removeAuthStateListener(listener) }
    }

    // Needs activityContext (not ApplicationContext) for the Credential Manager UI
    suspend fun signInWithGoogle(activityContext: Context): Result<FirebaseUser>

    fun signOut()
    suspend fun clearCredentialState() // call before signOut() for clean sign-out
}
```

## Sign-In Flow

```kotlin
// In ViewModel
fun signIn(activityContext: Context) {
    viewModelScope.launch {
        _state.value = _state.value.copy(isSigningIn = true, signInError = null)
        val result = authRepo.signInWithGoogle(activityContext)
        _state.value = _state.value.copy(isSigningIn = false)
        result.onFailure { e ->
            // GetCredentialCancellationException = user dismissed picker → silent
            if (e.message?.contains("cancel", ignoreCase = true) != true) {
                _state.value = _state.value.copy(signInError = e.message ?: "Inloggning misslyckades")
            }
        }
    }
}

// In Composable — pass LocalContext.current (which is the Activity context in Compose)
val context = LocalContext.current
Button(onClick = { vm.signIn(context) }) { Text("Logga in med Google") }
```

## Sign-Out Flow

```kotlin
// Always clear credential state before signing out
fun signOut() {
    viewModelScope.launch {
        authRepo.clearCredentialState() // removes cached credential from Credential Manager
        authRepo.signOut()              // signs out from Firebase
    }
}
```

## Observing Auth State

```kotlin
// In ViewModel init — collect authStateFlow to sync UI
viewModelScope.launch {
    authRepo.authStateFlow.collectLatest { user ->
        _state.value = _state.value.copy(
            googleAccountEmail = user?.email,
            googleAccountPhotoUrl = user?.photoUrl?.toString(),
            googleDisplayName = user?.displayName,
        )
    }
}

// Null user = signed out; non-null = signed in
```

## google-services.json Requirements

For Google Sign-In to work, `app/google-services.json` must contain:
- `client_type: 1` — Android OAuth client (requires the app's SHA-1 fingerprint registered in Firebase/Google Cloud)
- `client_type: 3` — Web OAuth client (generates `R.string.default_web_client_id`)

The debug SHA-1 for this project: `50:5B:DC:3B:C9:49:F5:96:91:84:F2:23:0F:D1:BE:25:1B:10:7E:59`

If `client_type: 1` is missing:
1. Go to Firebase Console → Project Settings → Your Android app
2. Add SHA-1 fingerprint (must not be registered in any other Google Cloud project)
3. Download new `google-services.json` → replace `app/google-services.json`
4. Rebuild

## GetSignInWithGoogleOption vs GetGoogleIdOption

| Option | Behavior | When to use |
|---|---|---|
| `GetSignInWithGoogleOption` | Full-page Google account picker | **Use this** — more reliable, matches old `GoogleSignIn` behavior |
| `GetGoogleIdOption` | Bottom sheet with pre-selected account | Can fail with "Failed to retrieve an ID token" if no cached credential |

Always use `GetSignInWithGoogleOption` for first-time sign-in flows.

## WorkManager Integration

`BackupWorker` checks `authRepo.currentUser` before doing work:

```kotlin
override suspend fun doWork(): Result {
    if (authRepo.currentUser == null) return Result.success() // skip if not signed in
    // ... perform backup
}
```

## Error Categories

| Exception | Meaning | Handle |
|---|---|---|
| `GetCredentialCancellationException` | User dismissed picker | Silent — no error shown |
| `GetCredentialException` | No Google accounts on device, API not available | Show error |
| `FirebaseAuthException` | Firebase rejected the token | Show error + log |
| Generic `Exception` | Network issue, other | Show error |

## Dependencies (already in project)

```toml
# libs.versions.toml
firebase-auth = { group = "com.google.firebase", name = "firebase-auth-ktx" }
credentials = { group = "androidx.credentials", name = "credentials", version.ref = "credentialManager" }
credentials-play-services = { group = "androidx.credentials", name = "credentials-play-services-auth", version.ref = "credentialManager" }
googleid = { group = "com.google.android.libraries.identity.googleid", name = "googleid", version.ref = "googleIdentity" }
```
