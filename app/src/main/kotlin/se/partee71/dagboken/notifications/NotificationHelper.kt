package se.partee71.dagboken.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import se.partee71.dagboken.MainActivity
import se.partee71.dagboken.R
import se.partee71.dagboken.ui.navigation.Screen

object NotificationHelper {

    const val CHANNEL_MEDS      = "meds"
    const val CHANNEL_SCREENING = "screening"
    const val EXTRA_NAV_ROUTE   = "extra_nav_route"

    private const val NOTIFICATION_ID_SCREENING = 1

    fun createChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_MEDS,
                "Medicinpåminnelser",
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply { description = "Påminner om att ta mediciner i rätt tid" }
        )
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_SCREENING,
                "Screeningpåminnelser",
                NotificationManager.IMPORTANCE_LOW,
            ).apply { description = "Daglig påminnelse om att logga screening" }
        )
    }

    fun postMedReminder(context: Context, namn: String, dos: String, tidpunkt: String) {
        val notificationId = "$namn-$tidpunkt".hashCode()
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val notification = NotificationCompat.Builder(context, CHANNEL_MEDS)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Dags för $namn")
            .setContentText("$dos om 15 minuter ($tidpunkt)")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(buildIntent(context, Screen.Mediciner.route, notificationId))
            .build()
        manager.notify(notificationId, notification)
    }

    fun postScreeningReminder(context: Context, eventLabel: String) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val notification = NotificationCompat.Builder(context, CHANNEL_SCREENING)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Dags för screening – $eventLabel")
            .setContentText("Hur mår du? Logga din dagliga screening.")
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setAutoCancel(true)
            .setContentIntent(buildIntent(context, Screen.Aktiviteter.route, NOTIFICATION_ID_SCREENING))
            .build()
        manager.notify(NOTIFICATION_ID_SCREENING, notification)
    }

    private fun buildIntent(context: Context, navRoute: String, requestCode: Int): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(EXTRA_NAV_ROUTE, navRoute)
        }
        return PendingIntent.getActivity(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}
