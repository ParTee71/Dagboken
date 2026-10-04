package se.partee71.dagboken.data.user

import javax.inject.Inject
import kotlin.time.Clock
import kotlin.time.Instant
import se.partee71.dagboken.core.schema.Doc
import se.partee71.dagboken.core.schema.Schema
import se.partee71.dagboken.data.common.DataError
import se.partee71.dagboken.data.common.suspendRunCatching

/** Hur det gick att förbereda användaren efter inloggning. */
sealed interface UserStatus {
    data class Ready(val uid: String) : UserStatus

    /** Användarens data är sparad med en nyare version av appen. */
    data class RequiresUpdate(val uid: String) : UserStatus
}

/**
 * Efter inloggning (AUTH-1): ser till att `users/{uid}` finns på servern. Vid första inloggningen
 * skapas det med `schemaVersion` = [Schema.CURRENT_VERSION] och `createdAt`; ett befintligt
 * dokument lämnas orört och dess version avgör om appen får öppnas.
 */
class EnsureUserUseCase @Inject constructor(
    private val directory: UserDirectory,
    private val clock: Clock,
) {
    suspend operator fun invoke(uid: String): Result<UserStatus> =
        suspendRunCatching({ DataError.Unknown }) {
            val stored = directory.createIfMissing(uid, initialDocument(clock.now())).getOrThrow()
            if (Schema.isNewerThanApp(Schema.versionOf(stored[SCHEMA_VERSION]))) {
                UserStatus.RequiresUpdate(uid)
            } else {
                UserStatus.Ready(uid)
            }
        }

    companion object {
        const val SCHEMA_VERSION = "schemaVersion"
        const val CREATED_AT = "createdAt"

        /** Ett nytt användardokument: bara versionen och när det skapades. */
        fun initialDocument(now: Instant): Doc = mapOf(SCHEMA_VERSION to Schema.CURRENT_VERSION, CREATED_AT to now)
    }
}
