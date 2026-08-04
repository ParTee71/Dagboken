package se.partee71.dagboken.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import se.partee71.dagboken.data.datastore.PreferencesRepository
import se.partee71.dagboken.data.datastore.SCREENING_EVENT_LABELS
import javax.inject.Inject

/**
 * Medicinpåminnelsen för en av dagens fyra tidpunkter (NOT-3). Larmen är
 * engångslarm, så mottagaren måste — precis som [ScreeningReminderReceiver] och
 * [PeriodReminderReceiver] — schemalägga om sig själv till nästa dag. Utan det
 * tystnade påminnelsen efter första utlösningen tills appens process startades om.
 */
@AndroidEntryPoint
class MedAlarmReceiver : BroadcastReceiver() {

    @Inject lateinit var prefs: PreferencesRepository
    @Inject lateinit var alarmScheduler: AlarmScheduler

    override fun onReceive(context: Context, intent: Intent) {
        val slot  = intent.getIntExtra(EXTRA_SLOT, -1)
        val label = if (slot in SCREENING_EVENT_LABELS.indices) SCREENING_EVENT_LABELS[slot] else ""
        NotificationHelper.postMedReminder(context, label)

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO + SupervisorJob()).launch {
            try {
                // Konfigurationen läses om i stället för att återanvända intentets extras:
                // tiden kan ha ändrats sedan larmet sattes, och påminnelser kan ha stängts
                // av — då ska inget nytt larm sättas.
                if (!prefs.medsNotificationsEnabled.first()) return@launch
                val config = prefs.screeningEventConfigs.first().getOrNull(slot) ?: return@launch
                if (config.enabled) alarmScheduler.scheduleMedAlarm(slot, config.time)
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        const val EXTRA_SLOT = "extra_slot"
    }
}
