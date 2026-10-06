package se.partee71.dagboken.core.engine

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import se.partee71.dagboken.core.model.Dose
import se.partee71.dagboken.core.model.DoseStatus
import se.partee71.dagboken.core.model.Occasion
import se.partee71.dagboken.core.model.OccasionReminder
import se.partee71.dagboken.core.model.ReminderSettings
import se.partee71.dagboken.core.model.Screening
import se.partee71.dagboken.core.model.Slot

// Dagens framsteg på Idag (HEM-18, HEM-19, HEM-20) – ren beräkning över dagens doser och
// måendeloggar; repositoryt läser dem, ViewModel visar resultatet.

/**
 * Om dosen hör till schemat – en receptdos (eller en migrerad 3.x-dos med schemalagd tidpunkt) – och
 * inte en vid behov-dos: tidpunkten är inte "Vid behov" och den är inte loggad från en vid behov-medicin
 * (en 3.x-favorit kan ha en lagrad tidpunkt, FAV-1). Samma urval som 3.x veckosammanfattning
 * (`tidpunktToHour != null`), och det som räknas i framstegen (HEM-18) och veckans dosandel (HEM-13).
 */
val Dose.isScheduled: Boolean get() = slot != Slot.AS_NEEDED && prnId == null

/**
 * MED-3: en receptdos i schemat ([isScheduled] och [isPrescribed]) – den hoppas över i stället för att raderas. En
 * engångsdos med en tidpunkt står i schemat (som 3.x) men är ingen receptdos, och raderas.
 */
val Dose.isScheduledPrescription: Boolean get() = isScheduled && isPrescribed

/** En schemalagd dos som är avklarad: tagen eller överhoppad (HEM-19). */
val Dose.isDone: Boolean get() = status == DoseStatus.TAKEN || status == DoseStatus.SKIPPED

/**
 * De aktiverade måendetillfällenas påminnelserader (HEM-4) – en per tillfälle (den första), i lagrad
 * ordning. Den enda regeln för vilka tillfällen Idag visar ([occasionStates]) och räknar ([dayProgress]).
 */
val ReminderSettings.enabledOccasionRows: List<OccasionReminder> get() = screeningOccasions.filter { it.enabled }.distinctBy { it.occasion }

/** De aktiverade måendetillfällena ([enabledOccasionRows]). */
val ReminderSettings.enabledOccasions: List<Occasion> get() = enabledOccasionRows.map { it.occasion }

/** Om måendetillfället [occasion] är loggat bland [screenings] (en dags måendeloggar). */
fun List<Screening>.hasLogged(occasion: Occasion): Boolean = any { it.occasion == occasion }

/**
 * HEM-18: hur många av dagens poster som är klara. Posterna är dagens schemalagda doser ([isScheduled];
 * vid behov-doser räknas inte) och de aktiverade måendetillfällena; klara är tagna eller överhoppade
 * doser ([isDone]) och tillfällen med en måendelogg.
 */
data class DayProgress(val done: Int, val total: Int) {
    /**
     * HEM-19: allt är klart. En dag utan något att klara (inga schemalagda doser, inga aktiverade
     * tillfällen) är **inte** klar – den ger varken belöning eller förlänger en svit (HEM-20).
     */
    val isComplete: Boolean get() = total > 0 && done >= total
}

/** [DayProgress] för [date] ur doser och måendeloggar (andra dagars poster i listorna hoppas över). */
fun dayProgress(date: LocalDate, doses: List<Dose>, screenings: List<Screening>, occasions: List<Occasion>): DayProgress {
    val scheduled = doses.filter { it.date == date && it.isScheduled }
    val logged = screenings.filter { it.date == date }
    val occasionsDone = occasions.distinct().count { logged.hasLogged(it) }
    return DayProgress(done = scheduled.count { it.isDone } + occasionsDone, total = scheduled.size + occasions.distinct().size)
}

/**
 * HEM-20: antal **klara dagar i följd** ([DayProgress.isComplete]) till och med [today]. Idag räknas
 * bara när den redan är klar – annars räknas sviten från igår, så att den inte nollställs bara för att
 * dagen inte är slut. En dag som inte är klar (också en dag utan poster) bryter sviten. Tillfällena är de
 * aktiverade nu ([occasions]) för varje dag – inställningarna har ingen historik. Sviten kan inte bli
 * längre än de dagar [doses] och [screenings] täcker.
 */
fun dayStreak(today: LocalDate, doses: List<Dose>, screenings: List<Screening>, occasions: List<Occasion>): Int {
    val dosesByDate = doses.groupBy { it.date }
    val screeningsByDate = screenings.groupBy { it.date }
    fun complete(date: LocalDate) =
        dayProgress(date, dosesByDate[date].orEmpty(), screeningsByDate[date].orEmpty(), occasions).isComplete
    // Varje klar dag har minst en post (total > 0), så slingan slutar senast vid den äldsta dagen med data.
    var day = if (complete(today)) today else today.minus(1, DateTimeUnit.DAY)
    var streak = 0
    while (complete(day)) {
        streak++
        day = day.minus(1, DateTimeUnit.DAY)
    }
    return streak
}
