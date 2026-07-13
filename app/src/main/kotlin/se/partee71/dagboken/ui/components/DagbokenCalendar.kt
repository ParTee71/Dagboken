package se.partee71.dagboken.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.kizitonwose.calendar.compose.HorizontalCalendar
import com.kizitonwose.calendar.compose.rememberCalendarState
import com.kizitonwose.calendar.core.CalendarDay
import com.kizitonwose.calendar.core.DayPosition
import com.kizitonwose.calendar.core.daysOfWeek
import com.kizitonwose.calendar.core.yearMonth
import kotlinx.coroutines.launch
import se.partee71.dagboken.R
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale

/**
 * Delad månadskalender (regel 4). Dagar med minst en post i [datesWithEntries] får en
 * prick under datumsiffran; [selectedDate] markeras med fylld cirkel. Enda call site
 * idag är Historik-kalenderläget (HIST-6), men komponenten tar bara generiska
 * datum-parametrar för att kunna återanvändas.
 */
@Composable
fun DagbokenCalendar(
    datesWithEntries: Set<LocalDate>,
    selectedDate: LocalDate?,
    onDateClick: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
) {
    val currentMonth = remember { YearMonth.now() }
    val startMonth = remember { currentMonth.minusMonths(24) }
    val endMonth = remember { currentMonth.plusMonths(24) }
    val weekDays = remember { daysOfWeek() }
    val calendarState = rememberCalendarState(
        startMonth        = startMonth,
        endMonth          = endMonth,
        firstVisibleMonth = currentMonth,
        firstDayOfWeek    = weekDays.first(),
    )
    val coroutineScope = rememberCoroutineScope()
    val visibleMonth = calendarState.firstVisibleMonth.yearMonth
    val locale = Locale("sv")

    Column(modifier = modifier) {
        Row(
            modifier              = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment     = Alignment.CenterVertically,
        ) {
            IconButton(onClick = {
                coroutineScope.launch { calendarState.animateScrollToMonth(visibleMonth.minusMonths(1)) }
            }) {
                Icon(Icons.Default.ChevronLeft, contentDescription = stringResource(R.string.calendar_previous_month))
            }
            Text(
                text  = "${visibleMonth.month.getDisplayName(TextStyle.FULL, locale)} ${visibleMonth.year}",
                style = MaterialTheme.typography.titleMedium,
            )
            IconButton(onClick = {
                coroutineScope.launch { calendarState.animateScrollToMonth(visibleMonth.plusMonths(1)) }
            }) {
                Icon(Icons.Default.ChevronRight, contentDescription = stringResource(R.string.calendar_next_month))
            }
        }
        Row(modifier = Modifier.fillMaxWidth()) {
            weekDays.forEach { dow ->
                Text(
                    text      = dow.getDisplayName(TextStyle.SHORT, locale),
                    modifier  = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    style     = MaterialTheme.typography.labelSmall,
                    color     = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        HorizontalCalendar(
            state      = calendarState,
            dayContent = { day ->
                CalendarDayCell(
                    day        = day,
                    hasEntries = day.date in datesWithEntries,
                    isSelected = day.date == selectedDate,
                    onClick    = { onDateClick(day.date) },
                )
            },
        )
    }
}

@Composable
private fun CalendarDayCell(
    day: CalendarDay,
    hasEntries: Boolean,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    val isCurrentMonth = day.position == DayPosition.MonthDate

    Box(
        modifier         = Modifier
            .aspectRatio(1f)
            .padding(2.dp)
            .clip(CircleShape)
            .background(if (isSelected) cs.primary else Color.Transparent)
            .then(
                if (isCurrentMonth) {
                    Modifier.clickable(
                        onClickLabel = stringResource(R.string.format_calendar_select_day, day.date.dayOfMonth),
                        role         = Role.Button,
                        onClick      = onClick,
                    )
                } else {
                    Modifier
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text  = day.date.dayOfMonth.toString(),
                color = when {
                    !isCurrentMonth -> cs.onSurfaceVariant.copy(alpha = 0.35f)
                    isSelected -> cs.onPrimary
                    else -> cs.onSurface
                },
                style = MaterialTheme.typography.bodyMedium,
            )
            if (hasEntries && isCurrentMonth) {
                Box(
                    modifier = Modifier
                        .size(4.dp)
                        .clip(CircleShape)
                        .background(if (isSelected) cs.onPrimary else cs.primary),
                )
            }
        }
    }
}
