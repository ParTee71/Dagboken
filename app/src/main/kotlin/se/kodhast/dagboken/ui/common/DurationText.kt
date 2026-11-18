package se.kodhast.dagboken.ui.common

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.res.stringResource
import se.kodhast.dagboken.R
import se.kodhast.dagboken.core.time.MINUTES_PER_HOUR
import se.kodhast.dagboken.core.time.SECONDS_PER_MINUTE

/** Tidsåtgång i text: "45 min", "2 tim", "1 tim 30 min" (AKT-7) – samma överallt. */
@Composable
@ReadOnlyComposable
fun durationText(minutes: Int): String = twoUnits(minutes, MINUTES_PER_HOUR, R.string.duration_minutes_format, R.string.duration_hours_format, R.string.duration_hours_minutes_format)

/** En kortare längd i sekunder: "45 s", "2 min", "2 min 15 s" (Stunden, NOT-20) – samma överallt. */
@Composable
@ReadOnlyComposable
fun formatDuration(seconds: Int): String = twoUnits(seconds, SECONDS_PER_MINUTE, R.string.duration_seconds_format, R.string.duration_minutes_format, R.string.duration_minutes_seconds_format)

/** [value] i den mindre enheten som "stor", "liten" eller "stor liten" – en gång för [durationText] och [formatDuration]. */
@Composable
@ReadOnlyComposable
private fun twoUnits(value: Int, perLarge: Int, @StringRes small: Int, @StringRes large: Int, @StringRes both: Int): String {
    val whole = value / perLarge
    val rest = value % perLarge
    return when {
        whole == 0 -> stringResource(small, rest)
        rest == 0 -> stringResource(large, whole)
        else -> stringResource(both, whole, rest)
    }
}
