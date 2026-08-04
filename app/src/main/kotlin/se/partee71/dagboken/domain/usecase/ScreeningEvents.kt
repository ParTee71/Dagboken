package se.partee71.dagboken.domain.usecase

import se.partee71.dagboken.data.datastore.SCREENING_EVENT_LABELS
import se.partee71.dagboken.data.datastore.ScreeningEventConfig
import se.partee71.dagboken.data.datastore.ScreeningTime
import se.partee71.dagboken.domain.model.Aktivitet
import java.time.LocalTime

data class ScreeningEventStatus(
    val label: String,
    val time: String,
    val logged: Boolean,
    val overdue: Boolean,
)

/**
 * Aktiverade screeningtillfällen (namn + klockslag) — index i [configs] motsvarar index i
 * [SCREENING_EVENT_LABELS].
 */
fun activeScreeningEventLabels(configs: List<ScreeningEventConfig>): List<Pair<String, String>> =
    configs.mapIndexedNotNull { i, c ->
        if (c.enabled) SCREENING_EVENT_LABELS.getOrNull(i)?.let { label -> label to c.time } else null
    }

/**
 * Status för dagens aktiverade screeningtillfällen. Ren funktion, delad mellan
 * `HomeViewModel` (Idag-vyn); delades tidigare även med screeningwidgeten (#161,
 * borttagen i #177).
 */
fun computeScreeningEvents(
    activeEvents: List<Pair<String, String>>,
    screeningsForDate: List<Aktivitet>,
    nowTime: LocalTime,
    isToday: Boolean,
): List<ScreeningEventStatus> = activeEvents.map { (label, timeStr) ->
    val st = ScreeningTime.parse(timeStr)
    val reminderTime = st?.let { LocalTime.of(it.hour, it.min) }
    val logged = screeningsForDate.any { it.aktivitet == label }
    val overdue = isToday && !logged && reminderTime != null && nowTime.isAfter(reminderTime)
    ScreeningEventStatus(label = label, time = timeStr, logged = logged, overdue = overdue)
}
