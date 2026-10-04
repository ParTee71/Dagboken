package se.partee71.dagboken.core.model

import kotlinx.datetime.LocalTime

/**
 * `users/{uid}/settings/app` – användarens enda inställningsdokument (DAT-11): tema, påminnelser,
 * profil och de 3.x-värden som bara bevaras ([legacy]). Defaults är 3.x-appens standardvärden, så
 * ett saknat dokument beter sig som en ny installation av 3.x.
 */
data class Settings(
    override val id: String = ID,
    val theme: ThemeSettings = ThemeSettings(),
    val reminders: ReminderSettings = ReminderSettings(),
    val profile: Profile = Profile(),
    val legacy: LegacySettings = LegacySettings(),
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

/**
 * 3.x-inställningar utan funktion i 4.0 som **bara bevaras** (ADR-001, beslut 11: varje 3.x-fält
 * har en plats). 4.0 läser dem aldrig för att styra något och visar dem inte; konverteraren skriver
 * dem och codecen skriver tillbaka dem oförändrade. `null` = värdet fanns inte i 3.x-datan.
 */
data class LegacySettings(
    /** 3.x `SettingsBackup.dynamicColor` (Material You av/på). 4.0 har fasta färger (SET-3). */
    val dynamicColor: Boolean? = null,
    /** 3.x `BackupJson.sheetsConfig` – adressen till Sheets-exporten, som inte finns i 4.0 (FUT-2). */
    val sheetsConfig: String? = null,
)
