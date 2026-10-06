package se.partee71.dagboken.navigation

import androidx.navigation3.runtime.NavKey
import kotlinx.datetime.LocalDate
import kotlinx.serialization.Serializable
import se.partee71.dagboken.core.engine.DiaryEntry
import se.partee71.dagboken.core.model.OptionKind
import se.partee71.dagboken.ui.components.LogChoice

/** Alla skärmar i appen (NAV-8, NAV-11). Sparas med kotlinx.serialization, så nycklarna överlever processdöd. */
@Serializable
sealed interface AppKey : NavKey

/** En flik i bottenraden – roten i en egen back stack. */
@Serializable
sealed interface TopLevelKey : AppKey

/** Idag – göra (§4). */
@Serializable
data object TodayKey : TopLevelKey

/** Dagbok – läsa (§16). */
@Serializable
data object DiaryKey : TopLevelKey

/** Trender (§17). */
@Serializable
data object TrendsKey : TopLevelKey

/** Mediciner (§21). */
@Serializable
data object MedicinesKey : TopLevelKey

/** Komponentgalleriet – nås bara från inställningsarket i debug-bygget. */
@Serializable
data object ComponentGalleryKey : AppKey

// Inställningsarkets underskärmar (NAV-9) – läggs på den aktuella flikens stack.

/** Profil: födelseår och kön (HLS-11). */
@Serializable
data object ProfileKey : AppKey

/** Påminnelser (SET-4, NOT-4, NOT-13, NOT-18). */
@Serializable
data object RemindersKey : AppKey

/** Tema (SET-1, SET-2). */
@Serializable
data object ThemeKey : AppKey

/** Listor: aktivitetstyper, symptom och händelsetyper (SET-5, SET-6, SET-9). */
@Serializable
data object ListsKey : AppKey

/** Nytt alternativ ([id] = `null`) eller namnbyte i listan [kind] (SET-11). */
@Serializable
data class OptionEditKey(val kind: OptionKind, val id: String? = null) : AppKey

/**
 * Nytt recept ([id] = `null`) eller ett befintligt (REC-1, MEDF-4); [extend] = "Förläng och aktivera" på
 * ett avslutat recept (MEDF-5).
 */
@Serializable
data class PrescriptionEditKey(val id: String? = null, val extend: Boolean = false) : AppKey

/** Ny vid behov-medicin ([id] = `null`) eller en befintlig (FAV-1, MEDF-3, MEDF-4). */
@Serializable
data class PrnMedicineEditKey(val id: String? = null) : AppKey

/** Ny aktivitet ([id] = `null`) mot dagen [date] (`null` = idag, NAV-10, HEM-14), eller en befintlig (AKT-1–AKT-12, HIST-3). */
@Serializable
data class ActivityEditKey(val id: String? = null, val date: LocalDate? = null) : AppKey

/** Ny händelse ([id] = `null`) mot dagen [date] (`null` = idag, NAV-10, HEM-14), eller en befintlig (HAN-1, HIST-3). */
@Serializable
data class EventEditKey(val id: String? = null, val date: LocalDate? = null) : AppKey

/** Dos och Sjukdom i plusknappens meny (NAV-10) – en platshållare tills formulären finns (#271). */
@Serializable
data class LogUpcomingKey(val choice: LogChoice) : AppKey

/** Vad en post i Dagbok är som ännu öppnar en platshållare (HIST-3, HIST-9). */
enum class DiaryEntryKind { DOSE, EPISODE, CHECKIN }

/**
 * En post i Dagbok utan eget formulär än (HIST-3): [id] är dokumentets id i sin samling, och för en incheckning är
 * [episodeId] episoden den ligger under. En platshållare tills dosformuläret (#271) och sjukdomsdetaljen (#240) tar över.
 */
@Serializable
data class DiaryEntryKey(val kind: DiaryEntryKind, val id: String, val episodeId: String? = null) : AppKey

/**
 * Skärmen som posten öppnar (HIST-3): aktiviteten och händelsen sina formulär, episodens start och slut episoden,
 * en incheckning sig själv under sin episod. `null` för en måendelogg – den öppnas i måendearket ovanpå fliken.
 */
val DiaryEntry.key: AppKey?
    get() = when (this) {
        is DiaryEntry.Mood -> null
        is DiaryEntry.Action -> ActivityEditKey(activity.id)
        is DiaryEntry.TakenDose -> DiaryEntryKey(DiaryEntryKind.DOSE, dose.id)
        is DiaryEntry.Happening -> EventEditKey(event.id)
        is DiaryEntry.EpisodeStart -> DiaryEntryKey(DiaryEntryKind.EPISODE, episode.id)
        is DiaryEntry.EpisodeEnd -> DiaryEntryKey(DiaryEntryKind.EPISODE, episode.id)
        is DiaryEntry.CheckIn -> DiaryEntryKey(DiaryEntryKind.CHECKIN, checkin.id, episode.id)
    }

/** Export och import (BCK-13, SET-8) – funktionen kommer i #230. */
@Serializable
data object ExportImportKey : AppKey

/** Om Dagboken: version och licenser. */
@Serializable
data object AboutKey : AppKey

/** Flikarna i bottenradens ordning; appen startar på den första (NAV-8). */
val TOP_LEVEL: List<TopLevelKey> = listOf(TodayKey, DiaryKey, TrendsKey, MedicinesKey)
