package se.partee71.dagboken.widget

import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import se.partee71.dagboken.domain.model.Favorit

/**
 * Per-widgetinstans-state för vid behov-widgeten (#162): väntar på bekräftelse av en
 * cooldown-varning (Glance har inga dialoger, se `LogVidBehovAction`), ett meddelande
 * (loggad/dagsgräns nådd) som visas en gång, eller om listan är expanderad till alla vid
 * behov-mediciner i stället för bara favoriter (#164). Flyktig UI-state, inte i backupen.
 */
object VidBehovWidgetKeys {
    val PENDING_FAVORIT_ID = stringPreferencesKey("vidbehov_pending_favorit_id")
    val PENDING_REMAINING_HOURS = doublePreferencesKey("vidbehov_pending_remaining_hours")
    val MESSAGE = stringPreferencesKey("vidbehov_message")
    val SHOW_ALL = booleanPreferencesKey("vidbehov_show_all")
}

data class VidBehovPendingConfirm(val favoritId: String, val remainingHours: Double)

data class VidBehovDraft(val pending: VidBehovPendingConfirm?, val message: String?, val showAll: Boolean)

fun Preferences.toVidBehovDraft(): VidBehovDraft {
    val favoritId = this[VidBehovWidgetKeys.PENDING_FAVORIT_ID]
    val remainingHours = this[VidBehovWidgetKeys.PENDING_REMAINING_HOURS]
    val pending = if (favoritId != null && remainingHours != null) {
        VidBehovPendingConfirm(favoritId, remainingHours)
    } else {
        null
    }
    return VidBehovDraft(
        pending = pending,
        message = this[VidBehovWidgetKeys.MESSAGE],
        showAll = this[VidBehovWidgetKeys.SHOW_ALL] ?: false,
    )
}

/*
 * Skrivningarna nedan muterar mottagaren direkt — se resonemanget i `ScreeningWidgetState`
 * om varför `prefs.toMutablePreferences()` inuti `updateAppWidgetState` aldrig sparades.
 */

/** Visar ett engångsmeddelande (loggad/dagsgräns) och avslutar ev. bekräftelsesteg. */
fun MutablePreferences.setVidBehovMessage(message: String) {
    clearVidBehovPendingConfirm()
    this[VidBehovWidgetKeys.MESSAGE] = message
}

/** Kvitterar meddelandet, så det bara visas en gång. */
fun MutablePreferences.clearVidBehovMessage() {
    remove(VidBehovWidgetKeys.MESSAGE)
}

/** Ber om bekräftelse av en cooldown-träff innan dosen loggas. */
fun MutablePreferences.setVidBehovPendingConfirm(favoritId: String, remainingHours: Double) {
    this[VidBehovWidgetKeys.PENDING_FAVORIT_ID] = favoritId
    this[VidBehovWidgetKeys.PENDING_REMAINING_HOURS] = remainingHours
}

/** Avbryter en väntande cooldown-bekräftelse. */
fun MutablePreferences.clearVidBehovPendingConfirm() {
    remove(VidBehovWidgetKeys.PENDING_FAVORIT_ID)
    remove(VidBehovWidgetKeys.PENDING_REMAINING_HOURS)
}

/** Växlar mellan favoriter och alla vid behov-mediciner (#164). */
fun MutablePreferences.setVidBehovShowAll(showAll: Boolean) {
    this[VidBehovWidgetKeys.SHOW_ALL] = showAll
}

/** Favoritmarkerade vid behov-mediciner, för widgetens första nivå (#162). */
fun favoriteVidBehov(all: List<Favorit>): List<Favorit> =
    all.filter { it.isFavorite }.sortedBy { it.namn }

/**
 * Alla vid behov-mediciner, favoritmarkerade först — sedan bokstavsordning inom varje
 * grupp — för widgetens expanderade "Fler"-läge (#164).
 */
fun allVidBehovSorted(all: List<Favorit>): List<Favorit> =
    all.sortedWith(compareByDescending<Favorit> { it.isFavorite }.thenBy { it.namn })
