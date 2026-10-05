package se.partee71.dagboken.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import se.partee71.dagboken.R
import se.partee71.dagboken.ui.common.DateFormat
import se.partee71.dagboken.ui.theme.AppShapes
import se.partee71.dagboken.ui.theme.AppTypography
import se.partee71.dagboken.ui.theme.Spacing

/**
 * Månadskalender (HIST-6): månaden som innehåller [month], veckan börjar på måndag. Markeringarna är
 * datumremsans ([DayMarkerDot]): dagar i [datesWithEntries] får en punkt och [today] den solgula punkten
 * med ring; [selectedDate] är fylld teal i `AppShapes.row`. Pilarna
 * byter månad via [onMonthChange] (första dagen i den nya månaden). Varje dag är en knapp som TalkBack
 * läser med datum och "har poster"; dagar utanför månaden visas inte.
 */
@Composable
fun DagbokenCalendar(
    month: LocalDate,
    onMonthChange: (LocalDate) -> Unit,
    datesWithEntries: Set<LocalDate>,
    selectedDate: LocalDate?,
    onDateClick: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
    today: LocalDate? = null,
) {
    val first = remember(month) { LocalDate(month.year, month.month, 1) }
    val weeks = remember(first) { monthGrid(first) }
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AppIconButton(R.drawable.ic_chevron_left, stringResource(R.string.calendar_previous_month), { onMonthChange(first.minus(ONE_MONTH)) })
            Text(
                DateFormat.month(first).replaceFirstChar { it.uppercase() },
                style = AppTypography.sectionTitle,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f).semantics { heading() },
            )
            AppIconButton(R.drawable.ic_chevron_right, stringResource(R.string.calendar_next_month), { onMonthChange(first.plus(ONE_MONTH)) })
        }
        Row(Modifier.clearAndSetSemantics {}) {
            weeks.first().forEach { day ->
                Text(
                    DateFormat.weekdayShort(day),
                    style = AppTypography.caption,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        weeks.forEach { week ->
            Row {
                week.forEach { day ->
                    Box(Modifier.weight(1f).height(TOUCH_TARGET), contentAlignment = Alignment.Center) {
                        if (day.month == first.month) {
                            DayCell(day, day in datesWithEntries, day == selectedDate, day == today) { onDateClick(day) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DayCell(day: LocalDate, hasEntries: Boolean, selected: Boolean, isToday: Boolean, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    // Samma uppläsning som datumremsan: datum, "idag", "har poster".
    val description = listOfNotNull(
        DateFormat.display(day),
        stringResource(R.string.date_strip_today).takeIf { isToday },
        stringResource(R.string.calendar_has_entries).takeIf { hasEntries },
    ).joinToString(", ")
    // Tryckytan är hela cellen (kolumnens bredd × 48 dp). I en smal kolumn (under 48 dp, t.ex. på
    // 360 dp breda skärmar) utvidgar Compose tryckytan till 48 dp åt sidorna (NFR-14).
    Box(
        Modifier
            .fillMaxSize()
            .clip(AppShapes.row)
            .clickable(role = Role.Button, onClick = onClick)
            .clearAndSetSemantics {
                contentDescription = description
                this.selected = selected
            },
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier
                .size(CELL)
                .clip(AppShapes.row)
                .then(if (selected) Modifier.background(colors.primary) else Modifier),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(DAY_MARKER_GAP, Alignment.CenterVertically),
        ) {
            Text(day.day.toString(), style = AppTypography.body, color = if (selected) colors.onPrimary else colors.onSurface)
            DayMarkerDot(isToday, hasEntries, selected)
        }
    }
}

/** Veckorna (måndag–söndag) som täcker månaden som börjar på [first]. */
private fun monthGrid(first: LocalDate): List<List<LocalDate>> {
    val start = first.weekMonday()
    val next = first.plus(ONE_MONTH)
    return generateSequence(start) { it.plus(ONE_WEEK) }
        .takeWhile { it < next }
        .map { monday -> List(DAYS_PER_WEEK) { monday.plus(DatePeriod(days = it)) } }
        .toList()
}

private val ONE_MONTH = DatePeriod(months = 1)

/** Den synliga dagen – ryms i kolumnen även på 360 dp breda skärmar. */
private val CELL = 40.dp
