package se.partee71.dagboken.ui.settings

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.StateFlow
import se.partee71.dagboken.ui.theme.DagbokenTheme
import se.partee71.dagboken.ui.theme.applySystemBars

/**
 * Kant till kant och appens tema runt [content] – en gång för appens aktiviteter (`MainActivity`, `MomentActivity`):
 * temavalet i inställningsarket ([theme], SET-1: ljust, mörkt eller auto på klockslag; utloggad följer systemet) slår igenom
 * direkt, och statusradens ikoner följer det.
 */
fun ComponentActivity.setDagbokenContent(theme: StateFlow<AppTheme>, content: @Composable () -> Unit) {
    enableEdgeToEdge()
    setContent {
        val chosen by theme.collectAsStateWithLifecycle()
        val dark = chosen.isDark(isSystemInDarkTheme())
        LaunchedEffect(dark) { applySystemBars(dark) }
        DagbokenTheme(darkTheme = dark, content = content)
    }
}
