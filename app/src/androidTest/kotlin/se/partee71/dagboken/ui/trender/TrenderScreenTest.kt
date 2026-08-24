package se.partee71.dagboken.ui.trender

import se.partee71.dagboken.data.repository.NoteRepository
import android.content.Context
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.lifecycle.viewModelScope
import androidx.room.Room
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import se.partee71.dagboken.data.datastore.PreferencesRepository
import se.partee71.dagboken.data.repository.AktiviteterRepository
import se.partee71.dagboken.data.repository.HealthAvailability
import se.partee71.dagboken.data.repository.HealthConnectRepository
import se.partee71.dagboken.data.room.AppDatabase
import se.partee71.dagboken.di.dagbokenJson
import se.partee71.dagboken.domain.model.Aktivitet
import se.partee71.dagboken.domain.model.DailyHealth
import se.partee71.dagboken.domain.model.HealthData
import se.partee71.dagboken.domain.model.SleepStages
import se.partee71.dagboken.domain.model.HealthHistory
import se.partee71.dagboken.domain.model.NightlySleepMeasurements
import se.partee71.dagboken.domain.model.WeeklyHealth
import se.partee71.dagboken.util.retryOnRenderGlitch
import java.time.Duration
import java.time.LocalDate

// Migrerad enligt POC i #112 — se SjukdomarScreenTest för fullständig förklaring.
@RunWith(AndroidJUnit4::class)
class TrenderScreenTest {

    @get:Rule
    val composeRule = createEmptyComposeRule()

    private lateinit var db: AppDatabase
    private lateinit var repo: AktiviteterRepository
    private lateinit var vm: TrenderViewModel
    private lateinit var scenario: ActivityScenario<ComponentActivity>

    private val today get() = LocalDate.now().toString()

    private fun setUp(healthRepo: HealthConnectRepository = FakeHealthRepo()) {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(ctx, AppDatabase::class.java)
                 .allowMainThreadQueries().build()
        repo = AktiviteterRepository(db.aktivitetDao(), NoteRepository(db.noteDao()))
        vm = TrenderViewModel(repo, healthRepo, PreferencesRepository(ctx, dagbokenJson()))
        scenario = ActivityScenario.launch(ComponentActivity::class.java)
    }

    private fun tearDown() {
        // Stop the ViewModel's Room-flow collector before closing the DB,
        // otherwise its viewModelScope coroutine queries the closed in-memory
        // DB and throws "attempt to re-open an already-closed SQLiteDatabase".
        vm.viewModelScope.cancel()
        db.close()
        scenario.close()
    }

    private fun setContent() {
        scenario.onActivity {
            it.setContent { MaterialTheme { TrenderScreen(onBack = {}, vm = vm) } }
        }
    }

    /**
     * Diagramkorten är stängda som standard (TRD-14) — allt utom titeln ligger bakom en
     * utfällning. Testerna nedan fäller ut via ViewModel:en i stället för via ett klick,
     * så att de testar sitt eget beteende och inte utfällningsgesten; den har egna tester.
     */
    private fun expand(vararg sections: TrenderSection) {
        composeRule.runOnUiThread { sections.forEach { vm.setExpanded(it, true) } }
    }

    @Test fun energy_slot_section_shows_its_title() = retryOnRenderGlitch {
        setUp()
        try {
            setContent()
            composeRule.onNodeWithText("Energi per tillfälle").assertIsDisplayed()
        } finally {
            tearDown()
        }
    }

    @Test fun energy_daily_section_renders_with_logged_screenings() = retryOnRenderGlitch {
        setUp()
        try {
            runBlocking {
                repo.save(
                    Aktivitet(
                        id = "s1", timestamp = "x", datum = today, tid = "09:00",
                        aktivitet = "Lunch", energy = 6, stress = 3, somatiska = 0,
                        symptom = "", type = "screening",
                    ),
                )
            }
            setContent()
            composeRule.onNodeWithText("Energi (dag)").assertIsDisplayed()
        } finally {
            tearDown()
        }
    }

    @Test fun energy_slot_selector_does_not_list_discovered_symptoms() = retryOnRenderGlitch {
        setUp()
        try {
            runBlocking {
                repo.save(
                    Aktivitet(
                        id = "a1", timestamp = "x", datum = today, tid = "09:00",
                        aktivitet = "Promenad", energy = 5, stress = 3, somatiska = 0,
                        symptom = "Yrsel:4", type = "aktivitet",
                    ),
                )
            }
            expand(TrenderSection.ENERGI_TILLFALLE)
            setContent()
            composeRule.onNodeWithTag("trender_series_selector_energy").performScrollTo().performClick()
            composeRule.waitUntil(20_000) {
                composeRule.onAllNodes(hasText("Energi Lunch")).fetchSemanticsNodes().isNotEmpty()
            }
            composeRule.onNodeWithText("Energi Lunch").assertIsDisplayed()
            composeRule.onNodeWithText("Yrsel").assertDoesNotExist()
        } finally {
            tearDown()
        }
    }

    @Test fun symptom_selector_lists_discovered_symptom_series() = retryOnRenderGlitch {
        setUp()
        try {
            runBlocking {
                repo.save(
                    Aktivitet(
                        id = "a1", timestamp = "x", datum = today, tid = "09:00",
                        aktivitet = "Promenad", energy = 5, stress = 3, somatiska = 0,
                        symptom = "Yrsel:4", type = "aktivitet",
                    ),
                )
            }
            expand(TrenderSection.SYMPTOM)
            setContent()
            // Symptom är den sista sektionen i den scrollbara kolumnen — scrolla in
            // knappen innan klick, annars kan touch-injektionen missa den.
            composeRule.onNodeWithTag("trender_series_selector_symptom").performScrollTo().performClick()
            composeRule.waitUntil(20_000) {
                composeRule.onAllNodes(hasText("Yrsel")).fetchSemanticsNodes().isNotEmpty()
            }
            composeRule.onNodeWithText("Yrsel").performScrollTo().assertIsDisplayed()
        } finally {
            tearDown()
        }
    }

    @Test fun selecting_a_symptom_series_adds_it_to_the_legend() = retryOnRenderGlitch {
        setUp()
        try {
            runBlocking {
                repo.save(
                    Aktivitet(
                        id = "a1", timestamp = "x", datum = today, tid = "09:00",
                        aktivitet = "Promenad", energy = 5, stress = 3, somatiska = 0,
                        symptom = "Yrsel:4", type = "aktivitet",
                    ),
                )
            }
            expand(TrenderSection.SYMPTOM)
            setContent()
            composeRule.runOnUiThread { vm.toggleSeries("Yrsel") }
            composeRule.waitUntil(20_000) {
                composeRule.onAllNodes(hasTestTag("trender_legend_item_Yrsel")).fetchSemanticsNodes().isNotEmpty()
            }
            // Legenden ligger under diagrammet i en scrollbar kolumn — scrolla in den i
            // vyn innan assertion. Egen testTag eftersom väljarknappens etikett också blir
            // exakt "Yrsel" när det är den enda valda serien i symptomkategorin.
            composeRule.onNodeWithTag("trender_legend_item_Yrsel").performScrollTo().assertIsDisplayed()
        } finally {
            tearDown()
        }
    }

    // ─── Trendlinje per dataserie — TRD-13, #170 ──────────────────────────────

    @Test fun selecting_a_series_with_at_least_two_points_shows_the_trend_legend() = retryOnRenderGlitch {
        setUp()
        try {
            runBlocking {
                repo.save(
                    Aktivitet(
                        id = "a1", timestamp = "x", datum = today, tid = "09:00",
                        aktivitet = "Promenad", energy = 5, stress = 3, somatiska = 0,
                        symptom = "Yrsel:4", type = "aktivitet",
                    ),
                )
                repo.save(
                    Aktivitet(
                        id = "a2", timestamp = "x", datum = LocalDate.now().minusDays(1).toString(), tid = "09:00",
                        aktivitet = "Promenad", energy = 5, stress = 3, somatiska = 0,
                        symptom = "Yrsel:6", type = "aktivitet",
                    ),
                )
            }
            expand(TrenderSection.SYMPTOM)
            setContent()
            composeRule.runOnUiThread { vm.toggleSeries("Yrsel") }
            composeRule.waitUntil(20_000) {
                composeRule.onAllNodes(hasTestTag("trender_legend_item_trend")).fetchSemanticsNodes().isNotEmpty()
            }
            composeRule.onNodeWithTag("trender_legend_item_trend").performScrollTo().assertIsDisplayed()
        } finally {
            tearDown()
        }
    }

    @Test fun range_selector_switches_the_selected_diagrams_range() = retryOnRenderGlitch {
        setUp()
        try {
            expand(TrenderSection.SYMPTOM)
            setContent()
            composeRule.onNodeWithTag("trender_range_selector_symptom").performScrollTo().performClick()
            composeRule.waitUntil(20_000) {
                composeRule.onAllNodes(hasText("7 dagar")).fetchSemanticsNodes().isNotEmpty()
            }
            composeRule.onNodeWithText("7 dagar").performClick()
            composeRule.waitUntil(20_000) {
                vm.state.value.ranges.getValue(TrenderSection.SYMPTOM) == TrenderRange.SEVEN_DAYS
            }
        } finally {
            tearDown()
        }
    }

    @Test fun range_selector_offers_an_all_time_option() = retryOnRenderGlitch {
        setUp()
        try {
            expand(TrenderSection.SYMPTOM)
            setContent()
            composeRule.onNodeWithTag("trender_range_selector_symptom").performScrollTo().performClick()
            composeRule.waitUntil(20_000) {
                composeRule.onAllNodes(hasText("Allt")).fetchSemanticsNodes().isNotEmpty()
            }
            composeRule.onNodeWithText("Allt").performClick()
            composeRule.waitUntil(20_000) {
                vm.state.value.ranges.getValue(TrenderSection.SYMPTOM) == TrenderRange.ALL
            }
        } finally {
            tearDown()
        }
    }

    // ─── Periodväljare per diagram, övre högra hörnet — #149 ──────────────────

    @Test fun every_diagram_has_its_own_period_selector() = retryOnRenderGlitch {
        setUp()
        try {
            expand(*TrenderSection.entries.toTypedArray())
            setContent()
            listOf(
                "trender_range_selector_energi_dag",
                "trender_range_selector_energi_tillfalle",
                "trender_range_selector_stress_belastning",
                "trender_range_selector_symptom",
                "trender_range_selector_steg",
                "trender_range_selector_vilopuls",
            ).forEach { tag ->
                composeRule.onNodeWithTag(tag).performScrollTo().assertIsDisplayed()
            }
        } finally {
            tearDown()
        }
    }

    @Test fun changing_one_diagrams_period_leaves_another_diagrams_period_label_unchanged() = retryOnRenderGlitch {
        setUp()
        try {
            expand(TrenderSection.SYMPTOM, TrenderSection.STRESS_BELASTNING)
            setContent()
            composeRule.onNodeWithTag("trender_range_selector_symptom").performScrollTo().performClick()
            composeRule.waitUntil(20_000) {
                composeRule.onAllNodes(hasText("7 dagar")).fetchSemanticsNodes().isNotEmpty()
            }
            composeRule.onNodeWithText("7 dagar").performClick()
            composeRule.waitUntil(20_000) {
                vm.state.value.ranges.getValue(TrenderSection.SYMPTOM) == TrenderRange.SEVEN_DAYS
            }
            // Stress & belastning-diagrammets period ska fortfarande vara oförändrad (Månad).
            assertEquals(TrenderRange.MONTH, vm.state.value.ranges.getValue(TrenderSection.STRESS_BELASTNING))
            composeRule.onNodeWithTag("trender_range_selector_stress_belastning").performScrollTo()
                .assertTextEquals("Månad")
        } finally {
            tearDown()
        }
    }

    @Test fun period_selector_is_positioned_to_the_right_of_the_diagram_title() = retryOnRenderGlitch {
        setUp()
        try {
            expand(TrenderSection.SYMPTOM)
            setContent()
            val titleLeft = composeRule.onNodeWithText("Symptom", useUnmergedTree = true)
                .fetchSemanticsNode().boundsInRoot.left
            val selectorLeft = composeRule.onNodeWithTag("trender_range_selector_symptom").performScrollTo()
                .fetchSemanticsNode().boundsInRoot.left
            assertTrue(
                "Förväntade periodväljaren till höger om titeln ($selectorLeft > $titleLeft)",
                selectorLeft > titleLeft,
            )
        } finally {
            tearDown()
        }
    }

    @Test fun empty_state_shown_in_energy_slot_section_when_no_series_selected() = retryOnRenderGlitch {
        setUp()
        try {
            composeRule.runOnUiThread { vm.toggleSeries("Energi Frukost") }
            expand(TrenderSection.ENERGI_TILLFALLE, TrenderSection.STRESS_BELASTNING, TrenderSection.SYMPTOM)
            setContent()
            // Stress- och symptomdiagrammen har heller inget valt som standard, så
            // samma tomlägestext kan visas i flera sektioner samtidigt.
            composeRule.waitUntil(20_000) {
                composeRule.onAllNodesWithText("Välj minst en dataserie").fetchSemanticsNodes().isNotEmpty()
            }
        } finally {
            tearDown()
        }
    }

    // ─── Ihopfällbara diagramkort — TRD-14/NFR-18, #189 ───────────────────────

    @Test fun all_diagram_cards_are_collapsed_when_the_screen_opens() = retryOnRenderGlitch {
        setUp()
        try {
            setContent()
            // Titlarna syns...
            composeRule.onNodeWithText("Symptom").performScrollTo().assertIsDisplayed()
            composeRule.onNodeWithText("Steg").performScrollTo().assertIsDisplayed()
            // ...men allt bakom utfällningen finns inte ens i trädet.
            composeRule.onNodeWithTag("trender_range_selector_symptom").assertDoesNotExist()
            composeRule.onNodeWithTag("trender_series_selector_symptom").assertDoesNotExist()
            composeRule.onNodeWithTag("trender_range_selector_steg").assertDoesNotExist()
        } finally {
            tearDown()
        }
    }

    @Test fun tapping_a_card_title_expands_only_that_card() = retryOnRenderGlitch {
        setUp()
        try {
            setContent()
            composeRule.onNodeWithText("Symptom").performScrollTo().performClick()
            composeRule.waitUntil(20_000) {
                vm.state.value.expanded.getValue(TrenderSection.SYMPTOM)
            }
            assertTrue(
                "Övriga diagramkort ska förbli stängda",
                TrenderSection.entries.filter { it != TrenderSection.SYMPTOM }
                    .all { vm.state.value.expanded.getValue(it) == false },
            )
            composeRule.onNodeWithTag("trender_range_selector_symptom").performScrollTo().assertIsDisplayed()
        } finally {
            tearDown()
        }
    }

    @Test fun tapping_an_expanded_card_title_collapses_it_again() = retryOnRenderGlitch {
        setUp()
        try {
            expand(TrenderSection.SYMPTOM)
            setContent()
            composeRule.onNodeWithText("Symptom").performScrollTo().performClick()
            composeRule.waitUntil(20_000) {
                vm.state.value.expanded.getValue(TrenderSection.SYMPTOM) == false
            }
            composeRule.onNodeWithTag("trender_range_selector_symptom").assertDoesNotExist()
        } finally {
            tearDown()
        }
    }

    /** Diagramkort är sektionskort (NFR-15) — aldrig långtryck, kontextmeny eller svep. */
    @Test fun a_diagram_card_has_no_long_press_action() = retryOnRenderGlitch {
        setUp()
        try {
            setContent()
            val header = composeRule.onNodeWithText("Steg").performScrollTo().fetchSemanticsNode()
            assertTrue(
                "Titelraden ska växla utfällning vid tryck",
                header.config.contains(SemanticsActions.OnClick),
            )
            assertFalse(
                "Ett sektionskort ska inte ha någon långtrycksåtgärd (NFR-15)",
                header.config.contains(SemanticsActions.OnLongClick),
            )
        } finally {
            tearDown()
        }
    }

    @Test fun expanded_state_survives_a_recomposition_from_scratch() = retryOnRenderGlitch {
        setUp()
        try {
            expand(TrenderSection.SYMPTOM)
            setContent()
            composeRule.onNodeWithTag("trender_range_selector_symptom").performScrollTo().assertIsDisplayed()
            // Tillståndet ligger i ViewModel:en, inte i kompositionen — en ny komposition
            // (t.ex. efter en rotation) ska därför visa samma utfällda kort.
            setContent()
            composeRule.onNodeWithTag("trender_range_selector_symptom").performScrollTo().assertIsDisplayed()
        } finally {
            tearDown()
        }
    }

    // ─── Hälsodiagram (Health Connect) — TRD-11/TRD-15, #191 ─────────────────

    @Test fun every_health_section_shows_its_title() = retryOnRenderGlitch {
        setUp()
        try {
            setContent()
            listOf(
                "Steg", "Vilopuls", "Sömn", "Sömnstadier", "Sömnkvalitet",
                "Träning", "Aktiva kalorier", "Sträcka", "Syremättnad", "Blodtryck", "Jämför",
            ).forEach { title ->
                composeRule.onNodeWithText(title).performScrollTo().assertIsDisplayed()
            }
        } finally {
            tearDown()
        }
    }

    @Test fun health_sections_show_empty_state_when_health_connect_not_connected() = retryOnRenderGlitch {
        setUp(FakeHealthRepo())
        try {
            expand(TrenderSection.STEG, TrenderSection.SOMN)
            setContent()
            composeRule.onNodeWithText("Ingen stegdata för vald period").performScrollTo().assertIsDisplayed()
            composeRule.onNodeWithText("Ingen sömndata för vald period").performScrollTo().assertIsDisplayed()
        } finally {
            tearDown()
        }
    }

    @Test fun step_section_renders_when_health_connect_data_available() = retryOnRenderGlitch {
        val today = LocalDate.now()
        setUp(
            FakeHealthRepo(
                history = HealthHistory(
                    listOf(
                        DailyHealth(today.minusDays(1), steps = 4000, restingHeartRate = 55),
                        DailyHealth(today, steps = 9000, restingHeartRate = 60),
                    ),
                ),
            ),
        )
        try {
            expand(TrenderSection.STEG)
            setContent()
            composeRule.waitUntil(20_000) {
                composeRule.onAllNodes(hasText("Ingen stegdata för vald period")).fetchSemanticsNodes().isEmpty()
            }
            composeRule.onNodeWithText("Steg").performScrollTo().assertIsDisplayed()
            composeRule.onNodeWithTag("trender_range_selector_steg").performScrollTo().assertIsDisplayed()
        } finally {
            tearDown()
        }
    }

    @Test fun the_resting_hr_section_offers_both_heart_rate_series() = retryOnRenderGlitch {
        val today = LocalDate.now()
        setUp(
            FakeHealthRepo(
                history = HealthHistory(
                    listOf(
                        DailyHealth(today.minusDays(1), restingHeartRate = 55, heartRateAvg = 70),
                        DailyHealth(today, restingHeartRate = 60, heartRateAvg = 74),
                    ),
                ),
            ),
        )
        try {
            expand(TrenderSection.VILOPULS)
            setContent()
            composeRule.onNodeWithTag("trender_series_selector_vilopuls").performScrollTo().performClick()
            composeRule.waitUntil(20_000) {
                composeRule.onAllNodes(hasText("Dygnssnitt")).fetchSemanticsNodes().isNotEmpty()
            }
            composeRule.onNodeWithText("Dygnssnitt").assertIsDisplayed()
        } finally {
            tearDown()
        }
    }

    // ─── Jämförelsediagram — TRD-17, #194 ────────────────────────────────────

    @Test fun the_comparison_section_asks_for_two_series_before_it_draws_anything() = retryOnRenderGlitch {
        setUp()
        try {
            expand(TrenderSection.JAMFOR)
            setContent()
            composeRule.onNodeWithText("Välj minst två serier att jämföra")
                .performScrollTo().assertIsDisplayed()
        } finally {
            tearDown()
        }
    }

    @Test fun the_comparison_legend_shows_each_series_real_range_with_its_unit() = retryOnRenderGlitch {
        val today = LocalDate.now()
        setUp(
            FakeHealthRepo(
                history = HealthHistory(
                    listOf(
                        DailyHealth(today.minusDays(1), steps = 4000, restingHeartRate = 50),
                        DailyHealth(today, steps = 12000, restingHeartRate = 60),
                    ),
                ),
            ),
        )
        try {
            expand(TrenderSection.JAMFOR)
            setContent()
            composeRule.runOnUiThread {
                vm.toggleHealthSeries(TrenderSection.JAMFOR, "Steg")
                vm.toggleHealthSeries(TrenderSection.JAMFOR, "Vilopuls")
            }
            composeRule.waitUntil(20_000) {
                composeRule.onAllNodes(hasTestTag("trender_compare_legend_Steg"))
                    .fetchSemanticsNodes().isNotEmpty()
            }
            // Y-axeln visar index, så legenden måste bära de verkliga värdena (TRD-17).
            composeRule.onNodeWithText("Steg 4000–12000 steg").performScrollTo().assertIsDisplayed()
            composeRule.onNodeWithText("Vilopuls 50–60 bpm").performScrollTo().assertIsDisplayed()
        } finally {
            tearDown()
        }
    }

    @Test fun the_sleep_stages_section_stacks_the_stages_per_night() = retryOnRenderGlitch {
        val today = LocalDate.now()
        setUp(
            FakeHealthRepo(
                history = HealthHistory(
                    listOf(
                        DailyHealth(
                            today.minusDays(1),
                            sleepStages = SleepStages(
                                deep = Duration.ofMinutes(90),
                                rem = Duration.ofMinutes(90),
                                light = Duration.ofMinutes(240),
                                awake = Duration.ofMinutes(30),
                            ),
                        ),
                        DailyHealth(
                            today,
                            sleepStages = SleepStages(
                                deep = Duration.ofMinutes(60),
                                rem = Duration.ofMinutes(120),
                                light = Duration.ofMinutes(270),
                                awake = Duration.ofMinutes(30),
                            ),
                        ),
                    ),
                ),
            ),
        )
        try {
            expand(TrenderSection.SOMNSTADIER)
            setContent()
            composeRule.waitUntil(20_000) {
                composeRule.onAllNodes(hasText("Inga sömnstadier för vald period"))
                    .fetchSemanticsNodes().isEmpty()
            }
            // Alla fyra stadier visas alltid — sammansättningen är hela poängen (TRD-16).
            listOf("Djup", "REM", "Lätt", "Vaken").forEach { stage ->
                composeRule.onNodeWithTag("trender_stage_legend_item_$stage")
                    .performScrollTo().assertIsDisplayed()
            }
        } finally {
            tearDown()
        }
    }

    @Test fun the_sleep_section_offers_its_stage_series() = retryOnRenderGlitch {
        val today = LocalDate.now()
        setUp(
            FakeHealthRepo(
                history = HealthHistory(
                    listOf(
                        DailyHealth(today.minusDays(1), sleepDuration = Duration.ofHours(7)),
                        DailyHealth(today, sleepDuration = Duration.ofHours(8)),
                    ),
                ),
            ),
        )
        try {
            expand(TrenderSection.SOMN)
            setContent()
            composeRule.onNodeWithTag("trender_series_selector_somn").performScrollTo().performClick()
            composeRule.waitUntil(20_000) {
                composeRule.onAllNodes(hasText("Djup")).fetchSemanticsNodes().isNotEmpty()
            }
            composeRule.onNodeWithText("REM").assertIsDisplayed()
        } finally {
            tearDown()
        }
    }
}

private class FakeHealthRepo(
    private val weekly: WeeklyHealth? = null,
    private val history: HealthHistory? = null,
) : HealthConnectRepository {
    private val connected = weekly != null || history != null
    override val permissions: Set<String> = emptySet()
    override val requiredPermissions: Set<String> = emptySet()
    override fun availability() = if (connected) HealthAvailability.AVAILABLE else HealthAvailability.NOT_INSTALLED
    override suspend fun hasRequiredPermissions() = connected
    override suspend fun readToday() = HealthData()
    override suspend fun readWeeklyHealth() = weekly ?: WeeklyHealth()
    override suspend fun readHealthRange(days: Int) = weekly ?: WeeklyHealth()
    override suspend fun readHealthHistory(days: Int) = history ?: HealthHistory()
    override suspend fun readSleepMeasurements(nights: Int) = null
    override suspend fun readSleepMeasurementsHistory(nights: Int) = emptyList<NightlySleepMeasurements>()
}
