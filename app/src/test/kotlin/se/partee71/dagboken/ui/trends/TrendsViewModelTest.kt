package se.partee71.dagboken.ui.trends

import app.cash.turbine.test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
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
import se.partee71.dagboken.core.engine.CompareKey
import se.partee71.dagboken.core.engine.IntervalPoint
import se.partee71.dagboken.core.engine.SLEEP_SCORE_KEY
import se.partee71.dagboken.core.engine.SLEEP_QUALITY_KEYS
import se.partee71.dagboken.core.engine.StackedPoint
import se.partee71.dagboken.core.engine.StressSeries
import se.partee71.dagboken.core.engine.TrendRange
import se.partee71.dagboken.core.engine.WATCH_COMPARE_KEYS
import se.partee71.dagboken.core.engine.WatchMetric
import se.partee71.dagboken.core.model.Activity
import se.partee71.dagboken.core.model.Checkin
import se.partee71.dagboken.core.model.DailyHealth
import se.partee71.dagboken.core.model.Event
import se.partee71.dagboken.core.model.IllnessEpisode
import se.partee71.dagboken.core.model.Occasion
import se.partee71.dagboken.core.model.Option
import se.partee71.dagboken.core.model.OptionKind
import se.partee71.dagboken.core.model.Profile
import se.partee71.dagboken.core.model.Screening
import se.partee71.dagboken.core.model.Settings
import se.partee71.dagboken.core.model.SleepStages
import se.partee71.dagboken.core.model.SymptomScore
import se.partee71.dagboken.data.FakeCollectionFactory
import se.partee71.dagboken.data.FixedClock
import se.partee71.dagboken.core.engine.health.OptionalHealthMetric
import se.partee71.dagboken.data.health.FakeHealthPermissions
import se.partee71.dagboken.data.health.FakeHealthRepository
import se.partee71.dagboken.data.health.HealthStatus
import se.partee71.dagboken.data.repository.ActivityRepository
import se.partee71.dagboken.data.repository.DefaultActivityRepository
import se.partee71.dagboken.data.repository.DefaultEventRepository
import se.partee71.dagboken.data.repository.DefaultIllnessRepository
import se.partee71.dagboken.data.repository.DefaultOptionsRepository
import se.partee71.dagboken.data.repository.DefaultScreeningRepository
import se.partee71.dagboken.data.repository.DefaultSettingsRepository
import se.partee71.dagboken.data.repository.EventRepository
import se.partee71.dagboken.data.repository.IllnessRepository
import se.partee71.dagboken.data.repository.ScreeningRepository
import se.partee71.dagboken.testing.MainDispatcherRule

/**
 * Fliken Trender (TRD-1, TRD-3, TRD-8, TRD-11, TRD-14–TRD-18, TRD-21) mot `FakeCollection`, `FakeHealthRepository` och
 * en fast klocka: tisdag 6 oktober 2026 kl. 10:00 i Europe/Stockholm. Läsningarna räknas per repository, så att
 * testerna visar att ett stängt kort inte läser och att två kort med samma period delar en läsning – också klockans.
 */
class TrendsViewModelTest {

    @get:Rule
    val main = MainDispatcherRule()

    private val zone = TimeZone.of("Europe/Stockholm")
    private val today = LocalDate(2026, 10, 6)
    private val clock = FixedClock(LocalDateTime(today, LocalTime(10, 0)).toInstant(zone))
    private val factory = FakeCollectionFactory(clock = clock)

    private val screeningReads = mutableListOf<ClosedRange<LocalDate>>()
    private val activityReads = mutableListOf<ClosedRange<LocalDate>>()
    private val eventReads = mutableListOf<ClosedRange<LocalDate>>()
    private var episodeReads = 0

    private val screenings = object : ScreeningRepository by DefaultScreeningRepository(factory, clock) {
        private val real = DefaultScreeningRepository(factory, clock)
        override fun observeDays(from: LocalDate, to: LocalDate): Flow<List<Screening>> = real.observeDays(from, to).onStart { screeningReads += from..to }
    }
    private val activities = object : ActivityRepository by DefaultActivityRepository(factory, clock) {
        private val real = DefaultActivityRepository(factory, clock)
        override fun observeDays(from: LocalDate, to: LocalDate): Flow<List<Activity>> = real.observeDays(from, to).onStart { activityReads += from..to }
    }
    private val events = object : EventRepository by DefaultEventRepository(factory, clock) {
        private val real = DefaultEventRepository(factory, clock)
        override fun observeDays(from: LocalDate, to: LocalDate): Flow<List<Event>> = real.observeDays(from, to).onStart { eventReads += from..to }
    }
    private val illnesses = object : IllnessRepository by DefaultIllnessRepository(factory, clock) {
        private val real = DefaultIllnessRepository(factory, clock)
        override fun observeEpisodes(): Flow<List<IllnessEpisode>> = real.observeEpisodes().onStart { episodeReads++ }
    }

    private val health = FakeHealthRepository()
    private val permissions = FakeHealthPermissions()

    private fun day(daysAgo: Int) = today.minus(daysAgo, DateTimeUnit.DAY)

    /** Två nätter med klocka: igår med alla stadier, för tre dagar sedan bara längd och steg. */
    private fun seedHealth() {
        health.measured[day(1)] = DailyHealth(
            day(1),
            steps = 8_250,
            restingHeartRate = 56,
            heartRateAvg = 71,
            sleepDuration = 7.hours + 30.minutes,
            sleepStages = SleepStages(deep = 1.hours + 15.minutes, rem = 1.hours + 30.minutes, light = 4.hours, awake = 45.minutes),
        )
        health.measured[day(3)] = DailyHealth(day(3), steps = 4_100, sleepDuration = 6.hours)
    }

    private fun ranges(vararg read: ClosedRange<LocalDate>): List<ClosedRange<LocalDate>> = read.toList()

    private fun mood(id: String, date: LocalDate, energy: Int, occasion: Occasion = Occasion.BREAKFAST, stress: Int = 3, symptoms: List<SymptomScore> = emptyList()) =
        Screening(id, date, LocalTime(8, 0), occasion, energy = energy, stress = stress, symptoms = symptoms, createdAt = Instant.fromEpochSeconds(1))

    private suspend fun seed() {
        factory.screenings().batch(
            listOf(
                mood("s1", today, 4),
                mood("s2", today, 8, Occasion.LUNCH),
                mood("s3", day(1), 6, symptoms = listOf(SymptomScore("yrsel", 5))),
                mood("s4", day(40), 5, Occasion.LUNCH),
                mood("s6", day(10), 7, symptoms = listOf(SymptomScore("yrsel", 8))),
                mood("s5", day(100), 2, symptoms = listOf(SymptomScore("okand", 2))),
            ),
        ).getOrThrow()
        factory.activities().upsert(Activity("a1", day(1), LocalTime(16, 0), optionId = "promenad", stress = 7, recovering = true)).getOrThrow()
        factory.events().batch(listOf(Event("e1", today, severity = 7), Event("e2", day(2), severity = 4), Event("old", day(60), severity = 9))).getOrThrow()
        factory.illnessEpisodes().upsert(IllnessEpisode("flu", "Förkylning", start = day(6), createdAt = Instant.fromEpochSeconds(2))).getOrThrow()
        factory.checkins("flu").upsert(Checkin("c1", day(5), LocalTime(9, 0), severity = 6)).getOrThrow()
        factory.options().upsert(Option("yrsel", OptionKind.SYMPTOM, "Yrsel")).getOrThrow()
    }

    private fun viewModel() = TrendsViewModel(screenings, activities, events, illnesses, DefaultOptionsRepository(factory), health, permissions, DefaultSettingsRepository(factory), clock) { zone }

    private fun TestScope.started(): TrendsViewModel {
        val vm = viewModel()
        backgroundScope.launch { vm.state.collect {} }
        runCurrent()
        return vm
    }

    private fun TrendsViewModel.card(card: TrendCard): TrendCardState = state.value.cards.getValue(card)

    @Test
    fun `alla kort är stängda från början med Månad, utan föregående period – och inget läses (TRD-14, TRD-15)`() = runTest(main.dispatcher) {
        seed()
        val vm = started()
        val state = vm.state.value
        assertEquals(TrendGroup.MOOD, state.group)
        TrendCard.entries.forEach { card ->
            val c = state.cards.getValue(card).controls
            assertFalse(c.expanded, card.name)
            assertEquals(TrendRange.MONTH, c.range)
            assertFalse(c.previous)
            assertNull(state.cards.getValue(card).data)
        }
        assertEquals(setOf(Occasion.BREAKFAST.wire), state.cards.getValue(TrendCard.ENERGY_OCCASION).controls.selected)
        assertEquals(setOf(StressSeries.STRESS.name), state.cards.getValue(TrendCard.STRESS).controls.selected)
        assertEquals(emptySet(), state.cards.getValue(TrendCard.SYMPTOMS).controls.selected)
        assertEquals(setOf(WatchMetric.RESTING_HEART_RATE.name), state.cards.getValue(TrendCard.HEART_RATE).controls.selected)
        assertEquals(setOf(WatchMetric.SLEEP_TOTAL.name), state.cards.getValue(TrendCard.SLEEP).controls.selected)
        assertEquals(setOf(SLEEP_SCORE_KEY), state.cards.getValue(TrendCard.SLEEP_QUALITY).controls.selected)
        assertEquals(emptySet(), state.cards.getValue(TrendCard.COMPARE).controls.selected, "Jämför har inget förval (TRD-17)")
        assertTrue(screeningReads.isEmpty() && activityReads.isEmpty() && eventReads.isEmpty() && episodeReads == 0, "stängda kort läser inget")
        assertTrue(health.reads.isEmpty(), "klockan läses först när ett kort fälls ut (TRD-15)")
        assertEquals(listOf(TrendCard.STEPS, TrendCard.HEART_RATE, TrendCard.SLEEP, TrendCard.SLEEP_STAGES, TrendCard.SLEEP_QUALITY, TrendCard.EXERCISE, TrendCard.CALORIES, TrendCard.DISTANCE, TrendCard.OXYGEN), TrendCard.inGroup(TrendGroup.WATCH))
        assertEquals(listOf(TrendCard.COMPARE), TrendCard.inGroup(TrendGroup.COMPARE))
    }

    // ---- Klocka (TRD-11, TRD-15, TRD-16, HLS-12, HLS-13) ----

    @Test
    fun `klockkorten läser en gång per period, delar läsningen och visar luckor där klockan inte mätt (TRD-15)`() = runTest(main.dispatcher) {
        seedHealth()
        val vm = started()
        vm.onEvent(TrendsEvent.Toggle(TrendCard.STEPS))
        vm.onEvent(TrendsEvent.Toggle(TrendCard.HEART_RATE))
        runCurrent()
        assertEquals(ranges(day(29)..today), health.reads, "en hälsoläsning för båda korten")
        assertTrue(screeningReads.isEmpty(), "klockkorten läser inte dagboken")

        val steps = assertIs<CardData.Lines>(vm.card(TrendCard.STEPS).data)
        assertEquals(30, steps.days.size)
        assertEquals(listOf(SeriesInfo(WatchMetric.STEPS.name, null)), steps.available)
        assertEquals(listOf(WatchMetric.STEPS.name), steps.shown.map { it.key }, "ett kort utan serieval visar sin serie")
        assertEquals(8_250f, steps.shown.single().points[28])
        assertEquals(4_100f, steps.shown.single().points[26])
        assertNull(steps.shown.single().points.last(), "idag utan mätning är en lucka")

        val pulse = assertIs<CardData.Lines>(vm.card(TrendCard.HEART_RATE).data)
        assertEquals(listOf(WatchMetric.RESTING_HEART_RATE.name, WatchMetric.HEART_RATE_AVG.name), pulse.available.map { it.key })
        assertEquals(listOf(WatchMetric.RESTING_HEART_RATE.name), pulse.shown.map { it.key }, "Vilopuls är förvald")
        vm.onEvent(TrendsEvent.ToggleSeries(TrendCard.HEART_RATE, WatchMetric.HEART_RATE_AVG.name))
        runCurrent()
        assertEquals(71f, assertIs<CardData.Lines>(vm.card(TrendCard.HEART_RATE).data).shown[1].points[28])
        assertEquals(1, health.reads.size, "serievalet läser inte om")

        vm.onEvent(TrendsEvent.SetPrevious(TrendCard.STEPS, true))
        runCurrent()
        assertEquals<ClosedRange<LocalDate>>(day(59)..today, health.reads.last(), "båda perioderna i ett svep (TRD-18)")
        assertEquals(30, assertIs<CardData.Lines>(vm.card(TrendCard.STEPS).data).previous.single().points.size)
    }

    @Test
    fun `Allt kapas vid 365 dagar för klockan (TRD-15)`() = runTest(main.dispatcher) {
        seedHealth()
        val vm = started()
        vm.onEvent(TrendsEvent.Toggle(TrendCard.STEPS))
        vm.onEvent(TrendsEvent.SetRange(TrendCard.STEPS, TrendRange.ALL))
        runCurrent()
        assertEquals<ClosedRange<LocalDate>>(day(364)..today, health.reads.last())
        val data = assertIs<CardData.Lines>(vm.card(TrendCard.STEPS).data)
        assertEquals(365, data.days.size)
        assertEquals(today, data.days.last())
        assertFalse(vm.card(TrendCard.STEPS).controls.showsPrevious(TrendCard.STEPS))
    }

    @Test
    fun `sömnstadierna staplas per natt och sömnkvaliteten kräver födelseåret ur profilen (TRD-16, HLS-11, HLS-13)`() = runTest(main.dispatcher) {
        seedHealth()
        val vm = started()
        vm.onEvent(TrendsEvent.Toggle(TrendCard.SLEEP_STAGES))
        vm.onEvent(TrendsEvent.Toggle(TrendCard.SLEEP_QUALITY))
        runCurrent()
        assertEquals(1, health.reads.size)
        val stages = assertIs<CardData.Stacked>(vm.card(TrendCard.SLEEP_STAGES).data)
        assertEquals(StackedPoint(listOf(1.25f, 1.5f, 4f, 0.75f)), stages.points[28])
        assertEquals(StackedPoint(listOf(null, null, null, null)), stages.points[26], "natt utan stadier tar ingen höjd")

        var quality = assertIs<CardData.Lines>(vm.card(TrendCard.SLEEP_QUALITY).data)
        assertEquals(SLEEP_QUALITY_KEYS, quality.available.map { it.key })
        assertTrue(quality.available.any { it.key == "REGULARITY" }, "regelbundenheten är ett valbart delmått (HLS-13)")
        assertEquals(listOf(SLEEP_SCORE_KEY), quality.shown.map { it.key })
        assertTrue(quality.shown.single().points.all { it == null }, "utan födelseår ingen poäng (HLS-11)")
        assertTrue(quality.needsBirthYear, "kortet ber om födelseåret")

        factory.settings().upsert(Settings(profile = Profile(birthYear = 1976))).getOrThrow()
        runCurrent()
        quality = assertIs(vm.card(TrendCard.SLEEP_QUALITY).data)
        assertFalse(quality.needsBirthYear)
        val score = quality.shown.single().points
        assertNotNull(score[28], "igår har en poäng")
        assertNotNull(score[26], "en natt med bara längd bedöms på längden")
        assertNull(score[27])
        assertEquals(1, health.reads.size, "profilen läser inte om klockan")
    }

    @Test
    fun `utan Health Connect står korten kvar tomma – läget visas av ClockViewModel, inte här (HLS-4, TRD-20)`() = runTest(main.dispatcher) {
        health.status.value = HealthStatus.UNAVAILABLE
        val vm = started()
        vm.onEvent(TrendsEvent.Toggle(TrendCard.STEPS))
        runCurrent()
        val data = assertIs<CardData.Lines>(vm.card(TrendCard.STEPS).data)
        assertTrue(data.shown.single().points.all { it == null })
    }

    @Test
    fun `när Health Connect blir tillgängligt läses perioden om och öppna kort fylls på – också efter ett fel (HLS-4)`() = runTest(main.dispatcher) {
        seedHealth()
        health.status.value = HealthStatus.PERMISSIONS_MISSING
        health.failure = SecurityException("ingen behörighet")
        val vm = started()
        vm.onEvent(TrendsEvent.Toggle(TrendCard.STEPS))
        runCurrent()
        assertEquals(1, health.reads.size)
        assertTrue(assertIs<CardData.Lines>(vm.card(TrendCard.STEPS).data).shown.single().points.all { it == null }, "felet visar tomt")

        health.failure = null
        health.status.value = HealthStatus.AVAILABLE
        runCurrent()
        assertEquals(2, health.reads.size, "ny läsning för det nya läget")
        assertEquals(8_250f, assertIs<CardData.Lines>(vm.card(TrendCard.STEPS).data).shown.single().points[28])

        vm.onEvent(TrendsEvent.Toggle(TrendCard.HEART_RATE))
        runCurrent()
        assertEquals(2, health.reads.size, "samma läge och period – läsningen delas")
    }

    @Test
    fun `när en valfri behörighet ges eller återkallas läses perioden om (HLS-14)`() = runTest(main.dispatcher) {
        seedHealth()
        permissions.missingOptional.value = setOf(OptionalHealthMetric.EXERCISE)
        val vm = started()
        vm.onEvent(TrendsEvent.Toggle(TrendCard.EXERCISE))
        runCurrent()
        assertEquals(1, health.reads.size)

        permissions.missingOptional.value = emptySet()
        runCurrent()
        assertEquals(2, health.reads.size, "träningen fick åtkomst – ny läsning")

        permissions.missingOptional.value = emptySet()
        runCurrent()
        assertEquals(2, health.reads.size, "samma behörigheter igen – ingen ny läsning")

        permissions.missingOptional.value = setOf(OptionalHealthMetric.EXERCISE)
        runCurrent()
        assertEquals(3, health.reads.size, "återkallad – ny läsning")
    }

    // ---- Jämför (TRD-17) ----

    @Test
    fun `Jämför visar alla serier i menyn, läser klockan bara när en klockserie valts och indexerar de valda`() = runTest(main.dispatcher) {
        seed()
        seedHealth()
        val vm = started()
        vm.onEvent(TrendsEvent.Toggle(TrendCard.COMPARE))
        runCurrent()
        var data = assertIs<CardData.Compare>(vm.card(TrendCard.COMPARE).data)
        assertEquals(1, screeningReads.size)
        assertEquals(1, activityReads.size)
        assertTrue(health.reads.isEmpty(), "ingen klockserie vald – klockan läses inte")
        val expectedKeys = listOf(CompareKey.EnergyDay.wire) + Occasion.entries.map { CompareKey.EnergyOccasion(it).wire } +
            StressSeries.entries.map { CompareKey.Stress(it).wire } + listOf(CompareKey.Symptom("yrsel").wire) + WATCH_COMPARE_KEYS.map { it.wire }
        assertEquals(expectedKeys, data.available.map { it.key })
        assertEquals("Yrsel", data.available.first { it.key == CompareKey.Symptom("yrsel").wire }.name, "symptomets namn ur Listor")
        assertEquals(emptyList(), data.shown)
        assertEquals(0, data.selectedCount)
        assertFalse(data.needsBirthYear)

        vm.onEvent(TrendsEvent.ToggleSeries(TrendCard.COMPARE, CompareKey.EnergyDay.wire))
        runCurrent()
        data = assertIs(vm.card(TrendCard.COMPARE).data)
        assertEquals(1, data.shown.size, "en serie räcker inte – skärmen visar tomt läge")
        assertTrue(health.reads.isEmpty())

        vm.onEvent(TrendsEvent.ToggleSeries(TrendCard.COMPARE, CompareKey.Watch(WatchMetric.STEPS).wire))
        runCurrent()
        assertEquals(ranges(day(29)..today), health.reads, "nu läses klockan – en gång")
        data = assertIs(vm.card(TrendCard.COMPARE).data)
        assertEquals(listOf(CompareKey.EnergyDay.wire, CompareKey.Watch(WatchMetric.STEPS).wire), data.shown.map { it.key })
        val energy = data.shown[0]
        assertEquals(6f to 7f, energy.min to energy.max, "verkligt spann i perioden för legenden – loggarna för 40 och 100 dagar sedan ligger utanför")
        assertEquals(100f, energy.points[19], "för tio dagar sedan 7 → 100")
        assertEquals(0f, energy.points.last(), "idag (4 + 8) / 2 = 6 → 0")
        assertNull(energy.points[27])
        val steps = data.shown[1]
        assertEquals(4_100f to 8_250f, steps.min to steps.max)
        assertEquals(listOf(0f, 100f), listOf(steps.points[26], steps.points[28]))
        assertNull(steps.points.last(), "lucka förblir lucka")
        assertEquals(30, data.days.size)

        vm.onEvent(TrendsEvent.ToggleSeries(TrendCard.COMPARE, CompareKey.Watch(WatchMetric.OXYGEN_SATURATION).wire))
        runCurrent()
        data = assertIs(vm.card(TrendCard.COMPARE).data)
        assertEquals(2, data.shown.size, "en vald serie utan data i perioden visas inte")
        assertEquals(3, data.selectedCount, "men räknas som vald – skärmen säger För lite data, inte Välj minst två")
        assertEquals(1, health.reads.size, "samma period – ingen ny läsning")

        // Ett valt symptom utan data i perioden står kvar i menyn så att det går att avmarkera.
        vm.onEvent(TrendsEvent.ToggleSeries(TrendCard.COMPARE, CompareKey.Symptom("okand").wire))
        runCurrent()
        data = assertIs(vm.card(TrendCard.COMPARE).data)
        assertEquals(SeriesInfo(CompareKey.Symptom("okand").wire, null), data.available.last())
        assertEquals(2, data.shown.size)
        vm.onEvent(TrendsEvent.ToggleSeries(TrendCard.COMPARE, CompareKey.Symptom("okand").wire))
        runCurrent()
        assertEquals(expectedKeys, assertIs<CardData.Compare>(vm.card(TrendCard.COMPARE).data).available.map { it.key })

        // Sömnkvaliteten vald utan födelseår: serien utelämnas och kortet säger varför.
        vm.onEvent(TrendsEvent.ToggleSeries(TrendCard.COMPARE, CompareKey.SleepQuality.wire))
        runCurrent()
        data = assertIs(vm.card(TrendCard.COMPARE).data)
        assertTrue(data.needsBirthYear)
        assertEquals(2, data.shown.size)
    }

    @Test
    fun `ett utfällt kort läser sin period och visar samma dagsvärde som Idag – ihopfällt står datan kvar (TRD-8, TRD-14)`() = runTest(main.dispatcher) {
        seed()
        val vm = started()
        vm.onEvent(TrendsEvent.Toggle(TrendCard.ENERGY_DAY))
        runCurrent()
        assertEquals(ranges(day(29)..today), screeningReads, "30 dagar till och med idag")
        val data = assertIs<CardData.EnergyDay>(vm.card(TrendCard.ENERGY_DAY).data)
        assertEquals(30, data.days.size)
        assertEquals(IntervalPoint(4f, 6f, 8f), data.points.last(), "dagens spann 4–8 och dagsvärdet 6")
        assertEquals(IntervalPoint(6f, 6f, 6f), data.points[28])
        assertNull(data.points[27])

        vm.onEvent(TrendsEvent.Toggle(TrendCard.ENERGY_DAY))
        runCurrent()
        assertFalse(vm.card(TrendCard.ENERGY_DAY).controls.expanded)
        assertEquals(data, vm.card(TrendCard.ENERGY_DAY).data, "sammanfattningen behöver datan")
        assertEquals(1, screeningReads.size, "ingen ny läsning")
    }

    @Test
    fun `perioden gäller per kort, och två kort med samma period delar en läsning (TRD-3)`() = runTest(main.dispatcher) {
        seed()
        val vm = started()
        vm.onEvent(TrendsEvent.Toggle(TrendCard.ENERGY_DAY))
        vm.onEvent(TrendsEvent.Toggle(TrendCard.ENERGY_OCCASION))
        runCurrent()
        assertEquals(1, screeningReads.size, "samma 30 dagar läses en gång")

        vm.onEvent(TrendsEvent.SetRange(TrendCard.ENERGY_DAY, TrendRange.SEVEN_DAYS))
        runCurrent()
        assertEquals(ranges(day(29)..today, day(6)..today), screeningReads)
        assertEquals(7, vm.card(TrendCard.ENERGY_DAY).data!!.days.size)
        assertEquals(TrendRange.MONTH, vm.card(TrendCard.ENERGY_OCCASION).controls.range, "det andra kortet rörs inte")
        assertEquals(30, vm.card(TrendCard.ENERGY_OCCASION).data!!.days.size)
    }

    @Test
    fun `serieval per kort – frukost från början, lunch till och från (TRD-1, TRD-2)`() = runTest(main.dispatcher) {
        seed()
        val vm = started()
        vm.onEvent(TrendsEvent.Toggle(TrendCard.ENERGY_OCCASION))
        runCurrent()
        var lines = assertIs<CardData.Lines>(vm.card(TrendCard.ENERGY_OCCASION).data)
        assertEquals(Occasion.entries.map { it.wire }, lines.available.map { it.key })
        assertEquals(listOf(Occasion.BREAKFAST.wire), lines.shown.map { it.key })
        assertEquals(4f, lines.shown.single().points.last())
        assertEquals(6f, lines.shown.single().points[28])

        vm.onEvent(TrendsEvent.ToggleSeries(TrendCard.ENERGY_OCCASION, Occasion.LUNCH.wire))
        runCurrent()
        lines = assertIs(vm.card(TrendCard.ENERGY_OCCASION).data)
        assertEquals(listOf(Occasion.BREAKFAST.wire, Occasion.LUNCH.wire), lines.shown.map { it.key })
        assertEquals(8f, lines.shown[1].points.last())

        vm.onEvent(TrendsEvent.ToggleSeries(TrendCard.ENERGY_OCCASION, Occasion.BREAKFAST.wire))
        runCurrent()
        lines = assertIs(vm.card(TrendCard.ENERGY_OCCASION).data)
        assertEquals(listOf(Occasion.LUNCH.wire), lines.shown.map { it.key })
        assertEquals(setOf(StressSeries.STRESS.name), vm.card(TrendCard.STRESS).controls.selected, "ett annat korts val rörs inte")
    }

    @Test
    fun `föregående period läses i ett svep på samma x-index, är av från början och försvinner vid Allt (TRD-18)`() = runTest(main.dispatcher) {
        seed()
        val vm = started()
        vm.onEvent(TrendsEvent.Toggle(TrendCard.ENERGY_OCCASION))
        runCurrent()
        assertEquals(emptyList(), assertIs<CardData.Lines>(vm.card(TrendCard.ENERGY_OCCASION).data).previous)
        assertTrue(vm.card(TrendCard.ENERGY_OCCASION).controls.showsPrevious(TrendCard.ENERGY_OCCASION))

        vm.onEvent(TrendsEvent.SetPrevious(TrendCard.ENERGY_OCCASION, true))
        runCurrent()
        assertEquals<ClosedRange<LocalDate>>(day(59)..today, screeningReads.last(), "båda perioderna i en läsning")
        var lines = assertIs<CardData.Lines>(vm.card(TrendCard.ENERGY_OCCASION).data)
        assertEquals(30, lines.days.size)
        assertEquals(listOf(Occasion.BREAKFAST.wire), lines.previous.map { it.key })
        assertEquals(30, lines.previous.single().points.size, "lika många punkter – dag 1 mot dag 1")
        // Lunchloggen för 40 dagar sedan hamnar i föregående period på index 19 (dag 59…30 bakåt).
        vm.onEvent(TrendsEvent.ToggleSeries(TrendCard.ENERGY_OCCASION, Occasion.LUNCH.wire))
        runCurrent()
        assertEquals(5f, assertIs<CardData.Lines>(vm.card(TrendCard.ENERGY_OCCASION).data).previous[1].points[19])

        vm.onEvent(TrendsEvent.SetRange(TrendCard.ENERGY_OCCASION, TrendRange.ALL))
        runCurrent()
        lines = assertIs(vm.card(TrendCard.ENERGY_OCCASION).data)
        assertFalse(vm.card(TrendCard.ENERGY_OCCASION).controls.showsPrevious(TrendCard.ENERGY_OCCASION))
        assertEquals(emptyList(), lines.previous, "Allt har ingen föregående period")
        assertEquals(day(100), lines.days.first(), "Allt börjar på första loggen")
        assertEquals(today, lines.days.last())
        assertEquals<ClosedRange<LocalDate>>(TrendRange.ALL_FROM..today, screeningReads.last())
    }

    @Test
    fun `stress räknar över mående och aktiviteter, symptomen får namn ur Listor (TRD-1)`() = runTest(main.dispatcher) {
        seed()
        val vm = started()
        vm.onEvent(TrendsEvent.Toggle(TrendCard.STRESS))
        vm.onEvent(TrendsEvent.Toggle(TrendCard.SYMPTOMS))
        runCurrent()
        assertEquals(1, screeningReads.size)
        assertEquals(1, activityReads.size)
        val stress = assertIs<CardData.Lines>(vm.card(TrendCard.STRESS).data)
        assertEquals(StressSeries.entries.map { it.name }, stress.available.map { it.key })
        assertEquals(5f, stress.shown.single().points[28], "igår: (3 + 7) / 2")

        val symptoms = assertIs<CardData.Lines>(vm.card(TrendCard.SYMPTOMS).data)
        assertEquals(listOf(SeriesInfo("yrsel", "Yrsel")), symptoms.available, "bara periodens symptom, med namnet ur Listor")
        assertEquals(emptyList(), symptoms.shown, "symptomen väljs")
        vm.onEvent(TrendsEvent.ToggleSeries(TrendCard.SYMPTOMS, "yrsel"))
        runCurrent()
        assertEquals(5f, assertIs<CardData.Lines>(vm.card(TrendCard.SYMPTOMS).data).shown.single().points[28])

        vm.onEvent(TrendsEvent.SetRange(TrendCard.SYMPTOMS, TrendRange.ALL))
        runCurrent()
        assertEquals(listOf(SeriesInfo("okand", null), SeriesInfo("yrsel", "Yrsel")), assertIs<CardData.Lines>(vm.card(TrendCard.SYMPTOMS).data).available, "ett okänt alternativ har inget namn – skärmen sätter ett")
    }

    @Test
    fun `ett valt symptom utan data i perioden står kvar i menyn, och föregående period visar bara det som visas nu (TRD-2, TRD-18)`() = runTest(main.dispatcher) {
        seed()
        val vm = started()
        vm.onEvent(TrendsEvent.Toggle(TrendCard.SYMPTOMS))
        vm.onEvent(TrendsEvent.SetRange(TrendCard.SYMPTOMS, TrendRange.ALL))
        vm.onEvent(TrendsEvent.ToggleSeries(TrendCard.SYMPTOMS, "okand"))
        vm.onEvent(TrendsEvent.ToggleSeries(TrendCard.SYMPTOMS, "yrsel"))
        runCurrent()
        assertEquals(listOf("okand", "yrsel"), assertIs<CardData.Lines>(vm.card(TrendCard.SYMPTOMS).data).shown.map { it.key })

        vm.onEvent(TrendsEvent.SetRange(TrendCard.SYMPTOMS, TrendRange.SEVEN_DAYS))
        vm.onEvent(TrendsEvent.SetPrevious(TrendCard.SYMPTOMS, true))
        runCurrent()
        val lines = assertIs<CardData.Lines>(vm.card(TrendCard.SYMPTOMS).data)
        assertEquals(listOf(SeriesInfo("yrsel", "Yrsel"), SeriesInfo("okand", null)), lines.available, "periodens symptom först, sedan det valda utan data – så det går att avmarkera")
        assertEquals(listOf("yrsel"), lines.shown.map { it.key })
        assertEquals(listOf("yrsel"), lines.previous.map { it.key }, "föregående period bara för serier som visas nu – inte det valda utan data")
        assertEquals(8f, lines.previous.single().points[3], "yrsel för tio dagar sedan ligger på dag 4 av 7 i föregående period")
        assertEquals(7, lines.previous.single().points.size)

        vm.onEvent(TrendsEvent.ToggleSeries(TrendCard.SYMPTOMS, "okand"))
        runCurrent()
        assertEquals(listOf(SeriesInfo("yrsel", "Yrsel")), assertIs<CardData.Lines>(vm.card(TrendCard.SYMPTOMS).data).available)
    }

    @Test
    fun `episoderna läses en gång och delas av periodbyte och ny utfällning – gårdagens läsningar rensas vid midnatt`() = runTest(main.dispatcher) {
        seed()
        val vm = started()
        vm.onEvent(TrendsEvent.Toggle(TrendCard.EVENTS_ILLNESS))
        runCurrent()
        vm.onEvent(TrendsEvent.SetRange(TrendCard.EVENTS_ILLNESS, TrendRange.SEVEN_DAYS))
        runCurrent()
        vm.onEvent(TrendsEvent.Toggle(TrendCard.EVENTS_ILLNESS))
        vm.onEvent(TrendsEvent.Toggle(TrendCard.EVENTS_ILLNESS))
        runCurrent()
        assertEquals(1, episodeReads, "episoderna och incheckningarna lyssnas inte om")
        assertEquals(2, eventReads.size, "två perioder för händelserna")
        assertEquals(3, vm.cachedReads, "händelser × 2 perioder + sjukdom")

        // Midnatt: nycklarna slutade på igår och rensas; de nya läsningarna slutar på den nya dagen.
        val tomorrow = LocalDate(2026, 10, 7)
        clock.instant = LocalDateTime(tomorrow, LocalTime(0, 1)).toInstant(zone)
        advanceTimeBy(1.days)
        runCurrent()
        assertEquals(tomorrow, vm.card(TrendCard.EVENTS_ILLNESS).data!!.days.last())
        assertEquals(2, vm.cachedReads, "bara dagens: händelser (7 dagar) + sjukdom")
        assertEquals(2, episodeReads)
        assertTrue(eventReads.last().endInclusive == tomorrow)
    }

    @Test
    fun `händelser och sjukdom läses först utfällt och räknar periodens händelser, incheckningar och episoder (TRD-21)`() = runTest(main.dispatcher) {
        seed()
        val vm = started()
        assertEquals(0, episodeReads)
        vm.onEvent(TrendsEvent.Toggle(TrendCard.EVENTS_ILLNESS))
        runCurrent()
        assertEquals(ranges(day(29)..today), eventReads)
        assertEquals(1, episodeReads)
        val data = assertIs<CardData.EventsIllness>(vm.card(TrendCard.EVENTS_ILLNESS).data)
        val trend = data.trend
        assertEquals(2, trend.eventCount, "händelsen för 60 dagar sedan ligger utanför")
        assertEquals(5.5f, trend.averageSeverity)
        assertEquals(7f, trend.events.last())
        assertEquals(4f, trend.events[27])
        assertEquals(6f, trend.checkins[24])
        val span = trend.episodes.single()
        assertEquals("flu", span.episode.id)
        assertEquals(23 to 29, span.from to span.to)
        assertTrue(span.ongoing)
    }

    @Test
    fun `gruppbytet syns i tillståndet (TRD-19)`() = runTest(main.dispatcher) {
        val vm = started()
        vm.state.test {
            assertEquals(TrendGroup.MOOD, awaitItem().group)
            vm.onEvent(TrendsEvent.ShowGroup(TrendGroup.WATCH))
            assertEquals(TrendGroup.WATCH, awaitItem().group)
            assertNotNull(vm.state.value.cards[TrendCard.ENERGY_DAY])
        }
    }
}
