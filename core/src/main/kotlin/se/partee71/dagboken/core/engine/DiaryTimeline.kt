package se.partee71.dagboken.core.engine

import kotlin.time.Instant
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import se.partee71.dagboken.core.model.Activity
import se.partee71.dagboken.core.model.Checkin
import se.partee71.dagboken.core.model.Dose
import se.partee71.dagboken.core.model.DoseStatus
import se.partee71.dagboken.core.model.Event
import se.partee71.dagboken.core.model.Identified
import se.partee71.dagboken.core.model.IllnessEpisode
import se.partee71.dagboken.core.model.Screening

// Dagbokens tidslinje (HIST-1, HIST-2, HIST-7, HIST-8, HIST-9) – ren beräkning över posterna.

/** Posttyperna i Dagbokens filter (HIST-2), i chipsens ordning efter "Alla". */
enum class DiaryType { SCREENING, ACTIVITY, DOSE, EVENT, ILLNESS }

/**
 * HIST-2: vilka typer som visas – aldrig ingen. Från början alla ([showsAll], chippet "Alla" markerat).
 * [toggle] på en typ när alla visas ger bara den typen; annars slås typen av eller på, men den sista går
 * inte att slå av. [showAll] slår på samtliga.
 */
data class DiaryFilter(val types: Set<DiaryType> = ALL) {
    init {
        require(types.isNotEmpty()) { "Minst en typ visas alltid (HIST-2)" }
    }

    /** "Alla" är markerat exakt när alla typer visas. */
    val showsAll: Boolean get() = types.containsAll(ALL)

    fun showAll(): DiaryFilter = DiaryFilter()

    fun toggle(type: DiaryType): DiaryFilter = when {
        showsAll -> DiaryFilter(setOf(type))
        type !in types -> DiaryFilter(types + type)
        types.size == 1 -> this
        else -> DiaryFilter(types - type)
    }

    fun shows(type: DiaryType): Boolean = type in types

    companion object {
        val ALL: Set<DiaryType> = DiaryType.entries.toSet()
    }
}

/**
 * En post i Dagboken: dag, klockslag och typ ur den sparade posten. Id:t är unikt över typerna
 * (`"dose:{id}"`), så att posterna kan stå i samma lista; ordningen är [chronological].
 */
sealed interface DiaryEntry : Identified {
    val type: DiaryType
    val date: LocalDate
    val time: LocalTime?
    val createdAt: Instant?
    val note: String?

    /**
     * Platsen inom dagen före klockslaget: episodens start står först på sin dag ([START_OF_DAY]), dess slut
     * sist ([END_OF_DAY]) och övriga poster däremellan efter klockslaget – så att slutet alltid är nyare än
     * dagens incheckningar och en start samma dag som slutet är äldst.
     */
    val dayPart: Int get() = DURING_DAY

    /** En måendelogg (SCR-1). */
    data class Mood(val screening: Screening, override val date: LocalDate) : DiaryEntry {
        override val id: String get() = "screening:${screening.id}"
        override val type: DiaryType get() = DiaryType.SCREENING
        override val time: LocalTime? get() = screening.time
        override val createdAt: Instant? get() = screening.createdAt
        override val note: String? get() = screening.note
    }

    /** En aktivitet (AKT-1). */
    data class Action(val activity: Activity, override val date: LocalDate) : DiaryEntry {
        override val id: String get() = "activity:${activity.id}"
        override val type: DiaryType get() = DiaryType.ACTIVITY
        override val time: LocalTime? get() = activity.time
        override val createdAt: Instant? get() = activity.createdAt
        override val note: String? get() = activity.note
    }

    /**
     * HIST-7, MED-14: en **tagen** dos; [date] och [time] är tagningstidens dag och klockslag (en kvällsdos tagen
     * 00:30 hör till dagen efter), eller dosens dag och planerade tid för en dos utan tagningstid.
     */
    data class TakenDose(val dose: Dose, override val date: LocalDate, override val time: LocalTime?) : DiaryEntry {
        override val id: String get() = "dose:${dose.id}"
        override val type: DiaryType get() = DiaryType.DOSE
        override val createdAt: Instant? get() = dose.createdAt
        override val note: String? get() = dose.note
    }

    /** En hälsohändelse. */
    data class Happening(val event: Event, override val date: LocalDate) : DiaryEntry {
        override val id: String get() = "event:${event.id}"
        override val type: DiaryType get() = DiaryType.EVENT
        override val time: LocalTime? get() = event.time
        override val createdAt: Instant? get() = event.createdAt
        override val note: String? get() = event.note
    }

    /** HIST-9: episodens första dag ([day] = 1). */
    data class EpisodeStart(val episode: IllnessEpisode, override val date: LocalDate) : DiaryEntry {
        override val id: String get() = "episode-start:${episode.id}"
        override val type: DiaryType get() = DiaryType.ILLNESS
        override val time: LocalTime? get() = null
        override val createdAt: Instant? get() = episode.createdAt
        override val note: String? get() = episode.note
        override val dayPart: Int get() = START_OF_DAY
        val day: Int? get() = illnessDay(episode.start, date)
    }

    /** HIST-9: episodens sista dag; [day] är dag N i sjukdomen ([illnessDay]). */
    data class EpisodeEnd(val episode: IllnessEpisode, override val date: LocalDate) : DiaryEntry {
        override val id: String get() = "episode-end:${episode.id}"
        override val type: DiaryType get() = DiaryType.ILLNESS
        override val time: LocalTime? get() = null
        override val createdAt: Instant? get() = episode.createdAt
        override val note: String? get() = episode.note
        override val dayPart: Int get() = END_OF_DAY
        val day: Int? get() = illnessDay(episode.start, date)
    }

    /** HIST-9: en incheckning under [episode]; [day] är dag N i sjukdomen ([illnessDay]). */
    data class CheckIn(val checkin: Checkin, val episode: IllnessEpisode, override val date: LocalDate) : DiaryEntry {
        override val id: String get() = "checkin:${episode.id}/${checkin.id}"
        override val type: DiaryType get() = DiaryType.ILLNESS
        override val time: LocalTime? get() = checkin.time
        override val createdAt: Instant? get() = checkin.createdAt
        override val note: String? get() = checkin.note
        val day: Int? get() = illnessDay(episode.start, date)
    }

    companion object {
        const val START_OF_DAY = 0
        const val DURING_DAY = 1
        const val END_OF_DAY = 2
    }
}

/** Det Dagboken läser: posterna i fönstret och alla episoder med sina incheckningar (per episod-id). */
data class DiarySources(
    val screenings: List<Screening> = emptyList(),
    val activities: List<Activity> = emptyList(),
    val doses: List<Dose> = emptyList(),
    val events: List<Event> = emptyList(),
    val episodes: List<IllnessEpisode> = emptyList(),
    val checkins: Map<String, List<Checkin>> = emptyMap(),
) {
    operator fun plus(other: DiarySources): DiarySources = DiarySources(
        screenings + other.screenings,
        activities + other.activities,
        doses + other.doses,
        events + other.events,
        episodes + other.episodes,
        checkins + other.checkins,
    )
}

/**
 * HIST-1: alla poster som en tidslinje, nyast först: dag, platsen inom dagen ([DiaryEntry.dayPart]) och sedan
 * [chronological] (klockslag, skapandetid, id – flera poster samma minut står alltid i samma ordning). Bara **tagna** doser, med tagningstiden i
 * [zone] (HIST-7, MED-14); episodens start och slut är egna poster och incheckningarna hör till sin episod
 * (HIST-9). En post utan dag har ingen plats i tidslinjen och tas inte med.
 */
fun diaryEntries(sources: DiarySources, zone: TimeZone): List<DiaryEntry> {
    val entries = buildList {
        sources.screenings.forEach { s -> s.date?.let { add(DiaryEntry.Mood(s, it)) } }
        sources.activities.forEach { a -> a.date?.let { add(DiaryEntry.Action(a, it)) } }
        // En dos kan läsas av två år (läsningen tar med dagen före varje år, HIST-8) – en gång räcker.
        sources.doses.filter { it.status == DoseStatus.TAKEN }.distinctBy { it.id }.forEach { d ->
            val taken = d.takenAt?.toLocalDateTime(zone)
            (taken?.date ?: d.date)?.let { add(DiaryEntry.TakenDose(d, it, taken?.time ?: d.plannedTime)) }
        }
        sources.events.forEach { e -> e.date?.let { add(DiaryEntry.Happening(e, it)) } }
        sources.episodes.forEach { episode ->
            episode.start?.let { add(DiaryEntry.EpisodeStart(episode, it)) }
            episode.end?.let { add(DiaryEntry.EpisodeEnd(episode, it)) }
            sources.checkins[episode.id].orEmpty().forEach { c -> c.date?.let { add(DiaryEntry.CheckIn(c, episode, it)) } }
        }
    }
    val order = compareBy<DiaryEntry>({ it.date }, { it.dayPart }).then(chronological({ it.date }, { it.time }, { it.createdAt }))
    return entries.sortedWith(order.reversed())
}

/** HIST-2: posterna av de typer [filter] visar, i samma ordning. */
fun List<DiaryEntry>.shownBy(filter: DiaryFilter): List<DiaryEntry> = filter { filter.shows(it.type) }

/** Dagarna med minst en post – kalenderns punkter (HIST-6). */
fun List<DiaryEntry>.dates(): Set<LocalDate> = mapTo(mutableSetOf()) { it.date }

/** Posterna på [date] (kalenderns valda dag, HIST-6). */
fun List<DiaryEntry>.on(date: LocalDate): List<DiaryEntry> = filter { it.date == date }

/** Dagsrubrikens slag (HIST-1): "Idag", "Igår" eller bara veckodag och datum. */
enum class DayLabel { TODAY, YESTERDAY, OTHER }

fun dayLabel(date: LocalDate, today: LocalDate): DayLabel = when (date) {
    today -> DayLabel.TODAY
    today.minus(1, DateTimeUnit.DAY) -> DayLabel.YESTERDAY
    else -> DayLabel.OTHER
}

/** En dag i tidslinjen: rubrik och dagens poster, nyast först. */
data class DiaryDay(val date: LocalDate, val label: DayLabel, val entries: List<DiaryEntry>)

/** HIST-1: [entries] (nyast först, [diaryEntries]) per dag, nyaste dagen först; dagar utan poster finns inte. */
fun diaryDays(entries: List<DiaryEntry>, today: LocalDate): List<DiaryDay> =
    entries.groupBy { it.date }.map { (date, day) -> DiaryDay(date, dayLabel(date, today), day) }.sortedByDescending { it.date }

/**
 * HIST-8: Dagbokens fönster – [years] år bakåt, förankrat i [anchor] (dagen fönstret skapades) så att årsgränserna
 * står still: år 0 går från dagen efter [anchor] ett år tidigare till och med [today] (det enda år som växer vid
 * midnatt), år [year] ≥ 1 är fasta år före det. Varje år läses för sig, så att "Visa äldre" ([older]) bara läser
 * det nya året och de tidigare står kvar.
 */
data class DiaryWindow(val anchor: LocalDate, val years: Int = 1, val today: LocalDate = anchor) {
    init {
        require(years >= 1) { "Fönstret är minst ett år" }
        require(today >= anchor) { "Idag är aldrig före ankaret" }
    }

    /** År [index] bakåt: från dagen efter samma datum [index] + 1 år före [anchor] till och med samma datum [index] år före (år 0: till [today]). */
    fun year(index: Int): ClosedRange<LocalDate> =
        anchor.minus(index + 1, DateTimeUnit.YEAR).plus(1, DateTimeUnit.DAY)..(if (index == 0) today else anchor.minus(index, DateTimeUnit.YEAR))

    /** Fönstrets första dag. */
    val from: LocalDate get() = year(years - 1).start

    /** Ett år till bakåt (HIST-8). */
    fun older(): DiaryWindow = copy(years = years + 1)

    /** Fönstret med så många år till att [date] ryms (kalenderns månadsbyte bakåt, HIST-6); oförändrat om det redan gör det. */
    fun covering(date: LocalDate): DiaryWindow {
        var window = this
        while (date < window.from) window = window.older()
        return window
    }

    /** Om [date] ligger i fönstret. */
    operator fun contains(date: LocalDate): Boolean = date >= from && date <= today

    /** [entries] inom fönstret. */
    fun within(entries: List<DiaryEntry>): List<DiaryEntry> = entries.filter { it.date in this }
}
