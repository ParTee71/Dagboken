package se.partee71.dagboken.ui.moment

import android.app.KeyguardManager
import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import javax.inject.Provider
import kotlin.time.Clock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn
import se.partee71.dagboken.reminders.MomentLaunch
import se.partee71.dagboken.ui.settings.AppThemeViewModel
import se.partee71.dagboken.ui.settings.setDagbokenContent

/**
 * Stunden i helskärm (NOT-20) – en egen aktivitet, inte `MainActivity`, så att den kan visas ovanpå låsskärmen
 * (`setShowWhenLocked`/`setTurnScreenOn`) utan att något av appens innehåll kan nås därifrån: den visar bara
 * tillfälle, tid, text och fråga ([MomentLaunch]), och skärmen hålls tänd under pausen. Inte exporterad; startas bara
 * av Stunden-notisens egna PendingIntents. En ny Stunden ersätter den som är öppen ([onNewIntent]). **Välj känslor**
 * öppnar `MainActivity` på Idag med tillfällets måendeark på känslosteget – låst skärm ber först om upplåsning;
 * **Inte nu** och Bakåt stänger utan att logga, och notisen ligger kvar. Appen använder inte låsläge, enhetsadministration
 * eller kioskläge.
 */
@AndroidEntryPoint
class MomentActivity : ComponentActivity() {
    private val theme: AppThemeViewModel by viewModels()

    @Inject lateinit var clock: Clock

    @Inject lateinit var zone: Provider<TimeZone>

    private val launch = MutableStateFlow<MomentLaunch?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setShowWhenLocked(true)
        setTurnScreenOn(true)
        if (!show(intent)) return
        setDagbokenContent(theme.theme, lightSystemBarIcons = true) {
            val current by launch.collectAsStateWithLifecycle()
            current?.let { moment ->
                MomentRoute(
                    moment,
                    today = clock.todayIn(zone.get()),
                    onChooseFeelings = { chooseFeelings(moment) },
                    // Bakåt = Inte nu: aktiviteten stängs (standardbeteendet), notisen står kvar.
                    onNotNow = ::finish,
                    onPauseDone = { window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
                )
            }
        }
    }

    /** Nästa tillfälle medan en Stunden är öppen (orimligt nära tider): den nya ersätter den gamla. */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        show(intent)
    }

    /** Visar Stunden ur [intent] med skärmen tänd; ett okänt tillfälle stänger aktiviteten. */
    private fun show(intent: Intent?): Boolean {
        val moment = MomentLaunch.from(intent)
        if (moment == null) {
            finish()
            return false
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        launch.value = moment
        return true
    }

    /** NOT-20, SCR-7: måendearket för tillfället (första steget är känslorna) – samma väg som "Logga nu" ([MomentLaunch.moodIntent]). */
    private fun chooseFeelings(moment: MomentLaunch) {
        val open = moment.moodIntent(this)
        val keyguard = getSystemService(KeyguardManager::class.java)
        if (keyguard?.isKeyguardLocked == true) {
            keyguard.requestDismissKeyguard(
                this,
                object : KeyguardManager.KeyguardDismissCallback() {
                    override fun onDismissSucceeded() = startAndFinish(open)
                }
            )
        } else {
            startAndFinish(open)
        }
    }

    private fun startAndFinish(open: Intent) {
        startActivity(open)
        finish()
    }
}
