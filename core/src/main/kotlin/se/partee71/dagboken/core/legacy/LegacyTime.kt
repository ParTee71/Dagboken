package se.partee71.dagboken.core.legacy

import kotlin.time.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Duration.Companion.hours
import se.partee71.dagboken.core.time.HOME_ZONE

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
    val ZONE: TimeZone = HOME_ZONE

    /** 3.x `timestamp` (`Instant.toString()`, ISO med zon) → ögonblick; `null` om tomt eller ogiltigt. */
    fun instant(iso: String): Instant? = iso.takeIf { it.isNotBlank() }?.let { runCatching { Instant.parse(it) }.getOrNull() }

    /** Dag och klockslag i Europe/Stockholm (3.x `tagenTid` på dosens dag; reservvärde för `timestamp`). */
    fun at(date: LocalDate, time: LocalTime): Instant = LocalDateTime(date, time).toInstant(ZONE)

    /** Vad sommartidsbytet gör med ett klockslag som ligger i det. */
    enum class Shift { GAP, OVERLAP }

    /**
     * `null` när [time] på [date] finns exakt en gång; [Shift.GAP] när det inte finns (läses tillbaka som ett
     * senare klockslag), [Shift.OVERLAP] när det finns två gånger (en timme senare ger samma klockslag).
     */
    fun shift(date: LocalDate, time: LocalTime): Shift? {
        val instant = at(date, time)
        return when {
            instant.toLocalDateTime(ZONE).time != time -> Shift.GAP
            (instant + 1.hours).toLocalDateTime(ZONE).time == time -> Shift.OVERLAP
            else -> null
        }
    }

    /** Midnatt i Europe/Stockholm (receptets `skapad`); dagen går att läsa tillbaka exakt. */
    fun midnight(date: LocalDate): Instant = date.atStartOfDayIn(ZONE)

    /** Epok-millisekunder (episoder och incheckningar); 0 (v1) → `null`, aldrig "nu". */
    fun epochMillis(millis: Long): Instant? = millis.takeIf { it != 0L }?.let(Instant::fromEpochMilliseconds)

    /**
     * Ett ögonblick som text: ISO dag och klockslag utan zon (tolkas i Europe/Stockholm; mellanslag i stället för `T`
     * godtas) eller ett ISO-ögonblick med zon. Backupfilens `createdAt` (3.x skrev `LocalDateTime.now()` utan zon) och
     * ett recepts `skapad` som inte är ett datum. `null` om tomt eller ogiltigt – även för ett rent datum.
     */
    fun moment(text: String): Instant? =
        text.takeIf { it.isNotBlank() }?.let { runCatching { LocalDateTime.parse(it.replaceFirst(' ', 'T')).toInstant(ZONE) }.getOrNull() } ?: instant(text)

    /**
     * Ett värdes form utan innehåll, för rapporten: siffror → `9`, bokstäver → `a`, annat som det står, högst
     * [SHAPE_MAX] tecken (`2025-11-20T08:15:00.000Z` → `9999-99-99a99:99:99.999a`).
     */
    fun shape(text: String): String =
        text.take(SHAPE_MAX).map { if (it.isDigit()) '9' else if (it.isLetter()) 'a' else it }.joinToString("") + if (text.length > SHAPE_MAX) "…" else ""

    private const val SHAPE_MAX = 32
}
