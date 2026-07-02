package se.partee71.dagboken.data.migration

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
    val aktiviteterOptionsV2: List<SymptomOptionBackup>? = null,
    val symptomOptionsV2: List<SymptomOptionBackup>? = null,
    val sjukdomsepisoder: List<SjukdomsEpisodJson> = emptyList(),
    val sjukdomsIncheckningar: List<SjukdomsIncheckningJson> = emptyList(),
    val handelser: List<HandelseJson> = emptyList(),
    val notes: List<NoteJson> = emptyList(),
    val screeningEventConfigs: List<ScreeningEventConfigJson>? = null,
    val sheetsConfig: String? = null,
    val handelseTypOptions: List<SymptomOptionBackup>? = null,
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
    val isFavorite: Boolean = false,
)

@Serializable
data class SjukdomsEpisodJson(
    val id: String = "",
    val typ: String = "",
    val startDatum: String = "",
    val slutDatum: String = "",
    val anteckning: String = "",
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
    val typ: String = "",
    val svarighetsgrad: Int = 0,
    val varaktighetMinuter: Int = 0,
    val triggers: String = "",
    val atgarder: String = "",
    val anteckning: String = "",
)

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

@Serializable
data class SymptomOptionBackup(val name: String, val isFavorite: Boolean = false)
