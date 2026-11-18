package se.kodhast.dagboken.testing

import android.content.Context
import kotlin.time.Duration
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.datetime.TimeZone
import se.kodhast.dagboken.core.schema.Doc
import se.kodhast.dagboken.core.schema.Schema
import se.kodhast.dagboken.data.FakeStore
import se.kodhast.dagboken.data.FixedClock
import se.kodhast.dagboken.data.auth.AuthRepository
import se.kodhast.dagboken.data.auth.AuthUser
import se.kodhast.dagboken.data.auth.ProfileCheckUseCase
import se.kodhast.dagboken.data.auth.SignOutUseCase
import se.kodhast.dagboken.data.common.DailyGate
import se.kodhast.dagboken.data.common.DataError
import se.kodhast.dagboken.data.common.LocalCacheCleaner
import se.kodhast.dagboken.data.common.MemoryDayMarks
import se.kodhast.dagboken.data.common.UserVersionSource
import se.kodhast.dagboken.data.common.testCoverage
import se.kodhast.dagboken.data.firestore.Paths
import se.kodhast.dagboken.data.user.EnsureUserUseCase
import se.kodhast.dagboken.data.user.UserDirectory
import se.kodhast.dagboken.data.user.UserSession
import se.kodhast.ui.account.NetworkStatus
import se.kodhast.ui.account.ProfilePhotoCache

/**
 * Användarkedjan för tester: riktig [UserSession] och [EnsureUserUseCase], fejkad inloggning
 * ([FakeAuthRepository]) och fejkad Firestore ([FakeStore]). Versionen läses ur det lagrade
 * användardokumentet `users/{uid}`.
 */
class UserFixture(scope: CoroutineScope, val auth: FakeAuthRepository = FakeAuthRepository()) {
    val store = FakeStore()
    val clock = FixedClock()

    /** `false` = versionen går inte att läsa just nu (som när servern inte svarat). */
    val versionsKnown = MutableStateFlow(true)

    /** Användare vars dokument servern nekar. */
    val denied = MutableStateFlow(emptySet<String>())

    /** Stämplingar som sessionen gjort: (uid, version). */
    val stamps = mutableListOf<Pair<String, Int>>()

    private val versions = object : UserVersionSource {
        override fun schemaVersion(uid: String): Flow<Int?> = combine(store.documents, versionsKnown, denied) { all, known, denied ->
            if (uid in denied) throw DataError.PermissionDenied
            known to all[Paths.USERS]?.get(uid)?.let { Schema.versionOf(it[EnsureUserUseCase.SCHEMA_VERSION]) }
        }.filter { (known, _) -> known }.map { (_, version) -> version }

        override fun stamp(uid: String, version: Int) {
            stamps += uid to version
            if (store.read(Paths.USERS, uid) != null) store.set(Paths.USERS, uid, mapOf(EnsureUserUseCase.SCHEMA_VERSION to version), merge = true)
        }
    }

    val session = UserSession(auth, versions, scope)
    val directory = FakeUserDirectory(store)
    val ensureUser = EnsureUserUseCase(directory, clock)
    val cache = FakeLocalCacheCleaner(auth)

    /** Enhetens anteckningar – historiklägets täckning (NFR-24) ligger här och glöms före tömningen. */
    val marks = MemoryDayMarks()
    val photos = FakeProfilePhotoCache(auth)
    val signOut = SignOutUseCase(auth, cache, testCoverage(marks, clock), photos)

    /** Om appen har internet – profilkontrollen (AUTH-3) väntar på den. */
    val network = FakeNetworkStatus()
    val profileCheck = ProfileCheckUseCase(auth, session, network, DailyGate(marks, session), clock, TimeZone.UTC)

    /** Lägger ett användardokument direkt i den fejkade databasen, som om en annan enhet skapat det. */
    fun storeUser(uid: String, doc: Doc = mapOf(EnsureUserUseCase.SCHEMA_VERSION to Schema.CURRENT_VERSION)) = store.set(Paths.USERS, uid, doc, merge = false)

    fun user(uid: String): Doc? = store.read(Paths.USERS, uid)
}

/**
 * Som servern: skapar `users/{uid}` bara om det saknas. [failure] simulerar t.ex. att nätet
 * saknas; [hold] låter anropet vänta tills testet släpper det.
 */
class FakeUserDirectory(private val store: FakeStore) : UserDirectory {
    var failure: DataError? = null
    var hold: CompletableDeferred<Unit>? = null
    var calls = 0
        private set

    override suspend fun createIfMissing(uid: String, initial: Doc): Result<Doc> {
        calls++
        hold?.await()
        failure?.let { return Result.failure(it) }
        store.read(Paths.USERS, uid)?.let { return Result.success(it) }
        store.set(Paths.USERS, uid, initial, merge = false)
        return Result.success(initial)
    }
}

/** Inloggning utan Google: [nextSignIn] bestämmer utfallet, lyckad inloggning sätter användaren. */
class FakeAuthRepository(initial: AuthUser? = null) : AuthRepository {
    override val authState = MutableStateFlow(initial)
    var nextSignIn: Result<AuthUser> = Result.success(AuthUser("uid-anna"))
    var signInCalls = 0
        private set

    override suspend fun signInWithGoogle(activityContext: Context): Result<AuthUser> {
        signInCalls++
        return nextSignIn.onSuccess { authState.value = it }
    }

    var nextSignOut: Result<Unit> = Result.success(Unit)
    var signOutCalls = 0
        private set

    override suspend fun signOut(): Result<Unit> {
        signOutCalls++
        return nextSignOut.onSuccess { authState.value = null }
    }

    var nextReload: Result<Unit> = Result.success(Unit)
    var reloadCalls = 0
        private set

    override suspend fun reload(): Result<Unit> {
        reloadCalls++
        return nextReload
    }
}

/** Nätet i tester: [online] sätts av testet. */
class FakeNetworkStatus(online: Boolean = false) : NetworkStatus {
    override val online = MutableStateFlow(online)
}

/** Profilfotots cache: varje tömning loggas med om någon då var inloggad; [nextClear] styr utfallet. */
class FakeProfilePhotoCache(private val auth: FakeAuthRepository) : ProfilePhotoCache {
    var nextClear: Result<Unit> = Result.success(Unit)
    val calls = mutableListOf<String>()

    override suspend fun clear(): Result<Unit> {
        calls += if (auth.authState.value == null) "clear utloggad" else "clear inloggad"
        return nextClear
    }
}

/**
 * Cachen utan Firestore: [synced] = `false` som när skrivningar väntar utan nät, [nextClear] styr
 * utfallet. Varje anrop loggas i [calls] tillsammans med om någon då var inloggad, så att testerna
 * ser ordningen mot utloggningen.
 */
class FakeLocalCacheCleaner(private val auth: FakeAuthRepository) : LocalCacheCleaner {
    var synced = true
    var nextClear: Result<Unit> = Result.success(Unit)
    val calls = mutableListOf<String>()
    val clearCalls get() = calls.count { it.startsWith("clear") }

    override suspend fun awaitPendingWrites(timeout: Duration): Boolean {
        calls += "await" + state()
        return synced
    }

    override suspend fun clear(): Result<Unit> {
        calls += "clear" + state()
        return nextClear
    }

    private fun state() = if (auth.authState.value == null) " utloggad" else " inloggad"
}
