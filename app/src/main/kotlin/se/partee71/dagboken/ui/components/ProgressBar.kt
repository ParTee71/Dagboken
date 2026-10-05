@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package se.partee71.dagboken.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.unit.dp
import se.partee71.dagboken.R
import se.partee71.dagboken.ui.theme.AppColors
import se.partee71.dagboken.ui.theme.AppShapes
import se.partee71.dagboken.ui.theme.AppTypography
import se.partee71.dagboken.ui.theme.Spacing

/**
 * Framstegsraden (HEM-18): hur många av dagens [total] poster som är klara ([done]), som text ("4 av 9
 * klara") och som ett spår som fylls animerat i teal. När allt är klart blir fyllnaden solgul med en mörk kontur och texten
 * "9 av 9 · allt klart" (belöningsläget, HEM-19, DSN-4). TalkBack läser "x av y klara" och framsteget.
 */
@Composable
fun ProgressBar(done: Int, total: Int, modifier: Modifier = Modifier) {
    val count = done.coerceIn(0, total.coerceAtLeast(0))
    val complete = total > 0 && count == total
    val fraction = if (total > 0) count.toFloat() / total else 0f
    val motion = MaterialTheme.motionScheme
    val animated by animateFloatAsState(fraction, motion.slowSpatialSpec(), label = "framsteg")
    val colors = MaterialTheme.colorScheme
    val fill by animateColorAsState(if (complete) colors.secondary else colors.primary, motion.defaultEffectsSpec(), label = "framstegsfärg")
    val description = stringResource(if (complete) R.string.progress_done_description else R.string.progress_format, count, total)
    Column(
        modifier
            .fillMaxWidth()
            .clearAndSetSemantics {
                contentDescription = description
                progressBarRangeInfo = ProgressBarRangeInfo(fraction, 0f..1f)
            },
        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        Text(
            stringResource(if (complete) R.string.progress_done_format else R.string.progress_format, count, total),
            style = AppTypography.itemSubtitle,
            color = if (complete) colors.onSurface else colors.onSurfaceVariant,
        )
        Box(Modifier.fillMaxWidth().height(TRACK).clip(AppShapes.pill).background(colors.primaryContainer)) {
            // Fjädern får slå över lite (Expressive) – fyllnaden stannar ändå inom spåret.
            // Solgult ensamt syns för dåligt mot kortet – vid klart får fyllnaden en kontur (ThemeContrastTest).
            val outline = if (complete) Modifier.border(OUTLINE, AppColors.extended.sunTone.content, AppShapes.pill) else Modifier
            Box(Modifier.fillMaxHeight().fillMaxWidth(animated.coerceIn(0f, 1f)).clip(AppShapes.pill).background(fill).then(outline))
        }
    }
}

private val TRACK = 8.dp
private val OUTLINE = 1.dp
