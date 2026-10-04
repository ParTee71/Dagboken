package se.partee71.dagboken.core.model

import kotlin.time.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime

// Dagbokens poster. Alla har dag, klockslag, `createdAt` (3.x `timestamp`) och anteckningen
// `note` (DAT-7). Id:t är 3.x-postens id (DAT-13).

/**
 * `users/{uid}/screenings/{id}` – en måendelogg (SCR-1…SCR-6). Ersätter 3.x `aktiviteter` med
 * `type = "screening"`.
 */
data class Screening(
    override val id: String,
    val date: LocalDate? = null,
    val time: LocalTime? = null,
    /** Måltidstillfället; fanns inte som fält i 3.x och härleds då (DAT-12, [Occasion.derive]). */
    val occasion: Occasion? = null,
    /** 3.x-screeningens namn (`aktivitet`) när det inte var ett tillfälles namn; annars `null`. */
    val customText: String? = null,
    /** Energi 0–10. */
    val energy: Int = 0,
    /** Stress 0–10. */
    val stress: Int = 0,
    val symptoms: List<SymptomScore> = emptyList(),
    val createdAt: Instant? = null,
    val note: String? = null,
) : Identified

/**
 * `users/{uid}/activities/{id}` – en aktivitet (AKT-1…AKT-12). Ersätter 3.x `aktiviteter` med
 * `type = "aktivitet"`.
 */
data class Activity(
    override val id: String,
    val date: LocalDate? = null,
    val time: LocalTime? = null,
    /** Aktivitetstypen i `options` (kind `activity`). */
    val optionId: String = "",
    /** Egen beskrivning vid "Övrigt" (AKT-2). */
    val customText: String? = null,
    /** Energi −10…+10 (AKT-4). */
    val energy: Int = 0,
    /** Stress 0–10 (AKT-5). */
    val stress: Int = 0,
    val symptoms: List<SymptomScore> = emptyList(),
    /** Återhämtande (AKT-3). */
    val recovering: Boolean = false,
    /** Energitjuv (AKT-3). */
    val drain: Boolean = false,
    /** Tidsåtgång i minuter (AKT-7); `null` = inte angiven. */
    val minutes: Int? = null,
    val createdAt: Instant? = null,
    val note: String? = null,
) : Identified

/** `users/{uid}/events/{id}` – en hälsohändelse. Ersätter 3.x `health_events`. */
data class Event(
    override val id: String,
    val date: LocalDate? = null,
    val time: LocalTime? = null,
    /** Händelsetypen i `options` (kind `event`). */
    val optionId: String = "",
    /** Svårighetsgrad 0–10. */
    val severity: Int = 0,
    /** Varaktighet i minuter. */
    val durationMinutes: Int = 0,
    /** Utlösande faktorer, fritext. */
    val triggers: String? = null,
    /** Åtgärder, fritext. */
    val actions: String? = null,
    val createdAt: Instant? = null,
    val note: String? = null,
) : Identified

/** `users/{uid}/illnessEpisodes/{id}` – en sjukdomsepisod (SJ-serien). Ersätter 3.x `sjukdomsepisoder`. */
data class IllnessEpisode(
    override val id: String,
    /** Sjukdomstypen, fritext. */
    val type: String = "",
    val start: LocalDate? = null,
    /** `null` = pågående (3.x `slutDatum == ""`). */
    val end: LocalDate? = null,
    val createdAt: Instant? = null,
    val note: String? = null,
) : Identified

/**
 * `users/{uid}/illnessEpisodes/{episodeId}/checkins/{id}` – en incheckning; episoden är
 * sökvägen (3.x `episodId`). Ersätter 3.x `sjukdoms_incheckningar`.
 */
data class Checkin(
    override val id: String,
    val date: LocalDate? = null,
    val time: LocalTime? = null,
    /** Svårighetsgrad 0–10. */
    val severity: Int = 0,
    val symptoms: List<SymptomScore> = emptyList(),
    val createdAt: Instant? = null,
    val note: String? = null,
) : Identified
