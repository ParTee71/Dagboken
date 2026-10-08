package se.partee71.dagboken

import android.app.Application
import androidx.work.Configuration
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject
import se.partee71.dagboken.data.legacy.LegacySessionCapture

/**
 * WorkManager initieras **på begäran** ([Configuration.Provider]; den automatiska initieraren är borttagen i
 * manifestet): 4.0 har inga egna workers (TP-7) och rör WorkManager bara för att avboka 3.x:s backupjobb vid första
 * starten (OMB-2). Med automatisk initiering skulle 3.x:s kvarliggande periodiska jobb kunna köras vid varje kallstart
 * – mot en worker som inte längre finns – innan avbokningen hunnit ske.
 */
@HiltAndroidApp
class DagbokenApplication : Application(), Configuration.Provider {
    @Inject lateinit var legacySession: LegacySessionCapture

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().build()

    override fun onCreate() {
        super.onCreate()
        // 3.x-sessionen fångas före inloggningsgrinden, så att bekräftelsesteget vet vilket konto 3.x använde (OMB-2).
        legacySession.capture()
    }
}
