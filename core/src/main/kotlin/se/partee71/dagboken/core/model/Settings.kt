package se.partee71.dagboken.core.model

import kotlinx.datetime.LocalTime

/**
 * `users/{uid}/settings/app` – användarens enda inställningsdokument (DAT-11): tema, påminnelser
 * och profil. Defaults är 3.x-appens standardvärden, så ett saknat dokument beter sig som en ny
 * installation av 3.x.
 */
data class Settings(
    override val id: String = ID,
    val theme: ThemeSettings = ThemeSettings(),
    val reminders: ReminderSettings = ReminderSettings(),
    val profile: Profile = Profile(),
) : Identified {
    companion object {
        /** Dokumentets enda tillåtna id (även i `firestore.rules`). */
        const val ID = "app"
    }
}

/** Tema (SET-1, SET-2). */
data class ThemeSettings(
    /** Ljust, mörkt eller auto på klockslag. */
    val mode: ThemeMode = ThemeMode.AUTO,
    /** Timme (0–23) då auto byter till ljust. */
    val lightStartHour: Int = 7,
    /** Timme (0–23) då auto byter till mörkt. */
    val darkStartHour: Int = 21,
    /** 3.x-reglaget för mörkt tema; bevaras även om 4.0 bara läser [mode]. */
    val isDarkTheme: Boolean = true,
)

enum class ThemeMode(override val wire: String) : WireEnum {
    LIGHT("light"),
    DARK("dark"),
    AUTO("auto"),
}

/** Påminnelser (NOT-2, NOT-4, NOT-13, NOT-18). */
data class ReminderSettings(
    /** Huvudreglaget för medicinpåminnelser. */
    val medsEnabled: Boolean = false,
    /** En rad per tidpunkt med klockslag ([Slot.SCHEDULED]), alltid i den ordningen. */
    val medSlots: List<SlotReminder> = Slot.SCHEDULED.map { SlotReminder(it) },
    /** En rad per måendetillfälle ([Occasion]), alltid i den ordningen. */
    val screeningOccasions: List<OccasionReminder> = Occasion.entries.map { OccasionReminder(it) },
    /** Klockslaget för periodslutspåminnelsen (NOT-12). */
    val periodReminderTime: LocalTime = LocalTime(9, 0),
)

/** Medicinpåminnelse för en tidpunkt: på/av och klockslag (NOT-18). */
data class SlotReminder(
    val slot: Slot,
    val enabled: Boolean = true,
    val time: LocalTime = slot.defaultTime,
)

/** Måendepåminnelse för ett tillfälle: på/av och klockslag (NOT-4). */
data class OccasionReminder(
    val occasion: Occasion,
    val enabled: Boolean = false,
    val time: LocalTime = occasion.defaultTime,
)

/** Profil för sömnkvalitetens åldersnormer (HLS-11). */
data class Profile(
    /** Födelseår; `null` = inte angivet. */
    val birthYear: Int? = null,
    val sex: Sex = Sex.UNSPECIFIED,
)

enum class Sex(override val wire: String) : WireEnum {
    MALE("male"),
    FEMALE("female"),
    UNSPECIFIED("unspecified"),
}
