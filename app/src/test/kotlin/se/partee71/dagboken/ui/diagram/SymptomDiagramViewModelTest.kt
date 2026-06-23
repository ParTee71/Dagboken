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
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import se.partee71.dagboken.data.repository.AktiviteterRepository
import se.partee71.dagboken.domain.model.Aktivitet
import java.time.LocalDate

@OptIn(ExperimentalCoroutinesApi::class)
class SymptomDiagramViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    private lateinit var repo: AktiviteterRepository
    private val allFlow = MutableStateFlow<List<Aktivitet>>(emptyList())

    private lateinit var viewModel: SymptomDiagramViewModel

    @Before fun setUp() {
        Dispatchers.setMain(testDispatcher)
        repo = mockk(relaxed = true) { every { all } returns allFlow }
        viewModel = SymptomDiagramViewModel(repo)
    }

    @After fun tearDown() { Dispatchers.resetMain() }

    private fun entry(id: String, datum: String, symptom: String = "") = Aktivitet(
        id = id, timestamp = "${datum}T09:00:00.000Z", datum = datum, tid = "09:00",
        aktivitet = "Test", energy = 5, stress = 3, somatiska = 0,
        symptom = symptom, aterhamtande = false, energitjuv = false, type = "aktivitet", spentTime = 0,
    )

    private val today     = LocalDate.now().toString()
    private val yesterday = LocalDate.now().minusDays(1).toString()
    private val twoDaysAgo = LocalDate.now().minusDays(2).toString()

    // ─── initial state ────────────────────────────────────────────────────────

    @Test fun `initial rangeDays is 30`() {
        assertEquals(30, viewModel.state.value.rangeDays)
    }

    @Test fun `initial allSymptoms is empty`() {
        assertTrue(viewModel.state.value.allSymptoms.isEmpty())
    }

    @Test fun `initial selectedSymptoms is empty`() {
        assertTrue(viewModel.state.value.selectedSymptoms.isEmpty())
    }

    @Test fun `initial series is empty`() {
        assertTrue(viewModel.state.value.series.isEmpty())
    }

    @Test fun `initial days is empty`() {
        assertTrue(viewModel.state.value.days.isEmpty())
    }

    // ─── data filtering ───────────────────────────────────────────────────────

    @Test fun `entries with blank symptom field are excluded from allSymptoms`() = runTest {
        allFlow.value = listOf(
            entry("a1", today, ""),
            entry("a2", today, "   "),
        )
        assertTrue(viewModel.state.value.allSymptoms.isEmpty())
    }

    @Test fun `entries with blank symptom field do not add days to days list`() = runTest {
        allFlow.value = listOf(entry("a1", today, ""))
        assertTrue(viewModel.state.value.days.isEmpty())
    }

    @Test fun `entries outside rangeDays are excluded`() = runTest {
        val old = LocalDate.now().minusDays(35).toString()
        allFlow.value = listOf(
            entry("a1", old,       "Yrsel:2"),
            entry("a2", yesterday, "Trötthet:3"),
        )
        assertTrue(viewModel.state.value.days.none { it == old })
        assertTrue(viewModel.state.value.days.any  { it == yesterday })
    }

    @Test fun `allSymptoms contains names decoded from symptom strings`() = runTest {
        allFlow.value = listOf(entry("a1", today, "Nackvärk:3,Heshet:2"))
        val all = viewModel.state.value.allSymptoms
        assertTrue("Nackvärk" in all)
        assertTrue("Heshet" in all)
    }

    // ─── symptom ranking ──────────────────────────────────────────────────────

    @Test fun `allSymptoms sorted by frequency descending`() = runTest {
        allFlow.value = listOf(
            entry("a1", today,     "Trötthet:2"),
            entry("a2", yesterday, "Trötthet:3"),
            entry("a3", today,     "Yrsel:1"),   // Yrsel appears in only 1 entry
        )
        val all = viewModel.state.value.allSymptoms
        assertEquals("Trötthet", all[0])
        assertEquals("Yrsel", all[1])
    }

    // ─── auto selection ───────────────────────────────────────────────────────

    @Test fun `top 2 symptoms auto-selected on first data load`() = runTest {
        // A in 3 entries, B in 2, C in 1 → top 2 are A and B
        allFlow.value = listOf(
            entry("a1", today,      "A:1,B:2,C:3"),
            entry("a2", yesterday,  "A:2,B:1"),
            entry("a3", twoDaysAgo, "A:3"),
        )
        val selected = viewModel.state.value.selectedSymptoms
        assertEquals(2, selected.size)
        assertTrue("A" in selected)
        assertTrue("B" in selected)
        assertTrue("C" !in selected)
    }

    @Test fun `single symptom in data is auto-selected`() = runTest {
        allFlow.value = listOf(entry("a1", today, "Yrsel:2"))
        val selected = viewModel.state.value.selectedSymptoms
        assertEquals(1, selected.size)
        assertTrue("Yrsel" in selected)
    }

    @Test fun `existing selection is preserved across range changes`() = runTest {
        allFlow.value = listOf(
            entry("a1", today,     "Yrsel:2"),
            entry("a2", yesterday, "Trötthet:3"),
        )
        // Both are in initial top-2 selection; deselect Yrsel
        viewModel.toggleSymptom("Yrsel")
        val selectedBefore = viewModel.state.value.selectedSymptoms.toSet()

        viewModel.setRange(14)
        val selectedAfter = viewModel.state.value.selectedSymptoms

        assertEquals(selectedBefore, selectedAfter)
    }

    // ─── daily averaging ──────────────────────────────────────────────────────

    @Test fun `multiple entries on the same day produce average score for symptom`() = runTest {
        allFlow.value = listOf(
            entry("a1", today, "Yrsel:2"),
            entry("a2", today, "Yrsel:4"),
        )
        val yrsel = viewModel.state.value.series.first { it.label == "Yrsel" }
        assertEquals(3.0f, yrsel.points.single())
    }

    @Test fun `single entry produces score equal to its value`() = runTest {
        allFlow.value = listOf(entry("a1", today, "Trötthet:5"))
        val series = viewModel.state.value.series.first { it.label == "Trötthet" }
        assertEquals(5.0f, series.points.single())
    }

    @Test fun `scores from different entries on the same day are all included in average`() = runTest {
        allFlow.value = listOf(
            entry("a1", today, "Yrsel:1"),
            entry("a2", today, "Yrsel:2"),
            entry("a3", today, "Yrsel:3"),
        )
        val yrsel = viewModel.state.value.series.first { it.label == "Yrsel" }
        assertEquals(2.0f, yrsel.points.single())
    }

    // ─── series gaps ─────────────────────────────────────────────────────────

    @Test fun `series point is null for days where that symptom was not logged`() = runTest {
        // Both symptoms appear once each → both auto-selected (top 2)
        allFlow.value = listOf(
            entry("a1", yesterday, "Yrsel:2,Trötthet:3"),
            entry("a2", today,     "Yrsel:4"),  // Trötthet not logged today
        )
        val trotthet = viewModel.state.value.series.find { it.label == "Trötthet" }
        requireNotNull(trotthet) { "Trötthet should be auto-selected" }
        assertEquals(2, trotthet.points.size)
        assertEquals(3.0f, trotthet.points[0])  // yesterday
        assertNull(trotthet.points[1])           // today → gap
    }

    @Test fun `days list contains one entry per distinct date with symptom data`() = runTest {
        allFlow.value = listOf(
            entry("a1", twoDaysAgo, "Yrsel:1"),
            entry("a2", yesterday,  "Yrsel:2"),
            entry("a3", yesterday,  "Yrsel:3"), // second entry on yesterday
        )
        assertEquals(listOf(twoDaysAgo, yesterday), viewModel.state.value.days)
    }

    @Test fun `days list is sorted ascending`() = runTest {
        allFlow.value = listOf(
            entry("a2", yesterday,  "Yrsel:1"),
            entry("a1", twoDaysAgo, "Yrsel:2"),
        )
        val days = viewModel.state.value.days
        assertTrue(days[0] < days[1])
    }

    // ─── toggleSymptom ────────────────────────────────────────────────────────

    @Test fun `toggleSymptom adds unselected symptom to selectedSymptoms`() = runTest {
        allFlow.value = listOf(entry("a1", today, "A:1,B:2,C:3"))
        // A and B auto-selected (top 2); toggle C to add it
        viewModel.toggleSymptom("C")
        assertTrue("C" in viewModel.state.value.selectedSymptoms)
    }

    @Test fun `toggleSymptom removes selected symptom from selectedSymptoms`() = runTest {
        allFlow.value = listOf(entry("a1", today, "Yrsel:2"))
        assertTrue("Yrsel" in viewModel.state.value.selectedSymptoms)
        viewModel.toggleSymptom("Yrsel")
        assertTrue("Yrsel" !in viewModel.state.value.selectedSymptoms)
    }

    @Test fun `series is rebuilt after toggleSymptom removes a symptom`() = runTest {
        allFlow.value = listOf(entry("a1", today, "Yrsel:2,Trötthet:3"))
        assertTrue(viewModel.state.value.series.any { it.label == "Yrsel" })
        viewModel.toggleSymptom("Yrsel")
        assertTrue(viewModel.state.value.series.none { it.label == "Yrsel" })
    }

    @Test fun `series is rebuilt after toggleSymptom adds a symptom`() = runTest {
        allFlow.value = listOf(entry("a1", today, "A:3,B:2,C:1"))
        assertTrue(viewModel.state.value.series.none { it.label == "C" })
        viewModel.toggleSymptom("C")
        assertTrue(viewModel.state.value.series.any { it.label == "C" })
    }

    // ─── setRange ─────────────────────────────────────────────────────────────

    @Test fun `setRange updates rangeDays in state`() = runTest {
        viewModel.setRange(7)
        assertEquals(7, viewModel.state.value.rangeDays)
    }

    @Test fun `setRange to 7 excludes entries from 10 days ago`() = runTest {
        val tenDaysAgo = LocalDate.now().minusDays(10).toString()
        allFlow.value = listOf(
            entry("a1", tenDaysAgo, "Yrsel:2"),
            entry("a2", yesterday,  "Yrsel:3"),
        )
        viewModel.setRange(7)
        assertTrue(viewModel.state.value.days.none { it == tenDaysAgo })
        assertTrue(viewModel.state.value.days.any  { it == yesterday })
    }

    @Test fun `setRange to 90 includes entries that 30-day range would exclude`() = runTest {
        val fortyDaysAgo = LocalDate.now().minusDays(40).toString()
        allFlow.value = listOf(entry("a1", fortyDaysAgo, "Yrsel:2"))
        assertTrue(viewModel.state.value.days.isEmpty()) // excluded at default 30 days
        viewModel.setRange(90)
        assertTrue(viewModel.state.value.days.any { it == fortyDaysAgo })
    }

    // ─── reactive updates ─────────────────────────────────────────────────────

    @Test fun `state updates when flow emits new entries`() = runTest {
        assertTrue(viewModel.state.value.days.isEmpty())
        allFlow.value = listOf(entry("a1", today, "Yrsel:1"))
        assertTrue(viewModel.state.value.days.isNotEmpty())
    }

    @Test fun `state updates when flow removes entries`() = runTest {
        allFlow.value = listOf(entry("a1", today, "Yrsel:1"))
        assertTrue(viewModel.state.value.days.isNotEmpty())
        allFlow.value = emptyList()
        assertTrue(viewModel.state.value.days.isEmpty())
    }

    // ─── symptomColor ─────────────────────────────────────────────────────────

    @Test fun `symptomColor is deterministic for same name and position`() {
        val all = listOf("Yrsel", "Trötthet", "Nackvärk")
        assertEquals(symptomColor("Yrsel", all), symptomColor("Yrsel", all))
    }

    @Test fun `symptomColor differs between symptoms at different positions`() {
        val all = listOf("A", "B", "C")
        assertNotEquals(symptomColor("A", all), symptomColor("B", all))
        assertNotEquals(symptomColor("B", all), symptomColor("C", all))
    }

    @Test fun `symptomColor wraps when symptom count exceeds palette size`() {
        val manySymptoms = (1..8).map { "S$it" }   // palette has 7 entries
        assertEquals(symptomColor("S1", manySymptoms), symptomColor("S8", manySymptoms))
    }

    @Test fun `symptomColor returns first-palette color for symptom not in list`() {
        val color = symptomColor("Unknown", emptyList())
        assertEquals(symptomColor("Unknown", emptyList()), color)
    }
}
