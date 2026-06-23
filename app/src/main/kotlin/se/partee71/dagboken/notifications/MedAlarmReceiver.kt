package se.partee71.dagboken.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class MedAlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        NotificationHelper.postMedReminder(context)
    }

    companion object {
        const val EXTRA_SLOT = "extra_slot"
    }
}
