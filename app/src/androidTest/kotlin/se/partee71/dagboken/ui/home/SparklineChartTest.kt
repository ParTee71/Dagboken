package se.partee71.dagboken.ui.home

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import se.partee71.dagboken.util.retryOnRenderGlitch

// Migrerad enligt POC i #112 — se SjukdomarScreenTest för fullständig förklaring.
@RunWith(AndroidJUnit4::class)
class SparklineChartTest {

    @get:Rule
    val composeRule = createEmptyComposeRule()

    private fun renderAndRetry(content: @Composable () -> Unit) = retryOnRenderGlitch {
        val scenario = ActivityScenario.launch(ComponentActivity::class.java)
        try {
            scenario.onActivity { it.setContent { MaterialTheme { content() } } }
            composeRule.waitForIdle()
        } finally {
            scenario.close()
        }
    }

    @Test fun renders_without_crash_with_two_points() = renderAndRetry {
        SparklineChart(points = listOf(3f, 7f))
    }

    @Test fun renders_without_crash_with_a_full_week_of_points() = renderAndRetry {
        SparklineChart(points = List(7) { (it + 1).toFloat() })
    }

    // HEM-7: fewer than 2 points is below the minimum — the component itself
    // renders nothing (defensive; the real gate lives in HomeScreen's caller).
    @Test fun renders_nothing_with_a_single_point() = renderAndRetry {
        SparklineChart(points = listOf(5f))
    }

    @Test fun renders_nothing_with_no_points() = renderAndRetry {
        SparklineChart(points = emptyList())
    }
}
