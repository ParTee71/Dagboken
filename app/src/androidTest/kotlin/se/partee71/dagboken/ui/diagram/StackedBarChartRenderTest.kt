package se.partee71.dagboken.ui.diagram

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import se.partee71.dagboken.util.retryOnRenderGlitch
import java.time.LocalDate

/**
 * Renderings- och a11y-test för det staplade stapeldiagrammet (TRD-16). Ett diagram är en
 * rityta utan text — beskrivningen är det enda TalkBack har att gå på, så den testas här.
 *
 * Migrerad enligt POC i #112 — se SjukdomarScreenTest för fullständig förklaring.
 */
@RunWith(AndroidJUnit4::class)
class StackedBarChartRenderTest {

    @get:Rule
    val composeRule = createEmptyComposeRule()

    private val mod = Modifier.fillMaxWidth().height(220.dp)

    private val segments = listOf(
        StackSegment("Djup", Color(0xFF4f46e5)),
        StackSegment("REM", Color(0xFFa78bfa)),
        StackSegment("Lätt", Color(0xFF93c5fd)),
        StackSegment("Vaken", Color(0xFFfbbf24)),
    )

    private fun dates(count: Int) =
        (count - 1 downTo 0).map { LocalDate.now().minusDays(it.toLong()).toString() }

    private fun renderAndRetry(content: @Composable () -> Unit, assertions: () -> Unit) =
        retryOnRenderGlitch {
            val scenario = ActivityScenario.launch(ComponentActivity::class.java)
            try {
                scenario.onActivity { it.setContent { MaterialTheme { content() } } }
                assertions()
            } finally {
                scenario.close()
            }
        }

    @Test fun `renders and describes the nights for a screen reader`() = renderAndRetry(
        content = {
            StackedBarChart(
                points = listOf(
                    StackedPoint(listOf(1.5f, 1.5f, 4f, 0.5f)),
                    StackedPoint(listOf(1f, 1.5f, 5f, 0.5f)),
                ),
                segments = segments,
                dates = dates(2),
                label = "Sömnstadier",
                minValue = 0f,
                maxValue = 10f,
                gridStep = 2f,
                modifier = mod,
            )
        },
        assertions = {
            // Antal staplar, lägsta och högsta total, dominerande stadium och trendriktning.
            composeRule.onNodeWithContentDescription(
                "Sömnstadier: 2 staplar, lägsta 7.5, högsta 8, mest Lätt, stigande trend",
            ).assertIsDisplayed()
        },
    )

    @Test fun `describes an empty period without inventing values`() = renderAndRetry(
        content = {
            StackedBarChart(
                points = emptyList(),
                segments = segments,
                label = "Sömnstadier",
                modifier = mod,
            )
        },
        assertions = {
            composeRule.onNodeWithContentDescription("Sömnstadier: inga värden").assertIsDisplayed()
        },
    )

    @Test fun `a night without any stage does not count as a bar`() = renderAndRetry(
        content = {
            StackedBarChart(
                points = listOf(
                    StackedPoint(listOf(1f, 1f, 4f, 0.5f)),
                    StackedPoint(listOf(null, null, null, null)),
                    StackedPoint(listOf(1f, 1f, 4f, 0.5f)),
                ),
                segments = segments,
                dates = dates(3),
                label = "Sömnstadier",
                minValue = 0f,
                maxValue = 10f,
                gridStep = 2f,
                modifier = mod,
            )
        },
        assertions = {
            // Två mätta nätter av tre — den omätta är en lucka, inte en nollhög stapel.
            composeRule.onNodeWithContentDescription(
                "Sömnstadier: 2 staplar, lägsta 6.5, högsta 6.5, mest Lätt, oförändrad trend",
            ).assertIsDisplayed()
        },
    )

    @Test fun `renders without dates`() = renderAndRetry(
        content = {
            StackedBarChart(
                points = listOf(StackedPoint(listOf(2f, 1f, 4f, 1f))),
                segments = segments,
                label = "Sömnstadier",
                minValue = 0f,
                maxValue = 10f,
                gridStep = 2f,
                modifier = mod,
            )
        },
        assertions = {
            composeRule.onNodeWithContentDescription(
                "Sömnstadier: 1 staplar, lägsta 8, högsta 8, mest Lätt, ingen trend",
            ).assertIsDisplayed()
        },
    )
}
