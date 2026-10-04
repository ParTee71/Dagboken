package se.partee71.dagboken.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import se.partee71.dagboken.R
import se.partee71.dagboken.ui.theme.AppColors
import se.partee71.dagboken.ui.theme.AppShapes
import se.partee71.dagboken.ui.theme.AppTypography

/** Ett val i [AppMenu] eller [AddSplitButton]. [destructive] färgar det som radering. */
data class AppMenuItem(
    val label: String,
    val onClick: () -> Unit,
    @param:DrawableRes val icon: Int? = null,
    val destructive: Boolean = false,
)

/** Menyn bakom "Fler val" (⋮): arkivera, radera, visa arkiverade m.m. */
@Composable
fun AppMenu(items: List<AppMenuItem>, modifier: Modifier = Modifier, contentDescription: String = stringResource(R.string.more_options)) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier) {
        AppIconButton(R.drawable.ic_more_vert, contentDescription, onClick = { expanded = true })
        AppMenuPopup(items, expanded, onDismiss = { expanded = false })
    }
}

/** Själva menyn – delas av [AppMenu] och [AddSplitButton]. */
@Composable
internal fun AppMenuPopup(items: List<AppMenuItem>, expanded: Boolean, onDismiss: () -> Unit) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss,
        shape = AppShapes.menu,
        containerColor = AppColors.extended.card,
    ) {
        items.forEach { item ->
            val color = if (item.destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
            DropdownMenuItem(
                text = { Text(item.label, style = AppTypography.body) },
                onClick = {
                    onDismiss()
                    item.onClick()
                },
                leadingIcon = item.icon?.let { { Icon(painterResource(it), contentDescription = null) } },
                colors = MenuDefaults.itemColors(textColor = color, leadingIconColor = color),
            )
        }
    }
}
