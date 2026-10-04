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
    DataError.Unknown -> R.string.error_unknown
}
