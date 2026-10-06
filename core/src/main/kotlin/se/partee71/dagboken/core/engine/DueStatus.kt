package se.partee71.dagboken.core.engine

import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import se.partee71.dagboken.core.model.Occasion
import se.partee71.dagboken.core.model.ReminderSettings
import se.partee71.dagboken.core.model.Screening

// När något på Idag är att göra (HEM-4, MED-1, MED-13) – en regel för doser och måendetillfällen.

/** Hur långt fram något får ligga och ändå märkas "Snart" i stället för att räknas som kommande (MED-13). */
val SOON_WINDOW = 3.hours

/** Var en post som inte är avklarad står i förhållande till nu (HEM-4, MED-13). */
enum class Due {
    /** Tiden är nådd idag: att göra nu, märkt "Försenat" (HEM-4). */
    LATE,

    /** Idag, inom [SOON_WINDOW] (MED-13). */
    SOON,

    /** Idag och mer än [SOON_WINDOW] fram, eller en senare dag (MED-13). */
    UPCOMING,

    /** En tidigare dag: bara inte gjort – aldrig försenat (HEM-4). */
    PAST,
}

/**
 * Var [time] på [date] står i förhållande till [now] (HEM-4, MED-13). "Idag" är dagen för [now] i
 * [zone]; avståndet räknas i verkliga timmar mellan ögonblicken, så att "Snart" gäller tre timmar också
 * över ett sommartidsbyte. Tiden nådd (lika med eller före [now]) är [Due.LATE].
 */
fun dueAt(date: LocalDate, time: LocalTime, now: Instant, zone: TimeZone): Due {
    val today = now.toLocalDateTime(zone).date
    return when {
        date < today -> Due.PAST
        date > today -> Due.UPCOMING
        else -> {
            val until = LocalDateTime(date, time).toInstant(zone) - now
            when {
                !until.isPositive() -> Due.LATE
                until <= SOON_WINDOW -> Due.SOON
                else -> Due.UPCOMING
            }
        }
    }
}

/** Ett måendetillfälles status på Idag (HEM-4, HEM-5). */
enum class OccasionStatus {
    LOGGED,
    LATE,
    SOON,
    UPCOMING,

    /** En tidigare dag utan logg – inte försenad (HEM-4). */
    NOT_LOGGED,
}

/** Ett aktiverat måendetillfälle på [date]: klockslaget ur påminnelserna, status och dagens loggar för det. */
data class OccasionState(
    val occasion: Occasion,
    val time: LocalTime,
    val status: OccasionStatus,
    val screenings: List<Screening>,
)

/**
 * HEM-4, HEM-5: de aktiverade måendetillfällena på [date] i tillfällenas ordning (Efter frukost → Läggdags), med klockslaget ur
 * [reminders] och status mot [now] ([dueAt]): loggat när en av dagens loggar har tillfället, annars
 * försenat, snart eller kommande idag och ej loggat en tidigare dag. Loggar för andra dagar hoppas över.
 */
fun occasionStates(
    reminders: ReminderSettings,
    screenings: List<Screening>,
    date: LocalDate,
    now: Instant,
    zone: TimeZone,
): List<OccasionState> {
    val onDate = screenings.filter { it.date == date }
    return reminders.enabledOccasionRows.map { row ->
        val logged = onDate.filter { it.occasion == row.occasion }
        val status = if (logged.isNotEmpty()) {
            OccasionStatus.LOGGED
        } else {
            when (dueAt(date, row.time, now, zone)) {
                Due.LATE -> OccasionStatus.LATE
                Due.SOON -> OccasionStatus.SOON
                Due.UPCOMING -> OccasionStatus.UPCOMING
                Due.PAST -> OccasionStatus.NOT_LOGGED
            }
        }
        OccasionState(row.occasion, row.time, status, logged)
    }
}

/**
 * HEM-8b: plusknappens tillfällesväljare – alla fyra tillfällen i ordning, så att också ett tillfälle utan påminnelse
 * går att logga. De aktiverade har samma status som på Idag ([occasionStates]); ett som inte är aktiverat är loggat
 * eller [OccasionStatus.NOT_LOGGED] – aldrig försenat, det påminns inte om. Klockslaget är påminnelsens (eller
 * tillfällets standardtid).
 */
fun occasionChoices(
    reminders: ReminderSettings,
    screenings: List<Screening>,
    date: LocalDate,
    now: Instant,
    zone: TimeZone,
): List<OccasionState> {
    val enabled = occasionStates(reminders, screenings, date, now, zone).associateBy { it.occasion }
    val onDate = screenings.filter { it.date == date }
    return Occasion.entries.map { occasion ->
        enabled[occasion] ?: run {
            val logged = onDate.filter { it.occasion == occasion }
            val time = reminders.screeningOccasions.firstOrNull { it.occasion == occasion }?.time ?: occasion.defaultTime
            OccasionState(occasion, time, if (logged.isEmpty()) OccasionStatus.NOT_LOGGED else OccasionStatus.LOGGED, logged)
        }
    }
}

/**
 * HEM-5: tillfällets senaste logg – den som visas med värdechips och öppnas för ändring – i den gemensamma
 * ordningen [latestBy]; `null` när tillfället inte är loggat.
 */
val OccasionState.latest: Screening?
    get() = screenings.latestBy(Screening::date, Screening::time, Screening::createdAt)
