package se.partee71.dagboken.ui.health

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import se.partee71.dagboken.domain.model.BloodPressure
import se.partee71.dagboken.domain.model.HealthData
import se.partee71.dagboken.domain.model.SleepFlag
import se.partee71.dagboken.domain.model.SleepQuality
import se.partee71.dagboken.domain.model.SleepQualityComponent
import se.partee71.dagboken.domain.model.SleepQualityKind
import se.partee71.dagboken.domain.model.SleepStages
import se.partee71.dagboken.util.retryOnRenderGlitch
import java.time.Duration

@RunWith(AndroidJUnit4::class)
class HealthScreenTest {

    @get:Rule
    val composeRule = createEmptyComposeRule()

    private fun render(content: @Composable () -> Unit, assertions: () -> Unit) =
        retryOnRenderGlitch {
            val scenario = ActivityScenario.launch(ComponentActivity::class.java)
            try {
                scenario.onActivity { it.setContent { MaterialTheme { content() } } }
                assertions()
            } finally {
                scenario.close()
            }
        }

    @Test fun data_state_shows_steps_and_heart_rate() = render(
        content = {
            HealthScreenContent(
                state = HealthUiState.Data(
                    HealthData(steps = 4200, heartRateAvg = 72, sleepDuration = Duration.ofMinutes(450)),
                ),
                onBack = {}, onGrantPermissions = {}, onRetry = {}, onOpenHealthConnect = {},
            )
        },
        assertions = {
            composeRule.onNodeWithText("Steg idag").assertIsDisplayed()
            composeRule.onNodeWithText("4200").assertIsDisplayed()
            composeRule.onNodeWithText("72 bpm").assertIsDisplayed()
            composeRule.onNodeWithText("7 h 30 min").assertIsDisplayed()
        },
    )

    // ─── HLS-8: sömnstadier, träning och vitalvärden ─────────────────────────

    @Test fun data_state_shows_sleep_stages_when_present() = render(
        content = {
            HealthScreenContent(
                state = HealthUiState.Data(
                    HealthData(
                        sleepDuration = Duration.ofMinutes(450),
                        sleepStages = SleepStages(
                            deep  = Duration.ofMinutes(75),
                            rem   = Duration.ofMinutes(90),
                            light = Duration.ofMinutes(273),
                            awake = Duration.ofMinutes(12),
                        ),
                    ),
                ),
                onBack = {}, onGrantPermissions = {}, onRetry = {}, onOpenHealthConnect = {},
            )
        },
        assertions = {
            // Skärmen är längre än vyn — skrolla fram varje rad innan den mäts.
            composeRule.onNodeWithText("Djupsömn").performScrollTo().assertIsDisplayed()
            composeRule.onNodeWithText("1 h 15 min").assertIsDisplayed()
            composeRule.onNodeWithText("REM-sömn").performScrollTo().assertIsDisplayed()
            composeRule.onNodeWithText("1 h 30 min").assertIsDisplayed()
            composeRule.onNodeWithText("Vaken").performScrollTo().assertIsDisplayed()
            composeRule.onNodeWithText("12 min").assertIsDisplayed()
        },
    )

    @Test fun data_state_hides_sleep_stage_rows_when_the_night_has_no_stages() = render(
        content = {
            HealthScreenContent(
                state = HealthUiState.Data(HealthData(sleepDuration = Duration.ofMinutes(450))),
                onBack = {}, onGrantPermissions = {}, onRetry = {}, onOpenHealthConnect = {},
            )
        },
        assertions = {
            composeRule.onNodeWithText("Total sömn").assertIsDisplayed()
            composeRule.onNodeWithText("Djupsömn").assertDoesNotExist()
        },
    )

    @Test fun data_state_shows_exercise_and_vitals() = render(
        content = {
            HealthScreenContent(
                state = HealthUiState.Data(
                    HealthData(
                        exerciseSessions    = 2,
                        exerciseDuration    = Duration.ofMinutes(65),
                        activeEnergyKcal    = 431.4,
                        distanceMeters      = 5234.0,
                        oxygenSaturationAvg = 96.4,
                        bloodPressure       = BloodPressure(systolic = 118, diastolic = 76),
                    ),
                ),
                onBack = {}, onGrantPermissions = {}, onRetry = {}, onOpenHealthConnect = {},
            )
        },
        assertions = {
            composeRule.onNodeWithText("Träningspass (2 st)").performScrollTo().assertIsDisplayed()
            composeRule.onNodeWithText("1 h 5 min").assertIsDisplayed()
            composeRule.onNodeWithText("431 kcal").performScrollTo().assertIsDisplayed()
            composeRule.onNodeWithText("${formatKilometers(5234.0)} km").performScrollTo().assertIsDisplayed()
            composeRule.onNodeWithText("96 %").performScrollTo().assertIsDisplayed()
            composeRule.onNodeWithText("118/76 mmHg").performScrollTo().assertIsDisplayed()
        },
    )

    @Test fun missing_optional_values_render_as_a_dash_instead_of_failing() = render(
        content = {
            HealthScreenContent(
                state = HealthUiState.Data(HealthData(steps = 4200)),
                onBack = {}, onGrantPermissions = {}, onRetry = {}, onOpenHealthConnect = {},
            )
        },
        assertions = {
            // Nekad valfri behörighet (HLS-8) ska visa "—", inte fälla skärmen.
            // Pulsraden ligger ovanför vikningen och visar "—" direkt.
            composeRule.onAllNodesWithText("—").onFirst().assertIsDisplayed()
            composeRule.onNodeWithText("Blodtryck (senaste mätningen)")
                .performScrollTo()
                .assertIsDisplayed()
        },
    )

    // ─── HLS-10: sömnkvalitet ────────────────────────────────────────────────

    @Test fun sleep_quality_score_and_breakdown_are_shown() = render(
        content = {
            HealthScreenContent(
                state = HealthUiState.Data(
                    health = HealthData(sleepDuration = Duration.ofMinutes(450)),
                    sleepQuality = SleepQuality(
                        score = 82,
                        components = listOf(
                            SleepQualityComponent(SleepQualityKind.DURATION, score = 100, weight = 25, measured = 7.6),
                            SleepQualityComponent(SleepQualityKind.REGULARITY, score = 60, weight = 20, measured = 54.0),
                        ),
                    ),
                ),
                onBack = {}, onGrantPermissions = {}, onRetry = {}, onOpenHealthConnect = {},
            )
        },
        assertions = {
            composeRule.onNodeWithText("Sömnkvalitet (0–100)").performScrollTo().assertIsDisplayed()
            composeRule.onNodeWithText("82").assertIsDisplayed()
            // Delkomponenterna ligger bakom en Foldout och syns först när den fällts ut.
            composeRule.onNodeWithText("Sömnlängd").assertDoesNotExist()
            composeRule.onNodeWithText("Så räknas poängen").performScrollTo().performClick()
            composeRule.onNodeWithText("Sömnlängd").performScrollTo().assertIsDisplayed()
            composeRule.onNodeWithText("Regelbundenhet").performScrollTo().assertIsDisplayed()
        },
    )

    @Test fun sleep_quality_flags_are_shown_next_to_the_score() = render(
        content = {
            HealthScreenContent(
                state = HealthUiState.Data(
                    health = HealthData(sleepDuration = Duration.ofMinutes(450)),
                    sleepQuality = SleepQuality(
                        score = 74,
                        components = listOf(
                            SleepQualityComponent(SleepQualityKind.DURATION, score = 100, weight = 25, measured = 7.6),
                        ),
                        flags = listOf(SleepFlag.LOW_OXYGEN_SATURATION),
                    ),
                ),
                onBack = {}, onGrantPermissions = {}, onRetry = {}, onOpenHealthConnect = {},
            )
        },
        assertions = {
            composeRule.onNodeWithText("Låg syremättnad under natten — ta upp det med vården")
                .performScrollTo()
                .assertIsDisplayed()
        },
    )

    @Test fun without_a_birth_year_the_screen_asks_for_it_instead_of_a_score() = render(
        content = {
            HealthScreenContent(
                state = HealthUiState.Data(
                    health = HealthData(sleepDuration = Duration.ofMinutes(450)),
                    birthYearMissing = true,
                ),
                onBack = {}, onGrantPermissions = {}, onRetry = {}, onOpenHealthConnect = {},
            )
        },
        assertions = {
            composeRule.onNodeWithText("Ange födelseår under Hantera → Profil för att få sömnkvalitet")
                .performScrollTo()
                .assertIsDisplayed()
            composeRule.onNodeWithText("Sömnkvalitet (0–100)").assertDoesNotExist()
        },
    )

    @Test fun permissions_required_shows_grant_button_and_invokes_callback() {
        var granted = false
        render(
            content = {
                HealthScreenContent(
                    state = HealthUiState.PermissionsRequired,
                    onBack = {}, onGrantPermissions = { granted = true }, onRetry = {}, onOpenHealthConnect = {},
                )
            },
            assertions = {
                composeRule.onNodeWithText("Ge åtkomst").assertIsDisplayed().performClick()
                assert(granted) { "Expected onGrantPermissions to be invoked" }
            },
        )
    }

    @Test fun unavailable_shows_missing_message_and_open_button() {
        var opened = false
        render(
            content = {
                HealthScreenContent(
                    state = HealthUiState.Unavailable(updateRequired = false),
                    onBack = {}, onGrantPermissions = {}, onRetry = {}, onOpenHealthConnect = { opened = true },
                )
            },
            assertions = {
                composeRule.onNodeWithText("Health Connect saknas").assertIsDisplayed()
                composeRule.onNodeWithText("Öppna Health Connect").assertIsDisplayed().performClick()
                assert(opened) { "Expected onOpenHealthConnect to be invoked" }
            },
        )
    }

    @Test fun error_state_shows_retry_and_invokes_callback() {
        var retried = false
        render(
            content = {
                HealthScreenContent(
                    state = HealthUiState.Error,
                    onBack = {}, onGrantPermissions = {}, onRetry = { retried = true }, onOpenHealthConnect = {},
                )
            },
            assertions = {
                composeRule.onNodeWithText("Kunde inte läsa hälsodata").assertIsDisplayed()
                composeRule.onNodeWithText("Försök igen").assertIsDisplayed().performClick()
                assert(retried) { "Expected onRetry to be invoked" }
            },
        )
    }
}
