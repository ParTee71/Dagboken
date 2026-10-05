package se.partee71.dagboken.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import se.partee71.dagboken.R
import se.partee71.dagboken.ui.theme.AppColors
import se.partee71.dagboken.ui.theme.AppShapes

/**
 * Välj en av de valbara färgerna (`AppColors.SWATCH_HEX`) som hex – det som lagras. Den valda har
 * [ChoiceGrid]s valmarkering; TalkBack läser färgens namn.
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
    ) { hex -> Box(Modifier.fillMaxSize().background(AppColors.swatch(hex), AppShapes.pill)) }
}

/** Två rader om fyra – åtta får inte plats på en rad med full tryckyta. */
private const val COLUMNS = 4
