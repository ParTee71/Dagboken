package se.partee71.dagboken.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.stateDescription
import kotlinx.datetime.LocalDate
import se.partee71.dagboken.R
import se.partee71.dagboken.core.engine.FeelingDay
import se.partee71.dagboken.ui.common.DateFormat
import se.partee71.dagboken.ui.common.FeelingSentenceKind
import se.partee71.dagboken.ui.common.feelingSentence
import se.partee71.dagboken.ui.theme.AppShapes
import se.partee71.dagboken.ui.theme.AppTypography
import se.partee71.dagboken.ui.theme.Spacing

/**
 * Känslornas vecka (TRD-22): ett [FeelingMiniWheel] ([FeelingWheelSize.DAY]) per dag i [days] med veckodagen under, i
 * jämnbreda kolumner. Varje kolumn är en flik som [DateStrip]s dagar – minst 48 dp hög, `AppShapes.smallTile` – som
 * TalkBack läser med datum och dagens mening ("tisdag 6 oktober: Mest glad och lugn.") och "vald" för [selected] (ringen
 * runt hjulet). Tryck ger dagen till [onSelect]; anroparen äger valet.
 */
@Composable
fun FeelingWeekRow(days: List<FeelingDay>, selected: LocalDate?, onSelect: (LocalDate) -> Unit, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth().selectableGroup()) {
        days.forEach { day -> WeekDay(day, day.date == selected, Modifier.weight(1f)) { onSelect(day.date) } }
    }
}

@Composable
private fun WeekDay(day: FeelingDay, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val sentence = feelingSentence(day.summary, FeelingSentenceKind.DAY)
    val spoken = stringResource(R.string.feeling_week_day_format, DateFormat.weekdayDayAndMonth(day.date), sentence)
    val chosen = stringResource(R.string.feeling_selected_state)
    Column(
        modifier
            .heightIn(min = TOUCH_TARGET)
            .clip(AppShapes.smallTile)
            .selectable(selected = selected, role = Role.Tab, onClick = onClick)
            .clearAndSetSemantics {
                contentDescription = spoken
                if (selected) stateDescription = chosen
            },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.xs)
    ) {
        FeelingMiniWheel(day.tally, sentence, size = FeelingWheelSize.DAY, selected = selected)
        Text(DateFormat.weekdayShort(day.date), style = AppTypography.caption, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
    }
}
