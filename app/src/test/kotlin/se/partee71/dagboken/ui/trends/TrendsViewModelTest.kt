package se.partee71.dagboken.ui.trends

import app.cash.turbine.test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.days
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
import se.partee71.dagboken.core.engine.IntervalPoint
import se.partee71.dagboken.core.engine.StressSeries
import se.partee71.dagboken.core.engine.TrendRange
import se.partee71.dagboken.core.model.Activity
import se.partee71.dagboken.core.model.Checkin
import se.partee71.dagboken.core.model.Event
import se.partee71.dagboken.core.model.IllnessEpisode
import se.partee71.dagboken.core.model.Occasion
import se.partee71.dagboken.core.model.Option
import se.partee71.dagboken.core.model.OptionKind
import se.partee71.dagboken.core.model.Screening
import se.partee71.dagboken.core.model.SymptomScore
import se.partee71.dagboken.data.FakeCollectionFactory
import se.partee71.dagboken.data.FixedClock
import se.partee71.dagboken.data.repository.ActivityRepository
import se.partee71.dagboken.data.repository.DefaultActivityRepository
import se.partee71.dagboken.data.repository.DefaultEventRepository
import se.partee71.dagboken.data.repository.DefaultIllnessRepository
import se.partee71.dagboken.data.repository.DefaultOptionsRepository
import se.partee71.dagboken.data.repository.DefaultScreeningRepository
import se.partee71.dagboken.data.repository.EventRepository
import se.partee71.dagboken.data.repository.IllnessRepository
import se.partee71.dagboken.data.repository.ScreeningRepository
import se.partee71.dagboken.testing.MainDispatcherRule

/**
 * Fliken Trender, gruppen Mående (TRD-1, TRD-3, TRD-8, TRD-14, TRD-15, TRD-18, TRD-21) mot `FakeCollection` och en
 * fast klocka: tisdag 6 oktober 2026 kl. 10:00 i Europe/Stockholm. Läsningarna räknas per repository, så att
 * testerna visar att ett stängt kort inte läser och att två kort med samma period delar en läsning.
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
    private val activities = object : ActivityRepository by DefaultActivityRepository(factory) {
        private val real = DefaultActivityRepository(factory)
        override fun observeDays(from: LocalDate, to: LocalDate): Flow<List<Activity>> = real.observeDays(from, to).onStart { activityReads += from..to }
    }
    private val events = object : EventRepository by DefaultEventRepository(factory) {
        private val real = DefaultEventRepository(factory)
        override fun observeDays(from: LocalDate, to: LocalDate): Flow<List<Event>> = real.observeDays(from, to).onStart { eventReads += from..to }
    }
    private val illnesses = object : IllnessRepository by DefaultIllnessRepository(factory) {
        private val real = DefaultIllnessRepository(factory)
        override fun observeEpisodes(): Flow<List<IllnessEpisode>> = real.observeEpisodes().onStart { episodeReads++ }
    }

    private fun day(daysAgo: Int) = today.minus(daysAgo, DateTimeUnit.DAY)

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

    private fun viewModel() = TrendsViewModel(screenings, activities, events, illnesses, DefaultOptionsRepository(factory), clock) { zone }

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
        assertTrue(screeningReads.isEmpty() && activityReads.isEmpty() && eventReads.isEmpty() && episodeReads == 0, "stängda kort läser inget")
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
