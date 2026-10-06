package se.partee71.dagboken

import android.content.Intent
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
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import se.partee71.dagboken.reminders.NotificationHelper
import se.partee71.dagboken.reminders.ReminderLaunch
import se.partee71.dagboken.reminders.ReminderSync
import se.partee71.dagboken.ui.AppRoot
import se.partee71.dagboken.ui.settings.AppTheme
import se.partee71.dagboken.ui.settings.AppThemeViewModel
import se.partee71.dagboken.ui.settings.isDark
import se.partee71.dagboken.ui.theme.DagbokenTheme

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val theme: AppThemeViewModel by viewModels()

    @Inject lateinit var reminderSync: ReminderSync
    @Inject lateinit var notifications: NotificationHelper

    /** Vad en tryckt påminnelse ska öppna (NOT-9, NOT-11, NOT-12) tills navigationen tagit hand om det. */
    private val launch = MutableStateFlow<ReminderLaunch?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        // Startskärmen (NFR-5) – före super.onCreate, enligt core-splashscreen. Den står kvar tills
        // temat är känt (högst AppThemeViewModel.LOAD_TIMEOUT), så att appen inte blinkar i fel tema.
        installSplashScreen().setKeepOnScreenCondition { theme.theme.value == AppTheme.Loading }
        super.onCreate(savedInstanceState)
        // Påminnelserna (NOT-1, NOT-7): kanalerna finns före första notisen, och larmen följer cachen medan appen kör.
        notifications.createChannels()
        reminderSync.start()
        // Ny start: notisens önskan, en gång (från Senaste spelas den inte upp igen). Ombyggd: den som ännu inte hanterats.
        launch.value = if (savedInstanceState == null) {
            ReminderLaunch.consume(intent)?.also(notifications::dismiss)
        } else {
            ReminderLaunch.restore(savedInstanceState)
        }
        // Temat läses medan aktiviteten är startad, oberoende av Compose: startskärmen skymmer UI:t tills
        // temat är känt, så det får inte vänta på att UI:t börjar samla flödet.
        lifecycleScope.launch { repeatOnLifecycle(Lifecycle.State.STARTED) { theme.theme.collect() } }
        enableEdgeToEdge()
        setContent {
            // Temavalet i inställningsarket (SET-1): ljust, mörkt eller auto på klockslag – slår igenom
            // direkt i hela appen. Utloggad (inga inställningar) följer appen systemet.
            val chosen by theme.theme.collectAsStateWithLifecycle()
            val opening by launch.collectAsStateWithLifecycle()
            val dark = chosen.isDark(isSystemInDarkTheme())
            // Statusradens ikoner följer appens tema.
            LaunchedEffect(dark) {
                val style = if (dark) SystemBarStyle.dark(Color.TRANSPARENT) else SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
                enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
            }
            DagbokenTheme(darkTheme = dark) { AppRoot(launch = opening, onLaunchHandled = { launch.value = null }) }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        ReminderLaunch.save(launch.value, outState)
    }

    /** En tryckt påminnelse när appen redan är öppen (`SINGLE_TOP`). */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        ReminderLaunch.consume(intent)?.let {
            notifications.dismiss(it)
            launch.value = it
        }
        setIntent(intent)
    }
}
