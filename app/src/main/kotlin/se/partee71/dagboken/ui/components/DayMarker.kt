package se.partee71.dagboken.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import se.partee71.dagboken.ui.theme.AppColors

/**
 * Dagens markering i `DateStrip` och `DagbokenCalendar` – en form för samma sak (regel 4): [isToday] ger
 * den solgula punkten med ring (som dagens punkt i diagrammen), annars ger [hasEntries] en punkt i teal.
 * På en vald dag ([selected], fylld teal) är punkten och ringen i `onPrimary`. Ytan är alltid lika stor,
 * så att dagar med och utan markering står i linje. Läses inte – dagens knapp säger "idag" och "har poster".
 */
@Composable
internal fun DayMarkerDot(isToday: Boolean, hasEntries: Boolean, selected: Boolean, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    Box(modifier.size(DOT + RING * 2), contentAlignment = Alignment.Center) {
        when {
            // Den solgula punkten har alltid en ring i underlagets kontrastfärg – solgult ensamt syns för
            // dåligt mot det vita kortet och mot ljust teal i mörkt tema (ThemeContrastTest).
            isToday -> Dot(colors.secondary, ring = if (selected) colors.onPrimary else AppColors.extended.sunTone.content)
            hasEntries -> Dot(if (selected) colors.onPrimary else colors.primary)
        }
    }
}

@Composable
private fun Dot(color: Color, ring: Color? = null) {
    val ringed = if (ring == null) Modifier.size(DOT) else Modifier.size(DOT + RING * 2).border(RING, ring, CircleShape).padding(RING)
    Box(ringed.background(color, CircleShape))
}

/** Måndagen i veckan som innehåller datumet – veckan börjar på måndag i både datumremsan och kalendern. */
internal fun LocalDate.weekMonday(): LocalDate = minus(DatePeriod(days = dayOfWeek.ordinal))

internal val ONE_WEEK = DatePeriod(days = 7)
internal const val DAYS_PER_WEEK = 7

/** Avståndet mellan dagens siffra och markeringen under den. */
internal val DAY_MARKER_GAP = 2.dp

private val DOT = 6.dp
private val RING = 1.dp
