package se.partee71.dagboken.core.schema

import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalTime
import org.junit.Test
import se.partee71.dagboken.core.model.Activity
import se.partee71.dagboken.core.model.Boost
import se.partee71.dagboken.core.model.Checkin
import se.partee71.dagboken.core.model.Dose
import se.partee71.dagboken.core.model.DoseStatus
import se.partee71.dagboken.core.model.Event
import se.partee71.dagboken.core.model.IllnessEpisode
import se.partee71.dagboken.core.model.LegacySettings
import se.partee71.dagboken.core.model.Occasion
import se.partee71.dagboken.core.model.OccasionReminder
import se.partee71.dagboken.core.model.Option
import se.partee71.dagboken.core.model.OptionKind
import se.partee71.dagboken.core.model.Period
import se.partee71.dagboken.core.model.Prescription
import se.partee71.dagboken.core.model.PrnMedicine
import se.partee71.dagboken.core.model.Profile
import se.partee71.dagboken.core.model.ReminderSettings
import se.partee71.dagboken.core.model.Repeat
import se.partee71.dagboken.core.model.Schedule
import se.partee71.dagboken.core.model.Screening
import se.partee71.dagboken.core.model.Settings
import se.partee71.dagboken.core.model.Sex
import se.partee71.dagboken.core.model.Slot
import se.partee71.dagboken.core.model.SlotReminder
import se.partee71.dagboken.core.model.SymptomScore
import se.partee71.dagboken.core.model.ThemeMode
import se.partee71.dagboken.core.model.ThemeSettings
import se.partee71.dagboken.core.schema.Samples.prescription

/** Ett kontraktstest per dokument-codec (skill data-safety-backup); proven i [Samples] är syntetiska. */
class CodecsTest {

    @Test
    fun `SettingsCodec`() {
        assertCodecContract(SettingsCodec, Samples.settings, Settings())
        assertEveryFieldDiffersFromDefault(Samples.settings.theme, ThemeSettings())
        assertEveryFieldDiffersFromDefault(Samples.settings.reminders, ReminderSettings())
        assertEveryFieldDiffersFromDefault(Samples.settings.profile, Profile())
        assertEveryFieldDiffersFromDefault(Samples.settings.legacy, LegacySettings())
        Samples.settings.reminders.medSlots.forEach {
            assertNotEquals(SlotReminder(it.slot).enabled, it.enabled)
            assertNotEquals(SlotReminder(it.slot).time, it.time)
        }
        Samples.settings.reminders.screeningOccasions.forEach {
            assertNotEquals(OccasionReminder(it.occasion).enabled, it.enabled)
            assertNotEquals(OccasionReminder(it.occasion).time, it.time)
        }
    }

    @Test
    fun `OptionCodec`() = assertCodecContract(OptionCodec, Samples.option, Option(Samples.option.id))

    @Test
    fun `PrescriptionCodec`() {
        assertCodecContract(PrescriptionCodec, prescription, Prescription(prescription.id))
        assertEveryFieldDiffersFromDefault(Samples.boost, Boost())
        assertEveryFieldDiffersFromDefault(prescription.period, Period())
    }

    @Test
    fun `PrnMedicineCodec`() = assertCodecContract(PrnMedicineCodec, Samples.prnMedicine, PrnMedicine(Samples.prnMedicine.id))

    @Test
    fun `DoseCodec`() = assertCodecContract(DoseCodec, Samples.dose, Dose(Samples.dose.id))

    @Test
    fun `ScreeningCodec`() = assertCodecContract(ScreeningCodec, Samples.screening, Screening(Samples.screening.id))

    @Test
    fun `ActivityCodec`() = assertCodecContract(ActivityCodec, Samples.activity, Activity(Samples.activity.id))

    @Test
    fun `EventCodec`() = assertCodecContract(EventCodec, Samples.event, Event(Samples.event.id))

    @Test
    fun `IllnessEpisodeCodec`() = assertCodecContract(IllnessEpisodeCodec, Samples.episode, IllnessEpisode(Samples.episode.id))

    @Test
    fun `CheckinCodec`() = assertCodecContract(CheckinCodec, Samples.checkin, Checkin(Samples.checkin.id))

    @Test
    fun `varje prov har ett värde i varje fält, även nästlade`() {
        for (entry in Samples.all) assertEveryFieldSet(entry.encoded(), entry.collection)
    }

    @Test
    fun `schedule - känt mönster bevarar dagar och intervall oavsett upprepning`() {
        assertVariantCodecContract(ScheduleCodec, listOf(prescription.schedule to Schedule.Repeating()))
        val daily = Schedule.Repeating(Repeat.DAILY, setOf(DayOfWeek.SUNDAY), intervalDays = 5)
        assertEquals(daily, ScheduleCodec.decode(ScheduleCodec.encode(daily)))
        assertEquals(mapOf("repeat" to "daily", "days" to listOf(7), "intervalDays" to 5), ScheduleCodec.encode(daily))
    }

    @Test
    fun `schedule - saknad eller okänd upprepning skrivs tillbaka exakt`() {
        val newer = mapOf("repeat" to "monthly", "dayOfMonth" to 15)
        assertEquals(Schedule.Unknown(newer), ScheduleCodec.decode(newer))
        assertEquals(newer, ScheduleCodec.encode(ScheduleCodec.decode(newer)))
        assertEquals(Schedule.Unknown("text"), ScheduleCodec.decode("text"))
        assertEquals(Schedule.Unknown(null), ScheduleCodec.decode(null))
        assertNull(ScheduleCodec.encode(Schedule.Unknown()))
        val stored = PrescriptionCodec.encode(prescription.copy(schedule = Schedule.Unknown(newer)))
        assertEquals(newer, PrescriptionCodec.encode(PrescriptionCodec.decode(prescription.id, stored))["schedule"])
    }

    @Test
    fun `okända enumvärden från en nyare app ger modellens default`() {
        assertEquals(OptionKind.ACTIVITY, OptionCodec.decode("o", mapOf("kind" to "plant")).kind)
        assertEquals(DoseStatus.PLANNED, DoseCodec.decode("d", mapOf("status" to "lost")).status)
        assertEquals(Slot.AS_NEEDED, DoseCodec.decode("d", mapOf("slot" to "brunch")).slot)
        assertEquals(Slot.AS_NEEDED, PrnMedicineCodec.decode("p", mapOf("slot" to 3)).slot)
        assertNull(ScreeningCodec.decode("s", mapOf("occasion" to "fika")).occasion)
        assertEquals(listOf(Slot.MORNING, Slot.NIGHT), PrescriptionCodec.decode("r", mapOf("slots" to listOf("morning", "brunch", 7, "night"))).slots)
        val settings = SettingsCodec.decode("app", mapOf("theme" to mapOf("mode" to "sepia"), "profile" to mapOf("sex" to "annat")))
        assertEquals(ThemeMode.AUTO, settings.theme.mode)
        assertEquals(Sex.UNSPECIFIED, settings.profile.sex)
    }

    @Test
    fun `varje enumvärde lagras med sitt engelska namn och överlever rundturen`() {
        for (slot in Slot.entries) assertEquals(slot, DoseCodec.decode("d", DoseCodec.encode(Dose("d", slot = slot))).slot)
        for (status in DoseStatus.entries) assertEquals(status, DoseCodec.decode("d", DoseCodec.encode(Dose("d", status = status))).status)
        for (kind in OptionKind.entries) assertEquals(kind, OptionCodec.decode("o", OptionCodec.encode(Option("o", kind = kind))).kind)
        for (o in Occasion.entries) assertEquals(o, ScreeningCodec.decode("s", ScreeningCodec.encode(Screening("s", occasion = o))).occasion)
        for (r in Repeat.entries) assertEquals(Schedule.Repeating(r), ScheduleCodec.decode(ScheduleCodec.encode(Schedule.Repeating(r))))
        assertEquals("asNeeded", DoseCodec.encode(Dose("d"))["slot"])
        assertEquals("planned", DoseCodec.encode(Dose("d"))["status"])
    }

    @Test
    fun `påminnelseraderna läses på nyckeln, inte på positionen, och saknade rader får standardvärdet`() {
        val stored = mapOf(
            "reminders" to mapOf(
                "medSlots" to listOf(
                    mapOf("slot" to "night", "enabled" to false, "time" to "23:10"),
                    mapOf("slot" to "framtida", "enabled" to true, "time" to "03:00"),
                    "inte en rad",
                    mapOf("slot" to "morning", "time" to "trasig"),
                ),
                "screeningOccasions" to listOf(mapOf("occasion" to "bedtime", "enabled" to true, "time" to "22:30")),
            ),
        )
        val reminders = SettingsCodec.decode("app", stored).reminders
        assertEquals(Slot.SCHEDULED, reminders.medSlots.map { it.slot })
        assertEquals(SlotReminder(Slot.NIGHT, enabled = false, time = LocalTime(23, 10)), reminders.medSlots.last())
        assertEquals(SlotReminder(Slot.MORNING), reminders.medSlots.first(), "ogiltigt klockslag → standardtiden")
        assertEquals(Occasion.entries, reminders.screeningOccasions.map { it.occasion })
        assertEquals(OccasionReminder(Occasion.BEDTIME, enabled = true, time = LocalTime(22, 30)), reminders.screeningOccasions.last())
        assertEquals(OccasionReminder(Occasion.BREAKFAST), reminders.screeningOccasions.first())
    }

    @Test
    fun `symptomen behåller ordningen och element som inte är objekt hoppas över`() {
        val stored = mapOf("symptoms" to listOf(mapOf("optionId" to "yrsel", "score" to 2L), "trasig", mapOf("optionId" to "huvudvark", "score" to 7.0)))
        assertEquals(listOf(SymptomScore("yrsel", 2), SymptomScore("huvudvark", 7)), CheckinCodec.decode("c", stored).symptoms)
        assertEveryFieldDiffersFromDefault(SymptomScore("ovrigt", 3, "Stel nacke"), SymptomScore())
    }

    @Test
    fun `ett tomt klockslag eller datum skrivs som null och läses som null`() {
        val encoded = DoseCodec.encode(Dose("d"))
        assertNull(encoded["date"])
        assertNull(encoded["plannedTime"])
        assertEquals(mapOf("start" to null, "end" to null), PrescriptionCodec.encode(Prescription("r"))["period"])
    }

    @Test
    fun `3x-värdena i legacy bevaras exakt, och saknade eller felaktiga blir null`() {
        val stored = SettingsCodec.encode(Samples.settings)
        assertEquals(mapOf("dynamicColor" to false, "sheetsConfig" to "https://docs.google.com/spreadsheets/d/exempel/edit"), stored["legacy"])
        assertEquals(LegacySettings(), SettingsCodec.decode("app", emptyMap()).legacy)
        assertEquals(LegacySettings(), SettingsCodec.decode("app", mapOf("legacy" to mapOf("dynamicColor" to "ja", "sheetsConfig" to 3))).legacy)
        assertEquals(mapOf("dynamicColor" to null, "sheetsConfig" to null), SettingsCodec.encode(Settings())["legacy"])
    }

    @Test
    fun `okända fält i ett listelement bevaras inte - listor skrivs hela (DAT-10)`() {
        // Dokumenterat beteende: okända toppfält och okända fält i nästlade objekt utanför listor
        // överlever en merge-skrivning av andra fält, men en lista skrivs alltid hel ur modellen.
        // Ett nytt fält i ett listelement kräver därför höjd schemaVersion (skill data-safety-backup).
        val stored = mapOf(
            "symptoms" to listOf(mapOf("optionId" to "yrsel", "score" to 2L, "customText" to null, "framtida" to "x")),
            "framtidaToppfalt" to 1,
        )
        val written = ActivityCodec.encode(ActivityCodec.decode("a", stored))
        assertEquals(listOf(mapOf("optionId" to "yrsel", "score" to 2, "customText" to null)), written["symptoms"])
        assertEquals(null, written["framtidaToppfalt"], "okända toppfält skrivs inte av codecen – merge lämnar dem orörda")
        val boosts = PrescriptionCodec.encode(PrescriptionCodec.decode("r", mapOf("boosts" to listOf(BoostCodec.encode(Samples.boost) + ("framtida" to 1)))))
        assertEquals(listOf(BoostCodec.encode(Samples.boost)), boosts["boosts"])
        val rows = SettingsCodec.encode(SettingsCodec.decode("app", mapOf("reminders" to mapOf("medSlots" to listOf(mapOf("slot" to "night", "enabled" to false, "time" to "23:10", "framtida" to true))))))
        assertEquals(mapOf("slot" to "night", "enabled" to false, "time" to "23:10"), (asDoc(rows["reminders"])["medSlots"] as List<*>).last())
    }
}
