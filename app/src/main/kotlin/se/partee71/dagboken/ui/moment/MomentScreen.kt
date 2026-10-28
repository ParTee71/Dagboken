package se.partee71.dagboken.ui.moment

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.findRootCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
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
import se.partee71.dagboken.ui.components.SunsetBreathing
import se.partee71.dagboken.ui.theme.AppColors
import se.partee71.dagboken.ui.theme.AppTypography
import se.partee71.dagboken.ui.theme.DagbokenTheme
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
    // En ny Stunden får en ny skärm, så att dess härledda tillstånd ("klar") läser den nya nedräkningen.
    key(launch) {
        MomentScreen(
            occasion = stringResource(launch.occasion.label()),
            time = DateFormat.time(launch.time),
            prompt = if (launch.question) prompts.getOrNull(ReflectionPrompts.indexFor(today)) else null,
            remainingSeconds = { remaining },
            onChooseFeelings = onChooseFeelings,
            onNotNow = onNotNow
        )
    }
}

/**
 * Stundens helskärm (NOT-20) på sin egen kvällston, oberoende av appens tema (`DagbokenTheme(darkTheme = true)` ger
 * knapparna Stundens färger): ett solnedgångslandskap (`SunsetBreathing`) fyller skärmen – solen andas i 4-4-takt, vid
 * reducerad rörelse står allt stilla utom texten – och innehållet ligger ovanpå, utan toppbar: rubriken "Stunden ·
 * Läggdags 21:00" och uppmaningen överst (hela innehållet rullar om det inte ryms; kullarna följer innehållets överkant), och längst ned på kullarna nedräkningen ("1:40 kvar"), frågan "Vad känner du
 * just nu?" (samma som känslosteget, SCR-7) med [prompt] under (NOT-21), hjälptexten och knapparna. **Välj känslor** är
 * inaktiv tills pausen är klar ([remainingSeconds] = 0) och blir då primär; [remainingSeconds] är en lambda som bara
 * nedräkningen läser, så att resten av skärmen inte räknas om varje sekund; **Inte nu** stänger alltid. Visar bara
 * tillfälle, tid, text och fråga – inga hälsodata.
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
    val colors = AppColors.moment
    // Där innehållet nedtill börjar (andel av höjden): kullarna följer det, så att allt ligger på mörk grund även vid
    // stor text, låg skärm eller lång hjälpfråga – och när innehållet rullar.
    var contentTop by remember { mutableFloatStateOf(Float.NaN) }
    val margin = with(LocalDensity.current) { Spacing.s.toPx() }
    DagbokenTheme(darkTheme = true) {
        Box(modifier.fillMaxSize()) {
            SunsetBreathing(active = !finished, reducedMotion = reducedMotion, hillsLevel = { contentTop })
            BoxWithConstraints(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
                Column(
                    Modifier
                        .verticalScroll(rememberScrollState())
                        .heightIn(min = maxHeight)
                        .padding(horizontal = Spacing.xl, vertical = Spacing.xl),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                        Text(
                            stringResource(R.string.moment_header, occasion, time),
                            Modifier.semantics { heading() },
                            style = AppTypography.itemTitle,
                            color = colors.accent
                        )
                        Text(stringResource(R.string.moment_intro), style = AppTypography.body, color = colors.textMuted)
                    }
                    Column(
                        Modifier.fillMaxWidth().onGloballyPositioned { contentTop = (it.positionInRoot().y - margin) / it.findRootCoordinates().size.height }
                    ) {
                        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Spacing.s), horizontalAlignment = Alignment.CenterHorizontally) {
                            Countdown(remainingSeconds)
                            Text(
                                stringResource(R.string.feelings_title),
                                Modifier.semantics { heading() },
                                style = AppTypography.headline,
                                color = colors.text,
                                textAlign = TextAlign.Center
                            )
                            prompt?.let { Text(it, style = AppTypography.body, color = colors.text, textAlign = TextAlign.Center) }
                            Text(stringResource(R.string.moment_helper), style = AppTypography.caption, color = colors.textMuted, textAlign = TextAlign.Center)
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
            }
        }
    }
}

/** "1:40 kvar" (TalkBack: "1 min 40 s kvar"), och "Pausen är klar" när den är slut. */
@Composable
private fun Countdown(remainingSeconds: () -> Int) {
    val remaining = remainingSeconds()
    val colors = AppColors.moment
    if (remaining <= 0) {
        Text(stringResource(R.string.moment_done), style = AppTypography.countdown, color = colors.accent)
        return
    }
    val spoken = stringResource(R.string.moment_remaining_description, formatDuration(remaining))
    Text(
        stringResource(R.string.moment_remaining, remaining / SECONDS_PER_MINUTE, remaining % SECONDS_PER_MINUTE),
        Modifier.semantics { contentDescription = spoken },
        style = AppTypography.countdown,
        color = colors.textMuted
    )
}

private const val SECOND_MILLIS = 1_000L
