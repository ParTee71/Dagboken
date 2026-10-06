package se.partee71.dagboken.core.engine

import kotlinx.datetime.LocalDate
import kotlinx.datetime.daysUntil
import se.partee71.dagboken.core.model.Checkin
import se.partee71.dagboken.core.model.IllnessEpisode

// Pågående sjukdom på Idag (HEM-12) – ren beräkning över episoderna och deras incheckningar.

/**
 * HEM-12: den pågående episoden – utan slutdatum (3.x `slutDatum == ""`). Finns flera visas den senast
 * skapade, som 3.x (`ORDER BY timestamp DESC LIMIT 1`, där `timestamp` var skapandetiden), och vid lika
 * skapandetid id:t. Startdatum påverkar inte valet; en start efter idag ger bara ingen dag ([illnessDay]).
 * `null` när ingen pågår.
 */
fun ongoingEpisode(episodes: List<IllnessEpisode>): IllnessEpisode? =
    episodes.filter { it.end == null }.maxWithOrNull(compareBy<IllnessEpisode>({ it.createdAt }, { it.id }))

/** HEM-12: vilken dag i sjukdomen [today] är – startdagen är dag 1. `null` utan start eller före den. */
fun illnessDay(start: LocalDate?, today: LocalDate): Int? = start?.daysUntil(today)?.takeIf { it >= 0 }?.plus(1)

/** HEM-12: den senaste incheckningen i den gemensamma ordningen [latestBy]; `null` utan incheckningar. */
fun latestCheckin(checkins: List<Checkin>): Checkin? = checkins.latestBy(Checkin::date, Checkin::time, Checkin::createdAt)

/** Kortet för pågående sjukdom på Idag (HEM-12): episoden, dag [day] ([illnessDay]) och [lastCheckin] ([latestCheckin]). */
data class OngoingIllness(val episode: IllnessEpisode, val day: Int?, val lastCheckin: Checkin?)

/** [OngoingIllness] för [episode] med dess [checkins] och [today]. */
fun ongoingIllness(episode: IllnessEpisode, checkins: List<Checkin>, today: LocalDate): OngoingIllness =
    OngoingIllness(episode, illnessDay(episode.start, today), latestCheckin(checkins))
