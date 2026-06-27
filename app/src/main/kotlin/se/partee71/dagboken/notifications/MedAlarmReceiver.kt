package se.partee71.dagboken.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import se.partee71.dagboken.data.datastore.SCREENING_EVENT_LABELS

class MedAlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val slot  = intent.getIntExtra(EXTRA_SLOT, -1)
        val label = if (slot in SCREENING_EVENT_LABELS.indices) SCREENING_EVENT_LABELS[slot] else ""
        NotificationHelper.postMedReminder(context, label)
    }

    companion object {
        const val EXTRA_SLOT = "extra_slot"
    }
}
