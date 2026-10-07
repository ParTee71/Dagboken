package se.partee71.dagboken.ui.health

import app.cash.turbine.test
import com.lemonappdev.konsist.api.Konsist
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import org.junit.Rule
import org.junit.Test
import se.partee71.dagboken.core.engine.health.OptionalHealthMetric
import se.partee71.dagboken.core.model.Profile
import se.partee71.dagboken.core.model.Sex
import se.partee71.dagboken.data.FakeCollectionFactory
import se.partee71.dagboken.data.FixedClock
import se.partee71.dagboken.data.health.FakeHealthPermissions
import se.partee71.dagboken.data.health.FakeHealthRepository
import se.partee71.dagboken.data.repository.DefaultSettingsRepository
import se.partee71.dagboken.testing.MainDispatcherRule
import se.partee71.dagboken.ui.common.SelectedDay

/**
 * HLS-5: inget klockvärde når en repository-skrivning eller exporten. Exporten är Firestore-dokumenten (`tools/db
 * export`, Export och import) – så räcker det att visa att databasen är **oförändrad** efter att Idag och Klocka läst,
 * visat och räknat sömnpoäng på klockans data och fått varje händelse, och att `ui/health` inte har någon väg dit.
 */
class HealthNotPersistedTest {

    @get:Rule
    val main = MainDispatcherRule()

    private val zone = TimeZone.of("Europe/Stockholm")
    private val clock = FixedClock(LocalDateTime(HealthSamples.today, LocalTime(10, 0)).toInstant(zone))
    private val factory = FakeCollectionFactory(clock = clock)

    @Test
    fun `Idag och Klocka läser klockan utan att skriva något – databasen och därmed exporten är orörda`() = runTest(main.dispatcher) {
        val settings = DefaultSettingsRepository(factory)
        settings.update { it.copy(profile = Profile(birthYear = 1971, sex = Sex.MALE)) }.getOrThrow()
        val before = factory.store.documents.value
        val health = FakeHealthRepository(measured = mutableMapOf(HealthSamples.today to HealthSamples.night))
        val permissions = FakeHealthPermissions(setOf(OptionalHealthMetric.OXYGEN_SATURATION))
        val idag = HealthTodayViewModel(health, permissions, SelectedDay(factory.scope), clock) { zone }
        val klocka = ClockViewModel(health, permissions, settings, clock) { zone }

        idag.state.test { assertEquals(7_842L, expectMostRecentItem()?.day?.steps) }
        klocka.state.test { assertNotNull(expectMostRecentItem()?.sleepScore, "sömnpoängen räknades") }
        listOf(HealthEvent.GrantAccess, HealthEvent.OpenHealthConnect).forEach {
            idag.onEvent(it)
            klocka.onEvent(it)
        }

        assertEquals(before, factory.store.documents.value, "klockdata får aldrig skrivas (HLS-5)")
        assertFalse(factory.store.hasPendingWrites)
    }

    @Test
    fun `ui-health har ingen väg till en skrivning – bara läsningar och porten`() {
        val files = Konsist.scopeFromProduction(moduleName = "app", sourceSetName = "main").files
            .filter { it.packagee?.name?.endsWith("ui.health") == true }
        assertTrue(files.isNotEmpty(), "ui/health saknas")
        val allowed = setOf("se.partee71.dagboken.data.repository.SettingsRepository")
        val writes = Regex("\\.(save|update|upsert|delete|remove|setStatus|archive|merge)\\(")
        files.forEach { file ->
            file.imports.filter { it.name.contains(".data.repository.") || it.name.contains(".data.firestore.") || it.name.contains("core.schema") }
                .forEach { assertTrue(it.name in allowed, "${file.name} importerar ${it.name}") }
            assertFalse(writes.containsMatchIn(file.text), "${file.name} anropar en skrivning (HLS-5)")
        }
    }
}
