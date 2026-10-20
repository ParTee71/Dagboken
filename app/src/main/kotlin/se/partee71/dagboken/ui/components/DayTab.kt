package se.partee71.dagboken.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import kotlinx.datetime.LocalDate
import se.partee71.dagboken.ui.common.DateFormat
import se.partee71.dagboken.ui.theme.AppTypography
import se.partee71.ui.theme.Spacing

/**
 * Dagfliken – skelettet för en dag i en veckorad, en gång för [DateStrip]s dagar och [FeelingWeekRow]s dagar (regel 4):
 * en centrerad kolumn minst [TOUCH_TARGET] hög, klippt i [shape], med valfri [background], som är en flik
 * (`Role.Tab`) – vald när [selected], tryck ger [onClick], inte valbar när ![enabled]. [inner] läggs efter valet, innanför
 * tryckytan: dagens egen semantik, tonat läge och inre marginal. Veckodagen skrivs med [DayTabWeekday].
 */
@Composable
internal fun DayTab(
    selected: Boolean,
    onClick: () -> Unit,
    shape: Shape,
    modifier: Modifier = Modifier,
    background: Color? = null,
    enabled: Boolean = true,
    inner: Modifier = Modifier,
    verticalArrangement: Arrangement.Vertical = Arrangement.spacedBy(Spacing.xs),
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier
            .heightIn(min = TOUCH_TARGET)
            .clip(shape)
            .then(if (background != null) Modifier.background(background) else Modifier)
            .selectable(selected = selected, enabled = enabled, role = Role.Tab, onClick = onClick)
            .then(inner),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = verticalArrangement,
        content = content
    )
}

/** Dagflikens veckodag ("mån") – liten text i [color], som standard `onSurfaceVariant`, centrerad. */
@Composable
internal fun DayTabWeekday(day: LocalDate, color: Color = MaterialTheme.colorScheme.onSurfaceVariant) {
    Text(DateFormat.weekdayShort(day), style = AppTypography.caption, color = color, textAlign = TextAlign.Center, maxLines = 1)
}
