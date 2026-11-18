package se.kodhast.dagboken.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import java.util.Locale
import kotlin.math.abs
import se.kodhast.dagboken.R
import se.kodhast.dagboken.ui.theme.Positive
import se.kodhast.ui.components.Confetti
import se.kodhast.ui.components.KeyFigure
import se.kodhast.ui.components.SectionCard
import se.kodhast.ui.theme.Spacing
import se.kodhast.ui.theme.Tone

/**
 * Dagen klar (belöningsläget, HEM-19, DSN-4): ett grönt kort med rubriken "Allt klart för idag" – en
 * rubrik med bock i grön ton – och tre nyckeltal: dagens snittenergi ([averageEnergy]), skillnaden mot igår ([versusYesterday], med tecken)
 * och dagar i rad ([streakDays]). Saknas underlag (`null`) står "—" (`R.string.value_missing`, samma som `StatPill`). Över kortet faller [Confetti] när
 * [play] blir sant; [onConfettiFinished] säger till när regnet är över, så att anroparen (som äger
 * "en gång per dag") kan slå av [play]. Kortet räknar inget självt – värdena kommer från `:core`.
 */
@Composable
fun DayDoneCard(
    averageEnergy: Double?,
    versusYesterday: Double?,
    streakDays: Int?,
    modifier: Modifier = Modifier,
    play: Boolean = false,
    onConfettiFinished: () -> Unit = {}
) {
    val missing = stringResource(R.string.value_missing)
    Box(modifier) {
        SectionCard(stringResource(R.string.day_done_title), icon = R.drawable.ic_check, tone = Tone.Positive, contentColor = MaterialTheme.colorScheme.onSurface) {
            Row(Modifier.semantics(mergeDescendants = true) {}, horizontalArrangement = Arrangement.spacedBy(Spacing.l)) {
                KeyFigure(averageEnergy?.let(::decimal) ?: missing, stringResource(R.string.day_done_average_energy), Modifier.weight(1f), Tone.Positive)
                KeyFigure(versusYesterday?.let(::signedDecimal) ?: missing, stringResource(R.string.day_done_vs_yesterday), Modifier.weight(1f), Tone.Positive)
                KeyFigure(streakDays?.toString() ?: missing, stringResource(R.string.day_done_streak), Modifier.weight(1f), Tone.Positive)
            }
        }
        Confetti(Modifier.matchParentSize(), play = play, onFinished = onConfettiFinished)
    }
}

private val swedish = Locale.forLanguageTag("sv-SE")

/** "6,8" – en decimal med svenskt kommatecken. */
private fun decimal(value: Double): String = "%.1f".format(swedish, value)

/** "+0,5", "−0,3", "0,0" – med typografiskt minustecken, som `scaleValueText`. */
private fun signedDecimal(value: Double): String {
    val text = decimal(abs(value))
    return when {
        text == decimal(0.0) -> text
        value > 0 -> "+$text"
        else -> "−$text"
    }
}
