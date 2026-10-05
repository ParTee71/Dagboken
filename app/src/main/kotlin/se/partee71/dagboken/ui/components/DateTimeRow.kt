package se.partee71.dagboken.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import se.partee71.dagboken.R
import se.partee71.dagboken.ui.theme.Spacing

/**
 * Datum och tid för en post på en rad: [DateField] och [TimeField] bredvid varandra, med samma
 * väljare och samma TalkBack-läsning som fälten var för sig.
 */
@Composable
fun DateTimeRow(
    date: LocalDate,
    time: LocalTime,
    onDateChange: (LocalDate) -> Unit,
    onTimeChange: (LocalTime) -> Unit,
    modifier: Modifier = Modifier,
    dateLabel: String = stringResource(R.string.date),
    timeLabel: String = stringResource(R.string.time),
) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
        DateField(dateLabel, date, onDateChange, Modifier.weight(DATE_WEIGHT))
        TimeField(timeLabel, time, onTimeChange, Modifier.weight(1f))
    }
}

/** Datumet ("lör 19 dec 2026") behöver mer plats än tiden ("07:00"). */
private const val DATE_WEIGHT = 1.6f
