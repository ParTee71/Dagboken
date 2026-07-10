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

    const val CHANNEL_MEDS         = "meds"
    const val CHANNEL_SCREENING    = "screening"
    const val EXTRA_NAV_ROUTE      = "extra_nav_route"
    const val EXTRA_SCREENING_LABEL = "extra_screening_label"

    const val NOTIFICATION_ID_MED       = 2
    private const val NOTIFICATION_ID_SCREENING = 1

    private const val REQUEST_MED_MARK_TAKEN = 20
    private const val REQUEST_SCREENING_LOG  = 10

    fun createChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_MEDS,
                context.getString(R.string.notification_channel_meds_name),
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply { description = context.getString(R.string.notification_channel_meds_description) }
        )
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_SCREENING,
                context.getString(R.string.notification_channel_screening_name),
                NotificationManager.IMPORTANCE_LOW,
            ).apply { description = context.getString(R.string.notification_channel_screening_description) }
        )
    }

    fun postMedReminder(context: Context, timeLabel: String = "") {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val title = if (timeLabel.isNotEmpty()) {
            context.getString(R.string.notification_med_title_with_time, timeLabel)
        } else {
            context.getString(R.string.notification_med_title)
        }
        val notification = NotificationCompat.Builder(context, CHANNEL_MEDS)
            .setSmallIcon(R.drawable.ic_notification_med)
            .setContentTitle(title)
            .setContentText(context.getString(R.string.notification_med_body))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(buildIntent(context, Screen.Idag.route, NOTIFICATION_ID_MED))
            .addAction(
                R.drawable.ic_notification_med,
                context.getString(R.string.notification_med_action_mark_taken),
                buildMarkTakenAction(context),
            )
            .build()
        manager.notify(NOTIFICATION_ID_MED, notification)
    }

    fun postScreeningReminder(context: Context, eventLabel: String) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val notification = NotificationCompat.Builder(context, CHANNEL_SCREENING)
            .setSmallIcon(R.drawable.ic_notification_screening)
            .setContentTitle(context.getString(R.string.notification_screening_title, eventLabel))
            .setContentText(context.getString(R.string.notification_screening_body))
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setAutoCancel(true)
            .setContentIntent(buildIntent(context, Screen.Idag.route, NOTIFICATION_ID_SCREENING))
            .addAction(
                R.drawable.ic_notification_screening,
                context.getString(R.string.notification_screening_action_log_now),
                buildIntent(context, Screen.Idag.route, REQUEST_SCREENING_LOG, screeningLabel = eventLabel),
            )
            .build()
        manager.notify(NOTIFICATION_ID_SCREENING, notification)
    }

    private fun buildIntent(
        context: Context,
        navRoute: String,
        requestCode: Int,
        screeningLabel: String? = null,
    ): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(EXTRA_NAV_ROUTE, navRoute)
            screeningLabel?.let { putExtra(EXTRA_SCREENING_LABEL, it) }
        }
        return PendingIntent.getActivity(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun buildMarkTakenAction(context: Context): PendingIntent {
        val intent = Intent(context, MedActionReceiver::class.java).apply {
            action = MedActionReceiver.ACTION_MARK_TAKEN
        }
        return PendingIntent.getBroadcast(
            context,
            REQUEST_MED_MARK_TAKEN,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}
