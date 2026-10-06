package se.partee71.dagboken.core.engine.health

import kotlin.time.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant

/** Hälsomotorns testtid: zonen och ett kortspråk för väggklockslag (påhittade tider, aldrig riktig data). */
internal val STOCKHOLM: TimeZone = TimeZone.of("Europe/Stockholm")

/** [date] klockan [time] ("HH:mm") i Stockholm. */
internal fun at(date: LocalDate, time: String): Instant = LocalDateTime(date, LocalTime.parse(time)).toInstant(STOCKHOLM)

/** En lokal tidpunkt ("2026-08-01T23:00") i Stockholm. */
internal fun local(dateTime: String): Instant = LocalDateTime.parse(dateTime).toInstant(STOCKHOLM)

/** En sömnsession i Stockholmstid, med valfria stadier. */
internal fun sleep(from: String, to: String, stages: List<SleepStageSlice> = emptyList(), origin: String = "watch") =
    SleepSession(origin, local(from), local(to), stages)
