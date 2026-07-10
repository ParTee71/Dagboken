package se.partee71.dagboken.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationManagerCompat
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import se.partee71.dagboken.data.repository.MedicinerRepository
import javax.inject.Inject

/**
 * Handles the "Markera tagen"-action on the medicine reminder notification. Marks
 * today's scheduled, still-pending doses as taken through the same repository the
 * app uses (single source of truth), then dismisses the notification — all without
 * opening the app.
 */
@AndroidEntryPoint
class MedActionReceiver : BroadcastReceiver() {

    @Inject lateinit var medicinerRepo: MedicinerRepository

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_MARK_TAKEN) return
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO + SupervisorJob()).launch {
            try {
                medicinerRepo.markTodayDosesTaken()
                NotificationManagerCompat.from(context)
                    .cancel(NotificationHelper.NOTIFICATION_ID_MED)
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        const val ACTION_MARK_TAKEN = "se.partee71.dagboken.action.MARK_MED_TAKEN"
    }
}
