package se.partee71.dagboken.core.legacy

import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.toLocalDateTime
import org.junit.Test

/** 3.x-tiderna i Europe/Stockholm, inklusive sommartidsbytet (ARKITEKTUR.md → Migrering, punkt 1). */
class LegacyTimeTest {

    @Test
    fun `vinter- och sommartid - klockslag på dosens dag`() {
        assertEquals(Instant.parse("2026-01-15T06:04:00Z"), LegacyTime.at(LocalDate(2026, 1, 15), LocalTime(7, 4)), "CET, UTC+1")
        assertEquals(Instant.parse("2026-07-15T05:04:00Z"), LegacyTime.at(LocalDate(2026, 7, 15), LocalTime(7, 4)), "CEST, UTC+2")
    }

    @Test
    fun `luckan när klockan ställs fram - klockslaget flyttas fram med luckans längd, deterministiskt`() {
        // 2026-03-29 02:00 → 03:00: 02:30 finns inte. Blir 03:30 sommartid (01:30Z) och läses tillbaka samma dag.
        val gap = LegacyTime.at(LocalDate(2026, 3, 29), LocalTime(2, 30))
        assertEquals(Instant.parse("2026-03-29T01:30:00Z"), gap)
        assertEquals(LocalDateTime(2026, 3, 29, 3, 30), gap.toLocalDateTime(LegacyTime.ZONE))
        assertEquals(Instant.parse("2026-03-29T00:59:00Z"), LegacyTime.at(LocalDate(2026, 3, 29), LocalTime(1, 59)), "strax före luckan: vintertid")
        assertEquals(Instant.parse("2026-03-29T01:00:00Z"), LegacyTime.at(LocalDate(2026, 3, 29), LocalTime(3, 0)), "strax efter luckan: sommartid")
    }

    @Test
    fun `överlappningen när klockan ställs tillbaka - den första förekomsten (sommartid) gäller`() {
        // 2026-10-25 03:00 → 02:00: 02:30 finns två gånger. Första = sommartid (UTC+2) = 00:30Z.
        val overlap = LegacyTime.at(LocalDate(2026, 10, 25), LocalTime(2, 30))
        assertEquals(Instant.parse("2026-10-25T00:30:00Z"), overlap)
        assertEquals(LocalDateTime(2026, 10, 25, 2, 30), overlap.toLocalDateTime(LegacyTime.ZONE), "läses tillbaka till samma klockslag")
        assertEquals(Instant.parse("2026-10-25T02:00:00Z"), LegacyTime.at(LocalDate(2026, 10, 25), LocalTime(3, 0)), "efter överlappningen: vintertid")
    }

    @Test
    fun `shift - luckan, överlappningen och vanliga klockslag`() {
        assertEquals(LegacyTime.Shift.GAP, LegacyTime.shift(LocalDate(2026, 3, 29), LocalTime(2, 30)))
        assertEquals(LegacyTime.Shift.OVERLAP, LegacyTime.shift(LocalDate(2026, 10, 25), LocalTime(2, 30)))
        assertNull(LegacyTime.shift(LocalDate(2026, 10, 25), LocalTime(3, 0)))
        assertNull(LegacyTime.shift(LocalDate(2026, 3, 29), LocalTime(1, 59)))
        assertNull(LegacyTime.shift(LocalDate(2026, 1, 15), LocalTime(2, 30)))
    }

    @Test
    fun `midnatt för receptets skapandedag - dagen går att läsa tillbaka exakt`() {
        assertEquals(Instant.parse("2025-12-31T23:00:00Z"), LegacyTime.midnight(LocalDate(2026, 1, 1)))
        assertEquals(Instant.parse("2025-05-31T22:00:00Z"), LegacyTime.midnight(LocalDate(2025, 6, 1)))
        assertEquals(LocalDate(2026, 1, 1), LegacyTime.midnight(LocalDate(2026, 1, 1)).toLocalDateTime(LegacyTime.ZONE).date)
    }

    @Test
    fun `ISO-ögonblick som 3x skrev dem, med eller utan decimaler och med offset`() {
        assertEquals(Instant.parse("2024-01-01T09:00:00Z"), LegacyTime.instant("2024-01-01T09:00:00.000Z"))
        assertEquals(Instant.parse("2024-01-01T09:00:00Z"), LegacyTime.instant("2024-01-01T09:00:00Z"))
        assertEquals(Instant.parse("2024-01-01T08:00:00Z"), LegacyTime.instant("2024-01-01T09:00:00+01:00"))
        assertEquals(Instant.parse("2024-01-01T09:00:00.123456789Z"), LegacyTime.instant("2024-01-01T09:00:00.123456789Z"))
        assertNull(LegacyTime.instant(""))
        assertNull(LegacyTime.instant("igår"))
        assertNull(LegacyTime.instant("2024-01-01T09:00:00"), "utan zon är det inget ögonblick")
    }

    @Test
    fun `epok-millisekunder - 0 blir null, aldrig nu`() {
        assertNull(LegacyTime.epochMillis(0))
        assertEquals(Instant.parse("2026-01-10T08:00:00Z"), LegacyTime.epochMillis(1_768_032_000_000))
    }

    @Test
    fun `backupfilens createdAt - 3x lokal tid utan zon tolkas i Stockholm, ISO-ögonblick godtas, annat blir null`() {
        assertEquals(Instant.parse("2026-01-15T20:00:00Z"), LegacyTime.backupCreatedAt("2026-01-15T21:00:00"))
        assertEquals(Instant.parse("2026-07-15T19:00:00.500Z"), LegacyTime.backupCreatedAt("2026-07-15T21:00:00.5"))
        assertEquals(Instant.parse("2026-01-15T21:00:00Z"), LegacyTime.backupCreatedAt("2026-01-15T21:00:00Z"))
        assertNull(LegacyTime.backupCreatedAt(""))
        assertNull(LegacyTime.backupCreatedAt("2026-01-15"))
        assertEquals(Instant.fromEpochSeconds(0), BackupJsonConverter.exportedAt(BackupJson(createdAt = "")))
    }
}
