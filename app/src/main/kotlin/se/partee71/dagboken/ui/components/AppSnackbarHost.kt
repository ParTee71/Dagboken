package se.partee71.dagboken.ui.components

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import se.partee71.dagboken.ui.theme.AppShapes
import se.partee71.dagboken.ui.theme.Spacing

/** Den enda platsen där meddelanden visas (skill shared-ui-components). */
@Composable
fun AppSnackbarHost(hostState: SnackbarHostState, modifier: Modifier = Modifier) {
    SnackbarHost(hostState, modifier) { data ->
        Snackbar(data, modifier = Modifier.padding(Spacing.l), shape = AppShapes.row)
    }
}
