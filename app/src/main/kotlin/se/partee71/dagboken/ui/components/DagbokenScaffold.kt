package se.partee71.dagboken.ui.components

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import se.partee71.dagboken.R

/**
 * Appens enda skärmramverk (regel 4): Scaffold + TopAppBar med enhetlig
 * navigering. De flesta skärmar bara sätter [title] och [onBack]; skärmar
 * utan tillbaka-navigering (rot-flikarna) sätter [navigationIcon] i stället
 * (t.ex. en `AccountBubble`) och kan ersätta [title] med [titleContent].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DagbokenScaffold(
    title: String = "",
    onBack: (() -> Unit)? = null,
    navigationIcon: (@Composable () -> Unit)? = null,
    titleContent: (@Composable () -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
    floatingActionButton: @Composable () -> Unit = {},
    snackbarHost: @Composable () -> Unit = {},
    contentWindowInsets: WindowInsets = ScaffoldDefaults.contentWindowInsets,
    content: @Composable (PaddingValues) -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    when {
                        navigationIcon != null -> navigationIcon()
                        onBack != null -> IconButton(onClick = onBack) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(R.string.back),
                            )
                        }
                    }
                },
                title   = { titleContent?.invoke() ?: Text(title) },
                actions = actions,
            )
        },
        floatingActionButton = floatingActionButton,
        snackbarHost         = snackbarHost,
        contentWindowInsets  = contentWindowInsets,
        content              = content,
    )
}
