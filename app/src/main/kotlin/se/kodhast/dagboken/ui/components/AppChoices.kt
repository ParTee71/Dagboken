package se.kodhast.dagboken.ui.components

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import se.kodhast.dagboken.R

/** De fem loggvalen bakom plusknappen, i ordning (NAV-10). */
enum class LogChoice(@StringRes val label: Int, @DrawableRes val icon: Int) {
    Mood(R.string.log_mood, R.drawable.ic_mood),
    Activity(R.string.log_activity, R.drawable.ic_activity),
    Dose(R.string.dose_label, R.drawable.ic_pill),
    Event(R.string.log_event, R.drawable.ic_event),
    Illness(R.string.log_illness, R.drawable.ic_thermometer)
}

/** Raderna i Inställningar i ordning (NAV-9) – var och en öppnar en underskärm med tillbakapil. */
enum class SettingsPage(@StringRes val label: Int, @DrawableRes val icon: Int) {
    Profile(R.string.settings_profile, R.drawable.ic_person),
    Reminders(R.string.settings_reminders, R.drawable.ic_bell),
    Theme(R.string.settings_theme, R.drawable.ic_moon),
    Lists(R.string.settings_lists, R.drawable.ic_list),
    ExportImport(R.string.settings_export_import, R.drawable.ic_download)
}
