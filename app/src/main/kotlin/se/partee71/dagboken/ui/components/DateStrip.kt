package se.partee71.dagboken.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import se.partee71.dagboken.R
import se.partee71.dagboken.ui.common.DateFormat
import se.partee71.dagboken.ui.theme.AppColors
import se.partee71.dagboken.ui.theme.AppShapes
import se.partee71.dagboken.ui.theme.AppTypography
import se.partee71.dagboken.ui.theme.IconSize
import se.partee71.dagboken.ui.theme.Spacing

/**
 * Datumremsan (HEM-14): veckan som innehåller [week] (måndag–söndag) som sju chips med veckodag, datum
 * och en markering. [selectedDate] är fylld teal; dagar i [datesWithEntries] får en punkt, [today] en
 * solgul punkt med ring – och en bock när [todayDone] (HEM-19, läses som "klar"). Dagar efter [today] är tonade
 * och går inte att välja. Svep åt höger visar föregående vecka och åt vänster nästa (aldrig en vecka som
 * bara har framtida dagar): [onWeekChange] får den nya veckans måndag, och anroparen äger veckan – så att
 * remsan är deterministisk. Samma byten finns som TalkBack-åtgärder.
 */
@Composable
fun DateStrip(
    week: LocalDate,
    selectedDate: LocalDate,
    onSelect: (LocalDate) -> Unit,
    onWeekChange: (LocalDate) -> Unit,
    today: LocalDate,
    modifier: Modifier = Modifier,
    datesWithEntries: Set<LocalDate> = emptySet(),
    todayDone: Boolean = false,
) {
    val monday = week.weekMonday()
    val previous = monday.minus(ONE_WEEK)
    val next = monday.plus(ONE_WEEK).takeIf { it <= today }
    val change by rememberUpdatedState(onWeekChange)
    val threshold = with(LocalDensity.current) { SWIPE_THRESHOLD.toPx() }
    val previousLabel = stringResource(R.string.date_strip_previous_week)
    val nextLabel = stringResource(R.string.date_strip_next_week)
    Row(
        modifier
            .fillMaxWidth()
            .pointerInput(monday, next) {
                var drag = 0f
                detectHorizontalDragGestures(
                    onDragStart = { drag = 0f },
                    onDragEnd = {
                        when {
                            drag > threshold -> change(previous)
                            drag < -threshold && next != null -> change(next)
                        }
                    },
                ) { _, amount -> drag += amount }
            }
            .semantics {
                customActions = listOfNotNull(
                    CustomAccessibilityAction(previousLabel) { change(previous); true },
                    next?.let { CustomAccessibilityAction(nextLabel) { change(it); true } },
                )
            },
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        val days = remember(monday) { List(DAYS_PER_WEEK) { monday.plus(DatePeriod(days = it)) } }
        days.forEach { day ->
            DayChip(
                day = day,
                selected = day == selectedDate,
                isToday = day == today,
                future = day > today,
                hasEntries = day in datesWithEntries,
                done = todayDone && day == today,
                onClick = { onSelect(day) },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun DayChip(
    day: LocalDate,
    selected: Boolean,
    isToday: Boolean,
    future: Boolean,
    hasEntries: Boolean,
    done: Boolean,
    onClick: () -> Unit,
    modifier: Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val content = if (selected) colors.onPrimary else colors.onSurface
    val muted = if (selected) colors.onPrimary else colors.onSurfaceVariant
    val description = listOfNotNull(
        DateFormat.display(day),
        stringResource(R.string.date_strip_today).takeIf { isToday },
        stringResource(R.string.calendar_has_entries).takeIf { hasEntries },
    ).joinToString(", ")
    val doneLabel = stringResource(R.string.date_strip_done)
    Column(
        modifier
            .heightIn(min = TOUCH_TARGET)
            .clip(AppShapes.row)
            .background(if (selected) colors.primary else AppColors.extended.card)
            .selectable(selected = selected, enabled = !future, role = Role.Tab, onClick = onClick)
            .semantics {
                contentDescription = description
                if (done) stateDescription = doneLabel
            }
            .inactive(future)
            .padding(vertical = Spacing.s),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(DAY_MARKER_GAP),
    ) {
        Column(Modifier.clearAndSetSemantics {}, horizontalAlignment = Alignment.CenterHorizontally) {
            Text(DateFormat.weekdayShort(day), style = AppTypography.caption, color = muted, textAlign = TextAlign.Center, maxLines = 1)
            Text(day.day.toString(), style = AppTypography.itemTitle, color = content, textAlign = TextAlign.Center, maxLines = 1)
        }
        Box(Modifier.size(IconSize.marker), contentAlignment = Alignment.Center) {
            if (done) {
                Icon(painterResource(R.drawable.ic_check), contentDescription = null, tint = if (selected) colors.onPrimary else colors.primary, modifier = Modifier.size(IconSize.marker))
            } else {
                DayMarkerDot(isToday, hasEntries, selected)
            }
        }
    }
}

/** Så långt ett svep måste gå för att byta vecka – kortare dragningar är bara ett tryck som gled. */
private val SWIPE_THRESHOLD = 48.dp
