@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package se.partee71.dagboken.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SplitButtonDefaults
import androidx.compose.material3.SplitButtonLayout
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import se.partee71.dagboken.R
import se.partee71.dagboken.ui.theme.AppTypography
import se.partee71.dagboken.ui.theme.IconSize
import se.partee71.dagboken.ui.theme.Spacing

/**
 * "Lägg till ▾": huvudåtgärden och, om [menuItems] finns, en pil med fler val. Utan menyval
 * blir den en vanlig sekundär [AppButton] med plus – samma plats och utseende.
 */
@Composable
fun AddSplitButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    menuItems: List<AppMenuItem> = emptyList(),
    menuDescription: String = stringResource(R.string.more_options),
) {
    if (menuItems.isEmpty()) {
        AppButton(label, onClick, modifier, variant = ButtonVariant.Secondary, icon = R.drawable.ic_add)
        return
    }
    var expanded by remember { mutableStateOf(false) }
    val rotation by animateFloatAsState(if (expanded) 180f else 0f, MaterialTheme.motionScheme.fastSpatialSpec(), label = "pil")
    val colors = ButtonDefaults.filledTonalButtonColors()
    Box(modifier) {
        SplitButtonLayout(
            leadingButton = {
                SplitButtonDefaults.LeadingButton(onClick = onClick, modifier = Modifier.heightIn(min = HEIGHT), colors = colors) {
                    Icon(painterResource(R.drawable.ic_add), contentDescription = null, modifier = Modifier.size(IconSize.button))
                    Spacer(Modifier.width(Spacing.s))
                    Text(label, style = AppTypography.button)
                }
            },
            trailingButton = {
                SplitButtonDefaults.TrailingButton(
                    checked = expanded,
                    onCheckedChange = { expanded = it },
                    modifier = Modifier.heightIn(min = HEIGHT),
                    colors = colors,
                ) {
                    Icon(
                        painterResource(R.drawable.ic_expand_more),
                        contentDescription = menuDescription,
                        modifier = Modifier.size(IconSize.button).rotate(rotation),
                    )
                }
            },
        )
        AppMenuPopup(menuItems, expanded, onDismiss = { expanded = false })
    }
}

private val HEIGHT = 56.dp
