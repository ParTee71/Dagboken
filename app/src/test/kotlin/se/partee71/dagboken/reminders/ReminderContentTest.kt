package se.partee71.dagboken.reminders

import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus
import org.junit.Test
import se.partee71.dagboken.core.engine.PeriodEnding
import se.partee71.dagboken.core.engine.plannedDoses
import se.partee71.dagboken.core.model.Dose
import se.partee71.dagboken.core.model.DoseIds
import se.partee71.dagboken.core.model.DoseStatus
import se.partee71.dagboken.core.model.MomentSettings
import se.partee71.dagboken.core.model.Occasion
import se.partee71.dagboken.core.model.Period
import se.partee71.dagboken.core.model.Prescription
import se.partee71.dagboken.core.model.ReminderSettings
import se.partee71.dagboken.core.model.Schedule
import se.partee71.dagboken.core.model.Slot
import se.partee71.dagboken.core.schema.DoseCodec
import se.partee71.dagboken.core.schema.PrescriptionCodec
import se.partee71.dagboken.data.firestore.Paths

/**
 * Vad påminnelserna visar och "Markera tagen", mot `FakeCollection` via samma repositories som UI:t (NOT-3, NOT-10,
 * NOT-12, NOT-17, NOT-19, REC-12).
 */
class ReminderContentTest {

    private val f = ReminderFixture()
    private val today = f.today
    private val uid get() = checkNotNull(f.user.uid.value)

    private val levaxin = Prescription(
        "lev",
        name = "Levaxin",
        dose = "100",
        unit = "µg",
        slots = listOf(Slot.MORNING, Slot.EVENING),
        schedule = Schedule.Repeating(),
        period = Period(start = LocalDate(2026, 1, 1))
    )
    private val metformin = levaxin.copy(id = "met", name = "Metformin", dose = "500", unit = "mg", slots = listOf(Slot.MORNING))

    /** Lägger recept och doser direkt i fejkdatabasen, som om de redan fanns i cachen. */
    private fun store(prescriptions: List<Prescription> = emptyList(), doses: List<Dose> = emptyList()) {
        prescriptions.forEach { f.factory.store.set(Paths.prescriptions(uid), it.id, PrescriptionCodec.encode(it), merge = false) }
        doses.forEach { f.factory.store.set(Paths.doses(uid), it.id, DoseCodec.encode(it), merge = false) }
    }

    private fun planned(p: Prescription, slot: Slot) = p.plannedDoses(today, f.zone, reminders = ReminderSettings()).single { it.slot == slot }

    private fun storedDose(id: String) = f.factory.store.read(Paths.doses(uid), id)

    @Test
    fun `medicinpåminnelsen listar tidpunktens otagna doser, också receptens som inte skapats än`() = runTest {
        f.enable(slots = setOf(Slot.MORNING))
        store(listOf(levaxin, metformin), listOf(planned(levaxin, Slot.MORNING)))

        val doses = f.content.medDoses(Slot.MORNING, today)

        assertEquals(listOf("Levaxin" to "100", "Metformin" to "500"), doses.all.map { it.name to it.dose })
        assertEquals(listOf(DoseIds.prescribed("met", today, Slot.MORNING)), doses.missing.map { it.id })
    }

    @Test
    fun `ingen notis när allt är taget, eller när påminnelsen slagits av sedan larmet sattes (NOT-3)`() = runTest {
        f.enable(slots = setOf(Slot.MORNING))
        store(listOf(levaxin), listOf(planned(levaxin, Slot.MORNING).copy(status = DoseStatus.TAKEN)))
        assertTrue(f.content.medDoses(Slot.MORNING, today).isEmpty)

        f.enable(slots = setOf(Slot.EVENING))
        assertTrue(f.content.medDoses(Slot.EVENING, today).all.isNotEmpty())
        assertTrue(f.content.medDoses(Slot.MORNING, today).isEmpty, "tidpunkten avslagen")
        f.enable()
        assertTrue(f.content.medDoses(Slot.EVENING, today).isEmpty, "huvudreglaget av")
    }

    @Test
    fun `markera tagen skriver bara status och tagningstid på befintliga doser`() = runTest {
        f.enable(slots = setOf(Slot.MORNING))
        val renamedElsewhere = planned(levaxin, Slot.MORNING).copy(note = "Fastande")
        store(listOf(levaxin), listOf(renamedElsewhere, planned(levaxin, Slot.EVENING)))
        val before = checkNotNull(storedDose(renamedElsewhere.id))

        f.content.markTaken(Slot.MORNING, today).getOrThrow()

        val after = checkNotNull(storedDose(renamedElsewhere.id))
        assertEquals(DoseStatus.TAKEN.wire, after[DoseCodec.STATUS])
        val changed = (after.keys + before.keys).filter { after[it] != before[it] }.toSet() - "updatedAt"
        assertEquals(setOf(DoseCodec.STATUS, DoseCodec.TAKEN_AT), changed)
        assertEquals(DoseStatus.PLANNED.wire, storedDose(planned(levaxin, Slot.EVENING).id)?.get(DoseCodec.STATUS), "andra tidpunkter orörda")
    }

    @Test
    fun `markera tagen skapar receptets dos som tagen när den inte genererats än, och fungerar offline`() = runTest {
        f.enable(slots = setOf(Slot.MORNING))
        store(listOf(levaxin))
        f.factory.store.online = false
        val id = DoseIds.prescribed("lev", today, Slot.MORNING)

        f.content.markTaken(Slot.MORNING, today).getOrThrow()

        val dose = DoseCodec.decode(id, checkNotNull(storedDose(id)))
        assertEquals(DoseStatus.TAKEN, dose.status)
        assertEquals(f.clock.instant, dose.takenAt)
        assertEquals("lev", dose.prescriptionId)
        assertTrue(f.factory.store.hasPendingWrites, "i cachen, synkas när nätet finns")
        assertNull(storedDose(DoseIds.prescribed("lev", today, Slot.EVENING)), "bara tidpunktens doser")
    }

    @Test
    fun `markera tagen rör aldrig vid behov-doser eller redan överhoppade`() = runTest {
        val prn = Dose("prn", date = today, slot = Slot.AS_NEEDED, name = "Alvedon", prnId = "alvedon")
        val skipped = planned(levaxin, Slot.MORNING).copy(status = DoseStatus.SKIPPED)
        store(listOf(levaxin), listOf(prn, skipped))

        f.content.markTaken(Slot.MORNING, today).getOrThrow()

        assertEquals(DoseStatus.PLANNED.wire, storedDose("prn")?.get(DoseCodec.STATUS))
        assertEquals(DoseStatus.SKIPPED.wire, storedDose(skipped.id)?.get(DoseCodec.STATUS))
    }

    @Test
    fun `måendepåminnelsen uteblir bara för det loggade tillfället (NOT-19)`() = runTest {
        f.enable(occasions = setOf(Occasion.LUNCH, Occasion.DINNER))
        f.screenings.save(null, f.screenings.new(today, Occasion.LUNCH)).getOrThrow()

        assertNull(f.content.moodNotice(Occasion.LUNCH))
        assertNotNull(f.content.moodNotice(Occasion.DINNER))
        assertNull(f.content.moodNotice(Occasion.BEDTIME), "avslaget tillfälle")
    }

    @Test
    fun `en logg i går tystar inte dagens påminnelse`() = runTest {
        f.enable(occasions = setOf(Occasion.LUNCH))
        f.screenings.save(null, f.screenings.new(LocalDate(2026, 5, 5), Occasion.LUNCH)).getOrThrow()

        assertNotNull(f.content.moodNotice(Occasion.LUNCH))
    }

    @Test
    fun `periodslut i morgon samlas, inget slut ger ingen notis (NOT-12)`() = runTest {
        val tomorrow = today.plus(1, DateTimeUnit.DAY)
        assertEquals(emptyList(), f.content.periodEndings())

        store(listOf(levaxin.copy(period = Period(LocalDate(2026, 1, 1), tomorrow)), metformin))

        assertEquals(listOf(PeriodEnding.PrescriptionEnds("lev", "Levaxin", tomorrow)), f.content.periodEndings())
    }

    @Test
    fun `utloggad visas ingenting`() = runTest {
        store(listOf(levaxin))
        f.user.uid.value = null

        assertTrue(f.content.medDoses(Slot.MORNING, today).isEmpty)
        assertNull(f.content.moodNotice(Occasion.LUNCH))
        assertEquals(emptyList(), f.content.periodEndings())
    }

    // ── Stunden (NOT-20) ──

    private suspend fun moment(moment: MomentSettings) = f.settings.update { it.copy(reminders = it.reminders.copy(moment = moment)) }.getOrThrow()

    @Test
    fun `med Stunden på blir tillfällets påminnelse Stunden – med tillfällets klockslag, längden och hjälpfrågan (NOT-20)`() = runTest {
        f.enable(occasions = setOf(Occasion.LUNCH, Occasion.BEDTIME))
        moment(MomentSettings(enabled = true, seconds = 45, takeover = true, question = false))

        assertEquals(
            MoodNotice(MomentLaunch(Occasion.BEDTIME, Occasion.BEDTIME.defaultTime, seconds = 45, question = false, takeover = true)),
            f.content.moodNotice(Occasion.BEDTIME)
        )
        moment(MomentSettings(enabled = true, takeover = false))
        assertEquals(false, f.content.moodNotice(Occasion.LUNCH)?.moment?.takeover)
        assertNotNull(f.content.moodNotice(Occasion.LUNCH))
    }

    @Test
    fun `Stunden av ger en vanlig måendepåminnelse, och ett loggat eller avslaget tillfälle ingen alls (NOT-19, NOT-20)`() = runTest {
        f.enable(occasions = setOf(Occasion.LUNCH, Occasion.DINNER))
        assertEquals(MoodNotice(), f.content.moodNotice(Occasion.LUNCH))

        moment(MomentSettings(enabled = true))
        f.screenings.save(null, f.screenings.new(today, Occasion.LUNCH)).getOrThrow()
        assertNull(f.content.moodNotice(Occasion.LUNCH), "loggat")
        assertNull(f.content.moodNotice(Occasion.BEDTIME), "avslaget")
        assertEquals(Occasion.DINNER, f.content.moodNotice(Occasion.DINNER)?.moment?.occasion)
    }

    @Test
    fun `Stundens innehåll är bara tillfälle, klockslag, längd, fråga och helskärm – inga hälsodata (NOT-20)`() = runTest {
        f.enable(occasions = setOf(Occasion.LUNCH))
        moment(MomentSettings(enabled = true))
        f.screenings.save(null, f.screenings.new(LocalDate(2026, 5, 5), Occasion.LUNCH).copy(energy = 2, note = "Hemligt")).getOrThrow()

        // MomentLaunch har bara de fyra fälten och helskärmsvalet – inget ur loggarna kan följa med till helskärmen eller notisen.
        assertEquals(MomentLaunch(Occasion.LUNCH, Occasion.LUNCH.defaultTime, MomentSettings.DEFAULT_SECONDS, question = true, takeover = true), f.content.moodNotice(Occasion.LUNCH)?.moment)
    }
}
