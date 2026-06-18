package se.partee71.dagboken.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import se.partee71.dagboken.R

object NotificationHelper {

    const val CHANNEL_MEDS      = "meds"
    const val CHANNEL_SCREENING = "screening"

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
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val notification = NotificationCompat.Builder(context, CHANNEL_MEDS)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Dags för $namn")
            .setContentText("$dos om 15 minuter ($tidpunkt)")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .build()
        manager.notify("$namn-$tidpunkt".hashCode(), notification)
    }

    fun postScreeningReminder(context: Context) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val notification = NotificationCompat.Builder(context, CHANNEL_SCREENING)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Glöm inte din screening")
            .setContentText("Hur mår du idag? Logga din dagliga screening.")
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setAutoCancel(true)
            .build()
        manager.notify(NOTIFICATION_ID_SCREENING, notification)
    }
}
