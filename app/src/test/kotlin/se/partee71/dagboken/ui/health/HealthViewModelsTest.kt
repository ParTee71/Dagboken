package se.partee71.dagboken.ui.health

import app.cash.turbine.test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.toInstant
import org.junit.Rule
import org.junit.Test
import se.partee71.dagboken.core.engine.daysEnding
import se.partee71.dagboken.core.engine.health.OptionalHealthMetric
import se.partee71.dagboken.core.engine.REGULARITY_WINDOW_NIGHTS
import se.partee71.dagboken.core.engine.SLEEP_SCORE_KEY
import se.partee71.dagboken.core.engine.sleepQualitySeries
import se.partee71.dagboken.core.model.HealthHistory
import se.partee71.dagboken.core.model.Settings
import se.partee71.dagboken.data.repository.SettingsRepository
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import se.partee71.dagboken.core.model.DailyHealth
import se.partee71.dagboken.core.model.Profile
import se.partee71.dagboken.core.model.Sex
import se.partee71.dagboken.data.FakeCollectionFactory
import se.partee71.dagboken.data.FixedClock
import se.partee71.dagboken.data.health.FakeHealthPermissions
import se.partee71.dagboken.data.health.FakeHealthRepository
import se.partee71.dagboken.data.health.HealthStatus
import se.partee71.dagboken.data.repository.DefaultSettingsRepository
import se.partee71.dagboken.testing.MainDispatcherRule
import se.partee71.dagboken.ui.common.SelectedDay

/**
 * Klockan på Idag (HEM-15, HEM-17, HLS-7) och Hälsa idag under Klocka (HLS-6, HLS-8, HLS-10, HLS-11, HLS-14) mot
 * `FakeHealthRepository`, `FakeHealthPermissions` och en fast klocka: tisdag 6 oktober 2026 kl. 10:00 i
 * Europe/Stockholm. Varje läge i `HealthStatus` och varje händelse till porten.
 */
class HealthViewModelsTest {

    @get:Rule
    val main = MainDispatcherRule()

    private val zone = TimeZone.of("Europe/Stockholm")
    private val today = LocalDate(2026, 10, 6)
    private val clock = FixedClock(LocalDateTime(today, LocalTime(10, 0)).toInstant(zone))
    private val factory = FakeCollectionFactory(clock = clock)
    private val health = FakeHealthRepository()
    private val permissions = FakeHealthPermissions()
    private val selected = SelectedDay(factory.scope)

    private fun day(daysAgo: Int) = today.minus(daysAgo, DateTimeUnit.DAY)

    private val night = HealthSamples.night

    private fun seedWeek() {
        health.measured[today] = night
        health.measured[day(1)] = DailyHealth(day(1), steps = 9_870, restingHeartRate = 57)
        health.measured[day(3)] = DailyHealth(day(3), steps = 4_020, restingHeartRate = 61)
        // Vilopuls bara en dag till – två dagar med steg men vilopuls på tre.
        health.measured[day(5)] = DailyHealth(day(5), restingHeartRate = 59)
    }

    private fun todayViewModel() = HealthTodayViewModel(health, permissions, selected, clock) { zone }

    private fun clockViewModel() = ClockViewModel(health, permissions, DefaultSettingsRepository(factory), clock) { zone }

    // ---- Idag (HEM-15, HEM-17) ----

    @Test
    fun `kopplad - den visade dagens steg och vilopuls och veckans trendrader (HEM-15, HEM-17)`() = runTest(main.dispatcher) {
        seedWeek()
        todayViewModel().state.test {
            val state = assertNotNull(expectMostRecentItem())
            assertEquals(HealthStatus.AVAILABLE, state.status)
            assertEquals(today, state.date)
            assertEquals(7_842L, state.day?.steps)
            assertEquals(58L, state.day?.restingHeartRate)
            assertEquals(daysEnding(today), state.weekDays)
            assertEquals(listOf(null, null, null, 4_020f, null, 9_870f, 7_842f), state.steps)
            assertEquals(listOf(null, 59f, null, 61f, null, 57f, 58f), state.restingHeartRate)
        }
        assertEquals<Set<ClosedRange<LocalDate>>>(setOf(today..today, day(6)..today), health.reads.toSet())
        assertEquals(2, health.reads.size, "dagen och veckan läses en gång var")
    }

    @Test
    fun `byter dag när datumremsan byter – veckan läses inte om (HEM-14, HEM-15)`() = runTest(main.dispatcher) {
        seedWeek()
        todayViewModel().state.test {
            assertEquals(today, expectMostRecentItem()?.date)
            selected.select(day(3))
            val state = assertNotNull(expectMostRecentItem())
            assertEquals(day(3), state.date)
            assertEquals(4_020L, state.day?.steps)
            selected.select(day(2))
            val empty = assertNotNull(expectMostRecentItem())
            assertEquals(day(2), empty.date)
            assertTrue(empty.day!!.isEmpty, "en dag utan mätning visar —")
        }
        assertEquals(1, health.reads.count { it.start != it.endInclusive }, "veckan läses en gång")
    }

    @Test
    fun `färre än två dagar med värde ger ingen trendrad (HEM-17)`() = runTest(main.dispatcher) {
        health.measured[today] = DailyHealth(today, steps = 7_842)
        todayViewModel().state.test {
            val state = assertNotNull(expectMostRecentItem())
            assertNull(state.steps)
            assertNull(state.restingHeartRate)
            assertEquals(7_842L, state.day?.steps)
        }
    }

    @Test
    fun `ett läsfel ger tomma värden och inga trendrader – Idag fäller inte (HLS-7)`() = runTest(main.dispatcher) {
        seedWeek()
        health.failure = IllegalStateException("klockan svarar inte")
        todayViewModel().state.test {
            val state = assertNotNull(expectMostRecentItem())
            assertTrue(state.day!!.isEmpty)
            assertNull(state.steps)
            assertNull(state.restingHeartRate)
        }
    }

    @Test
    fun `utan behörighet, utan Health Connect och med uppdatering krävs läses ingenting (HEM-15, HLS-4)`() = runTest(main.dispatcher) {
        seedWeek()
        for (status in listOf(HealthStatus.PERMISSIONS_MISSING, HealthStatus.UNAVAILABLE, HealthStatus.UPDATE_REQUIRED)) {
            health.status.value = status
            todayViewModel().state.test {
                val state = assertNotNull(expectMostRecentItem())
                assertEquals(status, state.status)
                assertNull(state.day)
                assertNull(state.steps)
            }
        }
        assertTrue(health.reads.isEmpty())
    }

    @Test
    fun `när behörigheten ges läses dagen och veckan (HLS-3)`() = runTest(main.dispatcher) {
        seedWeek()
        health.status.value = HealthStatus.PERMISSIONS_MISSING
        val vm = todayViewModel()
        vm.state.test {
            assertEquals(HealthStatus.PERMISSIONS_MISSING, expectMostRecentItem()?.status)
            vm.onEvent(HealthEvent.GrantAccess)
            assertEquals(1, permissions.accessRequests)
            health.status.value = HealthStatus.AVAILABLE
            val state = assertNotNull(expectMostRecentItem())
            assertEquals(7_842L, state.day?.steps)
            assertNotNull(state.steps)
        }
    }

    @Test
    fun `idag läses om varje minut medan Idag visas – en tidigare dag läses inte om (HEM-15)`() = runTest(main.dispatcher) {
        seedWeek()
        todayViewModel().state.test {
            assertEquals(7_842L, expectMostRecentItem()?.day?.steps)
            val before = health.reads.size
            health.measured[today] = night.copy(steps = 8_100)
            nextMinute()
            val state = assertNotNull(expectMostRecentItem())
            assertEquals(8_100L, state.day?.steps, "dagens steg följer klockan")
            assertEquals(8_100f, state.steps?.last(), "och veckans punkt för idag")
            assertTrue(health.reads.size > before)

            selected.select(day(3))
            assertEquals(4_020L, expectMostRecentItem()?.day?.steps)
            val dayReads = health.reads.count { it == day(3)..day(3) }
            nextMinute()
            assertEquals(dayReads, health.reads.count { it == day(3)..day(3) }, "en tidigare dag läses bara när den väljs")
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ---- Klocka (HLS-6, HLS-8, HLS-10, HLS-11, HLS-13, HLS-14) ----

    @Test
    fun `kopplad - dagens alla mått och Trenders sömnpoäng för natten (HLS-6, HLS-8, HLS-10, HLS-13)`() = runTest(main.dispatcher) {
        health.measured[today] = night
        DefaultSettingsRepository(factory).update { it.copy(profile = Profile(birthYear = 1971, sex = Sex.FEMALE)) }.getOrThrow()
        clockViewModel().state.test {
            val state = assertNotNull(expectMostRecentItem())
            assertEquals(HealthStatus.AVAILABLE, state.status)
            assertEquals(night, state.day)
            val nights = HealthHistory.of(today.minus(REGULARITY_WINDOW_NIGHTS - 1, DateTimeUnit.DAY), today, health.measured)
            val trend = sleepQualitySeries(nights, 55, Sex.FEMALE, nights.dates, listOf(SLEEP_SCORE_KEY)).single().points.last()
            assertEquals(trend, state.sleepScore?.toFloat(), "samma poäng som Trenders sömnkvalitet för samma natt")
            assertNotNull(state.sleepScore)
            assertFalse(state.needsBirthYear)
            assertEquals(emptySet(), state.missing)
        }
        assertEquals<Set<ClosedRange<LocalDate>>>(setOf(today..today, day(REGULARITY_WINDOW_NIGHTS - 1)..today), health.reads.toSet())
    }

    @Test
    fun `utan födelseår ingen poäng utan uppmaning (HLS-11)`() = runTest(main.dispatcher) {
        health.measured[today] = night
        clockViewModel().state.test {
            val state = assertNotNull(expectMostRecentItem())
            assertNull(state.sleepScore)
            assertTrue(state.needsBirthYear)
            assertEquals(58L, state.day?.restingHeartRate)
        }
    }

    @Test
    fun `kan profilen inte läsas visas ingen uppmaning om födelseår – bara — (HLS-11)`() = runTest(main.dispatcher) {
        health.measured[today] = night
        val failing = object : SettingsRepository by DefaultSettingsRepository(factory) {
            override val settings: Flow<Settings> = flow { throw IllegalStateException("inställningarna svarar inte") }
        }
        ClockViewModel(health, permissions, failing, clock) { zone }.state.test {
            val state = assertNotNull(expectMostRecentItem())
            assertNull(state.sleepScore)
            assertFalse(state.needsBirthYear)
            assertEquals(night, state.day)
        }
    }

    @Test
    fun `saknade valfria behörigheter följer porten – okänt läge ger ingen rad (HLS-14)`() = runTest(main.dispatcher) {
        permissions.missingOptional.value = setOf(OptionalHealthMetric.OXYGEN_SATURATION, OptionalHealthMetric.EXERCISE)
        val vm = clockViewModel()
        vm.state.test {
            assertEquals(setOf(OptionalHealthMetric.EXERCISE, OptionalHealthMetric.OXYGEN_SATURATION), expectMostRecentItem()?.missing)
            permissions.missingOptional.value = null
            assertNull(expectMostRecentItem()?.missing)
            vm.onEvent(HealthEvent.GrantAccess)
            vm.onEvent(HealthEvent.OpenHealthConnect)
        }
        assertEquals(1, permissions.accessRequests)
        assertEquals(1, permissions.healthConnectOpened)
    }

    @Test
    fun `när en valfri behörighet ges läses dagen om och måttet fylls i (HLS-14)`() = runTest(main.dispatcher) {
        permissions.missingOptional.value = setOf(OptionalHealthMetric.OXYGEN_SATURATION)
        health.measured[today] = night.copy(oxygenSaturationAvg = null)
        clockViewModel().state.test {
            assertNull(expectMostRecentItem()?.day?.oxygenSaturationAvg)
            health.measured[today] = night
            permissions.missingOptional.value = emptySet()
            val state = assertNotNull(expectMostRecentItem())
            assertEquals(97.0, state.day?.oxygenSaturationAvg)
            assertEquals(emptySet(), state.missing)
        }
    }

    @Test
    fun `Hälsa idag läses om varje minut medan Klocka visas (HLS-6)`() = runTest(main.dispatcher) {
        health.measured[today] = night
        clockViewModel().state.test {
            assertEquals(7_842L, expectMostRecentItem()?.day?.steps)
            health.measured[today] = night.copy(steps = 9_000)
            nextMinute()
            assertEquals(9_000L, expectMostRecentItem()?.day?.steps)
        }
    }

    @Test
    fun `utan kopplad klocka bara läget – Hälsa idag läses inte (HLS-4, TRD-20)`() = runTest(main.dispatcher) {
        health.measured[today] = night
        for (status in listOf(HealthStatus.PERMISSIONS_MISSING, HealthStatus.UNAVAILABLE, HealthStatus.UPDATE_REQUIRED)) {
            health.status.value = status
            clockViewModel().state.test { assertEquals(ClockUiState(status), expectMostRecentItem()) }
        }
        assertTrue(health.reads.isEmpty())
    }

    @Test
    fun `ett läsfel visar dagens mått som tomma (HLS-6)`() = runTest(main.dispatcher) {
        health.failure = IllegalStateException("klockan svarar inte")
        clockViewModel().state.test {
            val state = assertNotNull(expectMostRecentItem())
            assertTrue(state.day!!.isEmpty)
            assertNull(state.sleepScore)
        }
    }

    /** En minut senare: klockan och testets tid flyttas fram, så att minutklockan tickar (HEM-15, HLS-6). */
    private fun TestScope.nextMinute() {
        clock.instant += 1.minutes
        advanceTimeBy(61.seconds)
        runCurrent()
    }
}
