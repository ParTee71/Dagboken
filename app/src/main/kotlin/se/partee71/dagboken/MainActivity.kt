package se.partee71.dagboken

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.collectAsState
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
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        super.onCreate(savedInstanceState)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 0)
        }

        // Hold splash until DataStore has emitted the real migrationDone value.
        splashScreen.setKeepOnScreenCondition { vm.migrationDone.value == null }

        handleNavIntent(intent)

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
