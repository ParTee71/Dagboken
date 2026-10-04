package se.partee71.dagboken

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.LaunchedEffect
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import dagger.hilt.android.AndroidEntryPoint
import se.partee71.dagboken.ui.AppRoot
import se.partee71.dagboken.ui.theme.DagbokenTheme

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        // Startskärmen (NFR-5) – före super.onCreate, enligt core-splashscreen.
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            // Temat följer systemet tills temavalet finns i inställningsarket (SET-1, etapp 5).
            val dark = isSystemInDarkTheme()
            // Statusradens ikoner följer appens tema.
            LaunchedEffect(dark) {
                val style = if (dark) SystemBarStyle.dark(Color.TRANSPARENT) else SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
                enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
            }
            DagbokenTheme(darkTheme = dark) { AppRoot() }
        }
    }
}
