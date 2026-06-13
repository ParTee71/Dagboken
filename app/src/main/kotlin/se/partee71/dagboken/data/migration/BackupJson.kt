package se.partee71.dagboken.data.migration

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Mirrors BackupData from src/services/backup.ts */
@Serializable
data class BackupJson(
    val version: Int = 1,
    val createdAt: String = "",
    val aktiviteter: List<AktivitetJson> = emptyList(),
    val mediciner: List<MedicinJson> = emptyList(),
    val medicinRecipes: List<ReceptJson> = emptyList(),
    val medicinFavoriter: List<FavoritJson> = emptyList(),
    val aktiviteterOptions: List<String>? = null,
    val symptomOptions: List<String>? = null,
)

@Serializable
data class AktivitetJson(
    val id: String = "",
    val timestamp: String = "",
    val datum: String = "",
    val tid: String = "",
    val aktivitet: String = "",
    val energy: Int = 0,
    val stress: Int = 0,
    val somatiska: Int = 0,
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
    val tidpunkt: String = "",
    val tagen: Boolean = false,
    val anteckning: String = "",
    val receptId: String? = null,
    val skipped: Boolean = false,
)

@Serializable
data class ReceptJson(
    val id: String = "",
    val namn: String = "",
    val dos: String = "",
    val enhet: String = "",
    val tidpunkter: List<String> = emptyList(),
    val tidpunkt: String? = null,     // v1 compat — single tidpunkt
    val upprepning: String = "dagligen",
    val dagar: List<Int> = emptyList(),
    val intervalDagar: Int = 2,
    val anteckning: String = "",
    val aktiv: Boolean = true,
    val skapad: String = "",
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
)
