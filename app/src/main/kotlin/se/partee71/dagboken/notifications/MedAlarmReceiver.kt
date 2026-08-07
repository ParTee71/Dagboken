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
import se.partee71.dagboken.R
import se.partee71.dagboken.data.datastore.MED_NOTIFICATION_TIDPUNKTER
import se.partee71.dagboken.data.datastore.PreferencesRepository
import se.partee71.dagboken.data.repository.MedicinerRepository
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
    @Inject lateinit var medicinerRepo: MedicinerRepository
    @Inject lateinit var alarmScheduler: AlarmScheduler

    override fun onReceive(context: Context, intent: Intent) {
        val slot     = intent.getIntExtra(EXTRA_SLOT, -1)
        // Ett larm som sattes före NOT-18 saknar tidpunkten i sina extras — då härleds
        // den ur slot-index i stället, så det gamla larmet ändå påminner om rätt sak.
        val tidpunkt = intent.getStringExtra(EXTRA_TIDPUNKT)?.takeIf { it.isNotBlank() }
            ?: MED_NOTIFICATION_TIDPUNKTER.getOrNull(slot).orEmpty()

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO + SupervisorJob()).launch {
            try {
                // Doserna läses först så notisen kan lista vad som ska tas, med den dos
                // som gäller idag — alltså inklusive en eventuell doshöjning (NOT-17).
                // Bara doserna för larmets egen tidpunkt räknas; en läggdagspåminnelse
                // ska inte lista morgondosen.
                val doses = medicinerRepo.pendingScheduledDosesToday(tidpunkt).map {
                    context.getString(R.string.format_notification_med_dose, it.namn, it.dos, it.enhet)
                }
                // Ingen otagen dos vid tidpunkten betyder ingen notis alls (NOT-3) —
                // tidigare postades en allmän "Dags för medicin" även när inget var kvar.
                if (doses.isNotEmpty()) {
                    NotificationHelper.postMedReminder(context, tidpunkt, doses)
                }

                // Konfigurationen läses om i stället för att återanvända intentets extras:
                // tiden kan ha ändrats sedan larmet sattes, och påminnelser kan ha stängts
                // av — då ska inget nytt larm sättas.
                if (!prefs.medsNotificationsEnabled.first()) return@launch
                val config = prefs.medNotificationConfigs.first().getOrNull(slot) ?: return@launch
                if (config.enabled) alarmScheduler.scheduleMedAlarm(slot, config.time)
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        const val EXTRA_SLOT     = "extra_slot"
        const val EXTRA_TIDPUNKT = "extra_tidpunkt"
    }
}
