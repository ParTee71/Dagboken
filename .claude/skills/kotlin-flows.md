---
name: kotlin-flows
description: Use when working with Flow, StateFlow, SharedFlow, or Channel in Kotlin — including cold vs hot stream decisions, operator chains, lifecycle-safe collection, UI state management, callback bridging, or Channel migration in Android or KMP projects.
---

# Kotlin Flows

## Overview

Kotlin Flow is a cold, sequential stream built on coroutines. `StateFlow` and `SharedFlow` are hot variants for state and events. Choosing the right type, collecting safely, and never exposing mutable types are the core concerns here.

## Choosing the Right Type

| Type | Hot/Cold | Retains state | Use for |
|---|---|---|---|
| `Flow` | Cold | No | One-off streams, repository data |
| `StateFlow` | Hot | Yes (last value) | UI state |
| `Channel(BUFFERED).receiveAsFlow()` | Hot | No (queued until consumed) | **Single-consumer fire-once events: nav, snackbars, one-shot effects** |
| `SharedFlow` | Hot | Configurable | Multi-collector broadcast where missed events are acceptable |

- Representing current state that new collectors need immediately? → `StateFlow`
- Single-consumer fire-once event that must not be missed? → `Channel(BUFFERED).receiveAsFlow()`
- Broadcasting to multiple collectors? → `SharedFlow`
- Simple data stream from one source? → `Flow`

## Channel for One-Shot Events (Default)

```kotlin
private val _events = Channel<UiEvent>(Channel.BUFFERED)
val events: Flow<UiEvent> = _events.receiveAsFlow()

fun onItemClick(id: String) {
    viewModelScope.launch {
        _events.send(UiEvent.NavigateToDetail(id))
    }
}

// Collect in composable — never collectAsStateWithLifecycle
LaunchedEffect(Unit) {
    viewModel.events.collect { event ->
        when (event) {
            is UiEvent.Navigate -> onNavigate(event.route)
            is UiEvent.Snackbar -> snackbarHostState.showSnackbar(event.message)
        }
    }
}
```

**`Channel.receiveAsFlow()` is fan-out, not broadcast.** With multiple collectors, each event reaches one collector only. Use `SharedFlow` when all collectors must see every event.

## Key Operators

| Goal | Operator |
|---|---|
| Transform each value | `map` |
| Filter values | `filter` |
| Side effects without transformation | `onEach` — never `map` for side effects |
| Cancel previous on new emission | `flatMapLatest` — search queries, user input |
| Process all concurrently | `flatMapMerge` |
| Process sequentially in order | `flatMapConcat` |
| Debounce rapid input | `debounce(ms)` |
| Skip duplicate consecutive values | `distinctUntilChanged()` |
| Change upstream execution context | `flowOn(dispatcher)` |
| Convert cold flow to hot StateFlow | `stateIn(scope, started, initialValue)` |
| Combine latest values from multiple flows | `combine(flowA, flowB) { a, b -> }` |

### `combine` warning

`combine` waits for **every** input to emit at least once before producing its first value. If one input is a cold flow that never emits, the combined flow never emits ("screen stuck on loading"). Make every input a `StateFlow` or give cold inputs `onStart { emit(initial) }`.

## StateFlow Patterns

**Never expose `MutableStateFlow` publicly.**

```kotlin
class NewsViewModel : ViewModel() {
    private val _uiState = MutableStateFlow<NewsUiState>(NewsUiState.Loading)
    val uiState: StateFlow<NewsUiState> = _uiState

    fun refresh() {
        viewModelScope.launch {
            _uiState.update { currentState -> currentState.copy(isRefreshing = true) }
        }
    }
}
```

Use `_uiState.update { }` for thread-safe atomic updates. The lambda can be retried on CAS contention — never put side effects inside it.

### `stateIn` vs `MutableStateFlow`

- **`stateIn`** — when a repository exposes a cold `Flow` and the ViewModel wants to expose it as `StateFlow`. The flow drives the state.
- **`MutableStateFlow`** — when the ViewModel drives state imperatively: loading results, reacting to user actions, combining multiple sources.

**`stateIn` sharing strategies:**
- `SharingStarted.WhileSubscribed(5_000)` — stops when no collectors, survives config changes; use in ViewModels
- `SharingStarted.Eagerly` — starts immediately, never stops

**Never call `stateIn(scope, ...)` inside a function** — creates a fresh shared coroutine on every call. Put it at property declaration.

## Error Handling in Flows

```kotlin
// .catch — intercepts exceptions from upstream only
repository.getItems()
    .catch { e -> emit(emptyList()) }
    .collect { items -> updateUi(items) }

// .catch does NOT cover collector errors
repository.getItems()
    .catch { e -> /* does not catch exceptions thrown inside collect */ }
    .collect { items ->
        riskyOperation(items) // exception here propagates to the coroutine scope
    }
```

## Lifecycle-Safe Collection (Android)

```kotlin
// DO: collectAsStateWithLifecycle in Compose (preferred)
@Composable
fun NewsScreen(viewModel: NewsViewModel) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
}

// DO: repeatOnLifecycle in non-Compose code
lifecycleScope.launch {
    repeatOnLifecycle(Lifecycle.State.STARTED) {
        viewModel.uiState.collect { state -> updateUi(state) }
    }
}

// DO NOT: collects even when app is in background
lifecycleScope.launch {
    viewModel.uiState.collect { state -> updateUi(state) }
}
```

**One-shot events: use `collect` inside `LaunchedEffect(Unit)` — never `collectAsStateWithLifecycle`.** The latter preserves the last emission as Compose state, re-consuming the event on recomposition.

## Callback Bridging

```kotlin
// Stream callbacks → callbackFlow
fun EditText.textChanges(): Flow<String> = callbackFlow {
    val watcher = object : TextWatcher {
        override fun afterTextChanged(s: Editable?) { trySend(s.toString()) }
        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
    }
    addTextChangedListener(watcher)
    awaitClose { removeTextChangedListener(watcher) } // CRITICAL — always clean up
}
```

**`awaitClose {}` is mandatory in `callbackFlow`.** Omitting it leaks the registered callback and prevents the flow from completing.

## Common Pitfalls

| Pitfall | Fix |
|---|---|
| `lifecycleScope.launch { flow.collect {} }` without `repeatOnLifecycle` | Wrap with `repeatOnLifecycle(Lifecycle.State.STARTED)` |
| Missing `awaitClose {}` in `callbackFlow` | Always add `awaitClose { unregister() }` |
| `MutableStateFlow` or `MutableSharedFlow` exposed publicly | Private mutable, public immutable |
| `_state.value = _state.value.copy(...)` in concurrent code | Use `_state.update { it.copy(...) }` |
| `SharingStarted.Eagerly` in ViewModel | Use `WhileSubscribed(5_000)` |
| `StateFlow` for one-shot events (replays on resubscription) | Use `Channel(BUFFERED).receiveAsFlow()` |
| `catch (e: Exception)` inside `collect {}` | Catches `CancellationException` — catch specific types |
| Side effects inside `combine`/`map` transforms | Move to `onEach` outside the transform |
| `collectAsStateWithLifecycle` for one-shot events | Use `LaunchedEffect(Unit) { vm.events.collect {} }` |
| Manual `Job?` cancellation + re-launch pattern | Use `flatMapLatest` |
| `stateIn(scope, ...)` inside a function | Move `stateIn` to property declaration |
| `.map` on `StateFlow` returns `Flow`, not `StateFlow` | Re-terminate with `.stateIn(...)` |
| Side effects inside `combine`/`map` → re-fires on rotation | Only pure transforms inside `combine`/`map` |

## RIGHT vs WRONG: Manual Job vs flatMapLatest

```kotlin
// WRONG — manual Job lifecycle management; error-prone, verbose
class SearchViewModel : ViewModel() {
    private var searchJob: Job? = null

    fun onQueryChanged(query: String) {
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            delay(300)
            _results.value = repository.search(query)
        }
    }
}

// RIGHT — flatMapLatest cancels previous collection automatically
class SearchViewModel : ViewModel() {
    private val query = MutableStateFlow("")

    val results = query
        .debounce(300)
        .flatMapLatest { q -> repository.searchFlow(q) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun onQueryChanged(q: String) { query.value = q }
}
```

## This Project's Flow Usage

- `authStateFlow: Flow<FirebaseUser?>` — `callbackFlow` + `FirebaseAuth.AuthStateListener` in `FirebaseAuthRepository`
- `combine()` in `HomeViewModel` to merge `todayFlow()` + `authStateFlow`
- `prefs.isDarkTheme`, `prefs.dynamicColor`, etc. — DataStore `Flow` collected with `collectLatest` in SettingsViewModel
- `AktiviteterRepository.all`, `MedicinerRepository.allMediciner` — Room DAO `Flow` properties
