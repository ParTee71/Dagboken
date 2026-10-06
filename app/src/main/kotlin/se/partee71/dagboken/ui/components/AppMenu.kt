package se.partee71.dagboken.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import se.partee71.dagboken.R
import se.partee71.dagboken.ui.theme.AppColors
import se.partee71.dagboken.ui.theme.AppShapes
import se.partee71.dagboken.ui.theme.AppTypography
import se.partee71.dagboken.ui.theme.IconSize
import se.partee71.dagboken.ui.theme.Spacing

/**
 * Ett val i [AppMenu] eller [AddSplitButton]. [destructive] färgar det som radering. [section] är en
 * avdelning med rubrik: där den börjar står en avdelare och rubriken (t.ex. "Recept" i vid behov-kortets
 * Fler-lista, FAV-11); `null` = ingen rubrik.
 */
data class AppMenuItem(
    val label: String,
    val onClick: () -> Unit,
    @param:DrawableRes val icon: Int? = null,
    val destructive: Boolean = false,
    val section: String? = null,
    /**
     * Satt: posten är en **kryssrad** i menyn (seriervalet i Trender, TRD-12) – bocken visas när `true`, platsen
     * hålls tom när `false`, TalkBack läser den som kryssruta, och menyn står kvar öppen så att flera kan väljas.
     * `null` (standard): ett vanligt val som stänger menyn.
     */
    val checked: Boolean? = null,
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
        items.forEachIndexed { index, item ->
            val section = item.section
            if (section != null && section != items.getOrNull(index - 1)?.section) {
                if (index > 0) AppDivider(Modifier.padding(vertical = Spacing.xs))
                GroupLabel(section, Modifier.padding(horizontal = Spacing.s, vertical = Spacing.xs))
            }
            val color = if (item.destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
            val checked = item.checked
            // En kryssrad visar bocken (eller håller platsen tom) och stänger inte menyn – flera kan väljas.
            val icon = if (checked == null) item.icon else R.drawable.ic_check.takeIf { checked }
            val semantics = if (checked == null) Modifier else Modifier.semantics { role = Role.Checkbox; toggleableState = ToggleableState(checked) }
            DropdownMenuItem(
                text = { Text(item.label, style = AppTypography.body) },
                onClick = {
                    if (checked == null) onDismiss()
                    item.onClick()
                },
                modifier = semantics,
                leadingIcon = if (icon == null && checked == null) null else ({ icon?.let { Icon(painterResource(it), contentDescription = null) } ?: Spacer(Modifier.size(IconSize.control)) }),
                colors = MenuDefaults.itemColors(textColor = color, leadingIconColor = color),
            )
        }
    }
}
