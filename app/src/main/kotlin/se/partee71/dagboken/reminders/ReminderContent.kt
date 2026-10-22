package se.partee71.dagboken.reminders

import javax.inject.Inject
import javax.inject.Provider
import kotlin.time.Clock
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.todayIn
import se.partee71.dagboken.core.engine.PeriodEnding
import se.partee71.dagboken.core.engine.SlotDoses
import se.partee71.dagboken.core.engine.enabledOccasionRows
import se.partee71.dagboken.core.engine.endingOn
import se.partee71.dagboken.core.engine.isMoment
import se.partee71.dagboken.core.engine.moodReminderDue
import se.partee71.dagboken.core.engine.slotDoses
import se.partee71.dagboken.core.model.Occasion
import se.partee71.dagboken.core.model.ReminderSettings
import se.partee71.dagboken.core.model.Slot
import se.partee71.dagboken.data.common.DataError
import se.partee71.dagboken.data.repository.DoseRepository
import se.partee71.dagboken.data.repository.PrescriptionRepository
import se.partee71.dagboken.data.repository.ScreeningRepository
import se.partee71.dagboken.data.repository.SettingsRepository

/** Måendepåminnelsen som ska visas (NOT-4): med [moment] är den tillfällets Stunden (NOT-20), [takeover] = helskärm. */
data class MoodNotice(val moment: MomentLaunch? = null, val takeover: Boolean = false)

/**
 * Vad en påminnelse visar när den utlöses, och "Markera tagen" (NOT-3, NOT-10, NOT-12, NOT-17, NOT-19, NOT-20) – läst ur
 * Firestore-cachen via **samma repositories som UI:t**, offline först; ingen egen Firestore-kod. Beräkningen ligger
 * i `:core` (`slotDoses`, `moodReminderDue`, `endingOn`). En läsning som inte svarar i tid räknas som tom – då
 * postas ingen notis hellre än en felaktig.
 */
class ReminderContent @Inject constructor(
    private val settings: SettingsRepository,
    private val prescriptions: PrescriptionRepository,
    private val doses: DoseRepository,
    private val screenings: ScreeningRepository,
    private val clock: Clock,
    private val zone: Provider<TimeZone>
) {
    /**
     * NOT-3, NOT-17: doserna medicinpåminnelsen för [slot] på [date] listar – tom när inget är otaget, när
     * medicinpåminnelserna eller tidpunkten slagits av sedan larmet sattes, eller när inget går att läsa.
     */
    suspend fun medDoses(slot: Slot, date: LocalDate): SlotDoses {
        if (reminders()?.let { r -> !r.medsEnabled || r.medSlots.none { it.slot == slot && it.enabled } } == true) return SlotDoses()
        return slotDosesOn(slot, date) ?: SlotDoses()
    }

    /** NOT-4, NOT-19: om måendepåminnelsen för [occasion] ska visas – påslagen och inte redan loggad idag. */
    suspend fun moodDue(occasion: Occasion): Boolean = moodNotice(occasion) != null

    /**
     * NOT-4, NOT-19, NOT-20: måendepåminnelsen för [occasion] – `null` när den inte ska visas ([moodDue]), annars med
     * Stunden när den är på för tillfället ([isMoment]): tillfällets klockslag, längden och hjälpfrågan, och om den tar
     * över skärmen. Gick inställningarna inte att läsa blir det en vanlig måendepåminnelse. Inga hälsodata.
     */
    suspend fun moodNotice(occasion: Occasion): MoodNotice? {
        val reminders = reminders()
        if (reminders?.let { r -> r.screeningOccasions.none { it.occasion == occasion && it.enabled } } == true) return null
        val logged = screenings.observeDay(today()).current() ?: return null
        if (!moodReminderDue(occasion, logged)) return null
        val moment = reminders?.takeIf { it.isMoment(occasion) }?.let { r ->
            val time = r.enabledOccasionRows.first { it.occasion == occasion }.time
            MomentLaunch(occasion, time, r.moment.seconds, r.moment.question)
        }
        return MoodNotice(moment, takeover = moment != null && reminders?.moment?.takeover == true)
    }

    /** NOT-12: de aktiva recepten vars period eller höjning tar slut i morgon; tom = ingen notis. */
    suspend fun periodEndings(): List<PeriodEnding> {
        val list = prescriptions.observe().current() ?: return emptyList()
        return list.endingOn(today().plus(1, DateTimeUnit.DAY))
    }

    /**
     * NOT-10: "Markera tagen" – tidpunktens ej tagna schemalagda doser på [date] blir tagna nu, via
     * [DoseRepository.markTaken]: allt i cachen direkt i en skrivning, offline först som avbockningen i appen – väntar
     * aldrig på servern och synkas när nätet finns. Vid behov-doser rörs inte. Lyckas när doserna
     * skrivits **eller** inget fanns att markera; gick recepten eller doserna inte att läsa i tid
     * ([DataError.Offline]) eller misslyckades skrivningen blir det ett fel – då står notisen kvar.
     */
    suspend fun markTaken(slot: Slot, date: LocalDate): Result<Unit> {
        val pending = slotDosesOn(slot, date) ?: return Result.failure(DataError.Offline)
        return doses.markTaken(pending, clock.now())
    }

    /**
     * Tidpunktens otagna doser, eller `null` när recepten eller dagens doser inte gick att läsa – båda läsningarna
     * delar på [READ_WAIT], så att mottagaren alltid hinner visa utfallet. Klockslaget på de saknade doserna används
     * aldrig – notisen listar namnen och "Markera tagen" skriver inte `plannedTime` (`DoseRepository.markTaken`) – så
     * inställningarna läses inte för det; det skulle bara äta av tiden mottagaren har.
     */
    private suspend fun slotDosesOn(slot: Slot, date: LocalDate): SlotDoses? = withTimeoutOrNull(READ_WAIT) {
        val list = prescriptions.observe().firstValue() ?: return@withTimeoutOrNull null
        val day = doses.observeDay(date).firstValue() ?: return@withTimeoutOrNull null
        slotDoses(slot, date, today(), list, day, zone.get(), ReminderSettings())
    }

    /** Påminnelseinställningarna, eller `null` när de inte går att läsa i tid – då gäller larmet som det sattes. */
    private suspend fun reminders(): ReminderSettings? = withTimeoutOrNull(SETTINGS_WAIT) { settings.get().getOrNull()?.reminders }

    private fun today(): LocalDate = clock.todayIn(zone.get())

    /** Flödets första värde – ur cachen direkt – eller `null` vid fel, utloggad eller inget svar i tid. */
    private suspend fun <T> Flow<T>.current(): T? = withTimeoutOrNull(READ_WAIT) { firstValue() }

    /** Flödets första värde, eller `null` vid fel eller utloggad (utan egen tidsgräns – anroparen har en). */
    private suspend fun <T> Flow<T>.firstValue(): T? = catch { }.firstOrNull()

    internal companion object {
        /**
         * Tak för läsningarna i ett anrop ur cachen (de tar millisekunder när cachen kan svara). Tillsammans med
         * [SETTINGS_WAIT] ryms de i det mottagaren har kvar efter nästa larm (`ReceiverWork`).
         */
        val READ_WAIT = 2500.milliseconds

        /** Tak för inställningarna. */
        val SETTINGS_WAIT = 1500.milliseconds
    }
}
