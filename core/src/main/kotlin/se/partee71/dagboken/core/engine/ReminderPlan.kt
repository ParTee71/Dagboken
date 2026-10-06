package se.partee71.dagboken.core.engine

import kotlin.time.Duration
import kotlin.time.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import se.partee71.dagboken.core.model.Dose
import se.partee71.dagboken.core.model.DoseStatus
import se.partee71.dagboken.core.model.Occasion
import se.partee71.dagboken.core.model.Prescription
import se.partee71.dagboken.core.model.ReminderSettings
import se.partee71.dagboken.core.model.Screening
import se.partee71.dagboken.core.model.Slot

// Påminnelserna (NOT-2…NOT-5, NOT-10, NOT-12, NOT-17…NOT-19) – vilka larm som ska ligga i AlarmManager
// och vad en påminnelse visar när den utlöses. Ren beräkning: appens schemaläggare och mottagare läser
// inställningarna, recepten, doserna och måendeloggarna ur cachen och anropar härifrån.

/** En sorts påminnelse – ett larm i taget per sort, som schemalägger om sig självt till nästa dag (NOT-14). */
sealed interface Reminder {
    /** Medicinpåminnelsen för tidpunkten [slot] (NOT-2, NOT-18). */
    data class Med(val slot: Slot) : Reminder

    /** Måendepåminnelsen för tillfället [occasion] (NOT-4). */
    data class Mood(val occasion: Occasion) : Reminder

    /** Periodslutspåminnelsen dagen innan (NOT-12, NOT-13). */
    data object PeriodEnd : Reminder
}

/** Ett larm att schemalägga: [reminder] vid [at]; [time] är inställningens klockslag (tidpunktens, inte larmets). */
data class ReminderAlarm(val reminder: Reminder, val time: LocalTime, val at: Instant)

/**
 * NOT-2, NOT-4, NOT-12, NOT-18: larmen som ska ligga i AlarmManager efter [now] – de påslagna
 * medicintidpunkterna när huvudreglaget är på (15 min före, [nextMedAlarm]), de påslagna måendetillfällena
 * ([nextDailyAt], passerat → i morgon, NOT-5) och periodslutspåminnelsen, som alltid ligger (notisen postas
 * bara när något tar slut). En tidpunkt eller ett tillfälle som förekommer två gånger (äldre data) ger ett larm,
 * det första radens.
 *
 * NOT-14: ett larm vars tid i dag passerats för högst [grace] sedan ligger kvar på dagens tid – bara om exakt den
 * tiden redan var schemalagd för påminnelsen ([scheduled]) och påminnelsen inte utlösts sedan dess ([firedAt]). Ett
 * sent, inexakt larm (eller ett som missades under en omstart) flyttas alltså aldrig till i morgon och utlöses direkt;
 * en tid som användaren just flyttat till eller slagit på i det förflutna går till i morgon (NOT-5), och ett larm som
 * redan gått läggs inte igen.
 */
fun reminderAlarms(
    settings: ReminderSettings,
    now: Instant,
    zone: TimeZone,
    grace: Duration = Duration.ZERO,
    scheduled: Map<Reminder, Instant> = emptyMap(),
    firedAt: Map<Reminder, Instant> = emptyMap(),
): List<ReminderAlarm> {
    val meds = if (!settings.medsEnabled) emptyList() else settings.medSlots
        .filter { it.enabled && it.slot != Slot.AS_NEEDED }
        .distinctBy { it.slot }
        .map { Reminder.Med(it.slot) to it.time }
    val moods = settings.enabledOccasionRows.map { Reminder.Mood(it.occasion) to it.time }
    val rows = meds + moods + (Reminder.PeriodEnd to settings.periodReminderTime)
    return rows.map { (reminder, time) ->
        val overdue = nextAlarmAt(reminder, time, now - grace, zone).at
        val keep = grace.isPositive() && overdue <= now && scheduled[reminder] == overdue && firedAt[reminder]?.let { it >= overdue } != true
        if (keep) ReminderAlarm(reminder, time, overdue) else nextAlarmAt(reminder, time, now, zone)
    }
}

/**
 * NOT-6, NOT-14: larmen efter en omstart när inställningarna inte går att läsa (tom cache, inloggningen inte inläst) –
 * systemet har tömt AlarmManager, så de senast schemalagda tiderna ([scheduled]) läggs om på samma klockslag i [zone].
 * Ett larm som passerats för högst [grace] sedan och inte utlösts ([firedAt]) – det missades under omstarten – ligger
 * kvar och utlöses direkt; annars nästa förekomst. [ReminderAlarm.time] är inställningens klockslag (för en
 * medicinpåminnelse [MED_LEAD_MINUTES] efter larmet).
 */
fun restoredAlarms(
    scheduled: Map<Reminder, Instant>,
    firedAt: Map<Reminder, Instant>,
    now: Instant,
    zone: TimeZone,
    grace: Duration = Duration.ZERO,
): List<ReminderAlarm> = scheduled.map { (reminder, at) ->
    val alarmTime = at.toLocalDateTime(zone).time
    val time = if (reminder is Reminder.Med) medAlarmTime(alarmTime, -MED_LEAD_MINUTES) else alarmTime
    val missed = at <= now && now - at <= grace && firedAt[reminder]?.let { it >= at } != true
    ReminderAlarm(reminder, time, if (at > now || missed) at else nextDailyAt(alarmTime, now, zone))
}

/**
 * NOT-14: nästa larm för [reminder] enligt [settings] – samma regel som [reminderAlarms] – eller `null` när
 * påminnelsen är avslagen. Det mottagaren schemalägger om sig till när den utlösts.
 */
fun nextAlarm(reminder: Reminder, settings: ReminderSettings, now: Instant, zone: TimeZone): ReminderAlarm? =
    reminderAlarms(settings, now, zone).firstOrNull { it.reminder == reminder }

/** NOT-14: nästa larm för [reminder] med klockslaget [time] (också när inställningarna inte går att läsa). */
fun nextAlarmAt(reminder: Reminder, time: LocalTime, now: Instant, zone: TimeZone): ReminderAlarm = ReminderAlarm(
    reminder,
    time,
    if (reminder is Reminder.Med) nextMedAlarm(time, now, zone) else nextDailyAt(time, now, zone),
)

/**
 * NOT-3, NOT-10, NOT-17: dagens ej tagna schemalagda doser för en tidpunkt – [stored] finns redan som
 * planerade, [missing] har dosgenereringen inte hunnit skapa (appen har inte öppnats i dag) men receptet ger dem.
 */
data class SlotDoses(val stored: List<Dose> = emptyList(), val missing: List<Dose> = emptyList()) {
    /** Alla, i namnordning – det notisen listar. */
    val all: List<Dose> get() = (stored + missing).sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER, Dose::name).thenBy(Dose::id))

    val isEmpty: Boolean get() = stored.isEmpty() && missing.isEmpty()
}

/**
 * NOT-3, NOT-10, NOT-17: vad medicinpåminnelsen för [slot] på [date] gäller. [doses] är dagens doser (andra
 * dagar hoppas över): planerade doser i schemat ([isScheduled]) med tidpunkten – vid behov-doser aldrig; tagna
 * och överhoppade aldrig. Till dem läggs receptens doser som ännu saknas ([ensureDoses] mot [today], med
 * dagens totala dos, REC-12). Tom = ingen notis.
 */
fun slotDoses(
    slot: Slot,
    date: LocalDate,
    today: LocalDate,
    prescriptions: List<Prescription>,
    doses: List<Dose>,
    zone: TimeZone,
): SlotDoses {
    if (slot == Slot.AS_NEEDED) return SlotDoses()
    val onDate = doses.filter { it.date == date }
    return SlotDoses(
        stored = onDate.filter { it.slot == slot && it.isScheduled && it.status == DoseStatus.PLANNED },
        missing = ensureDoses(prescriptions, onDate, date, today, zone).create.filter { it.slot == slot },
    )
}

/**
 * NOT-19: om måendepåminnelsen för [occasion] ska visas – bara när tillfället inte redan är loggat bland
 * [screenings] (dagens loggar); andra tillfällens loggar tystar den inte.
 */
fun moodReminderDue(occasion: Occasion, screenings: List<Screening>): Boolean = !screenings.hasLogged(occasion)
