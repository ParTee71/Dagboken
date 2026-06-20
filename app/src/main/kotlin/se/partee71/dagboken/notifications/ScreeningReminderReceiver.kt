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
        val slot  = intent.getIntExtra(EXTRA_SLOT, 0)
        val time  = intent.getStringExtra(EXTRA_TIME) ?: "08:00"
        val label = intent.getStringExtra(EXTRA_LABEL) ?: "Screening"
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO + SupervisorJob()).launch {
            try {
                if (!aktiviteterRepo.hasScreeningToday()) {
                    NotificationHelper.postScreeningReminder(context, label)
                }
                alarmScheduler.scheduleScreeningAlarm(slot, time)
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        const val EXTRA_SLOT  = "extra_slot"
        const val EXTRA_TIME  = "extra_time"
        const val EXTRA_LABEL = "extra_label"
    }
}
