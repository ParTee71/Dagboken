package se.partee71.dagboken

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import dagger.hilt.android.AndroidEntryPoint
import se.partee71.dagboken.notifications.NotificationHelper
import se.partee71.dagboken.ui.navigation.AppNavigation
import se.partee71.dagboken.ui.navigation.Routes
import se.partee71.dagboken.ui.navigation.Screen
import se.partee71.dagboken.ui.theme.DagbokenTheme

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val vm: MainViewModel by viewModels()

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleNavIntent(intent)
    }

    private fun handleNavIntent(intent: Intent?) {
        intent?.getStringExtra(NotificationHelper.EXTRA_NAV_ROUTE)?.let { vm.setPendingNavRoute(it) }
        intent?.getStringExtra(NotificationHelper.EXTRA_SCREENING_LABEL)?.let { vm.setPendingScreeningLabel(it) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        super.onCreate(savedInstanceState)

        // Hold splash until DataStore has emitted the real migrationDone value.
        splashScreen.setKeepOnScreenCondition { vm.migrationDone.value == null }

        handleNavIntent(intent)

        enableEdgeToEdge()
        setContent {
            val isDarkTheme by vm.isDarkTheme.collectAsStateWithLifecycle()
            val dynamicColor by vm.dynamicColor.collectAsStateWithLifecycle()
            val migrationDone by vm.migrationDone.collectAsStateWithLifecycle()

            // Don't render until we know the real value — prevents NavHost from
            // locking in the wrong startDestination before DataStore loads.
            if (migrationDone == null) return@setContent

            DagbokenTheme(darkTheme = isDarkTheme, dynamicColor = dynamicColor) {
                AppNavigation(
                    startDestination = if (migrationDone == true) Screen.Idag.route else Routes.MIGRATION,
                )
            }
        }
    }
}
