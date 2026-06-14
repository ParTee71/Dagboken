package se.partee71.dagboken.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import se.partee71.dagboken.data.repository.AktiviteterRepository
import javax.inject.Inject

@AndroidEntryPoint
class ScreeningReminderReceiver : BroadcastReceiver() {

    @Inject lateinit var aktiviteterRepo: AktiviteterRepository
    @Inject lateinit var alarmScheduler: AlarmScheduler

    override fun onReceive(context: Context, intent: Intent) {
        val hour = intent.getIntExtra(EXTRA_HOUR, 8)
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO + SupervisorJob()).launch {
            try {
                if (!aktiviteterRepo.hasScreeningToday()) {
                    NotificationHelper.postScreeningReminder(context)
                }
                alarmScheduler.scheduleScreeningAlarm(hour)
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        const val EXTRA_HOUR = "extra_hour"
    }
}
