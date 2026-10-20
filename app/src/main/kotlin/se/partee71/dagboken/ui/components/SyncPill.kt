package se.partee71.dagboken.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.RowScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import se.partee71.dagboken.R
import se.partee71.dagboken.ui.theme.Neutral
import se.partee71.ui.components.InfoPill
import se.partee71.ui.theme.Tone

/** Synkindikatorn (NFR-22): [pending] när ändringar väntat på servern; [onClick] förklarar den. */
@Immutable
data class SyncIndicator(val pending: Boolean = false, val onClick: () -> Unit = {})

/**
 * Synkindikatorn som status i `AppTopBar` (android-ui): navigationen sätter den i `LocalTopBarStatus`, så att
 * den står före varje toppnivåskärms actions; utan den visas ingen indikator (t.ex. i tester).
 */
fun syncStatus(sync: SyncIndicator): @Composable RowScope.() -> Unit = { SyncPill(sync) }

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
            onClick = sync.onClick
        )
    }
}
