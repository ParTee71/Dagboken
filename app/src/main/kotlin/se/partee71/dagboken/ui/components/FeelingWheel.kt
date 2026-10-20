package se.partee71.dagboken.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import se.partee71.dagboken.R
import se.partee71.dagboken.core.model.FeelingCatalog
import se.partee71.dagboken.core.model.FeelingStrength
import se.partee71.dagboken.ui.common.feelingName
import se.partee71.dagboken.ui.common.withStrength
import se.partee71.dagboken.ui.theme.AppColors
import se.partee71.dagboken.ui.theme.AppShapes
import se.partee71.dagboken.ui.theme.AppTypography
import se.partee71.dagboken.ui.theme.FeelingColors
import se.partee71.ui.theme.Spacing

/**
 * Känslohjulet (SCR-7, SCR-8): de sex kärnkänslorna ur [FeelingCatalog] som sektorer runt ett nav, i hjulets ordning
 * medurs från klockan 12, var och en i sin ton (`AppColors.extended.feelingTones`) med namnet vågrätt i sektorn. En
 * kärna i [selected] (kärntokens som har minst ett val) fylls med tonens innehållsfärg och läses "vald"; med en styrka i
 * [strengths] (kärnans starkaste val, SCR-9) fylls den i styrkans intensitet och läser styrkan efter namnet. Tryck var som
 * helst i en sektor anropar [onCoreClick] med kärnans token; för TalkBack är varje sektor en knapp (minst 48 dp) med
 * kärnans namn. Ingången i `FeelingPicker`.
 */
@Composable
fun FeelingWheel(selected: Set<String>, onCoreClick: (String) -> Unit, modifier: Modifier = Modifier, strengths: Map<String, FeelingStrength> = emptyMap()) {
    val tones = AppColors.extended.feelingTones
    val cores = FeelingCatalog.cores
    val click by rememberUpdatedState(onCoreClick)
    BoxWithConstraints(modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        val diameter = min(maxWidth, WHEEL_MAX)
        Box(
            Modifier.size(diameter)
                .feelingSectors(cores.map { core -> tones.chosen(core, core in selected, strengths[core]).container }, GAP, HUB_FRACTION)
                .pointerInput(cores) { detectTapGestures { position -> coreAt(position, size.width / 2f, HUB_FRACTION)?.let { click(cores[it]) } } }
        ) {
            cores.forEachIndexed { index, core ->
                SectorLabel(core, core in selected, strengths[core], tones, sectorOffset(index, diameter / 2 * LABEL_FRACTION)) { click(core) }
            }
            Text(
                stringResource(R.string.feelings_wheel_center),
                Modifier.align(Alignment.Center).clearAndSetSemantics {},
                style = AppTypography.caption,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/** Kärnans namn i sektorn – tryckytan och TalkBack-noden för sektorn ("Lugn", "vald"). */
@Composable
private fun BoxScope.SectorLabel(
    core: String,
    isSelected: Boolean,
    strength: FeelingStrength?,
    tones: FeelingColors,
    offset: Pair<Dp, Dp>,
    onClick: () -> Unit
) {
    val name = stringResource(feelingName(core))
    val description = withStrength(name, strength)
    val selectedState = stringResource(R.string.feeling_selected_state)
    val colors = tones.chosen(core, isSelected, strength)
    Box(
        Modifier.align(Alignment.Center)
            .offset(offset.first, offset.second)
            .sizeIn(minWidth = TOUCH_TARGET, minHeight = TOUCH_TARGET)
            .clip(AppShapes.pill)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics {
                contentDescription = description
                if (isSelected) stateDescription = selectedState
            }
            .padding(horizontal = Spacing.xs),
        contentAlignment = Alignment.Center
    ) {
        Text(
            name,
            Modifier.clearAndSetSemantics {},
            style = AppTypography.itemTitle,
            color = colors.content,
            textAlign = TextAlign.Center,
            maxLines = 1
        )
    }
}

/** Hjulets mellanrum och nav. */
private val GAP = 3.dp
private const val HUB_FRACTION = 0.2f
