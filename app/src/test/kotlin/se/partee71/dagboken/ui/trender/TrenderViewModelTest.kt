package se.partee71.dagboken.ui.trender

import io.mockk.coEvery
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
import se.partee71.dagboken.data.repository.HealthAvailability
import se.partee71.dagboken.data.repository.HealthConnectRepository
import se.partee71.dagboken.domain.model.Aktivitet
import se.partee71.dagboken.domain.model.DailyRestingHeartRate
import se.partee71.dagboken.domain.model.DailySteps
import se.partee71.dagboken.domain.model.WeeklyHealth
import java.time.LocalDate

@OptIn(ExperimentalCoroutinesApi::class)
class TrenderViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    private lateinit var repo: AktiviteterRepository
    private lateinit var healthRepo: HealthConnectRepository
    private val allFlow = MutableStateFlow<List<Aktivitet>>(emptyList())

    private lateinit var viewModel: TrenderViewModel

    @Before fun setUp() {
        Dispatchers.setMain(testDispatcher)
        repo = mockk(relaxed = true) { every { all } returns allFlow }
        healthRepo = mockk(relaxed = true) { every { availability() } returns HealthAvailability.NOT_INSTALLED }
        viewModel = TrenderViewModel(repo, healthRepo)
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

    @Test fun `initial range is MONTH`() {
        assertEquals(TrenderRange.MONTH, viewModel.state.value.range)
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

    @Test fun `entries older than the selected range are excluded`() = runTest {
        val old    = LocalDate.now().minusDays(35).toString()
        val recent = LocalDate.now().minusDays(1).toString()
        allFlow.value = listOf(aktivitet("old", old), aktivitet("recent", recent))
        viewModel.setRange(TrenderRange.MONTH)
        assertTrue(viewModel.state.value.dates.none { it == old })
        assertTrue(viewModel.state.value.dates.any  { it == recent })
    }

    @Test fun `ALL range includes entries regardless of age`() = runTest {
        val ancient = LocalDate.now().minusDays(400).toString()
        allFlow.value = listOf(aktivitet("ancient", ancient))
        viewModel.setRange(TrenderRange.ALL)
        assertTrue(viewModel.state.value.dates.any { it == ancient })
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

    @Test fun `setRange updates state range`() {
        viewModel.setRange(TrenderRange.SEVEN_DAYS)
        assertEquals(TrenderRange.SEVEN_DAYS, viewModel.state.value.range)
    }

    // ─── Energi (dag) — TRD-8, #141 ───────────────────────────────────────────

    @Test fun `dailyEnergy has min, avg and max for a day with multiple screenings`() = runTest {
        val today = LocalDate.now().toString()
        allFlow.value = listOf(
            screening("s1", today, "Efter frukost", energy = 2),
            screening("s2", today, "Lunch", energy = 8),
        )
        val day = viewModel.state.value.dailyEnergy.first { it.datum == today }
        assertEquals(2f, day.min)
        assertEquals(5f, day.avg)
        assertEquals(8f, day.max)
    }

    @Test fun `dailyEnergy ignores non-screening entries`() = runTest {
        val today = LocalDate.now().toString()
        allFlow.value = listOf(aktivitet("a1", today))
        assertTrue(viewModel.state.value.dailyEnergy.none { it.datum == today })
    }

    @Test fun `dailyEnergy respects the selected range cutoff`() = runTest {
        val old = LocalDate.now().minusDays(35).toString()
        allFlow.value = listOf(screening("s1", old, "Lunch"))
        viewModel.setRange(TrenderRange.MONTH)
        assertTrue(viewModel.state.value.dailyEnergy.none { it.datum == old })
    }

    // ─── Kategoriuppdelning — #141 ─────────────────────────────────────────────

    @Test fun `seriesFor ENERGI_TILLFALLE only returns energy slot series`() = runTest {
        val today = LocalDate.now().toString()
        allFlow.value = listOf(
            screening("s1", today, "Efter frukost"),
            aktivitet("a1", today, symptom = "Yrsel:3"),
        )
        viewModel.toggleSeries("Stress")
        viewModel.toggleSeries("Yrsel")
        val labels = viewModel.state.value.seriesFor(TrenderCategory.ENERGI_TILLFALLE).map { it.label }
        assertEquals(listOf("Energi Frukost"), labels)
    }

    @Test fun `seriesFor STRESS_BELASTNING only returns stress and belastning series`() = runTest {
        val today = LocalDate.now().toString()
        allFlow.value = listOf(aktivitet("a1", today, symptom = "Yrsel:3"))
        viewModel.toggleSeries("Stress")
        viewModel.toggleSeries("Yrsel")
        val labels = viewModel.state.value.seriesFor(TrenderCategory.STRESS_BELASTNING).map { it.label }
        assertEquals(listOf("Stress"), labels)
    }

    @Test fun `seriesFor SYMPTOM only returns discovered symptom series`() = runTest {
        val today = LocalDate.now().toString()
        allFlow.value = listOf(aktivitet("a1", today, symptom = "Yrsel:3"))
        viewModel.toggleSeries("Stress")
        viewModel.toggleSeries("Yrsel")
        val labels = viewModel.state.value.seriesFor(TrenderCategory.SYMPTOM).map { it.label }
        assertEquals(listOf("Yrsel"), labels)
    }

    @Test fun `categoryOf maps every fixed series and defaults symptoms to SYMPTOM`() {
        ENERGY_SLOT_SERIES.forEach { assertEquals(TrenderCategory.ENERGI_TILLFALLE, categoryOf(it)) }
        STRESS_SERIES.forEach { assertEquals(TrenderCategory.STRESS_BELASTNING, categoryOf(it)) }
        assertEquals(TrenderCategory.SYMPTOM, categoryOf("Yrsel"))
    }

    // ─── Steg/vilopuls (Health Connect) — TRD-11, #146 ────────────────────────

    @Test fun `dailySteps and dailyRestingHeartRate are empty when Health Connect is not available`() = runTest {
        assertTrue(viewModel.state.value.dailySteps.isEmpty())
        assertTrue(viewModel.state.value.dailyRestingHeartRate.isEmpty())
    }

    @Test fun `dailySteps and dailyRestingHeartRate are empty when permissions are not granted`() = runTest {
        healthRepo = mockk(relaxed = true) {
            every { availability() } returns HealthAvailability.AVAILABLE
            coEvery { hasAllPermissions() } returns false
        }
        viewModel = TrenderViewModel(repo, healthRepo)
        assertTrue(viewModel.state.value.dailySteps.isEmpty())
    }

    @Test fun `dailySteps and dailyRestingHeartRate expose Health Connect data for the selected range`() = runTest {
        val today = LocalDate.now()
        val weekly = WeeklyHealth(
            dailySteps = listOf(DailySteps(today, 5000)),
            dailyRestingHeartRate = listOf(DailyRestingHeartRate(today, 58)),
        )
        healthRepo = mockk(relaxed = true) {
            every { availability() } returns HealthAvailability.AVAILABLE
            coEvery { hasAllPermissions() } returns true
            coEvery { readHealthRange(30) } returns weekly
        }
        viewModel = TrenderViewModel(repo, healthRepo)
        assertEquals(listOf(DailySteps(today, 5000)), viewModel.state.value.dailySteps)
        assertEquals(listOf(DailyRestingHeartRate(today, 58)), viewModel.state.value.dailyRestingHeartRate)
    }

    @Test fun `setRange re-reads Health Connect data for the new range`() = runTest {
        val weekly7 = WeeklyHealth(dailySteps = listOf(DailySteps(LocalDate.now(), 1000)))
        val weekly90 = WeeklyHealth(dailySteps = listOf(DailySteps(LocalDate.now(), 9000)))
        healthRepo = mockk(relaxed = true) {
            every { availability() } returns HealthAvailability.AVAILABLE
            coEvery { hasAllPermissions() } returns true
            coEvery { readHealthRange(30) } returns weekly7
            coEvery { readHealthRange(90) } returns weekly90
        }
        viewModel = TrenderViewModel(repo, healthRepo)
        assertEquals(1000L, viewModel.state.value.dailySteps.first().steps)

        viewModel.setRange(TrenderRange.THREE_MONTHS)
        assertEquals(9000L, viewModel.state.value.dailySteps.first().steps)
    }

    @Test fun `dailySteps stays empty when Health Connect read throws`() = runTest {
        healthRepo = mockk(relaxed = true) {
            every { availability() } returns HealthAvailability.AVAILABLE
            coEvery { hasAllPermissions() } returns true
            coEvery { readHealthRange(any()) } throws RuntimeException("boom")
        }
        viewModel = TrenderViewModel(repo, healthRepo)
        assertTrue(viewModel.state.value.dailySteps.isEmpty())
    }
}
