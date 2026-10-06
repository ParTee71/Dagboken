package se.partee71.dagboken.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SelectableChipColors
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import se.partee71.dagboken.R
import se.partee71.dagboken.ui.theme.AppShapes
import se.partee71.dagboken.ui.theme.AppTypography
import se.partee71.dagboken.ui.theme.IconSize

/**
 * Valchip: bock och korallton när det är valt. Används ensamt eller i en [ChipRow]. Med [onLongClick]
 * öppnar ett långtryck något mer – t.ex. vid behov-snabbvalets meny på Idag (HEM-11) – och TalkBack
 * erbjuder det som åtgärd med [onLongClickLabel]; tryck är fortfarande [onClick]. Långtrycket fungerar
 * också när chipet är inaktivt ([enabled] = false) – menyn ska nås även när tryck inte går.
 */
@Composable
fun AppFilterChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    @DrawableRes icon: Int? = null,
    onLongClick: (() -> Unit)? = null,
    onLongClickLabel: String? = null,
) {
    val leading = if (selected) R.drawable.ic_check else icon
    // Gesten startas en gång (nyckel Unit) och läser alltid den senaste callbacken.
    val longClick by rememberUpdatedState(onLongClick)
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label, style = AppTypography.pill) },
        modifier = modifier.heightIn(min = CHIP_HEIGHT).then(if (onLongClick != null) Modifier.longPress({ longClick?.invoke() }, onLongClickLabel) else Modifier),
        enabled = enabled,
        leadingIcon = leading?.let { { Icon(painterResource(it), contentDescription = null, modifier = Modifier.size(IconSize.tile)) } },
        shape = AppShapes.pill,
        colors = chipColors(),
    )
}

/** Samma färger för alla chips – även [PersonChip]. */
@Composable
internal fun chipColors(): SelectableChipColors = FilterChipDefaults.filterChipColors(
    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
    selectedLeadingIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
    labelColor = MaterialTheme.colorScheme.onSurface,
)

/**
 * Långtryck på en yta vars klick ägs av en Material-komponent utan eget långtryck. `FilterChip` tar bara
 * `onClick`, och `combinedClickable` går inte att lägga ovanpå utan att chipets egen klickyta (ripple,
 * vald-semantik, inaktivt läge) försvinner – därför en egen gest. Den läses före komponenten
 * ([PointerEventPass.Initial]) och avbryts, som `detectTapGestures`, när fingret släpps, rör sig mer än
 * touch slop (en rullning som börjar på chipet går vidare till listan) eller när någon annan förbrukat
 * gesten. Först efter ett långtryck förbrukas resten av gesten, så att komponentens klick inte också körs.
 * Fungerar även när chipet är inaktivt (menyn nås fortfarande). TalkBack får långtrycket som åtgärd.
 */
private fun Modifier.longPress(onLongClick: () -> Unit, label: String?): Modifier =
    semantics { onLongClick(label) { onLongClick(); true } }
        .pointerInput(Unit) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                val cancelled = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
                    while (true) {
                        val change = awaitPointerEvent(PointerEventPass.Initial).changes.firstOrNull { it.id == down.id }
                        val moved = change != null && (change.position - down.position).getDistance() > viewConfiguration.touchSlop
                        if (change == null || !change.pressed || change.isConsumed || moved) break
                    }
                    true
                }
                if (cancelled == true) return@awaitEachGesture
                onLongClick()
                do {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    event.changes.forEach { it.consume() }
                } while (event.changes.any { it.pressed })
            }
        }

private val CHIP_HEIGHT = 36.dp
