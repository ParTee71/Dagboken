package se.partee71.dagboken

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import se.partee71.dagboken.ui.AppRoot
import se.partee71.dagboken.ui.settings.AppTheme
import se.partee71.dagboken.ui.settings.AppThemeViewModel
import se.partee71.dagboken.ui.settings.isDark
import se.partee71.dagboken.ui.theme.DagbokenTheme

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val theme: AppThemeViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        // Startskärmen (NFR-5) – före super.onCreate, enligt core-splashscreen. Den står kvar tills
        // temat är känt (högst AppThemeViewModel.LOAD_TIMEOUT), så att appen inte blinkar i fel tema.
        installSplashScreen().setKeepOnScreenCondition { theme.theme.value == AppTheme.Loading }
        super.onCreate(savedInstanceState)
        // Temat läses medan aktiviteten är startad, oberoende av Compose: startskärmen skymmer UI:t tills
        // temat är känt, så det får inte vänta på att UI:t börjar samla flödet.
        lifecycleScope.launch { repeatOnLifecycle(Lifecycle.State.STARTED) { theme.theme.collect() } }
        enableEdgeToEdge()
        setContent {
            // Temavalet i inställningsarket (SET-1): ljust, mörkt eller auto på klockslag – slår igenom
            // direkt i hela appen. Utloggad (inga inställningar) följer appen systemet.
            val chosen by theme.theme.collectAsStateWithLifecycle()
            val dark = chosen.isDark(isSystemInDarkTheme())
            // Statusradens ikoner följer appens tema.
            LaunchedEffect(dark) {
                val style = if (dark) SystemBarStyle.dark(Color.TRANSPARENT) else SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
                enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
            }
            DagbokenTheme(darkTheme = dark) { AppRoot() }
        }
    }
}
