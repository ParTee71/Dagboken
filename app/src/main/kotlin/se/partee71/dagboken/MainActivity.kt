package se.partee71.dagboken

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import dagger.hilt.android.AndroidEntryPoint
import se.partee71.dagboken.ui.navigation.AppNavigation
import se.partee71.dagboken.ui.navigation.Routes
import se.partee71.dagboken.ui.navigation.Screen
import se.partee71.dagboken.ui.theme.DagbokenTheme

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val vm: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        super.onCreate(savedInstanceState)

        // Hold splash until DataStore has emitted the real migrationDone value.
        splashScreen.setKeepOnScreenCondition { vm.migrationDone.value == null }

        enableEdgeToEdge()
        setContent {
            val isDarkTheme by vm.isDarkTheme.collectAsState()
            val dynamicColor by vm.dynamicColor.collectAsState()
            val migrationDone by vm.migrationDone.collectAsState()

            // Don't render until we know the real value — prevents NavHost from
            // locking in the wrong startDestination before DataStore loads.
            if (migrationDone == null) return@setContent

            DagbokenTheme(darkTheme = isDarkTheme, dynamicColor = dynamicColor) {
                AppNavigation(
                    startDestination = if (migrationDone == true) Screen.Hem.route else Routes.MIGRATION,
                )
            }
        }
    }
}
