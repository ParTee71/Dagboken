package se.partee71.dagboken.navigation

import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlinx.datetime.LocalDate
import se.partee71.dagboken.core.engine.DiaryEntry
import se.partee71.dagboken.core.model.Activity
import se.partee71.dagboken.core.model.Checkin
import se.partee71.dagboken.core.model.Event
import se.partee71.dagboken.core.model.Screening
import se.partee71.dagboken.core.model.Dose
import se.partee71.dagboken.core.model.IllnessEpisode
import se.partee71.dagboken.core.model.OptionKind
import se.partee71.dagboken.ui.components.LogChoice
import se.partee71.dagboken.ui.components.SettingsPage
import se.partee71.dagboken.ui.log.LogEvent
import se.partee71.dagboken.ui.log.LogTarget
import org.junit.Test

/** Varje nyckel har en skärm i `appEntries` – en saknad skulle krascha appen (fallbacken är ett fel). */
class AppNavigationTest {

    /** En av varje nyckeltyp; när AppKey får en ny typ följer listan med (kompilatorn kräver det). */
    private val keys: List<AppKey> = listOf(
        TodayKey,
        DiaryKey,
        TrendsKey,
        MedicinesKey,
        ComponentGalleryKey,
        ProfileKey,
        RemindersKey,
        ThemeKey,
        ListsKey,
        OptionEditKey(OptionKind.SYMPTOM),
        OptionEditKey(OptionKind.ACTIVITY, "activity-promenad-c78928"),
        ExportImportKey,
        AboutKey,
        PrescriptionEditKey(),
        PrescriptionEditKey("6f1c2a9e"),
        PrescriptionEditKey("6f1c2a9e", extend = true),
        PrnMedicineEditKey(),
        PrnMedicineEditKey("a7b8c9d0"),
        EpisodeKey("flu"),
        EpisodeKey("flu", LocalDate(2026, 10, 5)),
        EpisodeEditKey("flu"),
        ActivityEditKey(),
        ActivityEditKey("a1"),
        ActivityEditKey(date = LocalDate(2026, 10, 5)),
        EventEditKey(),
        EventEditKey("e1"),
        EventEditKey(date = LocalDate(2026, 10, 5)),
        DoseEditKey(),
        DoseEditKey("d1"),
        DoseEditKey(prnId = "a7b8c9d0", date = LocalDate(2026, 10, 5)),
        EpisodeNewKey(),
        EpisodeNewKey(LocalDate(2026, 10, 5)),
        CheckinEditKey("flu"),
        CheckinEditKey("flu", "c1"),
    ).onEach { key ->
        // Uttömmande: en ny nyckeltyp utan gren här ger ett kompileringsfel.
        when (key) {
            TodayKey, DiaryKey, TrendsKey, MedicinesKey, ComponentGalleryKey -> Unit
            ProfileKey, RemindersKey, ThemeKey, ListsKey, is OptionEditKey, ExportImportKey, AboutKey -> Unit
            is PrescriptionEditKey, is PrnMedicineEditKey, is EpisodeKey, is EpisodeEditKey -> Unit
            is ActivityEditKey, is EventEditKey, is DoseEditKey, is EpisodeNewKey, is CheckinEditKey -> Unit
        }
    }

    @Test
    fun `varje rad i inställningsarket öppnar sin underskärm på den aktuella fliken (NAV-9)`() {
        assertEquals(
            listOf(ProfileKey, RemindersKey, ThemeKey, ListsKey, ExportImportKey, AboutKey),
            SettingsPage.entries.map { it.key },
        )
        val backStack = AppBackStack()
        backStack.select(TrendsKey)
        backStack.push(SettingsPage.Lists.key)
        backStack.push(OptionEditKey(OptionKind.EVENT))
        assertEquals(listOf(TodayKey, TrendsKey, ListsKey, OptionEditKey(OptionKind.EVENT)), backStack.entries)
    }

    @Test
    fun `nycklarna överlever processdöd`() {
        val backStack = AppBackStack()
        backStack.push(OptionEditKey(OptionKind.ACTIVITY, "activity-promenad-c78928"))
        backStack.push(PrnMedicineEditKey("a7b8c9d0"))
        backStack.push(PrescriptionEditKey("6f1c2a9e", extend = true))
        backStack.push(CheckinEditKey("flu", "c1", LocalDate(2026, 10, 5)))
        backStack.push(ActivityEditKey(date = LocalDate(2026, 10, 5)))
        backStack.push(EventEditKey("e1"))
        backStack.push(DoseEditKey(prnId = "a7b8c9d0", date = LocalDate(2026, 10, 5)))
        backStack.push(EpisodeNewKey(LocalDate(2026, 10, 5)))
        backStack.push(EpisodeKey("flu"))
        backStack.push(EpisodeEditKey("flu"))
        assertEquals(backStack.entries, AppBackStack.restore(backStack.save()).entries)
    }

    @Test
    fun `sjukdomsdetaljen läggs på den aktuella fliken och tillbaka återställer flikens stack (HIST-9, HEM-12)`() {
        val backStack = AppBackStack()
        backStack.push(EpisodeKey("flu"))
        backStack.push(EpisodeEditKey("flu"))
        assertEquals(listOf(TodayKey, EpisodeKey("flu"), EpisodeEditKey("flu")), backStack.entries)
        backStack.popIfTop(EpisodeEditKey("flu"))
        backStack.popIfTop(EpisodeKey("flu"))
        assertEquals(listOf(TodayKey), backStack.entries)

        backStack.select(DiaryKey)
        backStack.push(EpisodeKey("flu"))
        backStack.push(CheckinEditKey("flu", "c1"))
        backStack.select(TodayKey)
        backStack.select(DiaryKey)
        assertEquals(listOf(TodayKey, DiaryKey, EpisodeKey("flu"), CheckinEditKey("flu", "c1")), backStack.entries, "flikens stack står kvar vid flikbyte")
        backStack.popIfTop(CheckinEditKey("flu", "c1"))
        backStack.popIfTop(EpisodeKey("flu"))
        assertEquals(listOf(TodayKey, DiaryKey), backStack.entries)
    }

    @Test
    fun `Ny incheckning i detaljen behåller dagen som Idag visar, från Dagbok idag, och en befintlig öppnas utan dag (SJ-2, HEM-12, HEM-14)`() {
        val day = LocalDate(2026, 10, 5)
        assertEquals(CheckinEditKey("flu", date = day), EpisodeKey("flu", day).checkinKey(null))
        assertEquals(CheckinEditKey("flu"), EpisodeKey("flu").checkinKey(null), "från Dagbok: idag")
        assertEquals(CheckinEditKey("flu", "c1"), EpisodeKey("flu", day).checkinKey("c1"))
    }

    @Test
    fun `varje nyckel har en egen skärm`() {
        val entries = appEntries(AppBackStack(), account = { null }, onAccount = {})
        // En nyckel utan skärm når fallbacken, som kastar; en med skärm ger sin post.
        keys.forEach { key -> assertNotNull(entries(key)) }
    }

    @Test
    fun `en post i Dagbok öppnar sin egen skärm – en dos dosformuläret, en incheckning sitt under sin episod (HIST-3, HIST-9, MED-15)`() {
        val day = LocalDate(2026, 10, 6)
        assertEquals(ActivityEditKey("a1"), DiaryEntry.Action(Activity("a1", day), day).key)
        assertEquals(EventEditKey("e1"), DiaryEntry.Happening(Event("e1", day), day).key)
        assertNull(DiaryEntry.Mood(Screening("s1", day), day).key, "en måendepost öppnas i måendearket")
        val flu = IllnessEpisode("flu", "Förkylning", start = LocalDate(2026, 10, 3), end = LocalDate(2026, 10, 6))
        assertEquals(EpisodeKey("flu"), DiaryEntry.EpisodeStart(flu, LocalDate(2026, 10, 3)).key)
        assertEquals(EpisodeKey("flu"), DiaryEntry.EpisodeEnd(flu, LocalDate(2026, 10, 6)).key)
        assertEquals(CheckinEditKey("flu", "c1"), DiaryEntry.CheckIn(Checkin("c1"), flu, LocalDate(2026, 10, 5)).key)
        val dose = Dose("d1", LocalDate(2026, 10, 6))
        assertEquals(DoseEditKey("d1"), DiaryEntry.TakenDose(dose, LocalDate(2026, 10, 6), null).key)
    }

    @Test
    fun `plusknappens val öppnar sina formulär mot den visade dagen på den aktuella fliken (NAV-10, HEM-14)`() {
        val day = LocalDate(2026, 10, 5)
        val backStack = AppBackStack()
        backStack.select(DiaryKey)
        val events = mutableListOf<LogEvent>()
        backStack.log(LogChoice.Mood, day, events::add)
        assertEquals(listOf<LogEvent>(LogEvent.PickOccasion(day)), events, "Mående öppnar tillfällesväljaren")
        assertEquals(listOf(TodayKey, DiaryKey), backStack.entries)
        backStack.log(LogChoice.Activity, day, events::add)
        assertEquals(ActivityEditKey(date = day), backStack.entries.last())
        backStack.pop()
        backStack.log(LogChoice.Event, null, events::add)
        assertEquals(EventEditKey(), backStack.entries.last())
        backStack.pop()
        backStack.log(LogChoice.Dose, day, events::add)
        backStack.log(LogChoice.Illness, null, events::add)
        assertEquals(listOf(TodayKey, DiaryKey), backStack.entries, "Dos och Sjukdom öppnar sina val som ark")
        assertEquals(listOf(LogEvent.PickOccasion(day), LogEvent.PickDose(day), LogEvent.PickIllness(null)), events)
    }

    @Test
    fun `dos- och sjukdomsvalen öppnar formulären mot dagen (NAV-10, MED-16, SJ-1, SJ-2)`() {
        val day = LocalDate(2026, 10, 5)
        assertEquals(DoseEditKey(prnId = "p1", date = day), LogTarget.AsNeeded("p1", day).key)
        assertEquals(DoseEditKey(date = null), LogTarget.OneOffDose(null).key)
        assertEquals(CheckinEditKey("flu", date = day), LogTarget.Checkin("flu", day).key)
        assertEquals(EpisodeNewKey(day), LogTarget.NewEpisode(day).key)
    }
}
