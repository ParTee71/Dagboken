@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package se.partee71.dagboken.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FloatingToolbarDefaults
import androidx.compose.material3.HorizontalFloatingToolbar
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import se.partee71.dagboken.ui.theme.AppColors
import se.partee71.dagboken.ui.theme.AppShapes
import se.partee71.dagboken.ui.theme.AppTypography
import se.partee71.dagboken.ui.theme.Spacing

/** En flik i [AppFloatingToolbar]. */
data class ToolbarItem(val label: String, @DrawableRes val icon: Int)

/** Huvudknappen bredvid flikarna i [AppFloatingToolbar], t.ex. plusknappen som loggar (NAV-10). */
data class ToolbarAction(val label: String, @DrawableRes val icon: Int, val onClick: () -> Unit)

/**
 * Hur mycket plats den flytande verktygsraden tar längst ned. Navigationen sätter värdet när
 * raden syns; ramarna lägger till det under listan, knappen och snackbaren.
 */
val LocalBottomClearance = compositionLocalOf { NoBottomClearance }

/** Plats som verktygsraden tar inklusive marginal. */
val FloatingToolbarClearance: Dp = 96.dp

/** Ingen plats längst ned – verktygsraden syns inte (undersidor, NAV-3). */
val NoBottomClearance: Dp = 0.dp

/**
 * Toppnivånavigeringen (NAV-8): en flytande verktygsrad där vald flik visar ikon och namn och
 * övriga bara ikon (med namnet för TalkBack). [action] lägger en gör-något-knapp i solgult till
 * höger om flikarna (DSN-1); utan den visas bara flikarna.
 */
@Composable
fun AppFloatingToolbar(
    items: List<ToolbarItem>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    action: ToolbarAction? = null,
) {
    val colors = FloatingToolbarDefaults.standardFloatingToolbarColors(
        toolbarContainerColor = AppColors.extended.toolbar,
        toolbarContentColor = AppColors.extended.onToolbar,
    )
    val tabs: @Composable RowScope.() -> Unit = {
        items.forEachIndexed { index, item ->
            ToolbarTab(item, selected = index == selectedIndex, onClick = { onSelect(index) })
        }
    }
    if (action == null) {
        HorizontalFloatingToolbar(expanded = true, modifier = modifier, colors = colors, content = tabs)
    } else {
        HorizontalFloatingToolbar(
            expanded = true,
            floatingActionButton = {
                FloatingToolbarDefaults.StandardFloatingActionButton(
                    onClick = action.onClick,
                    containerColor = MaterialTheme.colorScheme.secondary,
                    contentColor = MaterialTheme.colorScheme.onSecondary,
                ) { Icon(painterResource(action.icon), contentDescription = action.label) }
            },
            modifier = modifier,
            colors = colors,
            content = tabs,
        )
    }
}

@Composable
private fun ToolbarTab(item: ToolbarItem, selected: Boolean, onClick: () -> Unit) {
    val background = if (selected) MaterialTheme.colorScheme.primary else AppColors.extended.toolbar
    val content = if (selected) MaterialTheme.colorScheme.onPrimary else AppColors.extended.onToolbar
    Row(
        Modifier
            .heightIn(min = TAB_HEIGHT)
            .widthIn(min = TAB_HEIGHT)
            .clip(AppShapes.pill)
            .background(background)
            .selectable(selected = selected, role = Role.Tab, onClick = onClick)
            .semantics { contentDescription = item.label }
            .animateContentSize(MaterialTheme.motionScheme.defaultSpatialSpec())
            .padding(horizontal = Spacing.m),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs, Alignment.CenterHorizontally),
    ) {
        Icon(painterResource(item.icon), contentDescription = null, tint = content, modifier = Modifier.size(ICON))
        if (selected) Text(item.label, style = AppTypography.button, color = content, maxLines = 1)
    }
}

private val TAB_HEIGHT = 48.dp
private val ICON = 22.dp
