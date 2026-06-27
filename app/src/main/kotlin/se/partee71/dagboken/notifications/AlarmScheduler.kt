package se.partee71.dagboken.notifications

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import se.partee71.dagboken.data.datastore.PreferencesRepository
import se.partee71.dagboken.data.datastore.SCREENING_EVENT_LABELS
import se.partee71.dagboken.data.datastore.ScreeningEventConfig
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AlarmScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val prefs: PreferencesRepository,
) {
    private val alarmManager = context.getSystemService(AlarmManager::class.java)

    suspend fun rescheduleAll() {
        val medsEnabled  = prefs.medsNotificationsEnabled.first()
        val eventConfigs = prefs.screeningEventConfigs.first()

        cancelAllMedAlarms()
        cancelAllScreeningAlarms()

        if (medsEnabled) scheduleMedAlarms(eventConfigs)
        scheduleScreeningAlarms(eventConfigs)
    }

    fun scheduleMedAlarms(configs: List<ScreeningEventConfig>) {
        cancelAllMedAlarms()
        configs.forEachIndexed { slot, config ->
            if (config.enabled) scheduleMedAlarm(slot, config.time)
        }
    }

    private fun scheduleMedAlarm(slot: Int, time: String) {
        val parts  = time.split(":")
        val hour   = parts.getOrNull(0)?.toIntOrNull() ?: return
        val minute = parts.getOrNull(1)?.toIntOrNull() ?: 0
        val triggerMs = medAlarmTriggerMs(hour, minute)
        val pending = PendingIntent.getBroadcast(
            context,
            REQUEST_CODE_MED_BASE + slot,
            Intent(context, MedAlarmReceiver::class.java).apply {
                putExtra(MedAlarmReceiver.EXTRA_SLOT, slot)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        scheduleExact(triggerMs, pending)
    }

    fun cancelAllMedAlarms() {
        repeat(4) { slot ->
            PendingIntent.getBroadcast(
                context,
                REQUEST_CODE_MED_BASE + slot,
                Intent(context, MedAlarmReceiver::class.java),
                PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
            )?.also { alarmManager.cancel(it) }
        }
    }

    fun scheduleScreeningAlarms(configs: List<ScreeningEventConfig>) {
        cancelAllScreeningAlarms()
        configs.forEachIndexed { slot, config ->
            if (config.enabled) scheduleScreeningAlarm(slot, config.time)
        }
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
                putExtra(ScreeningReminderReceiver.EXTRA_LABEL, SCREENING_EVENT_LABELS[slot])
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

    companion object {
        private const val REQUEST_CODE_SCREENING_BASE = 0x7FF0
        private const val REQUEST_CODE_MED_BASE       = 0x7FE0
    }
}

/** Returns epoch-ms for the next occurrence of [hour]:[minute] at or after [now]. */
internal fun screeningAlarmTriggerMs(hour: Int, minute: Int, now: LocalDateTime = LocalDateTime.now()): Long {
    var alarmAt = now.withHour(hour).withMinute(minute).withSecond(0).withNano(0)
    if (!alarmAt.isAfter(now)) alarmAt = alarmAt.plusDays(1)
    return alarmAt.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
}

/** Returns epoch-ms for [leadMinutes] before [hour]:[minute], rolling to next day if already past. */
internal fun medAlarmTriggerMs(
    hour: Int, minute: Int,
    leadMinutes: Int = 15,
    now: LocalDateTime = LocalDateTime.now(),
): Long {
    // Subtract lead inside LocalTime so midnight wraps cleanly (e.g. 00:00 - 15min = 23:45)
    val alarmTime = java.time.LocalTime.of(hour, minute).minusMinutes(leadMinutes.toLong())
    var alarmAt = now.with(alarmTime).withSecond(0).withNano(0)
    if (!alarmAt.isAfter(now)) alarmAt = alarmAt.plusDays(1)
    return alarmAt.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
}
