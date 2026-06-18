package se.partee71.dagboken.notifications

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import se.partee71.dagboken.data.datastore.PreferencesRepository
import se.partee71.dagboken.data.repository.MedicinerRepository
import se.partee71.dagboken.domain.model.Medicin
import se.partee71.dagboken.domain.model.tidpunktToHour
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AlarmScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val medicinerRepo: MedicinerRepository,
    private val prefs: PreferencesRepository,
) {
    private val alarmManager = context.getSystemService(AlarmManager::class.java)

    suspend fun rescheduleAll() {
        val medsEnabled      = prefs.medsNotificationsEnabled.first()
        val screeningEnabled = prefs.screeningNotificationsEnabled.first()
        val screeningTimes   = prefs.screeningReminderTimes.first()

        cancelAllMedAlarms()
        cancelAllScreeningAlarms()

        if (medsEnabled)      scheduleMedAlarms()
        if (screeningEnabled) scheduleScreeningAlarms(screeningTimes)
    }

    suspend fun scheduleMedAlarms() {
        cancelAllMedAlarms()
        medicinerRepo.todayFlow().first()
            .filter { !it.tagen && !it.skipped }
            .forEach { scheduleMedAlarm(it) }
    }

    private fun scheduleMedAlarm(medicin: Medicin) {
        val triggerMs = medAlarmTriggerMs(medicin.datum, medicin.tidpunkt) ?: return
        if (triggerMs <= System.currentTimeMillis()) return

        val intent = Intent(context, MedAlarmReceiver::class.java).apply {
            putExtra(MedAlarmReceiver.EXTRA_NAMN,     medicin.namn)
            putExtra(MedAlarmReceiver.EXTRA_DOS,      "${medicin.dos} ${medicin.enhet}")
            putExtra(MedAlarmReceiver.EXTRA_TIDPUNKT, medicin.tidpunkt)
        }
        val pending = PendingIntent.getBroadcast(
            context,
            requestCode(medicin.id),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        scheduleExact(triggerMs, pending)
    }

    private suspend fun cancelAllMedAlarms() {
        medicinerRepo.todayFlow().first().forEach { cancelMedAlarm(it.id) }
    }

    fun cancelMedAlarm(medicinId: String) {
        PendingIntent.getBroadcast(
            context,
            requestCode(medicinId),
            Intent(context, MedAlarmReceiver::class.java),
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
        )?.also { alarmManager.cancel(it) }
    }

    fun scheduleScreeningAlarms(times: List<String>) {
        cancelAllScreeningAlarms()
        times.forEachIndexed { slot, time -> scheduleScreeningAlarm(slot, time) }
    }

    fun scheduleScreeningAlarm(slot: Int, time: String) {
        val parts  = time.split(":")
        val hour   = parts.getOrNull(0)?.toIntOrNull() ?: return
        val minute = parts.getOrNull(1)?.toIntOrNull() ?: 0
        val triggerMs = screeningAlarmTriggerMs(hour, minute)
        val pending = PendingIntent.getBroadcast(
            context,
            REQUEST_CODE_SCREENING_BASE + slot,
            Intent(context, ScreeningReminderReceiver::class.java).apply {
                putExtra(ScreeningReminderReceiver.EXTRA_SLOT, slot)
                putExtra(ScreeningReminderReceiver.EXTRA_TIME, time)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        scheduleExact(triggerMs, pending)
    }

    fun cancelAllScreeningAlarms() {
        repeat(4) { slot ->
            PendingIntent.getBroadcast(
                context,
                REQUEST_CODE_SCREENING_BASE + slot,
                Intent(context, ScreeningReminderReceiver::class.java),
                PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
            )?.also { alarmManager.cancel(it) }
        }
    }

    private fun scheduleExact(triggerMs: Long, pending: PendingIntent) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerMs, pending)
        } else {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerMs, pending)
        }
    }

    private fun requestCode(medicinId: String) = medicinId.hashCode() and 0x7FFFFFFF

    companion object {
        private const val REQUEST_CODE_SCREENING_BASE = 0x7FF0
    }
}

/** Returns epoch-ms for the alarm trigger (tidpunkt hour minus 15 min), or null for "Vid behov". */
internal fun medAlarmTriggerMs(datum: String, tidpunkt: String): Long? {
    val hour = tidpunktToHour(tidpunkt) ?: return null
    return LocalDateTime
        .of(LocalDate.parse(datum), LocalTime.of(hour, 0))
        .minusMinutes(15)
        .atZone(ZoneId.systemDefault())
        .toInstant()
        .toEpochMilli()
}

/** Returns epoch-ms for the next occurrence of [hour]:[minute] at or after [now]. */
internal fun screeningAlarmTriggerMs(hour: Int, minute: Int, now: LocalDateTime = LocalDateTime.now()): Long {
    var alarmAt = now.withHour(hour).withMinute(minute).withSecond(0).withNano(0)
    if (!alarmAt.isAfter(now)) alarmAt = alarmAt.plusDays(1)
    return alarmAt.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
}
