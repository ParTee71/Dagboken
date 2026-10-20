package se.partee71.dagboken.reminders

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat

/**
 * NOT-16: om påminnelserna kan nå fram – [notifications] = appen får visa notiser, [exactAlarms] = appen får ställa
 * exakta larm (annars kommer de ungefärligt, NOT-8), [fullScreen] = appen får visa helskärmsnotiser (Stunden, NOT-20;
 * annars en vanlig notis). Systemets läge – läses om när skärmen visas igen.
 */
data class ReminderAccess(val notifications: Boolean = true, val exactAlarms: Boolean = true, val fullScreen: Boolean = true)

/** Behörigheterna just nu (NOT-16). */
fun Context.reminderAccess(): ReminderAccess = ReminderAccess(
    notifications = NotificationManagerCompat.from(this).areNotificationsEnabled() && !needsNotificationPermission(),
    exactAlarms = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || getSystemService(AlarmManager::class.java)?.canScheduleExactAlarms() != false,
    // Före Android 14 beviljas USE_FULL_SCREEN_INTENT vid installationen och kan inte dras in.
    fullScreen = Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE || getSystemService(NotificationManager::class.java)?.canUseFullScreenIntent() != false
)

/** NOT-16: notisbehörigheten (API 33+) saknas och ska begäras när en påminnelse slås på – aldrig vid första start. */
fun Context.needsNotificationPermission(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
    ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED

/** NOT-16: systemets notisinställningar för appen. */
fun Context.openNotificationSettings() = openSystemSettings(
    Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
)

/** NOT-16: systemets "Alarm och påminnelser" för appen (API 31+; tidigare behövs ingen rätt). */
fun Context.openExactAlarmSettings() {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
    openSystemSettings(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.fromParts("package", packageName, null)))
}

/** NOT-20: systemets "Helskärmsaviseringar" för appen (API 34+; tidigare behövs ingen tillåtelse). */
fun Context.openFullScreenIntentSettings() {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) return
    openSystemSettings(Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, Uri.fromParts("package", packageName, null)))
}

/** Öppnar systemskärmen; finns den inte på enheten öppnas appens informationssida i stället. */
private fun Context.openSystemSettings(intent: Intent) {
    val fallback = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null))
    for (candidate in listOf(intent, fallback)) {
        try {
            startActivity(candidate.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            return
        } catch (_: ActivityNotFoundException) {
            // Nästa kandidat.
        }
    }
}
