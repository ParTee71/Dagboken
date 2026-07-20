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

    @Test fun `initial range is MONTH for every section`() {
        TrenderSection.entries.forEach {
            assertEquals(TrenderRange.MONTH, viewModel.state.value.ranges.getValue(it))
        }
    }

    @Test fun `initial selectedSeries contains Energi Frukost`() {
        assertTrue("Energi Frukost" in viewModel.state.value.selectedSeries)
    }

    // ─── merging series from both domains ────────────────────────────────────

    @Test fun `category labels include the fixed aktivitet series and discovered symptoms`() = runTest {
        val today = LocalDate.now().toString()
        allFlow.value = listOf(aktivitet("a1", today, symptom = "Yrsel:3"))
        val trends = viewModel.state.value.categoryTrends
        assertTrue("Energi Frukost" in trends.getValue(TrenderCategory.ENERGI_TILLFALLE).labels)
        assertTrue("Stress" in trends.getValue(TrenderCategory.STRESS_BELASTNING).labels)
        assertTrue("Yrsel" in trends.getValue(TrenderCategory.SYMPTOM).labels)
    }

    @Test fun `selecting a symptom series adds it to the SYMPTOM category's rendered series`() = runTest {
        val today = LocalDate.now().toString()
        allFlow.value = listOf(aktivitet("a1", today, symptom = "Yrsel:4"))
        viewModel.toggleSeries("Yrsel")
        val series = viewModel.state.value.categoryTrends.getValue(TrenderCategory.SYMPTOM).series
        assertTrue(series.any { it.label == "Yrsel" })
    }

    @Test fun `two series from different categories can be selected simultaneously`() = runTest {
        val today = LocalDate.now().toString()
        allFlow.value = listOf(
            screening("s1", today, "Efter frukost", energy = 6),
            aktivitet("a1", today, symptom = "Yrsel:4"),
        )
        viewModel.toggleSeries("Yrsel")
        val trends = viewModel.state.value.categoryTrends
        assertTrue(trends.getValue(TrenderCategory.ENERGI_TILLFALLE).series.any { it.label == "Energi Frukost" })
        assertTrue(trends.getValue(TrenderCategory.SYMPTOM).series.any { it.label == "Yrsel" })
    }

    @Test fun `symptom series values are the daily average score`() = runTest {
        val today = LocalDate.now().toString()
        allFlow.value = listOf(
            aktivitet("a1", today, symptom = "Yrsel:2"),
            aktivitet("a2", today, symptom = "Yrsel:6"),
        )
        viewModel.toggleSeries("Yrsel")
        val yrsel = viewModel.state.value.categoryTrends.getValue(TrenderCategory.SYMPTOM).series.first { it.label == "Yrsel" }
        assertEquals(4.0f, yrsel.points[0])
    }

    // ─── range cutoff ─────────────────────────────────────────────────────────

    @Test fun `entries older than a section's range are excluded from that section`() = runTest {
        val old    = LocalDate.now().minusDays(35).toString()
        val recent = LocalDate.now().minusDays(1).toString()
        allFlow.value = listOf(aktivitet("old", old), aktivitet("recent", recent))
        viewModel.setRange(TrenderSection.ENERGI_TILLFALLE, TrenderRange.MONTH)
        val dates = viewModel.state.value.categoryTrends.getValue(TrenderCategory.ENERGI_TILLFALLE).dates
        assertTrue(dates.none { it == old })
        assertTrue(dates.any  { it == recent })
    }

    @Test fun `ALL range includes entries regardless of age`() = runTest {
        val ancient = LocalDate.now().minusDays(400).toString()
        allFlow.value = listOf(aktivitet("ancient", ancient))
        viewModel.setRange(TrenderSection.ENERGI_TILLFALLE, TrenderRange.ALL)
        val dates = viewModel.state.value.categoryTrends.getValue(TrenderCategory.ENERGI_TILLFALLE).dates
        assertTrue(dates.any { it == ancient })
    }

    @Test fun `changing one section's range does not affect another section's dates`() = runTest {
        val old = LocalDate.now().minusDays(35).toString()
        allFlow.value = listOf(aktivitet("old", old))
        viewModel.setRange(TrenderSection.ENERGI_TILLFALLE, TrenderRange.ALL)
        val energiDates = viewModel.state.value.categoryTrends.getValue(TrenderCategory.ENERGI_TILLFALLE).dates
        val stressDates = viewModel.state.value.categoryTrends.getValue(TrenderCategory.STRESS_BELASTNING).dates
        assertTrue(energiDates.any { it == old })
        assertTrue(stressDates.none { it == old })
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

    @Test fun `setRange updates only the given section's range`() {
        viewModel.setRange(TrenderSection.SYMPTOM, TrenderRange.SEVEN_DAYS)
        assertEquals(TrenderRange.SEVEN_DAYS, viewModel.state.value.ranges.getValue(TrenderSection.SYMPTOM))
        assertEquals(TrenderRange.MONTH, viewModel.state.value.ranges.getValue(TrenderSection.ENERGI_TILLFALLE))
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

    @Test fun `dailyEnergy respects its own section's range cutoff`() = runTest {
        val old = LocalDate.now().minusDays(35).toString()
        allFlow.value = listOf(screening("s1", old, "Lunch"))
        viewModel.setRange(TrenderSection.ENERGI_DAG, TrenderRange.MONTH)
        assertTrue(viewModel.state.value.dailyEnergy.none { it.datum == old })
    }

    // ─── Kategoriuppdelning — #141 ─────────────────────────────────────────────

    @Test fun `ENERGI_TILLFALLE category only returns energy slot series`() = runTest {
        val today = LocalDate.now().toString()
        allFlow.value = listOf(
            screening("s1", today, "Efter frukost"),
            aktivitet("a1", today, symptom = "Yrsel:3"),
        )
        viewModel.toggleSeries("Stress")
        viewModel.toggleSeries("Yrsel")
        val labels = viewModel.state.value.categoryTrends.getValue(TrenderCategory.ENERGI_TILLFALLE).series.map { it.label }
        assertEquals(listOf("Energi Frukost"), labels)
    }

    @Test fun `STRESS_BELASTNING category only returns stress and belastning series`() = runTest {
        val today = LocalDate.now().toString()
        allFlow.value = listOf(aktivitet("a1", today, symptom = "Yrsel:3"))
        viewModel.toggleSeries("Stress")
        viewModel.toggleSeries("Yrsel")
        val labels = viewModel.state.value.categoryTrends.getValue(TrenderCategory.STRESS_BELASTNING).series.map { it.label }
        assertEquals(listOf("Stress"), labels)
    }

    @Test fun `SYMPTOM category only returns discovered symptom series`() = runTest {
        val today = LocalDate.now().toString()
        allFlow.value = listOf(aktivitet("a1", today, symptom = "Yrsel:3"))
        viewModel.toggleSeries("Stress")
        viewModel.toggleSeries("Yrsel")
        val labels = viewModel.state.value.categoryTrends.getValue(TrenderCategory.SYMPTOM).series.map { it.label }
        assertEquals(listOf("Yrsel"), labels)
    }

    @Test fun `categoryOf maps every fixed series and defaults symptoms to SYMPTOM`() {
        ENERGY_SLOT_SERIES.forEach { assertEquals(TrenderCategory.ENERGI_TILLFALLE, categoryOf(it)) }
        STRESS_SERIES.forEach { assertEquals(TrenderCategory.STRESS_BELASTNING, categoryOf(it)) }
        assertEquals(TrenderCategory.SYMPTOM, categoryOf("Yrsel"))
    }

    // ─── Steg/vilopuls (Health Connect) — TRD-11, #146/#149 ───────────────────

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

    @Test fun `dailySteps and dailyRestingHeartRate expose Health Connect data for their own sections' ranges`() = runTest {
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

    @Test fun `setRange on STEG re-reads Health Connect steps without affecting VILOPULS`() = runTest {
        val weeklyMonth = WeeklyHealth(
            dailySteps = listOf(DailySteps(LocalDate.now(), 1000)),
            dailyRestingHeartRate = listOf(DailyRestingHeartRate(LocalDate.now(), 55)),
        )
        val weeklyThreeMonths = WeeklyHealth(dailySteps = listOf(DailySteps(LocalDate.now(), 9000)))
        healthRepo = mockk(relaxed = true) {
            every { availability() } returns HealthAvailability.AVAILABLE
            coEvery { hasAllPermissions() } returns true
            coEvery { readHealthRange(30) } returns weeklyMonth
            coEvery { readHealthRange(90) } returns weeklyThreeMonths
        }
        viewModel = TrenderViewModel(repo, healthRepo)
        assertEquals(1000L, viewModel.state.value.dailySteps.first().steps)
        assertEquals(55L, viewModel.state.value.dailyRestingHeartRate.first().bpm)

        viewModel.setRange(TrenderSection.STEG, TrenderRange.THREE_MONTHS)
        assertEquals(9000L, viewModel.state.value.dailySteps.first().steps)
        // VILOPULS läste inte om — dess period (MONTH) är oförändrad, så den behåller sitt värde.
        assertEquals(55L, viewModel.state.value.dailyRestingHeartRate.first().bpm)
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
