---
name: android-dev
description: Use this skill as the baseline for ALL Android and Kotlin Multiplatform (KMP) work — whenever the user mentions Android, Kotlin (in an Android context), KMP, CMP, commonMain, androidMain, iosMain, AndroidManifest, Gradle, build.gradle, Hilt, Dagger, Firestore, Retrofit, Ktor, ViewModel, LiveData, StateFlow, SharedFlow, Compose, Activity, Fragment, Intent, ADB, Logcat, MVVM, MVI, repository pattern, or any Android SDK / Jetpack / AndroidX API. Always load this skill alongside more specific skills (android-skills:compose, android-skills:kotlin-flows, android-skills:kmp-ktor, android-skills:android-retrofit, etc.) — it provides the architectural baseline, existing-pattern audit, and project-adaptability rules those skills defer to. Casual mentions like "fix this bug in my Android app," "refactor this ViewModel," "my KMP project," or any work inside an Android project directory should trigger this skill. In Dagboken this includes any work on Idag, Dagbok, Trender, Mediciner, mående, doser, recept, sjukdomar, Health Connect, påminnelser, Firestore, `:core`, the 3.x migration or Navigation 3.
---

# Senior Android Development Skills

You are a senior Android engineer. Apply the following guidelines to all Android and KMP work.

## Architecture

- Use clean architecture with repository pattern for data persistence.
- **MVVM** with `StateFlow<UiState>` and sealed events (CLAUDE.md, ARKITEKTUR.md) – do not ask or switch to MVI.
- Use Compose for all new UI. For legacy interop use `AndroidView` / `ComposeView`.
- Use `collectAsStateWithLifecycle` to observe state from ViewModels in composables.
- Use `StateFlow` / `State` to manage UI state.
- Use Material 3 for the UI (in this project: Material 3 Expressive via `ui/theme` – see skill `ui-style`).
- Use Hilt for DI with KSP.
- Use Coil for image loading.
- Use `kotlinx.serialization` for network model serialization.

**This project (Dagboken 4.0):**
- Cloud Firestore with offline persistence is the primary storage under `users/{uid}` (ADR-001); Room is **not** used except by the read-only legacy reader for the 3.x migration (`data/legacy`). All Firestore access goes through `FirestoreCollection<T>` + `DocCodec<T>` (skill `firestore-data-layer`).
- Pure domain logic (models, codecs, engines, chart math, the 3.x converter) lives in the `:core` JVM module without Android dependencies.

## Existing-pattern check (before designing new mechanisms)

Before adding any new mechanism — events, flows, navigation triggers, or state shape — in an existing project, check how the surrounding code already handles it.

### Audit procedure

Open a sibling ViewModel in the same feature module — or `Grep` the feature for the terms below — **before writing any new code**:

| Concern | What to look for |
|---|---|
| How actions reach the ViewModel | Sealed `Event` / `Intent` / `Action` interface, `onEvent()` dispatcher, and a Handler interface |
| How one-shot effects are emitted | Existing `SharedFlow` / `Channel` of sealed effect classes |
| How navigation is triggered | Nav callbacks, `NavController` (Nav 2) or `NavDisplay` (Nav 3) use, or navigation effects |
| How the ViewModel exposes new behaviour | Event class entries + handler methods, or direct `fun` |
| How state is structured | `UiState` sealed classes, `StateFlow<State>` shape, field granularity |

### Red flags

- **Adding a public `fun foo()` on the ViewModel** when the project has a sealed `Event` + `onEvent()` pattern → add an Event data object and a handler method instead.
- **Creating a new `SharedFlow` or `Channel`** → check whether an existing effects stream already carries this kind of signal.
- **New composable parameter that bypasses existing state/event wiring** → look at how sibling screens wire the same ViewModel.

### Rule

**If the project has an established pattern for X, use it — even if a simpler direct approach would also work.** Simplicity is not a valid reason to diverge from the architecture.

## State and Events

### Where state lives

| Scope | Owner | When |
|---|---|---|
| Single composable | `remember { mutableStateOf(...) }` | Transient UI state that resets on screen leave |
| Single composable, survives rotation | `rememberSaveable { mutableStateOf(...) }` | UI-local state that needs to outlive config change |
| Survives recomposition AND config change | `ViewModel` exposing `StateFlow<UiState>` | App state — anything the user can return to |
| Survives process death | `ViewModel.savedStateHandle` or persistence layer | User-input drafts |

### Effects: `Channel(BUFFERED)` vs `SharedFlow(replay = 0)`

For one-shot UI effects from a ViewModel (snack messages, navigation triggers, haptic feedback):

| Primitive | When |
|---|---|
| `Channel<Effect>(BUFFERED).receiveAsFlow()` | Effect must not be missed (navigation, payment outcome) |
| `SharedFlow<Effect>(replay = 0)` | Effect can be missed if UI is inactive (transient haptic, analytics-only) |

## Compose

- Hoist state to the lowest common ancestor — composables receive state and emit events upward.
- Screen-level composables connect to the ViewModel; child composables are stateless.
- For Compose specifics (stability, `remember`, Modifiers, side effects, navigation), defer to the `compose-expert` skill.

## Async & Concurrency

- Use Kotlin Coroutines and Flow for all async work. No `LiveData` in new code.
- `viewModelScope` for ViewModel coroutines; inject `CoroutineDispatcher` for testability.
- Expose `StateFlow` for UI state, `Flow` for streams, suspend functions for one-shot calls.
- Defer to `kotlin-coroutines` and `kotlin-flows` skills for operator-level details.

## Gradle

- Use version catalogs (`libs.versions.toml`) and Kotlin script (`.kts`) for all Gradle files.
- This project targets Java 17 (no toolchain, same as CI). Shared build settings live in one place (skill `android-gradle-logic`).
- Keep ProGuard/R8 rules updated when adding libraries.

## Package Structure

`:core` (pure Kotlin/JVM) + `:app`. The package tree is defined **only** in `ARKITEKTUR.md`
→ "Lager och moduler"; read it there. Key rule: look and shared behaviour live only in
`ui/theme`, `ui/components` and `ui/common`; Firestore only in `data/firestore`; errors are
mapped once in `data/common`.

## Data Flow

Compose → ViewModel → Repository → Data sources

- Repository lives in the `data` layer.
- ViewModel lives in the `ui/<feature>` layer.
- Data models are mapped to UI models inside ViewModels.
- UI models contain only what the screen needs to display.

## Error Handling

- Model success/error with sealed classes/interfaces (`Result<T>`).
- UI state must explicitly represent loading, success, and error.
- Never swallow exceptions silently in repositories or data sources.

**Error propagation by layer:**

1. **Platform APIs** (Firestore, Credential Manager, Firebase Auth, Health Connect) throw their own exceptions.
2. **The single access point maps them once** to `DataError` via `suspendRunCatching` (both in `data/common/`): `FirestoreCollection` for all Firestore access, `AuthRepository` for sign-in. Repositories built on `FirestoreCollection` do **not** catch or remap again – they pass `Result<T>` through.
3. **ViewModels** handle `Result<T>` and put the `DataError` in UI state; screens show it with `DataError.toMessage()`. `DataError.Cancelled` is silent.

## Navigation

- This project uses **Navigation 3** (TP-2): `@Serializable` `NavKey` objects, one back stack per tab (`AppBackStack`), `NavDisplay` with `entryProvider`, ViewModel scoping via `lifecycle-viewmodel-navigation3`. Navigation 2 (`navigation-compose`/`NavController`, the 3.x string routes) is not used.
- Single Activity host (`MainActivity`). The back stack is plain state in `navigation/` — never mutated from a ViewModel directly.
- Screen transitions are defined once in `navigation/Transitions`.
- For one-time navigation/UI events from the ViewModel, use `Channel` + `receiveAsFlow()` for exactly-once delivery.

## Background Work

- Use **WorkManager** for deferrable background tasks that must survive process death (TP-7). There is no backup worker in 4.0: Firestore syncs itself and the weekly backup runs in GitHub Actions (`tools/db`, BCK-12). Reminders use AlarmManager (skill `notifications-alarms`).
- Use `CoroutineWorker` for suspend-friendly workers.
- Constrain work with `Constraints` (network, charging) rather than implementing retry logic manually.
- With Hilt: use `@HiltWorker` + `@AssistedInject`. App must implement `Configuration.Provider` with injected `HiltWorkerFactory`. AndroidManifest must disable default `WorkManagerInitializer`.

## This Project's Conventions

- **Rules first:** the five non-negotiable rules in `CLAUDE.md` override anything generic here.
- **Architecture**: MVVM with `StateFlow<UiState>` and sealed events — each screen has a `*ViewModel` + `*UiState`.
- **DI**: Hilt, `@HiltViewModel` on all ViewModels.
- **State exposure**: `private val _state = MutableStateFlow(...)`, `val state = _state.asStateFlow()`; `_state.update { it.copy(...) }`.
- **Shared frames (rule 4)**: list screens use `EntityListScreen` + `asListUiState()`, edit screens use `EntityEditScreen` + `EditorState<T>`. Never hand-roll these.
- **Auth**: `AuthRepository` (`GoogleAuthRepository`) wraps Firebase Auth + Credential Manager — never call Firebase directly from UI; sign-in is required (AUTH-6).
- **Data**: repositories are thin facades over `FirestoreCollection<T>` under `users/{uid}` (`UserSession`); errors are mapped to `DataError` once.
- **Backup**: Firestore is the cloud copy; weekly encrypted `tools/db` export in GitHub Actions and manual JSON export in the app (BCK-11–13). The 3.x Drive backup (`DriveBackupRepository`, `BackupWorker`) is retired; Drive is only read by the legacy import (BCK-14).
- **Tests**: JVM first (JUnit, Turbine, Robolectric, Roborazzi); fakes built on `FakeCollection<T>`.

## Adaptability

- Always respect the project's established architecture and conventions first.
- If existing code contradicts these guidelines, flag the inconsistency and ask how to proceed — never silently override.
