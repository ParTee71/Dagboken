package se.partee71.dagboken.navigation

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable
import se.partee71.dagboken.core.engine.DiaryEntry
import se.partee71.dagboken.core.model.OptionKind

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

/** Vad en post i Dagbok är – avgör vilken skärm den öppnar (HIST-3, HIST-9). */
enum class DiaryEntryKind { SCREENING, ACTIVITY, DOSE, EVENT, EPISODE, CHECKIN }

/**
 * En post i Dagbok (HIST-3): [id] är dokumentets id i sin samling, och för en incheckning är [episodeId]
 * episoden den ligger under. En platshållare tills redigeringen (#239) och sjukdomsdetaljen (#240) tar över.
 */
@Serializable
data class DiaryEntryKey(val kind: DiaryEntryKind, val id: String, val episodeId: String? = null) : AppKey

/** Skärmen som posten öppnar: episodens start och slut öppnar episoden, en incheckning sig själv under sin episod. */
val DiaryEntry.key: DiaryEntryKey
    get() = when (this) {
        is DiaryEntry.Mood -> DiaryEntryKey(DiaryEntryKind.SCREENING, screening.id)
        is DiaryEntry.Action -> DiaryEntryKey(DiaryEntryKind.ACTIVITY, activity.id)
        is DiaryEntry.TakenDose -> DiaryEntryKey(DiaryEntryKind.DOSE, dose.id)
        is DiaryEntry.Happening -> DiaryEntryKey(DiaryEntryKind.EVENT, event.id)
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
