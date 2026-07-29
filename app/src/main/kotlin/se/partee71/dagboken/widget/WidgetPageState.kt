package se.partee71.dagboken.widget

import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey

/**
 * Vilken sida widgeten visar (#159) — framsida (status) eller full medicinchecklista.
 * Samma flyktiga per-widgetinstans-state (`PreferencesGlanceStateDefinition`) som
 * screeningguidens steg (WID-3), inte i backupen.
 */
object WidgetPageKeys {
    val SHOW_MEDS = booleanPreferencesKey("widget_show_meds")
}

fun Preferences.showMedsPage(): Boolean = this[WidgetPageKeys.SHOW_MEDS] ?: false
