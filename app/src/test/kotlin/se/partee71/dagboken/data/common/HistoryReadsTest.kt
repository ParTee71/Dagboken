package se.partee71.dagboken.data.common

import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
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
import org.junit.Test
import se.partee71.dagboken.core.model.Dose
import se.partee71.dagboken.core.schema.DoseCodec
import se.partee71.dagboken.core.time.recentDays
import se.partee71.dagboken.data.FakeCollectionFactory
import se.partee71.dagboken.data.FixedClock
import se.partee71.dagboken.data.TestUserScope
import se.partee71.dagboken.data.firestore.Paths

/**
 * Historikläget (NFR-24) mot `FakeCollection`, vars cache bara känner det som skrivits eller lästs på "enheten"
 * (`FakeStore.cached`) och som räknar debiterade läsningar (`FakeStore.reads`): inget får tyst saknas ur en ofullständig
 * cache, och det som redan hämtats läses aldrig igen från servern.
 */
class HistoryReadsTest {

    private val today = LocalDate(2026, 10, 6)
    private val clock = FixedClock(LocalDateTime(today, LocalTime(10, 0)).toInstant(TimeZone.UTC))
    private val scope = TestUserScope()
    private val factory = FakeCollectionFactory(scope = scope, clock = clock)
    private val store = factory.store
    private val doses = factory.doses()

    private fun day(daysAgo: Int) = today.minus(daysAgo, DateTimeUnit.DAY)

    private fun dose(daysAgo: Int) = Dose("d%03d".format(daysAgo), day(daysAgo), name = "Levaxin")

    /** En dos per dag, [days] dagar bakåt från idag – skrivna av en annan enhet: cachen här känner dem inte. */
    private suspend fun seedElsewhere(days: Int) {
        doses.batch((0 until days).map(::dose)).getOrThrow()
        store.clearCache()
    }

    private fun history(from: Int, to: Int): Flow<List<Dose>> = doses.observeHistory(DoseCodec.DATE, day(from), day(to))

    private fun ids(from: Int, to: Int) = (to..from).map { "d%03d".format(it) }.sorted()

    /** Det [flow] visar när allt som startats är klart (hämtningen fyller på cachelyssnaren). */
    private fun <T> TestScope.settled(flow: Flow<T>): T {
        val seen = mutableListOf<T>()
        val job = backgroundScope.launch { flow.collect { seen += it } }
        runCurrent()
        job.cancel()
        return seen.last()
    }

    private fun TestScope.read(from: Int, to: Int): List<String> = settled(history(from, to)).map { it.id }

    /** Läsningarna som debiterades under [block]. */
    private suspend fun billed(block: suspend () -> Unit): Long {
        val before = store.reads.get()
        block()
        return store.reads.get() - before
    }

    @Test
    fun `ett intervall som aldrig hämtats läses från servern en gång – inget saknas fast cachen är tom`() = runTest {
        seedElsewhere(10)
        assertEquals(10, billed { assertEquals(ids(9, 5), read(9, 5)) }, "allt från första dagen och framåt, en gång")
        assertEquals(0, billed { assertEquals(ids(9, 5), read(9, 5)) }, "samma intervall igen: ur cachen")
        assertEquals(0, billed { assertEquals(ids(4, 0), read(4, 0)) }, "senare dagar täcks av samma hämtning")
    }

    @Test
    fun `ett äldre intervall hämtar bara luckan före täckningen`() = runTest {
        seedElsewhere(20)
        read(9, 0)
        assertEquals(10, billed { assertEquals(ids(19, 15), read(19, 15)) }, "dagarna 19…10, inte de redan täckta")
        assertEquals(0, billed { read(19, 0) })
    }

    @Test
    fun `täckningen hämtas om efter en vecka från den äldsta täckta dagen – en skrivning förbi appen syns då`() = runTest {
        seedElsewhere(20)
        read(19, 0)
        // tools/db (import/migrate) lägger till en dos för tre dagar sedan; enhetens cache vet inget om den.
        store.set(Paths.doses(scope.uid.value!!), "importerad", DoseCodec.encode(Dose("importerad", day(3), name = "Atarax")), merge = false)
        store.cached.value = store.cached.value.mapValues { (_, known) -> known - "importerad" }
        assertEquals(ids(4, 0), read(4, 0), "inom veckan: cachen som den var")

        clock.instant += HistoryCoverage.REFRESH_DAYS.days
        assertEquals(21, billed { assertEquals((ids(4, 0) + "importerad").sorted(), read(4, 0)) }, "allt från dag 19 hämtas om, inte bara från dag 4")
        assertEquals(day(19), factory.marks.marks["history.${Paths.doses("uid-test")}.${DoseCodec.DATE}.from"], "den bredare täckningen krymper inte")
    }

    @Test
    fun `en gammal täckning och en lucka visas direkt också utan nät`() = runTest {
        seedElsewhere(5)
        read(4, 0)
        clock.instant += HistoryCoverage.REFRESH_DAYS.days
        store.online = false
        assertEquals(ids(4, 0), read(4, 0))
        assertEquals(emptyList(), read(9, 5), "dagar som aldrig hämtats: det cachen har, som en lyssnare utan nät")
    }

    @Test
    fun `en egen skrivning i ett täckt intervall syns direkt och debiteras inte`() = runTest {
        seedElsewhere(5)
        val seen = mutableListOf<List<String>>()
        backgroundScope.launch { history(4, 0).collect { list -> seen += list.map { it.id } } }
        runCurrent()
        assertEquals(
            0,
            billed {
                doses.upsert(Dose("ny", day(2), name = "Alvedon")).getOrThrow()
                doses.delete("d001").getOrThrow()
                runCurrent()
            }
        )
        assertEquals((ids(4, 0) + "ny" - "d001").sorted(), seen.last())
    }

    @Test
    fun `utan nät visas det cachen har, och servern försöks igen tills allt kommit (NFR-1)`() = runTest {
        seedElsewhere(5)
        doses.upsert(Dose("egen", day(1), name = "Alvedon")).getOrThrow()
        store.online = false
        val seen = mutableListOf<List<String>>()
        backgroundScope.launch { history(4, 0).collect { list -> seen += list.map { it.id } } }
        runCurrent()
        assertEquals(listOf("egen"), seen.last(), "det cachen har, som en lyssnare utan nät")
        assertTrue(factory.marks.marks.keys.none { it.startsWith("history.") }, "ingenting antecknas utan svar från servern")

        store.online = true
        advanceTimeBy(31.seconds)
        runCurrent()
        assertEquals((ids(4, 0) + "egen").sorted(), seen.last(), "cachelyssnaren fyller på när hämtningen lyckats")
    }

    @Test
    fun `täckningen gäller per konto`() = runTest {
        seedElsewhere(5)
        read(4, 0)
        scope.uid.value = "uid-bo"
        assertEquals(1, billed { assertEquals(emptyList(), read(4, 0)) }, "ett annat konto hämtar sitt eget – ett tomt svar kostar en läsning")
        scope.uid.value = "uid-test"
        assertEquals(0, billed { read(4, 0) })
    }

    @Test
    fun `glömd täckning hämtas från servern igen`() = runTest {
        seedElsewhere(5)
        read(4, 0)
        factory.coverage.forgetAll()
        assertEquals(5, billed { read(4, 0) })
    }

    @Test
    fun `går anteckningen inte att läsa hämtas intervallet varje gång – hellre en läsning för mycket`() = runTest {
        seedElsewhere(5)
        factory.marks.fails = true
        assertEquals(5, billed { assertEquals(ids(4, 0), read(4, 0)) })
        assertEquals(5, billed { read(4, 0) })
    }

    /** En hämtning som väntar på [gate] (långsamt nät) och räknas i [fetches]. */
    private class SlowFetch {
        val gate = CompletableDeferred<Unit>()
        var fetches = 0
        val fetch: suspend (String, LocalDate, LocalDate?) -> Unit = { _, _, _ ->
            fetches++
            gate.await()
        }
    }

    private fun slowFlow(uid: MutableStateFlow<String?>, coverage: HistoryCoverage, slow: SlowFetch) = historyFlow(uid, coverage, { "nyckel.$it" }, today, today, slow.fetch) { flowOf(listOf("ur cachen")) }

    @Test
    fun `långsamt nät – cachen visas direkt, samma hämtning inväntas och antecknas när den blir klar, också när skärmen lämnats, och ingen ny startar under tiden`() = runTest {
        val marks = MemoryDayMarks()
        val coverage = testCoverage(marks, clock)
        val slow = SlowFetch()
        val uid = MutableStateFlow<String?>("uid")
        backgroundScope.launch { slowFlow(uid, coverage, slow).collect {} }.also { runCurrent() }.cancel()
        runCurrent()
        val seen = mutableListOf<List<String>>()
        val again = backgroundScope.launch { slowFlow(uid, coverage, slow).collect { seen += it } }
        advanceTimeBy(5.minutes)
        assertEquals(listOf(listOf("ur cachen")), seen, "cachen visas direkt, utan att vänta på servern (NFR-1)")
        assertEquals(1, slow.fetches, "ingen ny hämtning medan en pågår – och ingen tidsgräns som startar om den")

        slow.gate.complete(Unit)
        runCurrent()
        assertEquals(1, slow.fetches)
        assertEquals(today, marks.marks["history.nyckel.uid.from"], "antecknad fast den som startade hämtningen gett upp")
        again.cancel()
    }

    @Test
    fun `ett kontobyte eller en utloggning under hämtningen antecknar ingenting för det gamla kontot`() = runTest {
        val marks = MemoryDayMarks()
        val coverage = testCoverage(marks, clock)
        val slow = SlowFetch()
        val uid = MutableStateFlow<String?>("uid")
        backgroundScope.launch { slowFlow(uid, coverage, slow).collect {} }
        runCurrent()
        uid.value = null
        runCurrent()
        slow.gate.complete(Unit)
        runCurrent()
        assertTrue(marks.marks.isEmpty(), "${marks.marks}")

        val forgotten = SlowFetch()
        uid.value = "uid"
        backgroundScope.launch { slowFlow(uid, coverage, forgotten).collect {} }
        runCurrent()
        coverage.forgetAll()
        forgotten.gate.complete(Unit)
        runCurrent()
        assertTrue(marks.marks.isEmpty(), "en hämtning som pågick när täckningen glömdes antecknas inte: ${marks.marks}")
    }

    @Test
    fun `ett annat fel än Offline och slut på kvoten avslutar flödet – de två visar cachen`() = runTest {
        val coverage = testCoverage(MemoryDayMarks(), clock)
        fun flowFailingWith(error: DataError) = historyFlow(MutableStateFlow<String?>("uid"), coverage, { "nyckel.$it" }, today, today, { _, _, _ -> throw error }) { flowOf(listOf("ur cachen")) }
        val failing = flowFailingWith(DataError.PermissionDenied)
        assertEquals(DataError.PermissionDenied, runCatching { failing.collect {} }.exceptionOrNull())
        assertEquals(listOf("ur cachen"), flowFailingWith(DataError.Offline).first())
        assertEquals(listOf("ur cachen"), flowFailingWith(DataError.QuotaExceeded).first(), "slut på kvoten: som offline")
    }

    @Test
    fun `utloggad emitteras inget – inloggad läses det`() = runTest {
        val coverage = testCoverage(MemoryDayMarks(), clock)
        val uid = MutableStateFlow<String?>(null)
        val seen = mutableListOf<List<String>>()
        backgroundScope.launch { historyFlow(uid, coverage, { "nyckel.$it" }, today, today, { _, _, _ -> }) { flowOf(listOf("ur cachen")) }.collect { seen += it } }
        runCurrent()
        assertEquals(emptyList(), seen)
        uid.value = "uid"
        runCurrent()
        assertEquals(listOf(listOf("ur cachen")), seen, "och läser när någon loggar in")
    }

    @Test
    fun `en lång period – lyssnare bara på de senaste dagarna, historiken ur cachen – en lista, sorterad och utan dubbletter`() = runTest {
        seedElsewhere(60)
        fun period() = doses.observeDates(DoseCodec.DATE, day(59), today, listOf(recentDays(today)))
        assertEquals(60 + 30, billed { assertEquals(ids(59, 0), settled(period()).map { it.id }) }, "historiken en gång (allt från första dagen) och lyssnaren")
        assertEquals(30, billed { assertEquals(ids(59, 0), settled(period()).map { it.id }) }, "andra gången bara lyssnaren på de 30 dagarna")
        assertEquals(30, billed { settled(doses.observeDates(DoseCodec.DATE, day(29), today, listOf(recentDays(today)))) }, "en period inom de 30 dagarna: historiken redan täckt")
        assertEquals(0, billed { assertEquals(emptyList(), settled(doses.observeDates(DoseCodec.DATE, today, day(1), emptyList()))) }, "en tom period läser inget")
        factory.coverage.forgetAll()
        assertEquals(0, billed { assertEquals(emptyList(), settled(doses.observeHistory(DoseCodec.DATE, today, day(1)))) }, "inte heller i historikläget direkt")
    }

    @Test
    fun `ihopslagna läsningar – den senaste listans version vinner, sorterat på id`() {
        val old = Dose("a", day(40), name = "Levaxin")
        val moved = old.copy(date = day(2), note = "flyttad")
        assertEquals(listOf(moved, Dose("b", day(1), name = "Alvedon")), mergeReads(listOf(listOf(old), listOf(Dose("b", day(1), name = "Alvedon"), moved))))
    }
}
