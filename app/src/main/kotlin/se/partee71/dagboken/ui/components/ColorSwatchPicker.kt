package se.partee71.dagboken.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import se.partee71.dagboken.R
import se.partee71.dagboken.ui.theme.AppColors
import se.partee71.dagboken.ui.theme.AppShapes

/**
 * Välj en av de valbara färgerna (`AppColors.SWATCH_HEX`) som hex – det som lagras. Den valda har
 * en ring och en bock; TalkBack läser färgens namn.
 */
@Composable
fun ColorSwatchPicker(selected: String, onSelect: (String) -> Unit, modifier: Modifier = Modifier) {
    val names = stringArrayResource(R.array.swatch_color_names)
    val hexes = AppColors.SWATCH_HEX
    // Ett lagrat värde med annan skiftläge (t.ex. från tools/db) är samma färg.
    val current = hexes.firstOrNull { it.equals(selected, ignoreCase = true) } ?: selected
    ChoiceGrid(
        hexes,
        current,
        onSelect,
        label = { stringResource(R.string.color_format, names.getOrElse(hexes.indexOf(it)) { "" }) },
        modifier = modifier,
        columns = COLUMNS,
    ) { hex, isSelected ->
        val ring = if (isSelected) Modifier.border(RING, MaterialTheme.colorScheme.onSurface, AppShapes.pill) else Modifier
        Box(ring.fillMaxSize().padding(if (isSelected) RING_GAP else 0.dp), contentAlignment = Alignment.Center) {
            Box(Modifier.fillMaxSize().background(AppColors.swatch(hex), AppShapes.pill), contentAlignment = Alignment.Center) {
                if (isSelected) Icon(painterResource(R.drawable.ic_check), contentDescription = null, tint = Color.White, modifier = Modifier.size(CHECK))
            }
        }
    }
}

/** Två rader om fyra – åtta får inte plats på en rad med full tryckyta. */
private const val COLUMNS = 4

private val RING = 2.dp
private val RING_GAP = 4.dp
private val CHECK = 20.dp
