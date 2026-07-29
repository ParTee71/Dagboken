package se.partee71.dagboken.widget

import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey

/**
 * Per-widgetinstans-state för vid behov-widgeten (#162): väntar på bekräftelse av en
 * cooldown-varning (Glance har inga dialoger, se `LogVidBehovAction`), eller ett
 * meddelande (loggad/dagsgräns nådd) som visas en gång. Flyktig UI-state, inte i backupen.
 */
object VidBehovWidgetKeys {
    val PENDING_FAVORIT_ID = stringPreferencesKey("vidbehov_pending_favorit_id")
    val PENDING_REMAINING_HOURS = doublePreferencesKey("vidbehov_pending_remaining_hours")
    val MESSAGE = stringPreferencesKey("vidbehov_message")
}

data class VidBehovPendingConfirm(val favoritId: String, val remainingHours: Double)

data class VidBehovDraft(val pending: VidBehovPendingConfirm?, val message: String?)

fun Preferences.toVidBehovDraft(): VidBehovDraft {
    val favoritId = this[VidBehovWidgetKeys.PENDING_FAVORIT_ID]
    val remainingHours = this[VidBehovWidgetKeys.PENDING_REMAINING_HOURS]
    val pending = if (favoritId != null && remainingHours != null) {
        VidBehovPendingConfirm(favoritId, remainingHours)
    } else {
        null
    }
    return VidBehovDraft(pending = pending, message = this[VidBehovWidgetKeys.MESSAGE])
}
