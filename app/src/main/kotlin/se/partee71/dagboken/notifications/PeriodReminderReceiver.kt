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
import se.partee71.dagboken.data.repository.MedicinerRepository
import se.partee71.dagboken.domain.usecase.PeriodEndingsUseCase
import java.time.LocalDate
import javax.inject.Inject

/**
 * Dagligt larm som påminner dagen innan en receptperiod eller dosperiod tar slut (NOT-12).
 * Postar ingen notis när inget tar slut, och schemalägger alltid om sig till nästa dag —
 * samma mönster som [ScreeningReminderReceiver].
 */
@AndroidEntryPoint
class PeriodReminderReceiver : BroadcastReceiver() {

    @Inject lateinit var medicinerRepo: MedicinerRepository
    @Inject lateinit var periodEndings: PeriodEndingsUseCase
    @Inject lateinit var prefs: PreferencesRepository
    @Inject lateinit var alarmScheduler: AlarmScheduler

    override fun onReceive(context: Context, intent: Intent) {
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO + SupervisorJob()).launch {
            try {
                val recept   = medicinerRepo.allRecept.first()
                val endings  = periodEndings.endingOn(recept, LocalDate.now().plusDays(1))
                NotificationHelper.postPeriodReminder(context, endings)
                alarmScheduler.schedulePeriodReminder(prefs.periodReminderTime.first())
            } finally {
                pendingResult.finish()
            }
        }
    }
}
