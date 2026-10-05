package se.partee71.dagboken.navigation

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable
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

/** Export och import (BCK-13, SET-8) – funktionen kommer i #230. */
@Serializable
data object ExportImportKey : AppKey

/** Om Dagboken: version och licenser. */
@Serializable
data object AboutKey : AppKey

/** Flikarna i bottenradens ordning; appen startar på den första (NAV-8). */
val TOP_LEVEL: List<TopLevelKey> = listOf(TodayKey, DiaryKey, TrendsKey, MedicinesKey)
