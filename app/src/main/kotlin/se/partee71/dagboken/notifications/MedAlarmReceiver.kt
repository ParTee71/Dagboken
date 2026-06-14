package se.partee71.dagboken.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class MedAlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val namn     = intent.getStringExtra(EXTRA_NAMN)     ?: return
        val dos      = intent.getStringExtra(EXTRA_DOS)      ?: ""
        val tidpunkt = intent.getStringExtra(EXTRA_TIDPUNKT) ?: ""
        NotificationHelper.postMedReminder(context, namn, dos, tidpunkt)
    }

    companion object {
        const val EXTRA_NAMN     = "extra_namn"
        const val EXTRA_DOS      = "extra_dos"
        const val EXTRA_TIDPUNKT = "extra_tidpunkt"
    }
}
