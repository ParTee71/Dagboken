package se.partee71.dagboken.ui.trender

import io.mockk.coEvery
import io.mockk.coVerify
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
import se.partee71.dagboken.data.datastore.PreferencesRepository
import se.partee71.dagboken.data.repository.AktiviteterRepository
import se.partee71.dagboken.data.repository.HealthAvailability
import se.partee71.dagboken.data.repository.HealthConnectRepository
import se.partee71.dagboken.domain.model.Aktivitet
import se.partee71.dagboken.domain.model.DailyHealth
import se.partee71.dagboken.domain.model.HealthHistory
import se.partee71.dagboken.domain.model.NightlySleepMeasurements
import se.partee71.dagboken.domain.model.Sex
import se.partee71.dagboken.domain.model.SleepMeasurements
import se.partee71.dagboken.domain.model.SleepStages
import java.time.Duration
import java.time.LocalDate

@OptIn(ExperimentalCoroutinesApi::class)
class TrenderViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    private lateinit var repo: AktiviteterRepository
    private lateinit var healthRepo: HealthConnectRepository
    private lateinit var prefs: PreferencesRepository
    private val allFlow = MutableStateFlow<List<Aktivitet>>(emptyList())

    private lateinit var viewModel: TrenderViewModel

    @Before fun setUp() {
        Dispatchers.setMain(testDispatcher)
        repo = mockk(relaxed = true) { every { all } returns allFlow }
        healthRepo = mockk(relaxed = true) { every { availability() } returns HealthAvailability.NOT_INSTALLED }
        prefs = mockk(relaxed = true) {
            every { birthYear } returns MutableStateFlow(1971)
            every { sex } returns MutableStateFlow(Sex.MAN)
        }
        viewModel = TrenderViewModel(repo, healthRepo, prefs)
    }

    /** Hälsodiagrammen läser först när kortet fällts ut (TRD-14/TRD-15). */
    private fun expand(section: TrenderSection) = viewModel.setExpanded(section, true)

    private fun seriesFor(section: TrenderSection, label: String) =
        viewModel.state.value.healthTrends[section]?.series?.firstOrNull { it.label == label }

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

    // ─── Ihopfällbara diagramkort — TRD-14, #189 ──────────────────────────────

    @Test fun `every section starts collapsed`() {
        assertTrue(
            "Trender ska öppna med samtliga diagramkort stängda",
            TrenderSection.entries.all { viewModel.state.value.expanded.getValue(it) == false },
        )
    }

    @Test fun `setExpanded expands only the given section`() {
        viewModel.setExpanded(TrenderSection.STEG, true)
        assertEquals(true, viewModel.state.value.expanded.getValue(TrenderSection.STEG))
        assertTrue(
            "Övriga kort ska förbli stängda",
            TrenderSection.entries.filter { it != TrenderSection.STEG }
                .all { viewModel.state.value.expanded.getValue(it) == false },
        )
    }

    @Test fun `setExpanded can collapse a section again`() {
        viewModel.setExpanded(TrenderSection.SYMPTOM, true)
        viewModel.setExpanded(TrenderSection.SYMPTOM, false)
        assertEquals(false, viewModel.state.value.expanded.getValue(TrenderSection.SYMPTOM))
    }

    @Test fun `setExpanded leaves ranges and selected series untouched`() {
        val rangesBefore = viewModel.state.value.ranges
        val seriesBefore = viewModel.state.value.selectedSeries
        viewModel.setExpanded(TrenderSection.VILOPULS, true)
        assertEquals(rangesBefore, viewModel.state.value.ranges)
        assertEquals(seriesBefore, viewModel.state.value.selectedSeries)
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

    // ─── Hälsodiagram (Health Connect) — TRD-11/TRD-15, #191 ─────────────────

    private fun history(vararg days: DailyHealth) = HealthHistory(days.toList())

    private fun healthRepoWith(
        history: HealthHistory = HealthHistory(),
        available: Boolean = true,
        granted: Boolean = true,
    ): HealthConnectRepository = mockk(relaxed = true) {
        every { availability() } returns
            if (available) HealthAvailability.AVAILABLE else HealthAvailability.NOT_INSTALLED
        coEvery { hasRequiredPermissions() } returns granted
        coEvery { readHealthHistory(any()) } returns history
    }

    @Test fun `no health diagram is read while every card is collapsed`() = runTest {
        healthRepo = healthRepoWith(history(DailyHealth(LocalDate.now(), steps = 5000)))
        viewModel = TrenderViewModel(repo, healthRepo, prefs)
        // Stängda kort ska inte kosta någon Health Connect-läsning alls (TRD-15).
        assertTrue(viewModel.state.value.healthTrends.isEmpty())
        coVerify(exactly = 0) { healthRepo.readHealthHistory(any()) }
    }

    @Test fun `expanding a health card reads its history`() = runTest {
        val today = LocalDate.now()
        healthRepo = healthRepoWith(
            history(
                DailyHealth(today.minusDays(1), steps = 4000),
                DailyHealth(today, steps = 9000),
            ),
        )
        viewModel = TrenderViewModel(repo, healthRepo, prefs)
        expand(TrenderSection.STEG)
        assertEquals(listOf(4000f, 9000f), seriesFor(TrenderSection.STEG, "Steg")?.points)
    }

    @Test fun `two diagrams with the same period share one read`() = runTest {
        healthRepo = healthRepoWith(history(DailyHealth(LocalDate.now(), steps = 100)))
        viewModel = TrenderViewModel(repo, healthRepo, prefs)
        expand(TrenderSection.STEG)
        expand(TrenderSection.TRANING)
        // Båda har standardperioden Månad — en läsning räcker för båda.
        coVerify(exactly = 1) { healthRepo.readHealthHistory(30) }
    }

    @Test fun `changing one diagram's period does not re-read another's`() = runTest {
        healthRepo = healthRepoWith(history(DailyHealth(LocalDate.now(), steps = 100)))
        viewModel = TrenderViewModel(repo, healthRepo, prefs)
        expand(TrenderSection.STEG)
        viewModel.setRange(TrenderSection.STEG, TrenderRange.THREE_MONTHS)
        coVerify(exactly = 1) { healthRepo.readHealthHistory(30) }
        coVerify(exactly = 1) { healthRepo.readHealthHistory(90) }
    }

    @Test fun `a collapsed card keeps its data and is not read again`() = runTest {
        healthRepo = healthRepoWith(history(DailyHealth(LocalDate.now(), steps = 100)))
        viewModel = TrenderViewModel(repo, healthRepo, prefs)
        expand(TrenderSection.STEG)
        viewModel.setExpanded(TrenderSection.STEG, false)
        expand(TrenderSection.STEG)
        coVerify(exactly = 1) { healthRepo.readHealthHistory(30) }
    }

    @Test fun `a day without a measurement stays a gap, never a zero`() = runTest {
        val today = LocalDate.now()
        healthRepo = healthRepoWith(
            history(
                DailyHealth(today.minusDays(1), steps = 4000),
                DailyHealth(today.minusDays(0), steps = null),
            ),
        )
        viewModel = TrenderViewModel(repo, healthRepo, prefs)
        expand(TrenderSection.STEG)
        assertEquals(listOf(4000f, null), seriesFor(TrenderSection.STEG, "Steg")?.points)
    }

    @Test fun `health trends stay empty when Health Connect is not available`() = runTest {
        healthRepo = healthRepoWith(available = false)
        viewModel = TrenderViewModel(repo, healthRepo, prefs)
        expand(TrenderSection.STEG)
        assertTrue(viewModel.state.value.healthTrends.getValue(TrenderSection.STEG).series.all { it.points.isEmpty() })
    }

    @Test fun `health trends stay empty when permissions are not granted`() = runTest {
        healthRepo = healthRepoWith(granted = false)
        viewModel = TrenderViewModel(repo, healthRepo, prefs)
        expand(TrenderSection.STEG)
        assertTrue(viewModel.state.value.healthTrends.getValue(TrenderSection.STEG).series.all { it.points.isEmpty() })
    }

    @Test fun `a failing read leaves the diagram empty without touching the logged diagrams`() = runTest {
        allFlow.value = listOf(screening("s1", LocalDate.now().toString(), "Lunch"))
        healthRepo = mockk(relaxed = true) {
            every { availability() } returns HealthAvailability.AVAILABLE
            coEvery { hasRequiredPermissions() } returns true
            coEvery { readHealthHistory(any()) } throws RuntimeException("boom")
        }
        viewModel = TrenderViewModel(repo, healthRepo, prefs)
        expand(TrenderSection.STEG)
        assertTrue(viewModel.state.value.healthTrends.getValue(TrenderSection.STEG).series.all { it.points.isEmpty() })
        // Dagboksdiagrammen påverkas inte av en trasig hälsokälla.
        assertTrue(viewModel.state.value.dailyEnergy.isNotEmpty())
    }

    @Test fun `the resting heart rate diagram offers both resting and average heart rate`() = runTest {
        val today = LocalDate.now()
        healthRepo = healthRepoWith(
            history(
                DailyHealth(today.minusDays(1), restingHeartRate = 55, heartRateAvg = 70),
                DailyHealth(today, restingHeartRate = 58, heartRateAvg = 74),
            ),
        )
        viewModel = TrenderViewModel(repo, healthRepo, prefs)
        expand(TrenderSection.VILOPULS)

        val trend = viewModel.state.value.healthTrends.getValue(TrenderSection.VILOPULS)
        assertEquals(listOf("Vilopuls", "Dygnssnitt"), trend.labels)
        // Bara vilopulsen är vald från början (TRD-11).
        assertEquals(listOf("Vilopuls"), trend.series.map { it.label })

        viewModel.toggleHealthSeries(TrenderSection.VILOPULS, "Dygnssnitt")
        assertEquals(
            listOf(70f, 74f),
            seriesFor(TrenderSection.VILOPULS, "Dygnssnitt")?.points,
        )
    }

    @Test fun `toggling a series in one health diagram leaves the others alone`() = runTest {
        healthRepo = healthRepoWith(history(DailyHealth(LocalDate.now(), steps = 100)))
        viewModel = TrenderViewModel(repo, healthRepo, prefs)
        expand(TrenderSection.VILOPULS)
        expand(TrenderSection.SOMN)

        viewModel.toggleHealthSeries(TrenderSection.SOMN, "Djup")
        assertEquals(
            setOf("Total", "Djup"),
            viewModel.state.value.selectedHealthSeries.getValue(TrenderSection.SOMN),
        )
        assertEquals(
            setOf("Vilopuls"),
            viewModel.state.value.selectedHealthSeries.getValue(TrenderSection.VILOPULS),
        )
    }

    @Test fun `sleep series are exposed in hours`() = runTest {
        val today = LocalDate.now()
        healthRepo = healthRepoWith(
            history(
                DailyHealth(today.minusDays(1), sleepDuration = Duration.ofMinutes(450)),
                DailyHealth(today, sleepDuration = Duration.ofHours(8)),
            ),
        )
        viewModel = TrenderViewModel(repo, healthRepo, prefs)
        expand(TrenderSection.SOMN)
        assertEquals(listOf(7.5f, 8f), seriesFor(TrenderSection.SOMN, "Total")?.points)
    }

    @Test fun `distance is exposed in kilometres`() = runTest {
        val today = LocalDate.now()
        healthRepo = healthRepoWith(
            history(
                DailyHealth(today.minusDays(1), distanceMeters = 5200.0),
                DailyHealth(today, distanceMeters = 1000.0),
            ),
        )
        viewModel = TrenderViewModel(repo, healthRepo, prefs)
        expand(TrenderSection.STRACKA)
        assertEquals(listOf(5.2f, 1f), seriesFor(TrenderSection.STRACKA, "Sträcka")?.points)
    }

    @Test fun `sleep quality is scored per night for its own period`() = runTest {
        val today = LocalDate.now()
        healthRepo = mockk(relaxed = true) {
            every { availability() } returns HealthAvailability.AVAILABLE
            coEvery { hasRequiredPermissions() } returns true
            coEvery { readSleepMeasurementsHistory(any()) } returns listOf(
                NightlySleepMeasurements(
                    date = today,
                    measurements = SleepMeasurements(
                        timeInBed = Duration.ofHours(8),
                        awake = Duration.ofMinutes(20),
                    ),
                ),
            )
        }
        viewModel = TrenderViewModel(repo, healthRepo, prefs)
        expand(TrenderSection.SOMNKVALITET)

        val points = seriesFor(TrenderSection.SOMNKVALITET, "Poäng")?.points
        assertEquals(1, points?.size)
        assertTrue("En åtta timmars natt ska ge en poäng", points?.first() != null)
    }

    @Test fun `sleep quality is a gap when the birth year is missing`() = runTest {
        val today = LocalDate.now()
        prefs = mockk(relaxed = true) {
            every { birthYear } returns MutableStateFlow<Int?>(null)
            every { sex } returns MutableStateFlow(Sex.MAN)
        }
        healthRepo = mockk(relaxed = true) {
            every { availability() } returns HealthAvailability.AVAILABLE
            coEvery { hasRequiredPermissions() } returns true
            coEvery { readSleepMeasurementsHistory(any()) } returns listOf(
                NightlySleepMeasurements(today, SleepMeasurements(timeInBed = Duration.ofHours(8))),
            )
        }
        viewModel = TrenderViewModel(repo, healthRepo, prefs)
        expand(TrenderSection.SOMNKVALITET)

        // Poängen är åldersjusterad (HLS-11) — utan födelseår en lucka, inte en nolla.
        assertEquals(listOf(null), seriesFor(TrenderSection.SOMNKVALITET, "Poäng")?.points)
    }

    @Test fun `the sleep stage diagram always shows all four stages and offers no picker`() = runTest {
        val today = LocalDate.now()
        healthRepo = healthRepoWith(
            history(
                DailyHealth(
                    today,
                    sleepStages = SleepStages(
                        deep = Duration.ofMinutes(90),
                        rem = Duration.ofMinutes(90),
                        light = Duration.ofMinutes(240),
                        awake = Duration.ofMinutes(30),
                    ),
                ),
            ),
        )
        viewModel = TrenderViewModel(repo, healthRepo, prefs)
        expand(TrenderSection.SOMNSTADIER)

        val trend = viewModel.state.value.healthTrends.getValue(TrenderSection.SOMNSTADIER)
        // Sammansättningen är hela poängen — ingen serieväljare, alla stadier alltid med.
        assertTrue("Ingen serieväljare för stadierna", trend.labels.isEmpty())
        assertEquals(listOf("Djup", "REM", "Lätt", "Vaken"), trend.series.map { it.label })
        assertEquals(listOf(1.5f), seriesFor(TrenderSection.SOMNSTADIER, "Djup")?.points)
        assertEquals(listOf(4f), seriesFor(TrenderSection.SOMNSTADIER, "Lätt")?.points)
    }

    @Test fun `a night without a stage leaves that stage as a gap`() = runTest {
        val today = LocalDate.now()
        healthRepo = healthRepoWith(
            history(
                DailyHealth(today, sleepStages = SleepStages(deep = Duration.ofMinutes(60))),
            ),
        )
        viewModel = TrenderViewModel(repo, healthRepo, prefs)
        expand(TrenderSection.SOMNSTADIER)
        assertEquals(listOf(1f), seriesFor(TrenderSection.SOMNSTADIER, "Djup")?.points)
        assertEquals(listOf(null), seriesFor(TrenderSection.SOMNSTADIER, "REM")?.points)
    }

    // ─── Jämförelsediagram — TRD-17, #194 ────────────────────────────────────

    @Test fun `normalising maps the lowest value to zero and the highest to a hundred`() {
        assertEquals(listOf(0f, 50f, 100f), normalizeSeries(listOf(10f, 20f, 30f)))
    }

    @Test fun `a constant series becomes a middle line instead of a division by zero`() {
        val normalised = normalizeSeries(listOf(60f, 60f, 60f))
        assertEquals(listOf(50f, 50f, 50f), normalised)
        assertTrue("Inga NaN", normalised.filterNotNull().none { it.isNaN() })
    }

    @Test fun `gaps survive normalising and never become zero`() {
        // En nolla vore ett påstående om en dag utan mätning.
        assertEquals(listOf(0f, null, 100f), normalizeSeries(listOf(4f, null, 8f)))
    }

    @Test fun `a series with a single known point does not crash`() {
        assertEquals(listOf(null, 50f, null), normalizeSeries(listOf(null, 7f, null)))
    }

    @Test fun `an empty series normalises to itself`() {
        assertEquals(emptyList<Float?>(), normalizeSeries(emptyList()))
        assertEquals(listOf(null, null), normalizeSeries(listOf(null, null)))
    }

    @Test fun `the comparison offers both watch metrics and diary series`() = runTest {
        allFlow.value = listOf(screening("s1", LocalDate.now().toString(), "Lunch"))
        healthRepo = healthRepoWith(history(DailyHealth(LocalDate.now(), steps = 5000)))
        viewModel = TrenderViewModel(repo, healthRepo, prefs)
        expand(TrenderSection.JAMFOR)

        val labels = viewModel.state.value.comparison.labels
        assertTrue("Klockmått ska gå att välja", "Steg" in labels)
        assertTrue("Dagboksserier ska gå att välja", "Stress" in labels)
        assertTrue("Energi (dag) ska gå att välja", "Energi (dag)" in labels)
    }

    @Test fun `comparison series are indexed while the legend keeps the real values`() = runTest {
        val today = LocalDate.now()
        healthRepo = healthRepoWith(
            history(
                DailyHealth(today.minusDays(1), steps = 4000, restingHeartRate = 50),
                DailyHealth(today, steps = 12000, restingHeartRate = 60),
            ),
        )
        viewModel = TrenderViewModel(repo, healthRepo, prefs)
        expand(TrenderSection.JAMFOR)
        viewModel.toggleHealthSeries(TrenderSection.JAMFOR, "Steg")
        viewModel.toggleHealthSeries(TrenderSection.JAMFOR, "Vilopuls")

        val comparison = viewModel.state.value.comparison
        val steps = comparison.series.first { it.label == "Steg" }.points.filterNotNull()
        val hr = comparison.series.first { it.label == "Vilopuls" }.points.filterNotNull()
        // Båda indexeras 0–100 mot sitt eget spann — annars vore vilopulsen en platt linje.
        assertEquals(listOf(0f, 100f), steps)
        assertEquals(listOf(0f, 100f), hr)

        val stepsLegend = comparison.legend.first { it.label == "Steg" }
        assertEquals(4000f, stepsLegend.min, 0.001f)
        assertEquals(12000f, stepsLegend.max, 0.001f)
        assertEquals("steg", stepsLegend.unit)
    }

    @Test fun `nothing is compared while the card is collapsed`() = runTest {
        healthRepo = healthRepoWith(history(DailyHealth(LocalDate.now(), steps = 100)))
        viewModel = TrenderViewModel(repo, healthRepo, prefs)
        assertTrue(viewModel.state.value.comparison.series.isEmpty())
        coVerify(exactly = 0) { healthRepo.readHealthHistory(any()) }
    }

    @Test fun `sleep quality is only read when it is actually selected`() = runTest {
        healthRepo = healthRepoWith(history(DailyHealth(LocalDate.now(), steps = 100)))
        viewModel = TrenderViewModel(repo, healthRepo, prefs)
        expand(TrenderSection.JAMFOR)
        viewModel.toggleHealthSeries(TrenderSection.JAMFOR, "Steg")
        coVerify(exactly = 0) { healthRepo.readSleepMeasurementsHistory(any()) }

        viewModel.toggleHealthSeries(TrenderSection.JAMFOR, "Sömnkvalitet")
        coVerify(exactly = 1) { healthRepo.readSleepMeasurementsHistory(any()) }
    }

    @Test fun `the all-time range caps the health read at one year`() = runTest {
        healthRepo = healthRepoWith(history(DailyHealth(LocalDate.now(), steps = 100)))
        viewModel = TrenderViewModel(repo, healthRepo, prefs)
        viewModel.setRange(TrenderSection.STEG, TrenderRange.ALL)
        expand(TrenderSection.STEG)
        coVerify { healthRepo.readHealthHistory(365) }
    }
}
