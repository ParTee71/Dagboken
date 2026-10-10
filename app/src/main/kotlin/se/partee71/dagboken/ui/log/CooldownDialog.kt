package se.partee71.dagboken.ui.log

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import kotlin.math.ceil
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.DurationUnit
import se.partee71.dagboken.R
import se.partee71.dagboken.core.model.PrnMedicine
import se.partee71.dagboken.ui.components.ConfirmDialog

/** "För tidigt" (FAV-4): [remaining] kvar av kylperioden för [medicine]. */
data class CooldownPrompt(val medicine: PrnMedicine, val remaining: Duration)

/**
 * "För tidigt" (FAV-4): kvarvarande tid och "Ta ändå" – en gång för snabbvalen på Idag och vid behov i efterhand
 * (MED-16), som båda prövar kylperioden med `DoseRepository.logAsNeeded`.
 */
@Composable
fun CooldownDialog(prompt: CooldownPrompt, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    ConfirmDialog(
        stringResource(R.string.today_cooldown_title),
        cooldownText(R.string.today_cooldown_message, prompt),
        stringResource(R.string.today_take_anyway),
        onConfirm = onConfirm,
        onDismiss = onDismiss,
    )
}

/** [format] med kvarvarande timmar, minuter och medicinens namn ("Du bör vänta 1h 20m till för Alvedon."). */
@Composable
fun cooldownText(@StringRes format: Int, prompt: CooldownPrompt): String {
    val (hours, minutes) = prompt.remaining.hoursAndMinutes()
    return stringResource(format, hours.toString(), minutes.toString(), prompt.medicine.displayName)
}

/** Hela timmar och påbörjade minuter – "0h 1m" hellre än "0h 0m" när några sekunder återstår. */
private fun Duration.hoursAndMinutes(): Pair<Long, Int> =
    ceil(toDouble(DurationUnit.MINUTES)).toLong().minutes.toComponents { hours, minutes, _, _ -> hours to minutes }
