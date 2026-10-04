package se.partee71.dagboken.navigation

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

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

/** Flikarna i bottenradens ordning; appen startar på den första (NAV-8). */
val TOP_LEVEL: List<TopLevelKey> = listOf(TodayKey, DiaryKey, TrendsKey, MedicinesKey)
