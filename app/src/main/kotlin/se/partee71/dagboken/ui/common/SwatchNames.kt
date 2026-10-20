package se.partee71.dagboken.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import se.partee71.dagboken.R
import se.partee71.dagboken.ui.theme.AppColors

/**
 * Namnet TalkBack läser för en valbar färg i `ColorSwatchPicker` (android-ui): "Färg: grön" – namnen står i
 * `swatch_color_names` i samma ordning som `AppColors.SWATCH_HEX`; ett lagrat värde med annat skiftläge är samma färg.
 */
@Composable
fun swatchLabel(hex: String): String {
    val names = stringArrayResource(R.array.swatch_color_names)
    val index = AppColors.SWATCH_HEX.indexOfFirst { it.equals(hex, ignoreCase = true) }
    return stringResource(R.string.color_format, names.getOrElse(index) { "" })
}
