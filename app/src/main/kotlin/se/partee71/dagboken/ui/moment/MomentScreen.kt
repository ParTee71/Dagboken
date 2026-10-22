package se.partee71.dagboken.ui.moment

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import kotlinx.coroutines.delay
import kotlinx.datetime.LocalDate
import se.partee71.dagboken.R
import se.partee71.dagboken.core.engine.ReflectionPrompts
import se.partee71.dagboken.core.time.SECONDS_PER_MINUTE
import se.partee71.dagboken.reminders.MomentLaunch
import se.partee71.dagboken.ui.common.DateFormat
import se.partee71.dagboken.ui.common.formatDuration
import se.partee71.dagboken.ui.common.label
import se.partee71.dagboken.ui.common.rememberReducedMotion
import se.partee71.dagboken.ui.components.BreathingCircle
import se.partee71.dagboken.ui.theme.AppTypography
import se.partee71.ui.components.AppButton
import se.partee71.ui.components.ButtonVariant
import se.partee71.ui.theme.Spacing

/**
 * Stunden för [launch] (NOT-20, NOT-21): räknar ned pausen en sekund i taget – nedräkningen överlever en rotation och
 * börjar om för en ny Stunden ([launch]) – och ger dagens hjälpfråga för [today] när den är påslagen
 * ([ReflectionPrompts.indexFor], texterna i `strings.xml`). [onPauseDone] anropas en gång när pausen är klar.
 */
@Composable
fun MomentRoute(
    launch: MomentLaunch,
    today: LocalDate,
    onChooseFeelings: () -> Unit,
    onNotNow: () -> Unit,
    onPauseDone: () -> Unit = {}
) {
    var remaining by rememberSaveable(launch) { mutableIntStateOf(launch.seconds) }
    val done by rememberUpdatedState(onPauseDone)
    LaunchedEffect(launch) {
        while (remaining > 0) {
            delay(SECOND_MILLIS)
            remaining -= 1
        }
        done()
    }
    val prompts = stringArrayResource(R.array.moment_prompts)
    MomentScreen(
        occasion = stringResource(launch.occasion.label()),
        time = DateFormat.time(launch.time),
        prompt = if (launch.question) prompts.getOrNull(ReflectionPrompts.indexFor(today)) else null,
        remainingSeconds = { remaining },
        onChooseFeelings = onChooseFeelings,
        onNotNow = onNotNow
    )
}

/**
 * Stundens helskärm (NOT-20) på appens tema utan toppbar – samma mönster som `EmptyState(fullScreen)`: innehållet i
 * mitten, knapparna längst ned. Rubriken "Stunden · Läggdags 21:00", uppmaningen, andningscirkeln (`BreathingCircle`, 4-4,
 * vid reducerad rörelse bara texten), nedräkningen ("1:40 kvar"), frågan "Vad känner du just nu?" (samma som
 * känslosteget, SCR-7) med [prompt] under (NOT-21) och hjälptexten. **Välj känslor** är inaktiv tills pausen är klar
 * ([remainingSeconds] = 0) och blir då primär; [remainingSeconds] är en lambda som bara nedräkningen läser, så att resten av
 * skärmen inte räknas om varje sekund; **Inte nu** stänger alltid. Visar bara tillfälle, tid, text och fråga –
 * inga hälsodata.
 */
@Composable
fun MomentScreen(
    occasion: String,
    time: String,
    prompt: String?,
    remainingSeconds: () -> Int,
    onChooseFeelings: () -> Unit,
    onNotNow: () -> Unit,
    modifier: Modifier = Modifier,
    reducedMotion: Boolean = rememberReducedMotion()
) {
    val finished by remember { derivedStateOf { remainingSeconds() <= 0 } }
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    Column(
        modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(horizontal = Spacing.xl, vertical = Spacing.xl)
    ) {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            Text(
                stringResource(R.string.moment_header, occasion, time),
                Modifier.semantics { heading() },
                style = AppTypography.itemTitle,
                color = MaterialTheme.colorScheme.primary
            )
            Text(stringResource(R.string.moment_intro), style = AppTypography.body, color = muted)
        }
        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            BreathingCircle(active = !finished, reducedMotion = reducedMotion)
        }
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Spacing.s), horizontalAlignment = Alignment.CenterHorizontally) {
            Countdown(remainingSeconds)
            Text(
                stringResource(R.string.feelings_title),
                Modifier.semantics { heading() },
                style = AppTypography.headline,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center
            )
            prompt?.let { Text(it, style = AppTypography.body, color = MaterialTheme.colorScheme.onSurface, textAlign = TextAlign.Center) }
            Text(stringResource(R.string.moment_helper), style = AppTypography.caption, color = muted, textAlign = TextAlign.Center)
        }
        Column(Modifier.fillMaxWidth().padding(top = Spacing.xl), verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
            AppButton(
                stringResource(R.string.moment_choose_feelings),
                onChooseFeelings,
                Modifier.fillMaxWidth(),
                variant = if (finished) ButtonVariant.Primary else ButtonVariant.Secondary,
                enabled = finished
            )
            AppButton(stringResource(R.string.moment_not_now), onNotNow, Modifier.fillMaxWidth(), variant = ButtonVariant.Text)
        }
    }
}

/** "1:40 kvar" (TalkBack: "1 min 40 s kvar"), och "Pausen är klar" när den är slut. */
@Composable
private fun Countdown(remainingSeconds: () -> Int) {
    val remaining = remainingSeconds()
    if (remaining <= 0) {
        Text(stringResource(R.string.moment_done), style = AppTypography.quantity, color = MaterialTheme.colorScheme.primary)
        return
    }
    val spoken = stringResource(R.string.moment_remaining_description, formatDuration(remaining))
    Text(
        stringResource(R.string.moment_remaining, remaining / SECONDS_PER_MINUTE, remaining % SECONDS_PER_MINUTE),
        Modifier.semantics { contentDescription = spoken },
        style = AppTypography.quantity,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

private const val SECOND_MILLIS = 1_000L
