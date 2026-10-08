package se.partee71.dagboken.ui.common

import androidx.annotation.StringRes
import se.partee71.dagboken.R
import se.partee71.dagboken.data.common.DataError

/** Den enda översättningen från fel till svensk text (regel 4). */
@StringRes
fun DataError.toMessage(): Int = when (this) {
    DataError.Offline -> R.string.error_offline
    DataError.PermissionDenied -> R.string.error_permission_denied
    DataError.Cancelled -> R.string.error_cancelled
    DataError.UpdateRequired -> R.string.error_update_required
    DataError.SignInRejected -> R.string.error_sign_in_rejected
    DataError.NotSignedIn -> R.string.error_not_signed_in
    DataError.NotFound -> R.string.error_not_found
    DataError.QuotaExceeded -> R.string.error_quota_exceeded
    DataError.Unknown -> R.string.error_unknown
}

/**
 * Ett fel som ska visas: felet och dess text. En vanlig klass (inte `data`), så att samma fel två
 * gånger i rad är två händelser – ett `StateFlow` slår inte ihop dem.
 */
class Failure(val error: DataError, @param:StringRes val message: Int = error.toMessage())

/**
 * Den enda översättningen från ett fel i ett `Result` till det som visas (regel 4): ett `DataError`
 * eller [DataError.Unknown], med en egen text från [message] när den ger en (t.ex. en dubblett som
 * datalagret nekar), annars `DataError.toMessage()`. Används av `EditorState` och `ArchiveActions`.
 */
fun Throwable.toFailure(message: (Throwable) -> Int? = { null }): Failure {
    val error = this as? DataError ?: DataError.Unknown
    return Failure(error, message(this) ?: error.toMessage())
}

/** [toFailure] för ett misslyckat [Result]; `null` om det lyckades. */
fun Result<*>.failureOrNull(message: (Throwable) -> Int? = { null }): Failure? = exceptionOrNull()?.toFailure(message)
