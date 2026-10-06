package se.partee71.dagboken.reminders

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.annotation.DrawableRes
import androidx.core.app.NotificationCompat
import androidx.core.os.bundleOf
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.datetime.LocalDate
import se.partee71.dagboken.MainActivity
import se.partee71.dagboken.R
import se.partee71.dagboken.core.engine.PeriodEnding
import se.partee71.dagboken.core.model.Dose
import se.partee71.dagboken.core.model.Occasion
import se.partee71.dagboken.core.model.Slot
import se.partee71.dagboken.ui.common.doseText
import se.partee71.dagboken.ui.common.label
import se.partee71.dagboken.ui.common.medicineTitle

/**
 * Notiskanalerna och påminnelsernas notiser (NOT-1, NOT-9…NOT-12, NOT-17; skill notifications-alarms). Alla
 * notiser är **privata** på låsskärmen: där syns bara en offentlig version utan medicinnamn eller annat
 * hälsoinnehåll (skill data-privacy-security). Varje tidpunkt och tillfälle har sin egen notis, så att
 * morgonens "Markera tagen" finns kvar när lunchens påminnelse kommer. Alla PendingIntents är explicita och
 * `FLAG_IMMUTABLE`. Saknas behörigheten att visa notiser postas ingenting (NOT-16 visar det på Påminnelser).
 */
@Singleton
class NotificationHelper @Inject constructor(@ApplicationContext private val context: Context) {
    private val manager = NotificationManagerCompat.from(context)

    /** NOT-1: kanalerna – idempotent; anropas vid start och före varje notis. */
    fun createChannels() {
        val system = context.getSystemService(NotificationManager::class.java) ?: return
        system.createNotificationChannel(
            NotificationChannel(CHANNEL_MEDS, context.getString(R.string.notification_channel_meds_name), NotificationManager.IMPORTANCE_DEFAULT)
                .apply { description = context.getString(R.string.notification_channel_meds_description) },
        )
        system.createNotificationChannel(
            NotificationChannel(CHANNEL_SCREENING, context.getString(R.string.notification_channel_mood_name), NotificationManager.IMPORTANCE_LOW)
                .apply { description = context.getString(R.string.notification_channel_mood_description) },
        )
    }

    /** Om appen får visa notiser (POST_NOTIFICATIONS från API 33, och inte avstängda i systemet) – [reminderAccess]. */
    fun canPost(): Boolean = context.reminderAccess().notifications

    /**
     * NOT-3, NOT-9, NOT-10, NOT-17: medicinpåminnelsen för [slot] – rubriken med tidpunktens namn, en rad per
     * otagen dos ("Levaxin 100 µg", dagens totala dos) och "Markera tagen" för doserna på [date]. Tom lista: ingen notis.
     */
    fun postMedReminder(slot: Slot, date: LocalDate, doses: List<Dose>) {
        if (doses.isEmpty()) return
        val body = doses.joinToString("\n") { medicineTitle(it.name, it.dose, it.unit) }
        val markTaken = Intent(context, MedActionReceiver::class.java)
            .setAction(ReminderIntents.ACTION_MARK_TAKEN)
            .putExtra(ReminderIntents.EXTRA_SLOT, slot.wire)
            .putExtra(ReminderIntents.EXTRA_DATE, date.toString())
        val notification = builder(CHANNEL_MEDS, R.drawable.ic_pill, R.string.notification_med_public_title)
            .setContentTitle(context.getString(R.string.notification_med_title, context.getString(slot.label())))
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .addExtras(bundleOf(EXTRA_BASE_TEXT to body))
            .setContentIntent(openApp(ReminderLaunch.TODAY, REQUEST_OPEN_MED_BASE + slot.ordinal))
            .addAction(
                R.drawable.ic_check,
                context.getString(R.string.notification_med_action_mark_taken),
                PendingIntent.getBroadcast(context, REQUEST_MARK_TAKEN_BASE + slot.ordinal, markTaken, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE),
            )
            .build()
        notify(medId(slot), notification)
    }

    /** NOT-4, NOT-9, NOT-11: måendepåminnelsen för [occasion]; "Logga nu" öppnar Idag med tillfällets måendeark. */
    fun postMoodReminder(occasion: Occasion) {
        val logNow = openApp(ReminderLaunch.MOOD, REQUEST_LOG_MOOD_BASE + occasion.ordinal) { putExtra(ReminderIntents.EXTRA_OCCASION, occasion.wire) }
        val notification = builder(CHANNEL_SCREENING, R.drawable.ic_mood, R.string.notification_mood_public_title)
            .setContentTitle(context.getString(R.string.notification_mood_title, context.getString(occasion.label())))
            .setContentText(context.getString(R.string.notification_mood_text))
            .setContentIntent(openApp(ReminderLaunch.TODAY, REQUEST_OPEN_MOOD_BASE + occasion.ordinal))
            .addAction(R.drawable.ic_mood, context.getString(R.string.notification_mood_action_log_now), logNow)
            .build()
        notify(moodId(occasion), notification)
    }

    /**
     * NOT-12: periodsluten i morgon i en samlad notis i kanalen Medicinpåminnelser; tryck öppnar Mediciner. Tom
     * lista: ingen notis.
     */
    fun postPeriodReminder(endings: List<PeriodEnding>) {
        val (title, body) = when {
            endings.isEmpty() -> return
            endings.size == 1 -> when (val ending = endings.single()) {
                is PeriodEnding.PrescriptionEnds ->
                    context.getString(R.string.notification_period_prescription_title, ending.name) to
                        context.getString(R.string.notification_period_prescription_text)
                is PeriodEnding.BoostEnds ->
                    context.getString(R.string.notification_period_boost_title, ending.name) to
                        context.getString(R.string.notification_period_boost_text, doseText(ending.newDose, ending.unit))
            }
            else -> context.resources.getQuantityString(R.plurals.notification_period_multi_title, endings.size, endings.size) to
                endings.joinToString(", ") { it.name }
        }
        val notification = builder(CHANNEL_MEDS, R.drawable.ic_pill, R.string.notification_period_public_title)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setContentIntent(openApp(ReminderLaunch.MEDICINES, REQUEST_OPEN_PERIOD))
            .build()
        notify(NOTIFICATION_PERIOD, notification)
    }

    /**
     * NOT-10: "Markera tagen" gick inte att genomföra – notisen står kvar, tyst, med en rad överst om att markera i
     * appen. Texten byggs från notisens grundtext, så att raden aldrig upprepas vid fler försök. Finns notisen inte
     * längre händer inget.
     */
    @SuppressLint("MissingPermission") // en notis som redan visas uppdateras; canPost() prövas ändå
    fun markTakenFailed(slot: Slot) {
        val system = context.getSystemService(NotificationManager::class.java) ?: return
        val shown = system.activeNotifications.firstOrNull { it.id == medId(slot) }?.notification ?: return
        val failed = context.getString(R.string.notification_mark_taken_failed)
        val body = shown.extras.getCharSequence(EXTRA_BASE_TEXT)
        val text = listOfNotNull(failed, body).joinToString("\n")
        val updated = Notification.Builder.recoverBuilder(context, shown)
            .setContentText(failed)
            .setStyle(Notification.BigTextStyle().bigText(text))
            .setOnlyAlertOnce(true)
            .build()
        if (canPost()) runCatching { manager.notify(medId(slot), updated) }
    }

    /**
     * NOT-11: appen öppnades från en notis med [launch] – "Logga nu" stänger måendepåminnelsen för tillfället (en
     * notisåtgärd som öppnar appen stänger den inte själv).
     */
    fun dismiss(launch: ReminderLaunch) {
        if (launch is ReminderLaunch.LogMood) manager.cancel(moodId(launch.occasion))
    }

    /** NOT-10: stänger medicinpåminnelsen för [slot] efter "Markera tagen". */
    fun cancelMed(slot: Slot) = manager.cancel(medId(slot))

    /** AUTH-6: utloggad – inga notiser med den förra användarens innehåll står kvar. */
    fun cancelAll() = manager.cancelAll()

    /** Gemensamt för alla notiser: kanal, ikon, privat på låsskärmen med en offentlig version utan innehåll. */
    private fun builder(channel: String, @DrawableRes icon: Int, publicTitle: Int): NotificationCompat.Builder {
        val public = NotificationCompat.Builder(context, channel)
            .setSmallIcon(icon)
            .setContentTitle(context.getString(publicTitle))
            .setContentText(context.getString(R.string.notification_public_text))
            .build()
        return NotificationCompat.Builder(context, channel)
            .setSmallIcon(icon)
            .setColor(ContextCompat.getColor(context, R.color.ic_launcher_background))
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPublicVersion(public)
            .setAutoCancel(true)
    }

    /** Öppnar appen (MainActivity, explicit) med [open] – Idag, måendearket eller Mediciner ([ReminderLaunch]). */
    private fun openApp(open: String, requestCode: Int, extras: Intent.() -> Unit = {}): PendingIntent {
        val intent = Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            .putExtra(ReminderIntents.EXTRA_OPEN, open)
            .apply(extras)
        return PendingIntent.getActivity(context, requestCode, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    @SuppressLint("MissingPermission") // canPost() prövar POST_NOTIFICATIONS
    private fun notify(id: Int, notification: Notification) {
        createChannels()
        if (!canPost()) return
        // canPost() har prövat POST_NOTIFICATIONS; SecurityException kan ändå komma om den dras in just nu.
        runCatching { manager.notify(id, notification) }
    }

    private fun medId(slot: Slot) = NOTIFICATION_MED_BASE + slot.ordinal

    private fun moodId(occasion: Occasion) = NOTIFICATION_MOOD_BASE + occasion.ordinal

    companion object {
        const val CHANNEL_MEDS = "meds"
        const val CHANNEL_SCREENING = "screening"

        /** Medicinpåminnelsens doslista utan tillägg – grunden när notisen uppdateras ([markTakenFailed]). */
        private const val EXTRA_BASE_TEXT = "se.partee71.dagboken.reminders.BASE_TEXT"

        private const val NOTIFICATION_MED_BASE = 100
        private const val NOTIFICATION_MOOD_BASE = 200
        private const val NOTIFICATION_PERIOD = 300

        private const val REQUEST_MARK_TAKEN_BASE = 1_000
        private const val REQUEST_OPEN_MED_BASE = 1_100
        private const val REQUEST_OPEN_MOOD_BASE = 1_200
        private const val REQUEST_LOG_MOOD_BASE = 1_300
        private const val REQUEST_OPEN_PERIOD = 1_400
    }
}
