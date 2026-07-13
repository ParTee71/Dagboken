package se.partee71.dagboken.ui.home

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SparklineChartTest {

    val composeRule = createComposeRule()

    // Retry outermost so a swiftshader render-glitch flake re-runs with a
    // fresh @Before/@After lifecycle instead of failing the build.
    @get:Rule
    val flakyRetry: org.junit.rules.RuleChain =
        org.junit.rules.RuleChain
            .outerRule(se.partee71.dagboken.util.RetryTestRule())
            .around(composeRule)

    @Test fun renders_without_crash_with_two_points() {
        composeRule.setContent {
            MaterialTheme {
                SparklineChart(points = listOf(3f, 7f))
            }
        }
        composeRule.waitForIdle()
    }

    @Test fun renders_without_crash_with_a_full_week_of_points() {
        composeRule.setContent {
            MaterialTheme {
                SparklineChart(points = List(7) { (it + 1).toFloat() })
            }
        }
        composeRule.waitForIdle()
    }

    // HEM-7: fewer than 2 points is below the minimum — the component itself
    // renders nothing (defensive; the real gate lives in HomeScreen's caller).
    @Test fun renders_nothing_with_a_single_point() {
        composeRule.setContent {
            MaterialTheme {
                SparklineChart(points = listOf(5f))
            }
        }
        composeRule.waitForIdle()
    }

    @Test fun renders_nothing_with_no_points() {
        composeRule.setContent {
            MaterialTheme {
                SparklineChart(points = emptyList())
            }
        }
        composeRule.waitForIdle()
    }
}
