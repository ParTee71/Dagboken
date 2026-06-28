package se.partee71.dagboken.ui.diagram

import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import kotlin.math.abs
import org.junit.Before
import org.junit.Test
import se.partee71.dagboken.data.repository.AktiviteterRepository
import se.partee71.dagboken.domain.model.Aktivitet
import java.time.LocalDate

@OptIn(ExperimentalCoroutinesApi::class)
class DiagramViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    private lateinit var repo: AktiviteterRepository
    private val allFlow = MutableStateFlow<List<Aktivitet>>(emptyList())

    private lateinit var viewModel: DiagramViewModel

    @Before fun setUp() {
        Dispatchers.setMain(testDispatcher)
        repo = mockk(relaxed = true) {
            every { all } returns allFlow
        }
        viewModel = DiagramViewModel(repo)
    }

    @After fun tearDown() { Dispatchers.resetMain() }

    private fun aktivitet(
        id: String,
        datum: String,
        energy: Int = 5,
        stress: Int = 3,
        somatiska: Int = 0,
        aterhamtande: Boolean = false,
        energitjuv: Boolean = false,
    ) = Aktivitet(
        id = id, timestamp = "${datum}T09:00:00.000Z", datum = datum, tid = "09:00",
        aktivitet = "Promenad", energy = energy, stress = stress, somatiska = somatiska,
        symptom = "", aterhamtande = aterhamtande, energitjuv = energitjuv, type = "aktivitet", spentTime = 0,
    )

    private fun screening(
        id: String,
        datum: String,
        slot: String,
        energy: Int = 5,
        stress: Int = 3,
        somatiska: Int = 0,
    ) = Aktivitet(
        id = id, timestamp = "${datum}T09:00:00.000Z", datum = datum, tid = "09:00",
        aktivitet = slot, energy = energy, stress = stress, somatiska = somatiska,
        symptom = "", aterhamtande = false, energitjuv = false, type = "screening", spentTime = 0,
    )

    // ─── initial state ────────────────────────────────────────────────────────

    @Test fun `initial rangeDays is 30`() {
        assertEquals(30, viewModel.state.value.rangeDays)
    }

    @Test fun `initial visibleSeries contains Energi Frukost`() {
        assertTrue(viewModel.state.value.visibleSeries.contains("Energi Frukost"))
    }

    @Test fun `initial stats are empty`() {
        assertTrue(viewModel.state.value.stats.isEmpty())
    }

    // ─── setRange / setSeries ─────────────────────────────────────────────────

    @Test fun `setRange updates rangeDays in state`() = runTest {
        viewModel.setRange(7)
        assertEquals(7, viewModel.state.value.rangeDays)
    }

    @Test fun `toggleSeries adds series to visibleSeries`() = runTest {
        viewModel.toggleSeries("Stress")
        assertTrue(viewModel.state.value.visibleSeries.contains("Stress"))
    }

    @Test fun `toggleSeries removes series already in visibleSeries`() = runTest {
        viewModel.toggleSeries("Energi Frukost")
        assertTrue("Energi Frukost" !in viewModel.state.value.visibleSeries)
    }

    // ─── per-slot energy ──────────────────────────────────────────────────────

    @Test fun `stats computes avgEnergyFrukost from Efter frukost screenings only`() = runTest {
        val today = LocalDate.now().toString()
        allFlow.value = listOf(
            screening("s1", today, "Efter frukost", energy = 4),
            screening("s2", today, "Efter frukost", energy = 6),
            screening("s3", today, "Lunch", energy = 10),
            aktivitet("a1", today, energy = 2),
        )
        assertEquals(5.0f, viewModel.state.value.stats[0].avgEnergyFrukost)
    }

    @Test fun `single Frukost screening produces avgEnergyFrukost equal to its value`() = runTest {
        val today = LocalDate.now().toString()
        allFlow.value = listOf(screening("s1", today, "Efter frukost", energy = 7))
        assertEquals(7.0f, viewModel.state.value.stats[0].avgEnergyFrukost)
    }

    @Test fun `avgEnergyLunch null when no Lunch screening that day`() = runTest {
        val today = LocalDate.now().toString()
        allFlow.value = listOf(screening("s1", today, "Efter frukost", energy = 5))
        assertNull(viewModel.state.value.stats[0].avgEnergyLunch)
    }

    @Test fun `all four energy slots computed independently`() = runTest {
        val today = LocalDate.now().toString()
        allFlow.value = listOf(
            screening("s1", today, "Efter frukost", energy = 2),
            screening("s2", today, "Lunch",         energy = 4),
            screening("s3", today, "Kvällsmat",     energy = 6),
            screening("s4", today, "Läggdags",      energy = 8),
        )
        val stats = viewModel.state.value.stats[0]
        assertEquals(2.0f, stats.avgEnergyFrukost)
        assertEquals(4.0f, stats.avgEnergyLunch)
        assertEquals(6.0f, stats.avgEnergyKvallsmat)
        assertEquals(8.0f, stats.avgEnergyLaggdags)
    }

    @Test fun `aktiviteter do not contribute to slot energy`() = runTest {
        val today = LocalDate.now().toString()
        allFlow.value = listOf(aktivitet("a1", today, energy = 9))
        val stats = viewModel.state.value.stats[0]
        assertNull(stats.avgEnergyFrukost)
        assertNull(stats.avgEnergyLunch)
        assertNull(stats.avgEnergyKvallsmat)
        assertNull(stats.avgEnergyLaggdags)
    }

    // ─── other series (unchanged) ─────────────────────────────────────────────

    @Test fun `stats computes mean stress per day`() = runTest {
        val today = LocalDate.now().toString()
        allFlow.value = listOf(
            aktivitet("a1", today, stress = 2),
            aktivitet("a2", today, stress = 8),
        )
        assertEquals(5.0f, viewModel.state.value.stats[0].avgStress)
    }

    @Test fun `stats computes mean somatiska per day`() = runTest {
        val today = LocalDate.now().toString()
        allFlow.value = listOf(
            aktivitet("a1", today, somatiska = 2),
            aktivitet("a2", today, somatiska = 6),
        )
        assertEquals(4.0f, viewModel.state.value.stats[0].avgSomatiska)
    }

    @Test fun `stats computes aterhamtande fraction scaled to 10`() = runTest {
        val today = LocalDate.now().toString()
        allFlow.value = listOf(
            aktivitet("a1", today, aterhamtande = true),
            aktivitet("a2", today, aterhamtande = false),
        )
        assertEquals(5.0f, viewModel.state.value.stats[0].avgAterhamtande)
    }

    @Test fun `stats computes energitjuv fraction scaled to 10`() = runTest {
        val today = LocalDate.now().toString()
        allFlow.value = listOf(
            aktivitet("a1", today, energitjuv = true),
            aktivitet("a2", today, energitjuv = false),
            aktivitet("a3", today, energitjuv = true),
        )
        val result = viewModel.state.value.stats[0].avgEnergitjuv ?: 0f
        assertTrue("Expected ~6.67 but got $result", abs(result - 6.667f) < 0.01f)
    }

    // ─── range cutoff ─────────────────────────────────────────────────────────

    @Test fun `entries older than rangeDays are excluded`() = runTest {
        val old    = LocalDate.now().minusDays(35).toString()
        val recent = LocalDate.now().minusDays(1).toString()
        allFlow.value = listOf(
            aktivitet("old",    old),
            aktivitet("recent", recent),
        )
        viewModel.setRange(30)
        val stats = viewModel.state.value.stats
        assertTrue(stats.none { it.datum == old })
        assertTrue(stats.any  { it.datum == recent })
    }

    @Test fun `changing range to 7 days excludes entries from 10 days ago`() = runTest {
        val tenDaysAgo = LocalDate.now().minusDays(10).toString()
        val yesterday  = LocalDate.now().minusDays(1).toString()
        allFlow.value = listOf(
            aktivitet("a1", tenDaysAgo),
            aktivitet("a2", yesterday),
        )
        viewModel.setRange(7)
        val stats = viewModel.state.value.stats
        assertTrue(stats.none { it.datum == tenDaysAgo })
        assertTrue(stats.any  { it.datum == yesterday })
    }

    // ─── sort order ───────────────────────────────────────────────────────────

    @Test fun `stats are sorted by date ascending`() = runTest {
        val d1 = LocalDate.now().minusDays(2).toString()
        val d2 = LocalDate.now().minusDays(1).toString()
        allFlow.value = listOf(aktivitet("a2", d2), aktivitet("a1", d1))
        val stats = viewModel.state.value.stats
        assertEquals(2, stats.size)
        assertTrue(stats[0].datum < stats[1].datum)
    }

    // ─── reactive updates ─────────────────────────────────────────────────────

    @Test fun `stats update when new entries are added to flow`() = runTest {
        val today = LocalDate.now().toString()
        assertEquals(0, viewModel.state.value.stats.size)
        allFlow.value = listOf(aktivitet("a1", today))
        assertEquals(1, viewModel.state.value.stats.size)
    }
}
