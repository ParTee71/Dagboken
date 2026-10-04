@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package se.partee71.dagboken.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.RowScope
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import se.partee71.dagboken.R
import se.partee71.dagboken.ui.theme.AppTypography
import se.partee71.dagboken.ui.theme.Tone

enum class TopBarSize {
    /** Toppnivåskärm: stor rundad rubrik som krymper vid rullning. */
    Large,

    /** Underskärm: tillbakaknapp och liten rubrik. */
    Small,
}

/** Synkindikatorn (NFR-1): [pending] när ändringar väntat på servern; [onClick] förklarar den. */
@Immutable
data class SyncIndicator(val pending: Boolean = false, val onClick: () -> Unit = {})

/** Sätts av navigationen; utan den visas ingen indikator (t.ex. i tester och galleriet). */
val LocalSyncIndicator = compositionLocalOf { SyncIndicator() }

/**
 * Skärmrubriken. [onBack] ger en tillbakaknapp; [actions] står till höger (t.ex. `AppMenu`).
 * [scrollBehavior] kopplas av ramarna (`EntityListScreen`, `EntityEditScreen`).
 * Före [actions] står synkindikatorn när [LocalSyncIndicator] säger att ändringar väntar.
 */
@Composable
fun AppTopBar(
    title: String,
    modifier: Modifier = Modifier,
    size: TopBarSize = TopBarSize.Large,
    subtitle: String? = null,
    onBack: (() -> Unit)? = null,
    scrollBehavior: TopAppBarScrollBehavior? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    val background = MaterialTheme.colorScheme.background
    val colors = TopAppBarDefaults.topAppBarColors(containerColor = background, scrolledContainerColor = background)
    val navigation: @Composable () -> Unit = {
        onBack?.let { AppIconButton(R.drawable.ic_arrow_back, stringResource(R.string.back), it, variant = IconButtonVariant.Tonal) }
    }
    val heading = Modifier.semantics { heading() }
    val sync = LocalSyncIndicator.current
    val allActions: @Composable RowScope.() -> Unit = {
        SyncPill(sync)
        actions()
    }
    when (size) {
        TopBarSize.Large -> LargeFlexibleTopAppBar(
            title = { Text(title, style = AppTypography.screenTitle, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = heading) },
            modifier = modifier,
            subtitle = subtitle?.let { { Text(it, style = AppTypography.itemSubtitle) } },
            navigationIcon = navigation,
            actions = allActions,
            colors = colors,
            scrollBehavior = scrollBehavior,
        )
        TopBarSize.Small -> TopAppBar(
            title = { Text(title, style = AppTypography.sectionTitle, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = heading) },
            modifier = modifier,
            navigationIcon = navigation,
            actions = allActions,
            colors = colors,
            scrollBehavior = scrollBehavior,
        )
    }
}

@Composable
private fun RowScope.SyncPill(sync: SyncIndicator) {
    val description = stringResource(R.string.sync_pending_description)
    AnimatedVisibility(sync.pending, enter = fadeIn(), exit = fadeOut()) {
        InfoPill(
            stringResource(R.string.sync_pending),
            Modifier.semantics {
                contentDescription = description
                liveRegion = LiveRegionMode.Polite
            },
            tone = Tone.Neutral,
            icon = R.drawable.ic_cloud_upload,
            onClick = sync.onClick,
        )
    }
}
