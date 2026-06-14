---
name: android-data-layer
description: Use when implementing the data layer in Android — Repository pattern, Room local database, offline-first synchronization, and coordinating local and remote sources.
---

# Android Data Layer

The data layer coordinates data from multiple sources. Its public API to the rest of the app is repository interfaces; its internal implementation details (DAOs, API services, DTOs) never leak upward.

**Related skills:** See `android-dev` for how the data layer fits into the overall architecture and error propagation model.

## Repository Pattern

The repository is the **single source of truth**. It decides whether to serve cached data or fetch fresh data, and maps raw data-layer types to domain models.

```kotlin
class NewsRepository @Inject constructor(
    private val newsDao: NewsDao,
    private val newsApi: NewsApi,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) {
    // Room DAO as the source of truth — UI always reads from local DB
    val newsStream: Flow<List<News>> = newsDao.getAllNews()

    // Triggered by UI or WorkManager to refresh data
    suspend fun refreshNews(): Result<Unit> = withContext(ioDispatcher) {
        try {
            val remoteNews = newsApi.fetchLatest()
            newsDao.insertAll(remoteNews.map { it.toDomain() })
            Result.success(Unit)
        } catch (e: IOException) {
            Result.failure(DataError.Network(e))
        } catch (e: HttpException) {
            Result.failure(DataError.Server(e.code(), e.message()))
        }
    }
}

sealed class DataError(message: String, cause: Throwable? = null) : Exception(message, cause) {
    class Network(cause: Throwable) : DataError("Network error", cause)
    class Server(val code: Int, message: String?) : DataError("Server error $code: $message")
    class Local(cause: Throwable) : DataError("Local storage error", cause)
}
```

## Room — Local Database

### Entity

```kotlin
@Entity(tableName = "articles")
data class ArticleEntity(
    @PrimaryKey val id: String,
    val title: String,
    val body: String,
    val publishedAt: Long
)
```

### DAO

Return `Flow<T>` for observable queries; `suspend fun` for one-shot reads and writes.

```kotlin
@Dao
interface ArticleDao {
    @Query("SELECT * FROM articles ORDER BY publishedAt DESC")
    fun observeAll(): Flow<List<ArticleEntity>>

    @Query("SELECT * FROM articles WHERE id = :id")
    suspend fun findById(id: String): ArticleEntity?

    @Upsert
    suspend fun upsertAll(articles: List<ArticleEntity>)

    @Query("DELETE FROM articles")
    suspend fun deleteAll()
}
```

### Database

```kotlin
@Database(entities = [ArticleEntity::class], version = 1, exportSchema = true)
abstract class AppDatabase : RoomDatabase() {
    abstract fun articleDao(): ArticleDao
}
```

Provide as a singleton via Hilt and export the schema for migration history tracking.

## Offline-First Strategies

### Read — Stale-While-Revalidate

Show local data immediately; trigger a background refresh in parallel.

```kotlin
fun loadNews() {
    viewModelScope.launch {
        repository.newsStream.collect { articles ->
            _uiState.update { it.copy(articles = articles) }
        }
    }
    viewModelScope.launch {
        repository.refreshNews().onFailure { error ->
            _uiState.update { it.copy(error = error.message) }
        }
    }
}
```

### Write — Outbox Pattern

Save changes locally first, then sync to the server via WorkManager.

## Model Mapping

Keep three distinct model types and map between them at layer boundaries:

| Layer | Model Type | Purpose |
|-------|-----------|---------|
| Network | DTO (`ArticleDto`) | Matches API JSON structure |
| Database | Entity (`ArticleEntity`) | Matches Room table schema |
| Domain/UI | Domain model (`Article`) | What the rest of the app uses |

```kotlin
fun ArticleDto.toEntity(): ArticleEntity = ArticleEntity(id = id, title = title, body = body, publishedAt = publishedAt)
fun ArticleEntity.toDomain(): Article = Article(id = id, title = title, body = body, publishedAt = Instant.ofEpochMilli(publishedAt))
```

## DAO Return Types — RIGHT vs WRONG

```kotlin
// WRONG — suspend fun when the UI needs to observe ongoing changes
@Dao
interface ArticleDao {
    @Query("SELECT * FROM articles")
    suspend fun getAll(): List<ArticleEntity> // caller must re-query manually to see new inserts
}

// RIGHT — Flow for queries the UI observes; suspend for one-shot reads and mutations
@Dao
interface ArticleDao {
    @Query("SELECT * FROM articles ORDER BY publishedAt DESC")
    fun observeAll(): Flow<List<ArticleEntity>> // emits whenever table changes

    @Query("SELECT * FROM articles WHERE id = :id")
    suspend fun findById(id: String): ArticleEntity? // one-shot lookup

    @Upsert
    suspend fun upsertAll(articles: List<ArticleEntity>) // mutation
}
```

## Error Boundary Placement

```kotlin
// WRONG — IOException and HttpException escape to the ViewModel
class ArticleRepository(private val api: NewsApi) {
    suspend fun refreshArticles() { // throws IOException, HttpException
        api.fetchLatest()
    }
}

// RIGHT — repository catches and maps to domain error types
class ArticleRepository(private val api: NewsApi) {
    suspend fun refreshArticles(): Result<Unit> = try {
        api.fetchLatest()
        Result.success(Unit)
    } catch (e: IOException) {
        Result.failure(DataError.Network(e))
    } catch (e: HttpException) {
        Result.failure(DataError.Server(e.code(), e.message()))
    }
}
```

## This Project's Repositories

- **`AktiviteterRepository`** — Room-backed, exposes `all: Flow<List<Aktivitet>>`, `todayFlow()`, etc.
- **`MedicinerRepository`** — Room-backed, exposes `allMediciner`, `allRecept`, `allFavoriter` flows
- **`PreferencesRepository`** — DataStore-backed, exposes `isDarkTheme`, `dynamicColor`, `aktivitetOptions`, `symptomOptions` flows
- **`FirebaseAuthRepository`** — Firebase Auth + Credential Manager, `authStateFlow: Flow<FirebaseUser?>`
- **`DriveBackupRepository`** — Google Drive APPDATA via OAuth 2.0, `uploadBackup()`, `listBackups()`, `downloadBackup()`

## Checklist

- [ ] Repository exposes `Flow` for streams and `suspend fun` returning `Result<T>` for one-shot operations
- [ ] Raw DTOs, entities, and HTTP/IO exceptions never reach the ViewModel
- [ ] Room DAOs return `Flow` for observed queries; `suspend` for mutations
- [ ] Schema exported (`exportSchema = true`) and migration scripts provided for version bumps
- [ ] Offline-first: local DB is the source of truth
- [ ] WorkManager used for sync operations that must survive process death
