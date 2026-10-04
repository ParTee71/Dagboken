package se.partee71.dagboken.core.legacy

import kotlinx.serialization.SerializationException
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

// Dagboken 3.x:s backupformat (branchen `legacy`, `data/migration/BackupJson.kt`), fält för fält
// med samma namn och standardvärden, så att en Drive-backup eller lokal JSON från 3.x läses exakt
// som 3.x läste den (OMB-3, BCK-14). Version 1 saknar `settings`, tidsstämplar på episoder och
// V2-listorna och har receptets enda `tidpunkt`; arvsfältet `anteckning` på posterna är från före
// notes-tabellen. Varje fält här har en rad i ARKITEKTUR.md → Fältparitet (ParityTableTest).
// Inga Android-beroenden: klasserna används av konverteraren, legacy-läsaren och legacyimporten.

/** Hela backupfilen. `version` 1 eller 2 (`BackupAssembler.BACKUP_FORMAT_VERSION`). */
@Serializable
data class BackupJson(
    val version: Int = 1,
    /** När filen skrevs, 3.x `LocalDateTime.now()` utan zon (`2026-01-15T21:00:00`). Metadata, inte användardata. */
    val createdAt: String = "",
    val aktiviteter: List<AktivitetJson> = emptyList(),
    val mediciner: List<MedicinJson> = emptyList(),
    val medicinRecipes: List<ReceptJson> = emptyList(),
    val medicinFavoriter: List<FavoritJson> = emptyList(),
    /** v1: aktivitetsalternativen som namn. `null` = fanns inte i backupen (3.x standardlista gäller). */
    val aktiviteterOptions: List<String>? = null,
    val symptomOptions: List<String>? = null,
    /** v2: med favoritmarkering; går före v1-listan när båda finns. */
    val aktiviteterOptionsV2: List<SymptomOptionBackup>? = null,
    val symptomOptionsV2: List<SymptomOptionBackup>? = null,
    val sjukdomsepisoder: List<SjukdomsEpisodJson> = emptyList(),
    val sjukdomsIncheckningar: List<SjukdomsIncheckningJson> = emptyList(),
    val handelser: List<HandelseJson> = emptyList(),
    /** Anteckningarna ur notes-tabellen; en post här går före arvsfältet `anteckning`. */
    val notes: List<NoteJson> = emptyList(),
    /** Måendepåminnelserna, position 0–3 = Efter frukost, Lunch, Kvällsmat, Läggdags. `null` = rör inte. */
    val screeningEventConfigs: List<ScreeningEventConfigJson>? = null,
    /** Medicinpåminnelsernas egna tider (NOT-18). `null` = rör inte. */
    val medNotificationConfigs: List<MedNotificationConfigJson>? = null,
    /** Adressen till Sheets-exporten (FUT-2); `null` = fanns inte. */
    val sheetsConfig: String? = null,
    val handelseTypOptions: List<SymptomOptionBackup>? = null,
    /** Klockslaget för periodslutspåminnelsen (NOT-13). */
    val periodReminderTime: String? = null,
    /** Appinställningar (3.x BCK-10); `null` i ett fält = "rör inte". */
    val settings: SettingsBackup? = null,
) {
    companion object {
        /** Som 3.x: okända fält ignoreras; `null` i ett fält utan `null`-stöd blir fältets standardvärde. */
        private val json = Json {
            ignoreUnknownKeys = true
            coerceInputValues = true
        }

        /**
         * Läser en 3.x-backupfil. Ett formatfel ger ett fast meddelande: kotlinx:s eget citerar
         * innehållet, och det är hälsodata.
         */
        fun parse(text: String): BackupJson = try {
            json.decodeFromString(serializer(), text)
        } catch (e: SerializationException) {
            throw IllegalArgumentException("Filen är inte en 3.x-backup (BackupJson) – innehållet visas inte", e)
        } catch (e: IllegalArgumentException) {
            throw IllegalArgumentException("Filen är inte en 3.x-backup (BackupJson) – innehållet visas inte", e)
        }
    }
}

/** Inställningar i backupen (3.x BCK-10). `null` = fanns inte i backupen och lämnas orörd. */
@Serializable
data class SettingsBackup(
    val medsNotificationsEnabled: Boolean? = null,
    /** `light`, `dark` eller `auto`. */
    val themeMode: String? = null,
    val themeLightStart: Int? = null,
    val themeDarkStart: Int? = null,
    val isDarkTheme: Boolean? = null,
    val dynamicColor: Boolean? = null,
    val birthYear: Int? = null,
    /** `man`, `kvinna` eller `ej_angivet` (3.x `Sex.storageKey`). */
    val sex: String? = null,
)

/** En aktivitet eller måendelogg; `type` skiljer dem (`aktivitet`/`screening`, tomt i äldre filer). */
@Serializable
data class AktivitetJson(
    val id: String = "",
    /** ISO-ögonblick (`Instant.toString()`), tomt i äldre filer. */
    val timestamp: String = "",
    val datum: String = "",
    val tid: String = "",
    /** Aktivitetens namn, fritexten vid "Övrigt", eller måendetillfällets namn. */
    val aktivitet: String = "",
    val energy: Int = 0,
    val stress: Int = 0,
    /** Summan av symptompoängen, som 3.x räknade den. */
    val somatiska: Int = 0,
    /** `Namn:Poäng,Namn:Poäng`; fritexten vid "Övrigt" ligger i namnet: `Övrigt (fritext):Poäng`. */
    val symptom: String = "",
    val aterhamtande: Boolean = false,
    val energitjuv: Boolean = false,
    val type: String = "aktivitet",
    val spentTime: Int? = null,
)

@Serializable
data class MedicinJson(
    val id: String = "",
    val timestamp: String = "",
    val datum: String = "",
    val tid: String = "",
    val namn: String = "",
    val dos: String = "",
    val enhet: String = "",
    /** Tidpunktens 3.x-namn (`Morgon` … `Vid behov`). */
    val tidpunkt: String = "",
    val tagen: Boolean = false,
    /** Arvsfält från före notes-tabellen. */
    val anteckning: String = "",
    val receptId: String? = null,
    val skipped: Boolean = false,
    /** Faktisk tagningstid `HH:mm` (MED-14). */
    val tagenTid: String? = null,
)

@Serializable
data class ReceptJson(
    val id: String = "",
    val namn: String = "",
    val dos: String = "",
    val enhet: String = "",
    val tidpunkter: List<String> = emptyList(),
    /** v1: receptets enda tidpunkt, när `tidpunkter` är tom. */
    val tidpunkt: String? = null,
    /** `dagligen`, `vardagar`, `helger`, `anpassad`/`specifika dagar`, `intervall`/`var x:e dag`. */
    val upprepning: String = "dagligen",
    /** 0 = måndag … 6 = söndag. */
    val dagar: List<Int> = emptyList(),
    val intervalDagar: Int = 2,
    val anteckning: String = "",
    val aktiv: Boolean = true,
    /** Skapandedatum `yyyy-MM-dd`. */
    val skapad: String = "",
    /** `""` = ingen uttalad periodstart (REC-7). */
    val startDatum: String = "",
    /** `null`/`""` = tills vidare. */
    val slutDatum: String? = null,
    val dosperioder: List<DosperiodJson> = emptyList(),
)

/** En doshöjning (REC-9). */
@Serializable
data class DosperiodJson(
    val id: String = "",
    val startDatum: String = "",
    val slutDatum: String? = null,
    val dos: String = "",
    val enhet: String = "",
)

@Serializable
data class FavoritJson(
    val id: String = "",
    val namn: String = "",
    val dos: String = "",
    val enhet: String = "",
    val tidpunkt: String = "",
    val anteckning: String = "",
    val minTidMellan: Int = 4,
    val dispenseringsTid: String = "",
    val maxDoserPerDag: Int = 0,
    val isFavorite: Boolean = false,
)

@Serializable
data class SjukdomsEpisodJson(
    val id: String = "",
    val typ: String = "",
    val startDatum: String = "",
    /** `""` = pågående. */
    val slutDatum: String = "",
    val anteckning: String = "",
    /** Epok-millisekunder; 0 i v1. */
    val timestamp: Long = 0,
)

@Serializable
data class SjukdomsIncheckningJson(
    val id: String = "",
    val episodId: String = "",
    val datum: String = "",
    val tid: String = "",
    val svarighetsgrad: Int = 0,
    val symptom: String = "",
    val somatiska: Int = 0,
    val anteckning: String = "",
    val timestamp: Long = 0,
)

@Serializable
data class HandelseJson(
    val id: String = "",
    val timestamp: String = "",
    val datum: String = "",
    val tid: String = "",
    /** Händelsetypens namn. */
    val typ: String = "",
    val svarighetsgrad: Int = 0,
    val varaktighetMinuter: Int = 0,
    val triggers: String = "",
    val atgarder: String = "",
    val anteckning: String = "",
)

/** En anteckning i notes-tabellen: `target` är 3.x `NoteTarget` (`ACTIVITY`, `MEDICATION` …). */
@Serializable
data class NoteJson(
    val target: String = "",
    val entityId: String = "",
    val text: String = "",
)

@Serializable
data class ScreeningEventConfigJson(
    val enabled: Boolean = false,
    val time: String = "",
)

/** En medicinpåminnelse (NOT-18); `tidpunkt` är namnet, tomt i filer skrivna före namnen fanns (då gäller positionen). */
@Serializable
data class MedNotificationConfigJson(
    val tidpunkt: String = "",
    val enabled: Boolean = false,
    val time: String = "",
)

/** Ett alternativ i v2-listorna och händelsetyperna. */
@Serializable
data class SymptomOptionBackup(
    val name: String = "",
    val isFavorite: Boolean = false,
)
