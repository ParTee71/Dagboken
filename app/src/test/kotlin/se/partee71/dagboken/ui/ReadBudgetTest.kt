package se.partee71.dagboken.ui

import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
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
import se.partee71.dagboken.core.engine.TrendRange
import se.partee71.dagboken.core.model.Activity
import se.partee71.dagboken.core.model.Dose
import se.partee71.dagboken.core.model.DoseStatus
import se.partee71.dagboken.core.model.Event
import se.partee71.dagboken.core.model.Option
import se.partee71.dagboken.core.model.OptionKind
import se.partee71.dagboken.core.model.Screening
import se.partee71.dagboken.core.model.Slot
import se.partee71.dagboken.core.schema.ActivityCodec
import se.partee71.dagboken.data.FakeCollectionFactory
import se.partee71.dagboken.data.FixedClock
import se.partee71.dagboken.data.common.DailyGate
import se.partee71.dagboken.data.common.cachedDates
import se.partee71.dagboken.data.common.MemoryDayMarks
import se.partee71.dagboken.data.health.FakeHealthPermissions
import se.partee71.dagboken.data.health.FakeHealthRepository
import se.partee71.dagboken.data.repository.DefaultActivityRepository
import se.partee71.dagboken.data.repository.DefaultEventRepository
import se.partee71.dagboken.data.repository.DefaultIllnessRepository
import se.partee71.dagboken.data.repository.DefaultOptionsRepository
import se.partee71.dagboken.data.repository.DefaultPrnMedicineRepository
import se.partee71.dagboken.data.repository.DefaultScreeningRepository
import se.partee71.dagboken.data.repository.DefaultSettingsRepository
import se.partee71.dagboken.data.repository.testDoses
import se.partee71.dagboken.data.repository.testPrescriptions
import se.partee71.dagboken.testing.MainDispatcherRule
import se.partee71.dagboken.ui.common.STOP_TIMEOUT_MILLIS
import se.partee71.dagboken.ui.common.SelectedDay
import se.partee71.dagboken.ui.diary.DiaryEvent
import se.partee71.dagboken.ui.diary.DiaryViewModel
import se.partee71.dagboken.ui.today.TodayEvent
import se.partee71.dagboken.ui.today.TodayViewModel
import se.partee71.dagboken.ui.trends.TrendCard
import se.partee71.dagboken.ui.trends.TrendsEvent
import se.partee71.dagboken.ui.trends.TrendsViewModel

/**
 * Läsbudgeten (NFR-24) med en dagbok som den som fick slut på Firestores dagskvot: ca 2 000 dokument över tre år (doser
 * 1 239, måendeloggar 569, aktiviteter 198, händelser 40, alternativ 44). Läsningarna räknas av `FakeStore.reads` som
 * Firestore debiterar dem – varje lyssnare för hela sitt resultat när den startar (en övre gräns: Firestore tar upp en
 * lyssnare inom 30 minuter för bara ändringarna), serverläsningar per dokument, cachen gratis. Enheten börjar med en tom
 * cache (en ny installation – det dyraste läget); varje besök är en ny ViewModel, som efter en processdöd.
 *
 * Före #305 lyssnade Idag på 91 dagar, Dagbok på hela år och Trenders "Allt" på hela historiken vid **varje** besök
 * efter en längre paus; jämförelsen står i varje test (`listenedBefore`).
 */
class ReadBudgetTest {

    @get:Rule
    val main = MainDispatcherRule()

    private val zone = TimeZone.of("Europe/Stockholm")
    private val today = LocalDate(2026, 10, 6)
    private val clock = FixedClock(LocalDateTime(today, LocalTime(10, 0)).toInstant(zone))
    private val factory = FakeCollectionFactory(clock = clock)
    private val doses = testDoses(factory, zone, clock)
    private val screenings = DefaultScreeningRepository(factory, clock)
    private val activities = DefaultActivityRepository(factory, clock)
    private val events = DefaultEventRepository(factory, clock)
    private val illnesses = DefaultIllnessRepository(factory, clock)

    /** Appens enda instans: städningen körs en gång per dag (#304). */
    private val prescriptions = testPrescriptions(factory, doses, zone, clock, gate = DailyGate(MemoryDayMarks(), factory.scope))

    private fun spread(count: Int, index: Int): LocalDate = today.minus(index * DAYS / count, DateTimeUnit.DAY)

    private fun at(date: LocalDate) = LocalDateTime(date, LocalTime(8, 0)).toInstant(zone)

    /** Dagboken, skriven på en annan enhet: cachen här är tom och ingenting är täckt. */
    private suspend fun seed() {
        factory.doses().batch(List(1_239) { i -> spread(1_239, i).let { Dose("d$i", it, Slot.MORNING, "Levaxin", status = DoseStatus.TAKEN, takenAt = at(it)) } }).getOrThrow()
        factory.screenings().batch(List(569) { i -> Screening("s$i", spread(569, i), LocalTime(8, 0), energy = 5) }).getOrThrow()
        factory.activities().batch(List(198) { i -> Activity("a$i", spread(198, i), LocalTime(17, 0), minutes = 30) }).getOrThrow()
        factory.events().batch(List(40) { i -> Event("e$i", spread(40, i), LocalTime(12, 0), severity = 4) }).getOrThrow()
        factory.options().batch(List(44) { i -> Option("o$i", OptionKind.SYMPTOM, "Symptom $i") }).getOrThrow()
        factory.store.clearCache()
    }

    private val reads: Long get() = factory.store.reads.get()

    /** Läsningarna under ett besök: skärmen följer [state] tills [block] är klar, sedan lämnas den. */
    private fun TestScope.visit(state: Flow<*>, block: () -> Unit = {}): Long {
        val before = reads
        val job: Job = backgroundScope.launch { state.collect {} }
        runCurrent()
        block()
        runCurrent()
        job.cancel()
        advanceTimeBy((STOP_TIMEOUT_MILLIS + 1).milliseconds)
        return reads - before
    }

    /** Vad lyssnare på hela perioderna debiterade per besök före #305. */
    private suspend fun listenedBefore(vararg periods: Flow<List<*>>): Long {
        val before = reads
        combine(periods.toList()) { it }.first()
        return reads - before
    }

    private fun todayVm() = TodayViewModel(
        doses, prescriptions, DefaultPrnMedicineRepository(factory), screenings, DefaultSettingsRepository(factory), illnesses, SelectedDay(factory.scope), clock,
    ) { zone }

    private fun diaryVm() = DiaryViewModel(screenings, activities, doses, events, illnesses, DefaultOptionsRepository(factory), clock) { zone }

    private fun trendsVm() = TrendsViewModel(
        screenings, activities, events, illnesses, DefaultOptionsRepository(factory), FakeHealthRepository(), FakeHealthPermissions(), DefaultSettingsRepository(factory), clock,
    ) { zone }

    @Test
    fun `en appöppning (Idag) läser svitens dagar en gång – nästa öppning samma dag bara veckan (HEM-13, HEM-20)`() = runTest(main.dispatcher) {
        seed()
        val first = visit(todayVm().state)
        val second = visit(todayVm().state)
        val before = listenedBefore(doses.observeDays(today.minus(90, DateTimeUnit.DAY), today), screenings.observeDays(today.minus(90, DateTimeUnit.DAY), today))

        assertTrue(first <= FIRST_TODAY, "första öppningen: $first läsningar (91 dagar en gång + veckan)")
        assertTrue(second <= AGAIN_TODAY, "andra öppningen: $second läsningar – bara veckans lyssnare och små samlingar")
        assertTrue(second * 5 <= before, "före #305 kostade varje öppning $before, nu $second")
    }

    @Test
    fun `tre veckor bakåt i datumremsan läser inte om historiken – bara de visade veckornas lyssnare (HEM-14, NFR-24)`() = runTest(main.dispatcher) {
        seed()
        val vm = todayVm()
        val job = backgroundScope.launch { vm.state.collect {} }
        runCurrent()
        val before = reads
        val monday = LocalDate(2026, 10, 5)
        for (weeks in 1..3) {
            vm.onEvent(TodayEvent.ShowWeek(monday.minus(weeks * 7, DateTimeUnit.DAY)))
            runCurrent()
        }
        val swipes = reads - before
        job.cancel()
        assertTrue(swipes <= SWIPES, "tre svep: $swipes läsningar – veckans och dagens lyssnare, ingen omläsning av de 91 dagarna")
    }

    @Test
    fun `ett Dagbok-besök läser året en gång – Visa äldre bara det nya året, nästa besök bara 30 dagar (HIST-8)`() = runTest(main.dispatcher) {
        seed()
        val first = visit(diaryVm().state)
        val older = diaryVm()
        val withOlder = visit(older.state) { older.onEvent(DiaryEvent.ShowOlder) }
        val again = visit(diaryVm().state)
        val yearAgo = today.minus(1, DateTimeUnit.YEAR)
        val before = listenedBefore(
            screenings.observeDays(yearAgo, today), activities.observeDays(yearAgo, today), doses.observeDays(yearAgo, today), events.observeDays(yearAgo, today),
        )

        assertTrue(first <= FIRST_DIARY, "första besöket: $first läsningar (ett år en gång + 30 dagar)")
        assertTrue(withOlder <= FIRST_DIARY, "Visa äldre: $withOlder – bara det nya året hämtas")
        assertTrue(again <= AGAIN_DIARY, "nästa besök: $again läsningar – bara de 30 dagarnas lyssnare och små samlingar")
        // Före #305 kostade lyssnarna på året ensamma `before` per besök; nu kostar hela besöket – med alternativen och
        // sjukdomarna, som fejken räknar en gång per lyssnare – `again`.
        assertTrue(again * 4 <= before, "före #305 kostade varje besök $before, nu $again")
    }

    @Test
    fun `ett Trender-besök med Allt läser historiken en gång – nästa besök bara 30 dagar (TRD-3)`() = runTest(main.dispatcher) {
        seed()
        fun open(vm: TrendsViewModel) {
            vm.onEvent(TrendsEvent.Toggle(TrendCard.STRESS))
            vm.onEvent(TrendsEvent.SetRange(TrendCard.STRESS, TrendRange.ALL))
        }
        val firstVm = trendsVm()
        val first = visit(firstVm.state) { open(firstVm) }
        val againVm = trendsVm()
        val again = visit(againVm.state) { open(againVm) }
        val before = listenedBefore(screenings.observeDays(TrendRange.ALL_FROM, today), activities.observeDays(TrendRange.ALL_FROM, today))

        assertTrue(first <= FIRST_TRENDS, "första besöket: $first läsningar (all historik en gång + 30 dagar)")
        assertTrue(again <= AGAIN_TRENDS, "nästa besök: $again läsningar")
        assertTrue(again * 10 <= before, "före #305 kostade varje besök $before, nu $again")
    }

    @Test
    fun `en ny aktivitet läser förvalen ur cachen – ingenting debiteras när året är täckt (AKT-12, NFR-24)`() = runTest(main.dispatcher) {
        seed()
        visit(diaryVm().state)
        val gap = reads.let { start -> activities.new(today, LocalTime(18, 0), today = today); reads - start }
        val before = reads
        val new = activities.new(today, LocalTime(18, 0), today = today)
        val prefill = reads - before
        val listened = reads.let { start -> factory.activities().cachedDates(ActivityCodec.DATE, today.minus(365, DateTimeUnit.DAY), today); reads - start }

        assertTrue(new.minutes == 30, "förvalen kommer med (tidsåtgången från den senaste aktiviteten)")
        // Förvalen söker 365 dagar bakåt, Dagbokens år börjar en dag senare: den dagen hämtas en gång (en läsning).
        assertTrue(gap <= 1L, "första nya aktiviteten efter Dagbok: $gap läsningar – bara dagen före Dagbokens år")
        assertTrue(prefill == 0L, "ny aktivitet: $prefill läsningar – året är täckt")
        assertTrue(listened >= 60, "före #309 kunde varje ny aktivitet kosta året: $listened")
    }

    private companion object {
        /** Tre år bakåt. */
        const val DAYS = 3 * 365

        /**
         * Gränserna (ca 2 000 dokument), med marginal över det uppmätta: första öppningen av Idag svitens 91 dagar (~150)
         * + veckan (uppmätt 161), Dagbok ett år av fyra samlingar (~690) + 30 dagar + alternativen (832; "Visa äldre"
         * 829), Trender "Allt" för måendeloggar och aktiviteter (~770) + 30 dagar (811). Ett besök till samma dag läser
         * bara lyssnarna på det som visas och de små samlingarna (uppmätt Idag 9, Dagbok 147, Trender 44).
         */
        const val FIRST_TODAY = 250L
        const val FIRST_DIARY = 900L
        const val FIRST_TRENDS = 900L
        const val AGAIN_TODAY = 30L
        const val AGAIN_DIARY = 200L
        const val AGAIN_TRENDS = 100L

        /** Tre veckor bakåt i remsan: varje svep startar veckans och dagens lyssnare (~15 läsningar). */
        const val SWIPES = 60L
    }
}
