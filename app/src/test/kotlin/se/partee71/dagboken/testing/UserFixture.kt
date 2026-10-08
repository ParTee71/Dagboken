package se.partee71.dagboken.testing

import android.content.Context
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlin.time.Duration
import se.partee71.dagboken.core.schema.Doc
import se.partee71.dagboken.core.schema.Schema
import se.partee71.dagboken.data.FakeStore
import se.partee71.dagboken.data.FixedClock
import se.partee71.dagboken.data.auth.AuthRepository
import se.partee71.dagboken.data.auth.AuthUser
import se.partee71.dagboken.data.auth.SignOutUseCase
import se.partee71.dagboken.data.common.DataError
import se.partee71.dagboken.data.common.LocalCacheCleaner
import se.partee71.dagboken.data.common.UserVersionSource
import se.partee71.dagboken.data.firestore.Paths
import se.partee71.dagboken.data.user.EnsureUserUseCase
import se.partee71.dagboken.data.user.UserDirectory
import se.partee71.dagboken.data.user.UserSession

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
        override fun schemaVersion(uid: String): Flow<Int?> =
            combine(store.documents, versionsKnown, denied) { all, known, denied ->
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
    val signOut = SignOutUseCase(auth, cache)

    /** Lägger ett användardokument direkt i den fejkade databasen, som om en annan enhet skapat det. */
    fun storeUser(uid: String, doc: Doc = mapOf(EnsureUserUseCase.SCHEMA_VERSION to Schema.CURRENT_VERSION)) =
        store.set(Paths.USERS, uid, doc, merge = false)

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
    override val currentUid: String? get() = authState.value?.uid
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
