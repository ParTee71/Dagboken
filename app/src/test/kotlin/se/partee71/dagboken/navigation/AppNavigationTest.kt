package se.partee71.dagboken.navigation

import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlinx.datetime.LocalDate
import se.partee71.dagboken.core.engine.DiaryEntry
import se.partee71.dagboken.core.model.Checkin
import se.partee71.dagboken.core.model.Dose
import se.partee71.dagboken.core.model.IllnessEpisode
import se.partee71.dagboken.core.model.OptionKind
import se.partee71.dagboken.ui.components.SettingsPage
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
        DiaryEntryKey(DiaryEntryKind.DOSE, "d1"),
        DiaryEntryKey(DiaryEntryKind.EPISODE, "flu"),
        DiaryEntryKey(DiaryEntryKind.CHECKIN, "c1", episodeId = "flu"),
    ).onEach { key ->
        // Uttömmande: en ny nyckeltyp utan gren här ger ett kompileringsfel.
        when (key) {
            TodayKey, DiaryKey, TrendsKey, MedicinesKey, ComponentGalleryKey -> Unit
            ProfileKey, RemindersKey, ThemeKey, ListsKey, is OptionEditKey, ExportImportKey, AboutKey -> Unit
            is PrescriptionEditKey, is PrnMedicineEditKey, is DiaryEntryKey -> Unit
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
        backStack.push(DiaryEntryKey(DiaryEntryKind.CHECKIN, "c1", episodeId = "flu"))
        assertEquals(backStack.entries, AppBackStack.restore(backStack.save()).entries)
    }

    @Test
    fun `varje nyckel har en egen skärm`() {
        val entries = appEntries(AppBackStack(), account = { null }, onAccount = {})
        // En nyckel utan skärm når fallbacken, som kastar; en med skärm ger sin post.
        keys.forEach { key -> assertNotNull(entries(key)) }
    }

    @Test
    fun `en post i Dagbok öppnar sin egen skärm – en incheckning med sin episod (HIST-3, HIST-9)`() {
        val flu = IllnessEpisode("flu", "Förkylning", start = LocalDate(2026, 10, 3), end = LocalDate(2026, 10, 6))
        assertEquals(DiaryEntryKey(DiaryEntryKind.EPISODE, "flu"), DiaryEntry.EpisodeStart(flu, LocalDate(2026, 10, 3)).key)
        assertEquals(DiaryEntryKey(DiaryEntryKind.EPISODE, "flu"), DiaryEntry.EpisodeEnd(flu, LocalDate(2026, 10, 6)).key)
        assertEquals(DiaryEntryKey(DiaryEntryKind.CHECKIN, "c1", "flu"), DiaryEntry.CheckIn(Checkin("c1"), flu, LocalDate(2026, 10, 5)).key)
        val dose = Dose("d1", LocalDate(2026, 10, 6))
        assertEquals(DiaryEntryKey(DiaryEntryKind.DOSE, "d1"), DiaryEntry.TakenDose(dose, LocalDate(2026, 10, 6), null).key)
    }
}
