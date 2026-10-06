package se.partee71.dagboken.reminders

import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import se.partee71.dagboken.core.model.ReminderSettings
import se.partee71.dagboken.core.model.Settings
import se.partee71.dagboken.data.common.UserScope
import se.partee71.dagboken.data.common.withFallback
import se.partee71.dagboken.data.repository.SettingsRepository
import se.partee71.dagboken.di.ApplicationScope

/**
 * Håller larmen i takt med cachen medan appen kör (NOT-7, NOT-15, AUTH-6; ARKITEKTUR.md → Risker, "Larm tystnar
 * när schemat ligger i cachen"): så länge någon är inloggad läggs alla larm om ([AlarmScheduler.rescheduleAll])
 * när påminnelseinställningarna ändras – från formuläret, från servern (en annan enhet) eller en import – och direkt
 * vid start. Recepten och doserna påverkar inte larmen (bara vad notisen visar, läst när larmet går), så de följs inte. När användaren loggar ut avbokas alla larm och notiserna stängs. Startas en gång
 * ([start]); lever i appens scope.
 */
@Singleton
@OptIn(ExperimentalCoroutinesApi::class)
class ReminderSync @Inject constructor(
    private val user: UserScope,
    private val settings: SettingsRepository,
    private val scheduler: AlarmScheduler,
    private val notifications: NotificationHelper,
    @ApplicationScope private val scope: CoroutineScope,
) {
    private val started = AtomicBoolean(false)

    /** Startar synken – fler anrop gör ingenting. */
    fun start() {
        if (started.compareAndSet(false, true)) scope.launch { run() }
    }

    /**
     * Synken själv (testerna kör den direkt). Ett utloggat läge vid start räknas inte som en utloggning: först när
     * någon varit inloggad och sedan inte är det avbokas larmen, så att en kallstart innan inloggningen lästs in
     * aldrig tar bort dem.
     */
    internal suspend fun run() {
        var signedIn = false
        user.uid.map { it != null }.distinctUntilChanged().collectLatest { now ->
            if (!now) {
                if (signedIn) {
                    scheduler.cancelAll()
                    notifications.cancelAll()
                }
                signedIn = false
                return@collectLatest
            }
            signedIn = true
            reminderSettings().conflate().collect { scheduler.rescheduleAll(known = it) }
        }
    }

    /**
     * Det som avgör larmen: påminnelseinställningarna (`reminderAlarms`), bara när de ändrats. Ett läsfel blir en
     * omschemaläggning, som då lämnar larmen orörda om inställningarna inte går att läsa.
     */
    private fun reminderSettings(): Flow<ReminderSettings> =
        settings.settings.withFallback(Settings()).map { it.reminders }.distinctUntilChanged()
}
