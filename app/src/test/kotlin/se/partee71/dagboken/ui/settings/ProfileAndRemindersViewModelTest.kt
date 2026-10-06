package se.partee71.dagboken.ui.settings

import app.cash.turbine.test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import org.junit.Rule
import org.junit.Test
import se.partee71.dagboken.R
import se.partee71.dagboken.core.model.Occasion
import se.partee71.dagboken.core.model.OccasionReminder
import se.partee71.dagboken.core.model.Profile
import se.partee71.dagboken.core.model.Settings
import se.partee71.dagboken.core.model.Sex
import se.partee71.dagboken.core.model.Slot
import se.partee71.dagboken.core.model.SlotReminder
import se.partee71.dagboken.core.model.ThemeMode
import se.partee71.dagboken.data.FakeCollectionFactory
import se.partee71.dagboken.data.FixedClock
import se.partee71.dagboken.data.TestUserScope
import se.partee71.dagboken.data.common.DataError
import se.partee71.dagboken.data.firestore.Paths
import se.partee71.dagboken.data.repository.DefaultSettingsRepository
import se.partee71.dagboken.data.repository.SettingsRepository
import se.partee71.dagboken.testing.MainDispatcherRule
import se.partee71.dagboken.ui.common.EditorEffect

/** Profil (HLS-11) och Påminnelser (SET-4, NOT-4, NOT-13, NOT-18) – formulären över `settings/app`. */
class ProfileAndRemindersViewModelTest {

    @get:Rule
    val main = MainDispatcherRule()

    private val factory = FakeCollectionFactory()
    private val settings = DefaultSettingsRepository(factory)

    /** FixedClock står i september 2026: rimliga födelseår är 1906–2026 (samma spann som sömnkvaliteten). */
    private fun profile() = ProfileEditViewModel(settings, FixedClock(), TimeZone.UTC)

    @Test
    fun `profilen laddas, valideras och sparas utan att röra något annat`() = runTest(main.dispatcher) {
        settings.update { it.copy(theme = it.theme.copy(mode = ThemeMode.DARK), profile = Profile(1971, Sex.FEMALE)) }.getOrThrow()
        val vm = profile()
        assertEquals(ProfileForm("1971", Sex.FEMALE), vm.editor.state.value.value)
        assertFalse(vm.editor.state.value.isDirty)

        vm.editor.state.test {
            skipItems(1)
            vm.onEvent(ProfileEvent.BirthYearChanged("18x9"))
            val invalid = awaitItem()
            assertEquals("189", invalid.value.birthYear, "bara siffror")
            assertEquals(R.string.profile_birth_year_invalid, invalid.errorFor(ProfileForm.BIRTH_YEAR))
            assertFalse(invalid.canSave)

            vm.onEvent(ProfileEvent.BirthYearChanged("19801"))
            assertEquals("1980", awaitItem().value.birthYear, "högst fyra siffror")
            vm.onEvent(ProfileEvent.SexChanged(Sex.MALE))
            assertTrue(awaitItem().canSave)
            cancelAndIgnoreRemainingEvents()
        }
        vm.editor.effects.test {
            vm.onEvent(ProfileEvent.Save)
            assertEquals(EditorEffect.Done, awaitItem())
        }
        val stored = settings.get().getOrThrow()
        assertEquals(Profile(1980, Sex.MALE), stored.profile)
        assertEquals(ThemeMode.DARK, stored.theme.mode, "temat orört")
    }

    @Test
    fun `tomt födelseår är inte angivet och giltigt`() = runTest(main.dispatcher) {
        settings.update { it.copy(profile = Profile(1971)) }.getOrThrow()
        val vm = profile()

        vm.onEvent(ProfileEvent.BirthYearChanged(""))
        assertTrue(vm.editor.state.value.canSave)
        vm.onEvent(ProfileEvent.Save)

        assertNull(settings.get().getOrThrow().profile.birthYear)
    }

    @Test
    fun `utanför det rimliga spannet är födelseåret ogiltigt`() = runTest(main.dispatcher) {
        val vm = profile()
        for (year in listOf("1905", "2027")) {
            vm.onEvent(ProfileEvent.BirthYearChanged(year))
            assertEquals(R.string.profile_birth_year_invalid, vm.editor.state.value.errorFor(ProfileForm.BIRTH_YEAR), year)
        }
        for (year in listOf("1906", "2026")) {
            vm.onEvent(ProfileEvent.BirthYearChanged(year))
            assertNull(vm.editor.state.value.errorFor(ProfileForm.BIRTH_YEAR), year)
        }
    }

    @Test
    fun `går inställningarna inte att läsa visas läsfelet och Försök igen läser om`() = runTest(main.dispatcher) {
        val scope = TestUserScope(uid = null)
        val vm = ProfileEditViewModel(DefaultSettingsRepository(FakeCollectionFactory(store = factory.store, scope = scope)), FixedClock(), TimeZone.UTC)
        assertEquals(DataError.NotSignedIn, vm.editor.state.value.loadError)

        scope.uid.value = "uid-test"
        vm.onEvent(ProfileEvent.Retry)
        assertNull(vm.editor.state.value.loadError)
        assertEquals(ProfileForm(), vm.editor.state.value.value)
    }

    @Test
    fun `påminnelserna har standardvärdena från 3x`() = runTest(main.dispatcher) {
        val reminders = RemindersEditViewModel(settings).editor.state.value.value
        assertFalse(reminders.medsEnabled)
        assertEquals(listOf(7, 10, 12, 15, 19, 22), reminders.medSlots.map { it.time.hour })
        assertEquals(Slot.SCHEDULED, reminders.medSlots.map { it.slot })
        assertEquals(Occasion.entries, reminders.screeningOccasions.map { it.occasion })
        assertEquals(LocalTime(9, 0), reminders.periodReminderTime)
    }

    @Test
    fun `påminnelserna ändras rad för rad och sparas bara med Spara`() = runTest(main.dispatcher) {
        val vm = RemindersEditViewModel(settings)
        vm.onEvent(RemindersEvent.MedsEnabledChanged(true))
        vm.onEvent(RemindersEvent.SlotChanged(SlotReminder(Slot.LUNCH, enabled = false, time = LocalTime(12, 30))))
        vm.onEvent(RemindersEvent.OccasionChanged(OccasionReminder(Occasion.BEDTIME, enabled = true, time = LocalTime(22, 15))))
        vm.onEvent(RemindersEvent.PeriodTimeChanged(LocalTime(8, 0)))
        assertEquals(false, settings.get().getOrThrow().reminders.medsEnabled, "inget sparat före Spara")

        vm.editor.effects.test {
            vm.onEvent(RemindersEvent.Save)
            assertEquals(EditorEffect.Done, awaitItem())
        }
        val stored = settings.get().getOrThrow().reminders
        assertTrue(stored.medsEnabled)
        assertEquals(SlotReminder(Slot.LUNCH, enabled = false, time = LocalTime(12, 30)), stored.medSlots.single { it.slot == Slot.LUNCH })
        assertEquals(SlotReminder(Slot.MORNING), stored.medSlots.first(), "övriga rader orörda")
        assertEquals(OccasionReminder(Occasion.BEDTIME, enabled = true, time = LocalTime(22, 15)), stored.screeningOccasions.last())
        assertEquals(LocalTime(8, 0), stored.periodReminderTime)
        assertFalse(vm.editor.state.value.isDirty)
    }

    private fun otherDevice(group: String, fields: Map<String, Any?>) =
        factory.store.set(Paths.settings("uid-test"), Settings.ID, mapOf(group to fields), merge = true)

    @Test
    fun `Profil skriver bara det som ändrats – en annan enhets ändring efter laddningen står kvar`() = runTest(main.dispatcher) {
        settings.update { it.copy(profile = Profile(1971, Sex.FEMALE)) }.getOrThrow()
        val vm = profile()
        otherDevice("profile", mapOf("sex" to "male"))

        vm.onEvent(ProfileEvent.BirthYearChanged("1980"))
        vm.onEvent(ProfileEvent.Save)

        assertEquals(Profile(1980, Sex.MALE), settings.get().getOrThrow().profile)
    }

    @Test
    fun `Påminnelser skriver bara det som ändrats – en annan enhets ändring efter laddningen står kvar`() = runTest(main.dispatcher) {
        val vm = RemindersEditViewModel(settings)
        otherDevice("reminders", mapOf("medsEnabled" to true))

        vm.onEvent(RemindersEvent.PeriodTimeChanged(LocalTime(8, 0)))
        vm.onEvent(RemindersEvent.Save)
        vm.onEvent(RemindersEvent.PeriodTimeChanged(LocalTime(10, 0)))
        vm.onEvent(RemindersEvent.Save)

        val stored = settings.get().getOrThrow().reminders
        assertTrue(stored.medsEnabled, "den andra enhetens påslag skrivs inte över")
        assertEquals(LocalTime(10, 0), stored.periodReminderTime)
    }

    @Test
    fun `ett lagrat födelseår utanför spannet visar felet direkt så att det kan rättas`() = runTest(main.dispatcher) {
        otherDevice("profile", mapOf("birthYear" to 1890L))
        val vm = profile()
        assertEquals(R.string.profile_birth_year_invalid, vm.editor.state.value.errorFor(ProfileForm.BIRTH_YEAR))
        vm.onEvent(ProfileEvent.BirthYearChanged("1950"))
        assertTrue(vm.editor.state.value.canSave)
    }

    @Test
    fun `SettingsDifference sparar två gånger i rad mot det senast sparade, och utan laddat värde inget`() = runTest(main.dispatcher) {
        val loaded = settings.get().getOrThrow()
        val difference = SettingsDifference(settings) { loaded }
        difference.save { it.copy(profile = Profile(1971)) }.getOrThrow()
        otherDevice("profile", mapOf("sex" to "female"))
        difference.save { it.copy(reminders = it.reminders.copy(medsEnabled = true)) }.getOrThrow()

        val stored = settings.get().getOrThrow()
        assertEquals(Profile(1971, Sex.FEMALE), stored.profile, "andra sparningen skriver inte tillbaka profilen")
        assertTrue(stored.reminders.medsEnabled)

        val nothing = SettingsDifference(settings) { null }.save { it.copy(profile = Profile(1980)) }
        assertEquals(DataError.NotFound, nothing.exceptionOrNull())
    }

    @Test
    fun `en delvis misslyckad sparning visas som fel och formuläret står kvar som ändrat`() = runTest(main.dispatcher) {
        // Datalagret sparar de skalära fälten men inte påminnelseraderna – och svarar Offline.
        val partial = object : SettingsRepository by settings {
            override suspend fun save(loaded: Settings, edited: Settings): Result<Unit> {
                this@ProfileAndRemindersViewModelTest.settings.save(loaded, edited.copy(reminders = edited.reminders.copy(medSlots = loaded.reminders.medSlots)))
                return Result.failure(DataError.Offline)
            }
        }
        val vm = RemindersEditViewModel(partial)
        vm.onEvent(RemindersEvent.MedsEnabledChanged(true))
        vm.onEvent(RemindersEvent.SlotChanged(SlotReminder(Slot.NIGHT, enabled = false)))
        vm.editor.effects.test {
            vm.onEvent(RemindersEvent.Save)
            (awaitItem() as EditorEffect.Failed).failure.let { assertEquals(DataError.Offline, it.error); assertEquals(R.string.error_offline, it.message) }
        }
        assertTrue(vm.editor.state.value.isDirty, "inte sparat – Spara går att trycka igen")
        assertTrue(vm.editor.state.value.canSave)
    }

    @Test
    fun `utan nät och cache visar Profil och Påminnelser läsfelet, och Försök igen läser om`() = runTest(main.dispatcher) {
        var offline = true
        val flaky = object : SettingsRepository by settings {
            override suspend fun get(): Result<Settings> =
                if (offline) Result.failure(DataError.Offline) else this@ProfileAndRemindersViewModelTest.settings.get()
        }
        val profile = ProfileEditViewModel(flaky, FixedClock(), TimeZone.UTC)
        val reminders = RemindersEditViewModel(flaky)
        assertEquals(DataError.Offline, profile.editor.state.value.loadError)
        assertEquals(DataError.Offline, reminders.editor.state.value.loadError)

        offline = false
        profile.onEvent(ProfileEvent.Retry)
        reminders.onEvent(RemindersEvent.Retry)
        assertNull(profile.editor.state.value.loadError)
        assertNull(reminders.editor.state.value.loadError)
    }
}
