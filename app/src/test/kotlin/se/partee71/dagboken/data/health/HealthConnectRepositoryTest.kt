package se.partee71.dagboken.data.health

import app.cash.turbine.test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.toInstant
import org.junit.Test
import se.partee71.dagboken.core.engine.health.ExerciseSession
import se.partee71.dagboken.core.engine.health.HealthReadWindows
import se.partee71.dagboken.core.engine.health.HealthRecords
import se.partee71.dagboken.core.engine.health.HeartRateSample
import se.partee71.dagboken.core.engine.health.OptionalHealthMetric
import se.partee71.dagboken.core.engine.health.RestingHeartRateSample
import se.partee71.dagboken.core.engine.health.SleepSession
import se.partee71.dagboken.core.engine.health.StepSample
import se.partee71.dagboken.core.engine.health.TimeSpan
import se.partee71.dagboken.core.engine.health.contains
import se.partee71.dagboken.core.engine.sleepQualityOn
import se.partee71.dagboken.core.model.Sex
import se.partee71.dagboken.data.FixedClock
import se.partee71.dagboken.data.common.DataError

/**
 * Health Connect-porten i JVM (HLS-3, HLS-4, HLS-7, HLS-9, HLS-12, HLS-13, HLS-14) mot en fejkad [HealthConnectSource]
 * som läser som den riktiga – varje posttyp över sitt fönster: läget ur SDK-status och kärnbehörigheterna, läst en gång
 * och delat, en läsning per period, dygnen ur `:core`, och fel som [DataError] utan värden.
 */
class HealthConnectRepositoryTest {
    private val zone = TimeZone.of("Europe/Stockholm")
    private fun at(date: LocalDate, hour: Int, minute: Int = 0) = LocalDateTime(date, LocalTime(hour, minute)).toInstant(zone)

    private val today = LocalDate(2026, 10, 7)
    private val yesterday = LocalDate(2026, 10, 6)
    private val now = at(today, 14)

    private val source = FakeHealthConnectSource()

    private fun TestScope.access() = HealthConnectAccess(source, backgroundScope)

    private fun TestScope.repository(access: HealthConnectAccess = access()) = HealthConnectRepository(source, access, FixedClock(now)) { zone }

    @Test
    fun `läget följer SDK-status och kärnbehörigheterna och läses om vid ändring`() = runTest {
        val access = access()
        val repository = repository(access)
        source.sdk = HealthSdkStatus.UNAVAILABLE
        repository.status.test {
            assertEquals(HealthStatus.UNAVAILABLE, awaitItem())
            source.sdk = HealthSdkStatus.UPDATE_REQUIRED
            access.changed()
            assertEquals(HealthStatus.UPDATE_REQUIRED, awaitItem())
            source.sdk = HealthSdkStatus.AVAILABLE
            source.granted = HealthPermissionSet.CORE - HealthPermissionSet.CORE.first()
            access.changed()
            assertEquals(HealthStatus.PERMISSIONS_MISSING, awaitItem())
            source.granted = HealthPermissionSet.CORE
            access.changed()
            assertEquals(HealthStatus.AVAILABLE, awaitItem())
        }
    }

    @Test
    fun `nekar Health Connect att lämna ut behörigheterna är läget behörighet saknas – annat fel lämnar det okänt`() = runTest {
        val access = access()
        val repository = repository(access)
        source.grantedFailure = SecurityException("nekad")
        repository.observedStatus().test {
            assertEquals(HealthStatus.PERMISSIONS_MISSING, awaitItem())
            source.grantedFailure = IllegalStateException("svarar inte")
            access.changed()
            assertNull(awaitItem(), "okänt läge, inte ett påhittat")
            cancelAndIgnoreRemainingEvents()
        }
        source.sdkFailure = SecurityException("nekad")
        access.changed()
        assertEquals(DataError.PermissionDenied, access.now().exceptionOrNull())
    }

    @Test
    fun `läget läses en gång per ändring och delas av läget, raden och läsningarna`() = runTest {
        val access = access()
        val repository = repository(access)
        source.granted = HealthPermissionSet.ALL
        backgroundScope.launch { repository.status.collect {} }
        backgroundScope.launch { repository.status.collect {} }
        backgroundScope.launch { access.current.collect {} } // raden för saknade mått (HealthPermissionsImpl)
        runCurrent()
        repository.day(today).getOrThrow()
        repository.history(yesterday, today).getOrThrow()
        assertEquals(1, source.sdkCalls)
        assertEquals(1, source.grantedCalls)

        access.changed()
        runCurrent()
        assertEquals(2, source.sdkCalls, "en återupptagning – ett nytt varv")
        assertEquals(2, source.grantedCalls)
    }

    @Test
    fun `utan Health Connect eller kärnbehörighet läses inget och varje dygn är en lucka`() = runTest {
        source.sdk = HealthSdkStatus.UNAVAILABLE
        assertTrue(repository().day(today).getOrThrow().isEmpty)
        source.sdk = HealthSdkStatus.AVAILABLE
        source.granted = HealthPermissionSet.OPTIONAL.values.toSet()
        assertEquals(false, repository().history(yesterday, today).getOrThrow().hasAnyData)
        assertEquals(emptyList(), source.reads)
    }

    @Test
    fun `historiken läses en gång över fönstren fram till nu och dygnen räknas i core`() = runTest {
        source.granted = HealthPermissionSet.ALL
        source.records = HealthRecords(
            // Två källor samma dygn: den mest kompletta väljs, aldrig summan (HLS-2).
            steps = listOf(
                StepSample("telefon", at(yesterday, 9), at(yesterday, 10), 4_000),
                StepSample("klocka", at(yesterday, 9), at(yesterday, 10), 6_000),
            ),
            // En natt över midnatt hör till morgonens datum (HLS-12).
            sleep = listOf(SleepSession("klocka", at(yesterday, 23), at(today, 7))),
            // Registrerad vilopuls före skattning (HLS-7).
            restingHeartRate = listOf(RestingHeartRateSample("klocka", at(today, 8), 54)),
            heartRate = listOf(HeartRateSample("klocka", at(today, 9), 70)),
        )
        val history = repository().history(yesterday, today).getOrThrow()

        assertEquals(listOf(6_000f, null), history.series { it.steps })
        assertEquals(listOf(null, 8f), history.series { day -> day.sleepDuration?.inWholeHours })
        assertEquals(listOf(null, 54f), history.series { it.restingHeartRate })
        val read = source.reads.single()
        assertEquals(TimeRead(at(yesterday, 0), now, HealthPermissionSet.ALL), read.copy(windows = null))
        assertEquals(at(yesterday, 0) - 24.hours, read.windows!!.lead.start)
        assertEquals(at(yesterday, 0) - 24.hours * 14, read.windows.sleep.start)
    }

    @Test
    fun `ett pass över periodens första midnatt dedupliceras mot sin dubblett i stället för att räknas på första dagen`() = runTest {
        source.granted = HealthPermissionSet.ALL
        source.records = HealthRecords(
            exercise = listOf(
                ExerciseSession("klocka", at(yesterday.minus(1, DateTimeUnit.DAY), 23, 30), at(yesterday, 0, 40)),
                ExerciseSession("telefon", at(yesterday, 0, 0), at(yesterday, 0, 30)),
                ExerciseSession("klocka", at(today, 7), at(today, 7, 45)),
            ),
        )
        val history = repository().history(yesterday, today).getOrThrow()
        assertEquals(listOf(null, 45f), history.series { it.exerciseDuration?.inWholeMinutes })
        assertEquals(listOf(0, 1), history.days.map { it.exerciseSessions }, "passet hör till dygnet det började – före perioden")
    }

    @Test
    fun `samma natt får samma regelbundenhet och poäng via Vecka, Månad och Hälsa idag (HLS-13)`() = runTest {
        source.granted = HealthPermissionSet.ALL
        // Fyrtio nätter 23:00–07:00 med mittpunkten mellan 02:40 och 03:20; ingen natt för tio dygn sedan.
        source.records = HealthRecords(
            sleep = (1..40).filter { it != 10 }.map { nightsAgo ->
                val morning = today.minus(nightsAgo - 1, DateTimeUnit.DAY)
                val shift = ((nightsAgo * 17) % 41 - 20).minutes
                SleepSession("klocka", at(morning.minus(1, DateTimeUnit.DAY), 23) + shift, at(morning, 7) + shift)
            },
        )
        val repository = repository()
        val week = repository.history(today.minus(6, DateTimeUnit.DAY), today).getOrThrow()
        val month = repository.history(today.minus(29, DateTimeUnit.DAY), today).getOrThrow()
        val clock = repository.history(today.minus(13, DateTimeUnit.DAY), today).getOrThrow()

        val inWeek = week.days.map { it.date to it.sleepMidpointSdMinutes }
        assertTrue(inWeek.all { it.second != null }, "varje natt i veckan har sitt fulla fönster")
        assertEquals(inWeek, month.days.takeLast(7).map { it.date to it.sleepMidpointSdMinutes })
        assertEquals(inWeek, clock.days.takeLast(7).map { it.date to it.sleepMidpointSdMinutes })
        val score = assertNotNull(sleepQualityOn(clock, today, 50, Sex.MALE)?.score)
        assertEquals(score, sleepQualityOn(week, today, 50, Sex.MALE)?.score)
        assertEquals(score, sleepQualityOn(month, today, 50, Sex.MALE)?.score)
    }

    @Test
    fun `day läser pulsen över dygnet, history från natten före – första dagens vilopuls och snittpuls är oförändrade (HLS-7, HLS-10)`() = runTest {
        source.granted = HealthPermissionSet.CORE
        val evening = at(yesterday.minus(1, DateTimeUnit.DAY), 23, 30)
        source.records = HealthRecords(
            heartRate = listOf(
                HeartRateSample("klocka", evening, 40), // i natt, före periodens första midnatt
                HeartRateSample("klocka", at(yesterday, 3), 52),
                HeartRateSample("klocka", at(yesterday, 10), 66),
                HeartRateSample("klocka", at(yesterday, 18), 80),
            ),
            sleep = listOf(SleepSession("klocka", evening - 30.minutes, at(yesterday, 6))),
        )
        val repository = repository()
        val day = repository.day(yesterday).getOrThrow()
        assertEquals(source.reads.last().windows!!.samples, source.reads.last().windows!!.heartRate, "dygnet läser bara sitt eget dygn")
        assertNull(day.sleepHeartRate, "Hälsa idags dygn räknar inga nattvärden")

        val first = repository.history(yesterday, today).getOrThrow().days.first()
        assertEquals(source.reads.last().windows!!.lead, source.reads.last().windows!!.heartRate, "historiken läser pulsen från natten före")
        assertEquals(day.restingHeartRate, first.restingHeartRate)
        assertEquals(day.heartRateAvg, first.heartRateAvg)
        assertEquals(66L, first.restingHeartRate)
        assertEquals(66L, first.heartRateAvg)
        assertEquals(46L, first.sleepHeartRate, "nattens prov före midnatt räknas in i sovpulsen")
        assertEquals(66L, first.sleepHeartRateBaseline)
    }

    @Test
    fun `dagens värden går via healthDay och en framtida dag läses inte`() = runTest {
        source.granted = HealthPermissionSet.CORE
        source.records = HealthRecords(
            steps = listOf(StepSample("klocka", at(today, 8), at(today, 9), 3_210)),
            heartRate = listOf(HeartRateSample("klocka", at(today, 3), 48), HeartRateSample("klocka", at(today, 10), 66)),
            sleep = listOf(SleepSession("klocka", at(yesterday, 23), at(today, 6))),
        )
        val repository = repository()
        val day = repository.day(today).getOrThrow()
        assertEquals(3_210L, day.steps)
        assertEquals(66L, day.restingHeartRate, "sömnens prov sållas bort ur skattningen (HLS-7)")
        assertEquals(7.hours, day.sleepDuration)
        assertEquals(TimeRead(at(today, 0), now, HealthPermissionSet.CORE), source.reads.single().copy(windows = null))

        assertTrue(repository.day(LocalDate(2026, 10, 8)).getOrThrow().isEmpty)
        assertEquals(1, source.reads.size, "en dag efter nu läses inte")
    }

    @Test
    fun `fel mappas i datalagret – nekad behörighet och övriga fel – utan feltexten`() = runTest {
        source.granted = HealthPermissionSet.CORE
        source.failure = SecurityException("steg 7 842")
        val repository = repository()
        assertEquals(DataError.PermissionDenied, repository.history(yesterday, today).exceptionOrNull())
        source.failure = IllegalStateException("puls 61")
        val error = repository.day(today).exceptionOrNull()
        assertEquals(DataError.Unknown, error)
        assertNull(error?.message)
    }

    @Test
    fun `de valfria mått som saknas, och inget när läget inte går att läsa`() = runTest {
        val access = access()
        source.historyFeature = true
        source.granted = HealthPermissionSet.CORE + HealthPermissionSet.OPTIONAL.getValue(OptionalHealthMetric.DISTANCE)
        assertEquals(
            setOf(OptionalHealthMetric.EXERCISE, OptionalHealthMetric.ACTIVE_ENERGY, OptionalHealthMetric.OXYGEN_SATURATION, OptionalHealthMetric.HISTORY),
            access.now().getOrThrow().missingOptional,
        )
        source.granted = HealthPermissionSet.ALL
        access.changed()
        assertEquals(emptySet(), access.now().getOrThrow().missingOptional)
        source.sdk = HealthSdkStatus.UNAVAILABLE
        access.changed()
        assertNull(access.now().getOrThrow().missingOptional)
    }

    @Test
    fun `utan historikfunktionen på enheten begärs historiken inte och räknas inte som saknad (HLS-9)`() = runTest {
        val access = access()
        source.historyFeature = false
        source.granted = HealthPermissionSet.CORE
        val state = access.now().getOrThrow()
        assertTrue(OptionalHealthMetric.HISTORY !in state.missingOptional!!)
        assertTrue(HealthPermissionSet.OPTIONAL.getValue(OptionalHealthMetric.HISTORY) !in state.requestable)
        assertEquals(state, access.latest)

        source.historyFeature = true
        access.changed()
        val supported = access.now().getOrThrow()
        assertTrue(OptionalHealthMetric.HISTORY in supported.missingOptional!!)
        assertEquals(HealthPermissionSet.ALL, supported.requestable)
    }

    @Test
    fun `behörighetsuppsättningen är bara läsning och saknar blodtryck`() {
        assertEquals(9, HealthPermissionSet.ALL.size)
        assertTrue(HealthPermissionSet.ALL.all { it.startsWith("android.permission.health.READ_") })
        assertTrue(HealthPermissionSet.ALL.none { "BLOOD_PRESSURE" in it })
        assertEquals(OptionalHealthMetric.entries.toSet(), HealthPermissionSet.OPTIONAL.keys)
    }

    @Test
    fun `sidorna följs till sista token`() = runTest {
        val pages = mapOf(null to (listOf(1, 2) to "a"), "a" to (listOf(3) to "b"), "b" to (listOf(4) to null))
        assertEquals(listOf(1, 2, 3, 4), readPages { token -> pages.getValue(token) })
    }
}

/** En läsning ur källan: perioden, behörigheterna den läste med och fönstren per posttyp. */
data class TimeRead(val start: Instant, val end: Instant, val granted: Set<String>, val windows: HealthReadWindows? = null)

/**
 * Källan i test: läget, behörigheterna och posterna styrs av testet. Varje posttyp lämnas ut över sitt fönster som den
 * riktiga källan läser (en post som överlappar fönstret kommer med), och varje anrop räknas.
 */
class FakeHealthConnectSource : HealthConnectSource {
    var sdk = HealthSdkStatus.AVAILABLE
    var sdkFailure: Throwable? = null
    var granted: Set<String> = emptySet()
    var grantedFailure: Throwable? = null
    var historyFeature = true
    var records = HealthRecords()
    var failure: Throwable? = null
    val reads = mutableListOf<TimeRead>()
    var sdkCalls = 0
        private set
    var grantedCalls = 0
        private set

    override suspend fun sdkStatus(): HealthSdkStatus {
        sdkCalls++
        sdkFailure?.let { throw it }
        return sdk
    }

    override suspend fun grantedPermissions(): Set<String> {
        grantedCalls++
        grantedFailure?.let { throw it }
        return granted
    }

    override suspend fun historySupported(): Boolean = historyFeature

    override suspend fun read(windows: HealthReadWindows, granted: Set<String>): HealthRecords {
        failure?.let { throw it }
        reads += TimeRead(windows.samples.start, windows.samples.end, granted, windows)
        fun <T : TimeSpan> List<T>.overlapping(window: TimeSpan) = filter { it.start < window.end && it.end > window.start }
        return HealthRecords(
            steps = records.steps.overlapping(windows.lead),
            heartRate = records.heartRate.filter { it.time in windows.heartRate },
            restingHeartRate = records.restingHeartRate.filter { it.time in windows.samples },
            sleep = records.sleep.overlapping(windows.sleep),
            exercise = records.exercise.overlapping(windows.lead),
            calories = records.calories.overlapping(windows.lead),
            distance = records.distance.overlapping(windows.lead),
            oxygen = records.oxygen.filter { it.time in windows.lead },
        )
    }
}
