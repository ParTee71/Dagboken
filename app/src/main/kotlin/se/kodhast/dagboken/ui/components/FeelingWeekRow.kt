package se.kodhast.dagboken.ui.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import kotlinx.datetime.LocalDate
import se.kodhast.dagboken.R
import se.kodhast.dagboken.core.engine.FeelingDay
import se.kodhast.dagboken.ui.common.DateFormat
import se.kodhast.dagboken.ui.common.FeelingSentenceKind
import se.kodhast.dagboken.ui.common.feelingSentence
import se.kodhast.dagboken.ui.theme.AppShapes

/**
 * Känslornas vecka (TRD-22): ett [FeelingMiniWheel] ([FeelingWheelSize.DAY]) per dag i [days] med veckodagen under, i
 * jämnbreda kolumner. Varje kolumn är en flik som [DateStrip]s dagar – minst 48 dp hög, `AppShapes.smallTile` – som
 * TalkBack läser med datum och dagens mening ("tisdag 6 oktober: Mest glad och lugn.") och som vald för [selected] (ringen
 * runt hjulet; fliken läser valet själv). Tryck ger dagen till [onSelect]; anroparen äger valet.
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
    DayTab(
        selected = selected,
        onClick = onClick,
        shape = AppShapes.smallTile,
        modifier = modifier,
        inner = Modifier.clearAndSetSemantics { contentDescription = spoken }
    ) {
        FeelingMiniWheel(day.tally, sentence, size = FeelingWheelSize.DAY, selected = selected)
        DayTabWeekday(day.date)
    }
}
