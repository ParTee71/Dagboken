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
    val legacy: LegacySettings = LegacySettings()
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
    val isDarkTheme: Boolean = true
)

enum class ThemeMode(override val wire: String) : WireEnum {
    LIGHT("light"),
    DARK("dark"),
    AUTO("auto")
}

/** Påminnelser (NOT-2, NOT-4, NOT-13, NOT-18, NOT-20). */
data class ReminderSettings(
    /** Huvudreglaget för medicinpåminnelser. */
    val medsEnabled: Boolean = false,
    /** En rad per tidpunkt med klockslag ([Slot.SCHEDULED]), alltid i den ordningen. */
    val medSlots: List<SlotReminder> = Slot.SCHEDULED.map { SlotReminder(it) },
    /** En rad per måendetillfälle ([Occasion]), alltid i den ordningen. */
    val screeningOccasions: List<OccasionReminder> = Occasion.entries.map { OccasionReminder(it) },
    /** Klockslaget för periodslutspåminnelsen (NOT-12). */
    val periodReminderTime: LocalTime = LocalTime(9, 0),
    /** Stunden före måendeincheckningarna (NOT-20). */
    val moment: MomentSettings = MomentSettings()
) {
    /**
     * NOT-18, REC-6: tidpunktens klockslag – det i [medSlots] (den första raden för tidpunkten, som larmen), också när
     * påminnelsen eller huvudreglaget är av; utan rad (t.ex. "Vid behov") tidpunktens standard ([Slot.defaultTime]).
     * Gäller både medicinpåminnelsen och tidpunktens doser (MED-4, DAT-8).
     */
    fun timeOf(slot: Slot): LocalTime = medSlots.firstOrNull { it.slot == slot }?.time ?: slot.defaultTime
}

/** Medicinpåminnelse för en tidpunkt: på/av och klockslag (NOT-18) – klockslaget gäller också tidpunktens doser ([ReminderSettings.timeOf]). */
data class SlotReminder(
    val slot: Slot,
    val enabled: Boolean = true,
    val time: LocalTime = slot.defaultTime
)

/** Måendepåminnelse för ett tillfälle: på/av och klockslag (NOT-4). */
data class OccasionReminder(
    val occasion: Occasion,
    val enabled: Boolean = false,
    val time: LocalTime = occasion.defaultTime
)

/**
 * Stunden (NOT-20, NOT-21): en reflektionspaus i helskärm före varje påslagen måendeincheckning. Lagras som
 * `settings.reminders.moment` (DAT-11). [seconds] är pausens längd, [MIN_SECONDS]–[MAX_SECONDS] i steg om
 * [STEP_SECONDS] (samma gränser i `firestore.rules`); ett lagrat värde utanför avrundas vid läsning ([roundSeconds]).
 */
data class MomentSettings(
    /** Av som standard: helskärm över låsskärmen är en stark åtgärd. */
    val enabled: Boolean = false,
    val seconds: Int = DEFAULT_SECONDS,
    /** Helskärm när skärmen är låst eller släckt (annars heads-up); av = alltid vanlig notis. */
    val takeover: Boolean = true,
    /** Dagens hjälpfråga under rubriken (NOT-21, `ReflectionPrompts`). */
    val question: Boolean = true
) {
    companion object {
        const val MIN_SECONDS = 15
        const val MAX_SECONDS = 300
        const val STEP_SECONDS = 15
        const val DEFAULT_SECONDS = 120

        /** De giltiga längderna, kortast först – reglagets steg. */
        val SECONDS: IntProgression = MIN_SECONDS..MAX_SECONDS step STEP_SECONDS

        /** Närmaste giltiga längd för [value] (sekunder): avrundad till ett [STEP_SECONDS]-steg (halvt uppåt) inom gränserna. */
        fun roundSeconds(value: Double): Int = if (value.isNaN()) {
            DEFAULT_SECONDS
        } else {
            (Math.round(value.coerceIn(MIN_SECONDS.toDouble(), MAX_SECONDS.toDouble()) / STEP_SECONDS) * STEP_SECONDS).toInt()
        }
    }
}

/** Profil för sömnkvalitetens åldersnormer (HLS-11). */
data class Profile(
    /** Födelseår; `null` = inte angivet. */
    val birthYear: Int? = null,
    val sex: Sex = Sex.UNSPECIFIED
)

enum class Sex(override val wire: String) : WireEnum {
    MALE("male"),
    FEMALE("female"),
    UNSPECIFIED("unspecified")
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
    val sheetsConfig: String? = null
)
