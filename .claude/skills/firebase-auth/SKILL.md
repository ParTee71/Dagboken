---
name: firebase-auth
description: Use when working with Firebase Authentication, Google Sign-In via Credential Manager, FirebaseUser, auth state listeners, or sign-out/credential clearing in Dagboken. Covers the CredentialManager + GetSignInWithGoogleOption pattern (replaces deprecated GoogleSignIn), the required sign-in gate in 4.0 and the users/{uid} bootstrap. Svenska trigger-ord: inloggning, logga in, logga ut, konto, Google-konto, användare, uid, EnsureUserUseCase, UserSession, SignInScreen, AuthGate.
---

# Firebase Auth — Credential Manager Pattern

This project uses **Firebase Auth + Android Credential Manager** (not the deprecated `GoogleSignIn` API).

**Dagboken 4.0 requires sign-in** (AUTH-6, TP-5; the 3.x requirement AUTH-5 "works without an
account" is struck – ADR-001, beslut 3). The app shows `SignInScreen` until a user is signed in;
after sign-in `EnsureUserUseCase` makes sure `users/{uid}` exists and `UserSession` follows the
uid. Firestore rules depend on `request.auth.uid == uid`, so nothing is readable signed out.
There is no household and no sharing – one account, one diary.

## Architecture

```
AppRoot → AuthViewModel.state.gate: Loading | SignedOut | NeedsUser | UpdateRequired | Ready
  SignInScreen → AuthEvent.SignIn(activityContext)
    → AuthRepository.signInWithGoogle(activityContext): Result<AuthUser>   (GoogleAuthRepository)
      → CredentialManager.getCredential() — Google account picker
      → GoogleIdTokenCredential.createFrom() — ID token
      → FirebaseAuth.signInWithCredential() — Firebase session
  authState = user && users/{uid} missing on the server
    → EnsureUserUseCase(uid) — UserDirectory.createIfMissing (server, atomic) with schemaVersion + createdAt
```

## Key Classes

- **`AuthRepository`** (`data/auth/`) — interface; `GoogleAuthRepository` is the only implementation (bound in `di/AuthModule`). Tests use a fake.
- **`AuthUser(uid, name?, email?)`** — all the app keeps of the user. Name and email are only shown in the settings sheet (Konto, SET-7) and are never stored or logged; never the photo (skill `data-privacy-security`).
- **`AuthErrors`** (`data/auth/`) — the one mapping to `DataError`.
- **`AuthViewModel`** (`ui/auth/`) — decides what `AppRoot` shows (`AuthGate`); sign-out lives in the settings sheet (NAV-9).
- **`UserSession`** (`data/user/`, implements `UserScope`) + **`EnsureUserUseCase`** + **`UserDirectory`** (Firestore implementation `FirestoreUserDirectory` in `data/firestore/`) — skill `firestore-data-layer`.
- **`GetSignInWithGoogleOption`** — use this (not `GetGoogleIdOption`) — opens full-page Google account picker.
- **`R.string.default_web_client_id`** — auto-generated from `google-services.json` by the `google-services` plugin. Requires `client_type: 3`. If the file were missing, a stub resource (`app/src/authStub`) provides it so the code compiles (skill `android-gradle-logic`).

## `AuthRepository` API

```kotlin
interface AuthRepository {
    val authState: Flow<AuthUser?>                                            // null = signed out
    suspend fun signInWithGoogle(activityContext: Context): Result<AuthUser> // Activity context for the picker
    suspend fun signOut(): Result<Unit> // Firebase session + Credential Manager state; the app signs out via SignOutUseCase
}
```

## Sign-In Flow (as built)

- One button, one event: `AuthEvent.SignIn(context)`. Signed out → sign in. Signed in but
  `users/{uid}` not yet on the server (the bootstrap failed, e.g. offline) → `gate = NeedsUser`,
  and the same button retries `EnsureUserUseCase` without a new sign-in.
- `UserDirectory.createIfMissing` talks to the **server** only and is atomic: an existing
  document (from another device, or migrated data) is never overwritten. Offline → nothing is
  created (`Offline` after the timeout).
- `gate = Loading` until the user's `schemaVersion` is known – the app is never shown before
  "Uppdatera appen" could apply; `gate = UpdateRequired` when it is newer than the app
  (`UpdateRequiredScreen`, BCK-15).
- Errors arrive already mapped; `Cancelled` is silent (AUTH-4), anything else is shown once via
  `AppSnackbarHost` and cleared with `AuthEvent.ErrorShown`.
- First start of 4.0 on a device with 3.x data: sign-in comes first, then the migration screen
  (OMB-2, OMB-5) writes the old data under the new `users/{uid}`.

## Sign-Out

`SignOutUseCase` in `data/auth/` (settings sheet → Konto, behind `ConfirmDialog`; called by
`AuthViewModel`) is the only sign-out path (AUTH-2, AUTH-6):

1. `LocalCacheCleaner.awaitPendingWrites(3 s)` – writes not yet on the server live only in the
   cache, and after sign-out the server rejects them.
2. `AuthRepository.signOut()` – Firebase session, then Credential Manager state.
3. Only if step 1 confirmed everything synced: `LocalCacheCleaner.clear()` – `FirestoreInstance`
   runs `terminate()` + `clearPersistence()` and hands out a fresh, empty instance on next use, so
   the next account starts with an empty cache. Unsynced writes keep the cache (data safety, rule 1);
   Firestore syncs them when the same account signs in again.

`UserSession` follows `authState` to `null`, so the collections show nothing and the next
account never sees the previous one's diary. Never call `AuthRepository.signOut()` directly
from UI code. Tests: `SignOutUseCaseTest` and `AuthViewModelTest` with `FakeLocalCacheCleaner`
(`testing/UserFixture.kt`); `FirestoreLocalCacheTest` against the emulator.
Reminders read the schedule through the same repositories and therefore follow the signed-in
user (skill `notifications-alarms`).

## google-services.json Requirements

For Google Sign-In to work, `app/google-services.json` must contain:
- `client_type: 1` — Android OAuth client (requires the app's SHA-1 fingerprint registered in Firebase/Google Cloud)
- `client_type: 3` — Web OAuth client (generates `R.string.default_web_client_id`)

The file is **checked in** in Dagboken (project `dagboken-711d2`; client config, not a secret). The
debug SHA-1 for this project: `50:5B:DC:3B:C9:49:F5:96:91:84:F2:23:0F:D1:BE:25:1B:10:7E:59`. The
release keystore's SHA-1 must be registered too (README → Firebase-setup).

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

## Background work and receivers

Workers and alarm receivers check the signed-in user before touching data; signed out they do
nothing (no backup worker exists in 4.0 – backup runs in GitHub Actions, BCK-12).

## Error Categories

`AuthRepository` maps every exception to `DataError` once, with `suspendRunCatching` (in
`data/common/`), exactly like `FirestoreCollection` does for data:

| Exception | Meaning | `DataError` | Handle |
|---|---|---|---|
| `GetCredentialCancellationException` | User dismissed picker | `Cancelled` | Silent — not an error (AUTH-4) |
| `GetCredentialException` | No Google accounts on device, API not available | `Unknown` | Show error |
| `FirebaseAuthException` | Firebase rejected the token | `SignInRejected` | Show error; log only the exception class, never email/uid (skill `data-privacy-security`) |
| `FirebaseNetworkException`, `IOException` | No network | `Offline` | Show error |

## Dependencies

```toml
# libs.versions.toml
firebase-auth = { group = "com.google.firebase", name = "firebase-auth" }  # version from Firebase BOM
credentials = { group = "androidx.credentials", name = "credentials", version.ref = "credentialManager" }
credentials-play-services = { group = "androidx.credentials", name = "credentials-play-services-auth", version.ref = "credentialManager" }
googleid = { group = "com.google.android.libraries.identity.googleid", name = "googleid", version.ref = "googleIdentity" }
```
