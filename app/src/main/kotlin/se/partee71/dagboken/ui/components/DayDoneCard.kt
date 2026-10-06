package se.partee71.dagboken.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import java.util.Locale
import kotlin.math.abs
import se.partee71.dagboken.R
import se.partee71.dagboken.ui.theme.AppColors
import se.partee71.dagboken.ui.theme.AppTypography
import se.partee71.dagboken.ui.theme.Spacing
import se.partee71.dagboken.ui.theme.Tone

/**
 * Dagen klar (belöningsläget, HEM-19, DSN-4): ett grönt kort med rubriken "Allt klart för idag" – en
 * [SectionHeader] med bock i grön ton – och tre nyckeltal: dagens snittenergi ([averageEnergy]), skillnaden mot igår ([versusYesterday], med tecken)
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
    onConfettiFinished: () -> Unit = {},
) {
    val missing = stringResource(R.string.value_missing)
    Box(modifier) {
        AppCard(tone = Tone.Positive) {
            SectionHeader(stringResource(R.string.day_done_title), icon = R.drawable.ic_check, tone = Tone.Positive)
            Row(Modifier.semantics(mergeDescendants = true) {}, horizontalArrangement = Arrangement.spacedBy(Spacing.l)) {
                KeyFigure(averageEnergy?.let(::decimal) ?: missing, stringResource(R.string.day_done_average_energy), Modifier.weight(1f))
                KeyFigure(versusYesterday?.let(::signedDecimal) ?: missing, stringResource(R.string.day_done_vs_yesterday), Modifier.weight(1f))
                KeyFigure(streakDays?.toString() ?: missing, stringResource(R.string.day_done_streak), Modifier.weight(1f))
            }
        }
        Confetti(Modifier.matchParentSize(), play = play, onFinished = onConfettiFinished)
    }
}

@Composable
private fun KeyFigure(value: String, label: String, modifier: Modifier) {
    val colors = AppColors.tone(Tone.Positive)
    Column(modifier) {
        Text(value, style = AppTypography.bigNumber, color = colors.content, maxLines = 1)
        Text(label, style = AppTypography.itemSubtitle, color = colors.content, maxLines = 2, overflow = TextOverflow.Ellipsis)
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
