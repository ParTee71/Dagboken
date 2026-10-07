package se.partee71.dagboken.core.engine

import kotlinx.datetime.LocalDate
import se.partee71.dagboken.core.model.Checkin
import se.partee71.dagboken.core.model.IllnessEpisode

// Sjukdomsdetaljens huvud och incheckningslista (SJ-4, SJ-5, SJ-13, #240) – ren beräkning över en episod och
// dess incheckningar. Dag N och senaste incheckning kommer från samma funktioner som kortet på Idag (Illness.kt).

/**
 * Episoddetaljen: [episode] med sina [checkins], senaste först (den gemensamma ordningen [chronological] omvänd –
 * samma "senaste" som [latestCheckin]).
 *
 * - [ongoing]: utan slutdatum (3.x `slutDatum == ""`) – chip "Pågår", annars "Avslutad".
 * - [durationDays]: dagar från start till slut, båda inräknade (3.x `varaktighetDagar`: samma dag = 1). För en
 *   pågående episod räknas till [today]; båda är dag N för den sista dagen ([illnessDay]). `null` utan startdatum, med
 *   ett slut före starten, eller för en pågående episod som börjar efter [today].
 * - [latestSeverity]: den senaste incheckningens svårighetsgrad (SJ-5); `null` utan incheckningar.
 */
data class IllnessSummary(
    val episode: IllnessEpisode,
    val checkins: List<Checkin>,
    val ongoing: Boolean,
    val durationDays: Int?,
    val latestSeverity: Int?,
) {
    /** "Dag N" för [checkin] i den här episoden ([checkinDay]). */
    fun dayOf(checkin: Checkin): Int? = checkinDay(episode, checkin)
}

/** [IllnessSummary] för [episode] med dess [checkins] och [today] (dagen då en pågående episod räknas till). */
fun illnessSummary(episode: IllnessEpisode, checkins: List<Checkin>, today: LocalDate): IllnessSummary = IllnessSummary(
    episode = episode,
    checkins = checkins.sortedWith(chronological(Checkin::date, Checkin::time, Checkin::createdAt).reversed()),
    ongoing = episode.end == null,
    durationDays = illnessDay(episode.start, episode.end ?: today),
    latestSeverity = latestCheckin(checkins)?.severity,
)

/** "Dag N" för [checkin] i [episode] – startdagen är dag 1 ([illnessDay]); `null` utan datum eller före starten. */
fun checkinDay(episode: IllnessEpisode, checkin: Checkin): Int? = checkin.date?.let { illnessDay(episode.start, it) }
