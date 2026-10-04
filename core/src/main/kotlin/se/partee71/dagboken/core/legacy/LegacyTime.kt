package se.partee71.dagboken.core.legacy

import kotlin.time.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.toInstant

/**
 * 3.x:s tider → 4.0:s ögonblick (ARKITEKTUR.md → Migrering, punkt 1). Tidszonen är alltid
 * Europe/Stockholm, aldrig enhetens: samma backup ger samma dokument överallt.
 *
 * **Sommartidsregeln** (kotlinx-datetime, samma som `java.time`), testad i `LegacyTimeTest`:
 * - i luckan när klockan ställs fram (sista söndagen i mars, 02:00–03:00 finns inte) flyttas
 *   klockslaget fram med luckans längd: `02:30` → `03:30` sommartid (01:30Z);
 * - vid överlappningen när klockan ställs tillbaka (sista söndagen i oktober, 02:00–03:00 finns två
 *   gånger) gäller den **första** förekomsten, sommartid (UTC+2): `02:30` → 00:30Z.
 * Båda är deterministiska, och ögonblicket läses tillbaka i Europe/Stockholm till samma dag.
 */
internal object LegacyTime {
    val ZONE: TimeZone = TimeZone.of("Europe/Stockholm")

    /** 3.x `timestamp` (`Instant.toString()`, ISO med zon) → ögonblick; `null` om tomt eller ogiltigt. */
    fun instant(iso: String): Instant? = iso.takeIf { it.isNotBlank() }?.let { runCatching { Instant.parse(it) }.getOrNull() }

    /** Dag och klockslag i Europe/Stockholm (3.x `tagenTid` på dosens dag; reservvärde för `timestamp`). */
    fun at(date: LocalDate, time: LocalTime): Instant = LocalDateTime(date, time).toInstant(ZONE)

    /** Midnatt i Europe/Stockholm (receptets `skapad`); dagen går att läsa tillbaka exakt. */
    fun midnight(date: LocalDate): Instant = date.atStartOfDayIn(ZONE)

    /** Epok-millisekunder (episoder och incheckningar); 0 (v1) → `null`, aldrig "nu". */
    fun epochMillis(millis: Long): Instant? = millis.takeIf { it != 0L }?.let(Instant::fromEpochMilliseconds)

    /**
     * Backupfilens `createdAt`: 3.x skrev `LocalDateTime.now()` utan zon (tolkas i Europe/Stockholm);
     * ett ISO-ögonblick godtas också. `null` om tomt eller ogiltigt.
     */
    fun backupCreatedAt(text: String): Instant? =
        text.takeIf { it.isNotBlank() }?.let { runCatching { LocalDateTime.parse(it).toInstant(ZONE) }.getOrNull() } ?: instant(text)
}
