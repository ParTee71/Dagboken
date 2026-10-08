package se.partee71.dagboken.core.schema

import kotlin.time.Instant
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import se.partee71.dagboken.core.model.Activity
import se.partee71.dagboken.core.model.Boost
import se.partee71.dagboken.core.model.Checkin
import se.partee71.dagboken.core.model.Dose
import se.partee71.dagboken.core.model.DoseIds
import se.partee71.dagboken.core.model.DoseStatus
import se.partee71.dagboken.core.model.Event
import se.partee71.dagboken.core.model.Identified
import se.partee71.dagboken.core.model.IllnessEpisode
import se.partee71.dagboken.core.model.LegacySettings
import se.partee71.dagboken.core.model.Occasion
import se.partee71.dagboken.core.model.OccasionReminder
import se.partee71.dagboken.core.model.Option
import se.partee71.dagboken.core.model.OptionIds
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

/**
 * Ett syntetiskt prov per samling med ett icke-default-värde i **varje** fält, även i de
 * nästlade, och samlingens codec – delat av codec-, paritets- och fixturtesterna.
 */
object Samples {
    private val created = Instant.parse("2026-09-21T06:00:00.123456Z")
    private val day = LocalDate(2026, 9, 21)

    /** En samling: namnet i `Paths`, codec, provet och modellens defaults. */
    class Entry<T : Identified>(val collection: String, val codec: DocCodec<T>, val sample: T, val defaults: T) {
        fun encoded(): Doc = codec.encode(sample)

        /** Dokumentet läst och skrivet igen med samlingens codec. */
        fun reencode(id: String, doc: Doc): Doc = codec.encode(codec.decode(id, doc))

        /** Fälten där dokumentet, läst med codecen, skiljer sig från modellens default. */
        fun fieldsDifferingFromDefault(id: String, doc: Doc): Set<String> = fieldsDifferingFromDefault(codec.decode(id, doc), defaults)

        fun fieldNames(): Set<String> = persistedFieldNames(sample)
    }

    val settings = Settings(
        theme = ThemeSettings(mode = ThemeMode.DARK, lightStartHour = 6, darkStartHour = 22, isDarkTheme = false),
        reminders = ReminderSettings(
            medsEnabled = true,
            medSlots = Slot.SCHEDULED.mapIndexed { i, slot -> SlotReminder(slot, enabled = false, time = LocalTime(6 + i * 3, 15)) },
            screeningOccasions = Occasion.entries.mapIndexed { i, o -> OccasionReminder(o, enabled = true, time = LocalTime(8 + i * 4, 45)) },
            periodReminderTime = LocalTime(8, 30),
        ),
        profile = Profile(birthYear = 1971, sex = Sex.FEMALE),
        legacy = LegacySettings(dynamicColor = false, sheetsConfig = "https://docs.google.com/spreadsheets/d/exempel/edit"),
    )

    val option = Option(OptionIds.of(OptionKind.SYMPTOM, "Huvudvärk"), kind = OptionKind.SYMPTOM, name = "Huvudvärk", favorite = true, sortOrder = 3, archived = true)

    val boost = Boost("b1", start = LocalDate(2026, 9, 1), end = LocalDate(2026, 9, 14), dose = "25", unit = "µg")

    val prescription = Prescription(
        id = "6f1c2a9e-0b7d-4c55-9a43-1f2e3d4c5b6a",
        name = "Levaxin",
        dose = "0,5",
        unit = "mg",
        slots = listOf(Slot.EVENING, Slot.MORNING),
        schedule = Schedule.Repeating(Repeat.INTERVAL, setOf(DayOfWeek.MONDAY, DayOfWeek.THURSDAY), intervalDays = 3),
        period = Period(start = LocalDate(2026, 1, 1), end = LocalDate(2026, 12, 31)),
        boosts = listOf(boost),
        active = false,
        createdAt = created,
        note = "På fastande mage",
    )

    val prnMedicine = PrnMedicine(
        id = "alvedon", name = "Alvedon", dose = "500", unit = "mg", slot = Slot.NIGHT, minHoursBetween = 6,
        dispensingTime = "30 min", maxPerDay = 4, favorite = true, note = "Max 3 g per dygn",
    )

    val dose = Dose(
        id = DoseIds.prescribed(prescription.id, day, Slot.MIDMORNING),
        date = day,
        slot = Slot.MIDMORNING,
        name = "Levaxin",
        dose = "0,5",
        unit = "mg",
        status = DoseStatus.TAKEN,
        plannedTime = LocalTime(10, 0),
        takenAt = Instant.parse("2026-09-21T08:12:45.000001Z"),
        prescriptionId = prescription.id,
        prnId = "alvedon",
        createdAt = created,
        note = "Tog med frukost",
    )

    private val symptoms = listOf(SymptomScore("huvudvark", 4, "Bakom ögonen"), SymptomScore("ovrigt", 2, "Stel nacke"))

    val screening = Screening(
        id = "s1", date = day, time = LocalTime(8, 15), occasion = Occasion.BREAKFAST, customText = "Morgonkoll",
        energy = 6, stress = 3, symptoms = symptoms, legacySomatic = 7, createdAt = created, note = "Sov dåligt",
    )

    val activity = Activity(
        id = "a1", date = day, time = LocalTime(17, 0), optionId = "promenad", customText = "Svamplockning",
        energy = -2, stress = 1, symptoms = symptoms, legacySomatic = 3, recovering = true, drain = true, minutes = 45,
        createdAt = created, note = "Regn",
    )

    val event = Event(
        id = "e1", date = day, time = LocalTime(10, 5), optionId = "yrsel-handelse", severity = 5, durationMinutes = 10,
        triggers = "Snabb uppresning", actions = "Satte mig ner", createdAt = created, note = "Första gången",
    )

    val episode = IllnessEpisode(
        id = "forkylning", type = "Förkylning", start = LocalDate(2026, 9, 10), end = LocalDate(2026, 9, 17),
        createdAt = created, note = "Hela familjen",
    )

    val checkin = Checkin(
        id = "c1", date = LocalDate(2026, 9, 11), time = LocalTime(19, 0), severity = 4, symptoms = symptoms, legacySomatic = 2,
        createdAt = created, note = "Feber på kvällen",
    )

    /** Alla samlingar i `CollectionNames.USER_COLLECTIONS` + `checkins`. */
    val all: List<Entry<*>> = listOf(
        Entry(CollectionNames.SETTINGS, SettingsCodec, settings, Settings()),
        Entry(CollectionNames.OPTIONS, OptionCodec, option, Option(option.id)),
        Entry(CollectionNames.PRESCRIPTIONS, PrescriptionCodec, prescription, Prescription(prescription.id)),
        Entry(CollectionNames.PRN_MEDICINES, PrnMedicineCodec, prnMedicine, PrnMedicine(prnMedicine.id)),
        Entry(CollectionNames.DOSES, DoseCodec, dose, Dose(dose.id)),
        Entry(CollectionNames.SCREENINGS, ScreeningCodec, screening, Screening(screening.id)),
        Entry(CollectionNames.ACTIVITIES, ActivityCodec, activity, Activity(activity.id)),
        Entry(CollectionNames.EVENTS, EventCodec, event, Event(event.id)),
        Entry(CollectionNames.ILLNESS_EPISODES, IllnessEpisodeCodec, episode, IllnessEpisode(episode.id)),
        Entry(CollectionNames.CHECKINS, CheckinCodec, checkin, Checkin(checkin.id)),
    )

    fun entry(collection: String): Entry<*> = all.single { it.collection == collection }
}
