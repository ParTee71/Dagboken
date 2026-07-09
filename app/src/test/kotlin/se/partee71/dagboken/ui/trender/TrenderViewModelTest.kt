package se.partee71.dagboken.ui.trender

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
class TrenderViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    private lateinit var repo: AktiviteterRepository
    private val allFlow = MutableStateFlow<List<Aktivitet>>(emptyList())

    private lateinit var viewModel: TrenderViewModel

    @Before fun setUp() {
        Dispatchers.setMain(testDispatcher)
        repo = mockk(relaxed = true) { every { all } returns allFlow }
        viewModel = TrenderViewModel(repo)
    }

    @After fun tearDown() { Dispatchers.resetMain() }

    private fun aktivitet(
        id: String,
        datum: String,
        stress: Int = 3,
        symptom: String = "",
    ) = Aktivitet(
        id = id, timestamp = "${datum}T09:00:00.000Z", datum = datum, tid = "09:00",
        aktivitet = "Promenad", energy = 5, stress = stress, somatiska = 0,
        symptom = symptom, type = "aktivitet", spentTime = 0,
    )

    private fun screening(id: String, datum: String, slot: String, energy: Int = 5) = Aktivitet(
        id = id, timestamp = "${datum}T09:00:00.000Z", datum = datum, tid = "09:00",
        aktivitet = slot, energy = energy, stress = 3, somatiska = 0,
        symptom = "", type = "screening", spentTime = 0,
    )

    // ─── initial state ────────────────────────────────────────────────────────

    @Test fun `initial rangeDays is 30`() {
        assertEquals(30, viewModel.state.value.rangeDays)
    }

    @Test fun `initial selectedSeries contains Energi Frukost`() {
        assertTrue("Energi Frukost" in viewModel.state.value.selectedSeries)
    }

    // ─── merging series from both domains ────────────────────────────────────

    @Test fun `allSeriesLabels includes the fixed aktivitet series and discovered symptoms`() = runTest {
        val today = LocalDate.now().toString()
        allFlow.value = listOf(aktivitet("a1", today, symptom = "Yrsel:3"))
        val labels = viewModel.state.value.allSeriesLabels
        assertTrue("Energi Frukost" in labels)
        assertTrue("Stress" in labels)
        assertTrue("Yrsel" in labels)
    }

    @Test fun `selecting a symptom series adds it to rendered series`() = runTest {
        val today = LocalDate.now().toString()
        allFlow.value = listOf(aktivitet("a1", today, symptom = "Yrsel:4"))
        viewModel.toggleSeries("Yrsel")
        val series = viewModel.state.value.series
        assertTrue(series.any { it.label == "Yrsel" })
    }

    @Test fun `two series from different domains can be overlaid simultaneously`() = runTest {
        val today = LocalDate.now().toString()
        allFlow.value = listOf(
            screening("s1", today, "Efter frukost", energy = 6),
            aktivitet("a1", today, symptom = "Yrsel:4"),
        )
        viewModel.toggleSeries("Yrsel")
        val labels = viewModel.state.value.series.map { it.label }
        assertTrue("Energi Frukost" in labels)
        assertTrue("Yrsel" in labels)
    }

    @Test fun `symptom series values are the daily average score`() = runTest {
        val today = LocalDate.now().toString()
        allFlow.value = listOf(
            aktivitet("a1", today, symptom = "Yrsel:2"),
            aktivitet("a2", today, symptom = "Yrsel:6"),
        )
        viewModel.toggleSeries("Yrsel")
        val yrsel = viewModel.state.value.series.first { it.label == "Yrsel" }
        assertEquals(4.0f, yrsel.points[0])
    }

    // ─── range cutoff ─────────────────────────────────────────────────────────

    @Test fun `entries older than rangeDays are excluded`() = runTest {
        val old    = LocalDate.now().minusDays(35).toString()
        val recent = LocalDate.now().minusDays(1).toString()
        allFlow.value = listOf(aktivitet("old", old), aktivitet("recent", recent))
        viewModel.setRange(30)
        assertTrue(viewModel.state.value.dates.none { it == old })
        assertTrue(viewModel.state.value.dates.any  { it == recent })
    }

    // ─── toggleSeries ─────────────────────────────────────────────────────────

    @Test fun `toggleSeries adds a series to selectedSeries`() {
        viewModel.toggleSeries("Stress")
        assertTrue("Stress" in viewModel.state.value.selectedSeries)
    }

    @Test fun `toggleSeries removes a series already selected`() {
        viewModel.toggleSeries("Energi Frukost")
        assertTrue("Energi Frukost" !in viewModel.state.value.selectedSeries)
    }
}
