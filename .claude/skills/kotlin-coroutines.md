---
name: kotlin-coroutines
description: Use when writing, reviewing, or debugging coroutine code in Kotlin — including dispatcher selection, scope management, structured concurrency, cancellation, exception handling, or async patterns in Android or KMP projects.
---

# Kotlin Coroutines

## Overview

Kotlin coroutines are built on **structured concurrency**: every coroutine runs within a scope, and cancellation/errors propagate through the parent-child hierarchy automatically.

**Core principle:** Suspend functions must always be main-safe. The function doing blocking work owns the `withContext` call — callers should never need to switch dispatchers.

## Diagnosing Coroutine Issues

| Symptom | Likely Cause | Fix |
|---------|-------------|-----|
| ANR / UI freeze | Blocking call on main thread | `withContext(Dispatchers.IO)` inside suspend fun |
| Memory leak / zombie coroutine | `GlobalScope` or unbound scope | Replace with `viewModelScope`, `lifecycleScope`, or injected scope |
| Cancellation silently broken | `catch (e: Exception)` swallows `CancellationException` | Catch specific types; rethrow `CancellationException` |
| Non-cancellable tight loop | No cancellation checkpoint | Add `ensureActive()` at loop start |
| Hard to test dispatchers | Hardcoded `Dispatchers.IO` | Inject `CoroutineDispatcher` via constructor |

## Dispatcher Selection

| Dispatcher | Use for |
|---|---|
| `Dispatchers.Main` | UI updates only |
| `Dispatchers.IO` | Blocking I/O: network, disk, database |
| `Dispatchers.Default` | CPU-intensive: parsing, sorting, computation |
| `Dispatchers.Unconfined` | Never use in production |

**Rule: Inject dispatchers — never hardcode them.**

## Main-Safety Rule

Every suspend function must be callable from the main thread. The class doing blocking work owns the `withContext` — callers must never switch dispatchers before calling a suspend function.

```kotlin
// DO: self-contained, main-safe
class NewsRepository(private val ioDispatcher: CoroutineDispatcher) {
    suspend fun fetchLatestNews(): List<Article> = withContext(ioDispatcher) {
        // blocking HTTP call here — caller does not need to know
    }
}

// DO NOT: push dispatcher responsibility to caller
class GetLatestNewsUseCase(private val repository: NewsRepository) {
    suspend operator fun invoke() = withContext(Dispatchers.IO) {
        repository.fetchLatestNews() // repository was not main-safe
    }
}
```

## Scope Management

| Scope | Lifetime | Use for |
|---|---|---|
| `viewModelScope` | ViewModel cleared | Business logic coroutines in ViewModels |
| `lifecycleScope` | Lifecycle destroyed | UI coroutines |
| `coroutineScope` | All children complete | Screen-bound work; one failure cancels all |
| `supervisorScope` | All children complete | Isolated child failures |

**Rule: Never use `GlobalScope`.** It creates unstructured, untestable, leak-prone coroutines.

### Prefer `suspend fun`, let the caller own the scope

A stored `CoroutineScope` on a non-UI class (repository, manager, use case) is a strong review signal. The fix is almost always: **make the API `suspend` and let the caller own the scope.**

## Structured Concurrency

```kotlin
// Parallel work — both fail together
suspend fun getBookAndAuthors(): BookAndAuthors = coroutineScope {
    val books = async { booksRepository.getAllBooks() }
    val authors = async { authorsRepository.getAllAuthors() }
    BookAndAuthors(books.await(), authors.await())
}

// Parallel work — failures are independent
suspend fun loadDashboard() = supervisorScope {
    launch { loadNews() }
    launch { loadWeather() }
}
```

## Cancellation

Cancellation is cooperative — coroutines must check for it explicitly in long operations.

```kotlin
launch {
    for (file in files) {
        ensureActive() // throws CancellationException if job is cancelled
        readFile(file)
    }
}
```

**Cleanup that must survive cancellation** — use `withContext(NonCancellable)`:
```kotlin
launch {
    try {
        doWork()
    } finally {
        withContext(NonCancellable) {
            db.saveCheckpoint() // suspend call safe here
        }
    }
}
```

## Exception Handling

```kotlin
// DO: catch specific exception types
viewModelScope.launch {
    try {
        loginRepository.login(username, token)
    } catch (e: IOException) {
        _uiState.value = UiState.Error("Network error")
    }
}

// DO NOT: catch Exception or Throwable — swallows CancellationException
viewModelScope.launch {
    try {
        loginRepository.login(username, token)
    } catch (e: Exception) { } // NEVER do this
}
```

### `suspendRunCatching`

`runCatching` catches all `Throwable` including `CancellationException`, silently breaking structured concurrency. Use this utility instead:

```kotlin
suspend inline fun <R> suspendRunCatching(block: () -> R): Result<R> = try {
    Result.success(block())
} catch (e: CancellationException) {
    throw e
} catch (e: Throwable) {
    Result.failure(e)
}
```

## Callback Bridging

```kotlin
// Single-value callbacks → suspend function
suspend fun authenticate(token: String): User = suspendCancellableCoroutine { continuation ->
    val call = authApi.authenticate(token) { user, error ->
        if (user != null) continuation.resume(user)
        else continuation.resumeWithException(error ?: Exception("Unknown error"))
    }
    continuation.invokeOnCancellation { call.cancel() }
}

// Stream callbacks → Flow (callbackFlow)
fun locationUpdates(): Flow<Location> = callbackFlow {
    val listener = LocationListener { location -> trySend(location) }
    locationManager.requestLocationUpdates(listener)
    awaitClose { locationManager.removeUpdates(listener) } // CRITICAL
}
```

## Android-Specific Rules

**ViewModel coroutine ownership:**

```kotlin
// DO: ViewModel creates coroutines, exposes immutable StateFlow
class LatestNewsViewModel(private val getLatestNews: GetLatestNewsUseCase) : ViewModel() {
    private val _uiState = MutableStateFlow<NewsUiState>(NewsUiState.Loading)
    val uiState: StateFlow<NewsUiState> = _uiState

    fun loadNews() {
        viewModelScope.launch {
            try {
                _uiState.value = NewsUiState.Success(getLatestNews())
            } catch (e: IOException) {
                _uiState.value = NewsUiState.Error
            }
        }
    }
}
```

**Lifecycle safety:**
- `lifecycleScope` + `repeatOnLifecycle` for flow collection in non-Compose UI
- Never launch coroutines in `onStart`/`onResume` without matching cancellation in `onStop`/`onPause`

## Common Pitfalls

| Pitfall | Fix |
|---|---|
| `catch (e: CancellationException) {}` | Rethrow: `catch (e: CancellationException) { throw e }` |
| `catch (e: Exception)` or `catch (e: Throwable)` | Catch specific types only |
| `GlobalScope.launch {}` | Inject a `CoroutineScope` instead |
| Hardcoded `Dispatchers.IO` in production | Inject via constructor |
| `try/catch` around `launch {}` | Put try/catch inside the coroutine body |
| No cancellation check in long loop | Add `ensureActive()` at start of each iteration |
| `runCatching {}` in suspend functions | Use `suspendRunCatching` instead |
