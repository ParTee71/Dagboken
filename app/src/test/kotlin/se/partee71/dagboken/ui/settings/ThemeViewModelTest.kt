package se.partee71.dagboken.ui.settings

import app.cash.turbine.test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import org.junit.Rule
import org.junit.Test
import se.partee71.dagboken.R
import se.partee71.dagboken.core.model.Settings
import se.partee71.dagboken.core.model.ThemeMode
import se.partee71.dagboken.core.model.ThemeSettings
import se.partee71.dagboken.data.FakeCollectionFactory
import se.partee71.dagboken.data.auth.AuthUser
import se.partee71.dagboken.data.repository.SettingsRepository
import se.partee71.dagboken.testing.FakeAuthRepository
import se.partee71.dagboken.data.TestUserScope
import se.partee71.dagboken.data.common.DataError
import se.partee71.dagboken.data.firestore.Paths
import se.partee71.dagboken.data.repository.DefaultSettingsRepository
import se.partee71.dagboken.testing.MainDispatcherRule
import se.partee71.dagboken.ui.common.DetailUiState
import se.partee71.dagboken.ui.common.STOP_TIMEOUT_MILLIS

/** Tema (SET-1, SET-2): direktverkan i inställningarna och i hela appen (`AppThemeViewModel`). */
@OptIn(ExperimentalCoroutinesApi::class)
class ThemeViewModelTest {

    @get:Rule
    val main = MainDispatcherRule()

    private val factory = FakeCollectionFactory()
    private val settings = DefaultSettingsRepository(factory)

    @Test
    fun `läget sparas direkt (SET-1)`() = runTest(main.dispatcher) {
        val vm = ThemeViewModel(settings)
        vm.state.test {
            assertEquals(ThemeForm(ThemeSettings()), (expectMostRecentItem() as DetailUiState.Content).value)
            vm.onEvent(ThemeEvent.ModeChosen(ThemeMode.LIGHT))
            assertEquals(ThemeMode.LIGHT, (awaitItem() as DetailUiState.Content).value.theme.mode)
        }
        assertEquals(ThemeMode.LIGHT, settings.get().getOrThrow().theme.mode)
    }

    @Test
    fun `giltiga starttimmar sparas direkt, ogiltiga visas med fel och sparas inte (SET-2)`() = runTest(main.dispatcher) {
        val vm = ThemeViewModel(settings)
        vm.state.test {
            skipItems(1)
            vm.onEvent(ThemeEvent.LightFromChanged(6))
            cancelAndIgnoreRemainingEvents()
        }
        assertEquals(6, settings.get().getOrThrow().theme.lightStartHour)

        vm.state.test {
            vm.onEvent(ThemeEvent.DarkFromChanged(5))
            val form = (expectMostRecentItem() as DetailUiState.Content).value
            assertEquals(5, form.theme.darkStartHour)
            assertTrue(form.hoursInvalid)
            cancelAndIgnoreRemainingEvents()
        }
        assertEquals(21, settings.get().getOrThrow().theme.darkStartHour, "ogiltigt sparas inte")

        vm.state.test {
            vm.onEvent(ThemeEvent.DarkFromChanged(22))
            val form = (expectMostRecentItem() as DetailUiState.Content).value
            assertEquals(ThemeSettings(lightStartHour = 6, darkStartHour = 22), form.theme)
            assertFalse(form.hoursInvalid)
            cancelAndIgnoreRemainingEvents()
        }
        assertEquals(22, settings.get().getOrThrow().theme.darkStartHour)
    }

    @Test
    fun `ett sparfel visas, och samma fel två gånger i rad är två händelser`() = runTest(main.dispatcher) {
        val scope = TestUserScope()
        val vm = ThemeViewModel(DefaultSettingsRepository(FakeCollectionFactory(scope = scope)))
        vm.state.test {
            skipItems(1)
            cancelAndIgnoreRemainingEvents()
        }
        vm.failure.test {
            assertNull(awaitItem())
            scope.setVersion(Int.MAX_VALUE) // nyare data än appen: skrivskyddat
            vm.onEvent(ThemeEvent.ModeChosen(ThemeMode.DARK))
            assertEquals(DataError.UpdateRequired to R.string.error_update_required, awaitItem()?.let { it.error to it.message })
            vm.onEvent(ThemeEvent.ModeChosen(ThemeMode.LIGHT))
            assertEquals(DataError.UpdateRequired, awaitItem()?.error, "samma fel igen")
            vm.onEvent(ThemeEvent.ErrorShown)
            assertNull(awaitItem())
        }
    }

    @Test
    fun `ett senare ogiltigt val nollställs inte när en tidigare skrivning blir klar`() = runTest(main.dispatcher) {
        val gate = CompletableDeferred<Unit>()
        val slow = object : SettingsRepository by settings {
            override suspend fun update(change: (Settings) -> Settings): Result<Unit> {
                gate.await()
                return this@ThemeViewModelTest.settings.update(change)
            }
        }
        val vm = ThemeViewModel(slow)
        vm.state.test {
            skipItems(1)
            vm.onEvent(ThemeEvent.LightFromChanged(6)) // giltigt – skrivningen väntar
            vm.onEvent(ThemeEvent.DarkFromChanged(5)) // ogiltigt – nyare val
            gate.complete(Unit)
            val form = (expectMostRecentItem() as DetailUiState.Content).value
            assertEquals(ThemeSettings(lightStartHour = 6, darkStartHour = 5), form.theme)
            assertTrue(form.hoursInvalid, "det nyare valet står kvar med sitt fel")
            cancelAndIgnoreRemainingEvents()
        }
        assertEquals(6 to 21, settings.get().getOrThrow().theme.let { it.lightStartHour to it.darkStartHour })
    }

    @Test
    fun `valet står kvar tills cachen visar det sparat (ingen hoppning tillbaka)`() = runTest(main.dispatcher) {
        val cache = MutableStateFlow(Settings())
        val lagging = object : SettingsRepository by settings {
            override val settings: Flow<Settings> = cache // cachen levererar först när testet säger till
        }
        val vm = ThemeViewModel(lagging)
        vm.state.test {
            skipItems(1)
            vm.onEvent(ThemeEvent.LightFromChanged(6))
            assertEquals(6, (expectMostRecentItem() as DetailUiState.Content).value.theme.lightStartHour, "skrivningen klar, cachen inte")
            vm.onEvent(ThemeEvent.DarkFromChanged(22))
            assertEquals(6 to 22, (expectMostRecentItem() as DetailUiState.Content).value.theme.let { it.lightStartHour to it.darkStartHour }, "nästa ändring bygger på valet")

            cache.value = this@ThemeViewModelTest.settings.get().getOrThrow()
            assertEquals(6 to 22, (vm.state.value as DetailUiState.Content).value.theme.let { it.lightStartHour to it.darkStartHour }, "samma som visas – ingen ny bild")
            cache.value = Settings(theme = ThemeSettings(lightStartHour = 8, darkStartHour = 20)) // ändrat på en annan enhet
            assertEquals(8 to 20, (expectMostRecentItem() as DetailUiState.Content).value.theme.let { it.lightStartHour to it.darkStartHour }, "valet är släppt")
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `samma timme som visas skrivs inte och döljer inte en senare ändring från en annan enhet`() = runTest(main.dispatcher) {
        val cache = MutableStateFlow(Settings())
        val lagging = object : SettingsRepository by settings {
            override val settings: Flow<Settings> = cache
        }
        val vm = ThemeViewModel(lagging)
        vm.state.test {
            skipItems(1)
            vm.onEvent(ThemeEvent.LightFromChanged(ThemeSettings().lightStartHour)) // bekräftar samma klockslag
            assertNull(settings.get().getOrThrow().let { if (it == Settings()) null else it }, "inget skrevs")
            cache.value = Settings(theme = ThemeSettings(lightStartHour = 8, darkStartHour = 20)) // ändrat på en annan enhet
            assertEquals(8 to 20, (expectMostRecentItem() as DetailUiState.Content).value.theme.let { it.lightStartHour to it.darkStartHour })
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `temat laddas bara en gång – tillbaka från bakgrunden står det kvar`() = runTest(main.dispatcher) {
        val app = AppThemeViewModel(signedIn, settings, MovableClock(at(12))) { TimeZone.UTC }
        app.theme.test {
            assertEquals(AppTheme.Chosen(dark = false), expectMostRecentItem())
            cancelAndIgnoreRemainingEvents()
        }
        testScheduler.advanceTimeBy(STOP_TIMEOUT_MILLIS + 1) // flödet stoppas som när appen ligger i bakgrunden
        testScheduler.runCurrent()
        app.theme.test {
            assertEquals(AppTheme.Chosen(dark = false), awaitItem())
            expectNoEvents()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `utan nät och cache visar Tema felet med Försök igen`() = runTest(main.dispatcher) {
        val failing = object : SettingsRepository by settings {
            override val settings: Flow<Settings> = kotlinx.coroutines.flow.flow { throw DataError.Offline }
        }
        val vm = ThemeViewModel(failing)
        vm.state.test {
            assertEquals(DetailUiState.Error(DataError.Offline), expectMostRecentItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    /** Klocka som testet flyttar fram. */
    private class MovableClock(var instant: Instant) : Clock {
        override fun now() = instant
    }

    private fun at(hour: Int, minute: Int = 0) = LocalDateTime(2026, 10, 5, hour, minute).toInstant(TimeZone.UTC)

    private val signedIn = FakeAuthRepository(AuthUser("uid-test"))

    @Test
    fun `hela appen följer valet och auto byter på timmen (SET-1, DSN-5)`() = runTest(main.dispatcher) {
        val clock = MovableClock(at(20, 59))
        val app = AppThemeViewModel(signedIn, settings, clock) { TimeZone.UTC }
        app.theme.test {
            assertEquals(AppTheme.Chosen(dark = false), expectMostRecentItem(), "auto 07–21: ljust klockan 20.59")

            clock.instant = at(21)
            testScheduler.advanceTimeBy(1.minutes)
            testScheduler.runCurrent()
            assertEquals(AppTheme.Chosen(dark = true), awaitItem(), "mörkt från 21")

            settings.update { it.copy(theme = it.theme.copy(mode = ThemeMode.LIGHT)) }.getOrThrow()
            assertEquals(AppTheme.Chosen(dark = false), awaitItem(), "ljust gäller direkt")

            signedIn.authState.value = null
            assertEquals(AppTheme.System, awaitItem(), "utloggad: systemet")
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `temat laddas först, och dröjer inställningarna gäller systemet efter en kort stund`() = runTest(main.dispatcher) {
        // Inloggad, men samlingen har ännu ingen användare: inställningarna kommer aldrig.
        val stuck = DefaultSettingsRepository(FakeCollectionFactory(scope = TestUserScope(uid = null)))
        val app = AppThemeViewModel(signedIn, stuck, MovableClock(at(12))) { TimeZone.UTC }
        app.theme.test {
            assertEquals(AppTheme.Loading, awaitItem())
            testScheduler.advanceTimeBy(AppThemeViewModel.LOAD_TIMEOUT)
            testScheduler.runCurrent()
            assertEquals(AppTheme.System, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
        assertTrue(AppTheme.System.isDark(systemDark = true))
        assertFalse(AppTheme.Chosen(dark = false).isDark(systemDark = true))
    }

    @Test
    fun `tidszonen läses vid varje omräkning`() = runTest(main.dispatcher) {
        var zone: TimeZone = TimeZone.UTC
        val app = AppThemeViewModel(signedIn, settings, MovableClock(at(20, 30))) { zone }
        app.theme.test {
            assertEquals(AppTheme.Chosen(dark = false), expectMostRecentItem(), "20.30 i UTC")
            zone = TimeZone.of("Europe/Stockholm") // 22.30 lokal tid
            testScheduler.advanceTimeBy(30.minutes)
            testScheduler.runCurrent()
            assertEquals(AppTheme.Chosen(dark = true), awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `ogiltiga lagrade timmar ger standardtimmarna`() = runTest(main.dispatcher) {
        factory.store.set(
            Paths.settings("uid-test"),
            Settings.ID,
            mapOf("theme" to mapOf("mode" to "auto", "lightStartHour" to 22L, "darkStartHour" to 6L)),
            merge = false,
        )
        val app = AppThemeViewModel(signedIn, settings, MovableClock(at(12))) { TimeZone.UTC }
        app.theme.test {
            assertEquals(AppTheme.Chosen(dark = false), expectMostRecentItem())
            cancelAndIgnoreRemainingEvents()
        }
        val vm = ThemeViewModel(settings)
        vm.state.test {
            assertTrue((expectMostRecentItem() as DetailUiState.Content).value.hoursInvalid)
            cancelAndIgnoreRemainingEvents()
        }
    }
}
