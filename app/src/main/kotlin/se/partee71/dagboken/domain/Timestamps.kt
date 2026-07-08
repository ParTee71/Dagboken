package se.partee71.dagboken.domain

import java.time.LocalDateTime
import java.time.ZoneId

/**
 * Builds the persisted ISO-8601 UTC instant string ("...T...Z") for a locally logged
 * [datum] ("yyyy-MM-dd") and [tid] ("HH:mm"), converting via the device's real time zone.
 * Previously this was built as a literal "${datum}T${tid}:00.000Z", which mislabels local
 * time as UTC — correct only when the device's offset happens to be zero. Callers that
 * parse it back with `Instant.parse` (e.g. medicine cooldown calculations) were comparing
 * a mislabeled instant to a true UTC `Instant.now()`, skewing results by the local UTC
 * offset.
 */
object Timestamps {
    fun of(datum: String, tid: String, zone: ZoneId = ZoneId.systemDefault()): String =
        LocalDateTime.parse("${datum}T$tid")
            .atZone(zone)
            .toInstant()
            .toString()
}
