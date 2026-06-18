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
import org.junit.Assert.assertTrue
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
    ) = Aktivitet(
        id = id, timestamp = "${datum}T09:00:00.000Z", datum = datum, tid = "09:00",
        aktivitet = "Promenad", energy = energy, stress = stress, somatiska = 0,
        symptom = "", aterhamtande = false, energitjuv = false, type = "aktivitet", spentTime = 0,
    )

    // ─── initial state ────────────────────────────────────────────────────────

    @Test fun `initial rangeDays is 30`() {
        assertEquals(30, viewModel.state.value.rangeDays)
    }

    @Test fun `initial selectedSeries is Energi`() {
        assertEquals("Energi", viewModel.state.value.selectedSeries)
    }

    @Test fun `initial stats are empty`() {
        assertTrue(viewModel.state.value.stats.isEmpty())
    }

    // ─── setRange / setSeries ─────────────────────────────────────────────────

    @Test fun `setRange updates rangeDays in state`() = runTest {
        viewModel.setRange(7)
        assertEquals(7, viewModel.state.value.rangeDays)
    }

    @Test fun `setSeries updates selectedSeries in state`() = runTest {
        viewModel.setSeries("Stress")
        assertEquals("Stress", viewModel.state.value.selectedSeries)
    }

    // ─── daily grouping and average ───────────────────────────────────────────

    @Test fun `stats groups entries by date and computes mean energy`() = runTest {
        val today = LocalDate.now().toString()
        allFlow.value = listOf(
            aktivitet("a1", today, energy = 4),
            aktivitet("a2", today, energy = 6),
        )
        val stats = viewModel.state.value.stats
        assertEquals(1, stats.size)
        assertEquals(5.0f, stats[0].avgEnergy)
    }

    @Test fun `stats computes mean stress per day`() = runTest {
        val today = LocalDate.now().toString()
        allFlow.value = listOf(
            aktivitet("a1", today, stress = 2),
            aktivitet("a2", today, stress = 8),
        )
        val stats = viewModel.state.value.stats
        assertEquals(5.0f, stats[0].avgStress)
    }

    @Test fun `single entry produces avgEnergy equal to its value`() = runTest {
        val today = LocalDate.now().toString()
        allFlow.value = listOf(aktivitet("a1", today, energy = 7))
        assertEquals(7.0f, viewModel.state.value.stats[0].avgEnergy)
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
