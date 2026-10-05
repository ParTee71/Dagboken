package se.partee71.dagboken.core.engine

import se.partee71.dagboken.core.model.Option
import se.partee71.dagboken.core.model.ThemeMode
import se.partee71.dagboken.core.model.ThemeSettings

// Reglerna bakom inställningsarket (SET-1, SET-2, SET-5, SET-6, SET-9) – en gång, här, så att
// temat i hela appen, temaskärmen och listornas formulär räknar likadant.

/** Timmarna ett dygn har (0–23) – samma intervall som `firestore.rules` för temats starttimmar. */
private val HOURS = 0..23

/** SET-2: auto-temats starttimmar är giltiga när båda är 0–23 och ljust börjar före mörkt. */
fun ThemeSettings.hasValidHours(): Boolean =
    lightStartHour in HOURS && darkStartHour in HOURS && lightStartHour < darkStartHour

/**
 * Om appen ska vara mörk klockan [hour] (0–23) (SET-1, DSN-5): ljust och mörkt gäller dygnet runt;
 * auto är mörkt från [ThemeSettings.darkStartHour] till [ThemeSettings.lightStartHour]. Ogiltiga
 * starttimmar (från en trasig eller äldre sparning) ger standardtimmarna i stället för ett tema som
 * aldrig byter.
 */
fun ThemeSettings.isDarkAt(hour: Int): Boolean = when (mode) {
    ThemeMode.LIGHT -> false
    ThemeMode.DARK -> true
    ThemeMode.AUTO -> {
        val hours = if (hasValidHours()) this else ThemeSettings()
        hour < hours.lightStartHour || hour >= hours.darkStartHour
    }
}

/**
 * SET-5, SET-6, SET-9 (inga dubbletter): om [name] redan finns bland de aktiva alternativen – samma
 * namn oavsett skiftläge och mellanslag runt omkring. Arkiverade alternativ räknas inte (de syns inte
 * i listan); [exceptId] är alternativet som byter namn och jämförs inte med sig självt.
 */
fun List<Option>.hasActiveName(name: String, exceptId: String? = null): Boolean {
    val wanted = name.normalizedName()
    return any { !it.archived && it.id != exceptId && it.name.normalizedName() == wanted }
}

private fun String.normalizedName() = trim().lowercase()
