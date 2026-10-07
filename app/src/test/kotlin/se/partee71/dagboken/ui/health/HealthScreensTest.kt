package se.partee71.dagboken.ui.health

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlinx.datetime.LocalDate
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import se.partee71.dagboken.core.engine.SleepFlag
import se.partee71.dagboken.core.engine.daysEnding
import se.partee71.dagboken.core.engine.health.OptionalHealthMetric
import se.partee71.dagboken.core.model.DailyHealth
import se.partee71.dagboken.core.model.SleepStages
import se.partee71.dagboken.data.health.HealthStatus
import se.partee71.dagboken.testing.captureLightAndDark
import se.partee71.dagboken.ui.theme.Spacing
import se.partee71.dagboken.ui.theme.DagbokenTheme

/**
 * Hälsokortet och "Senaste veckan" på Idag (HEM-15, HEM-17, HLS-7) och Klocka-gruppens topp med Hälsa idag (TRD-20,
 * HLS-4, HLS-6, HLS-8, HLS-10, HLS-11, HLS-14, NFR-14). Påhittad data; idag är tisdag 6 oktober 2026.
 */
@RunWith(RobolectricTestRunner::class)
class HealthScreensTest {

    @get:Rule
    val rule = createComposeRule()

    private fun show(content: @Composable () -> Unit) = rule.setContent { DagbokenTheme { Column { content() } } }

    private val week = daysEnding(HealthSamples.today)
    private val energy = listOf(5f, 6f, null, 7f, 6.5f, 5f, 7f)

    @Test
    fun `kopplad - steg och vilopuls för dagen, varje mätvärde läses som en enhet (HEM-15, NFR-14)`() {
        show { HealthTodayCard(HealthSamples.todayCard, {}) }
        rule.onNodeWithText("Hälsa").assertIsDisplayed()
        rule.onNodeWithText("7 842").assertIsDisplayed()
        rule.onNodeWithText("58").assertIsDisplayed()
        rule.onNode(hasClickAction()).assertDoesNotExist()
        rule.onNodeWithText("Ge åtkomst").assertDoesNotExist()
    }

    @Test
    fun `en dag utan mätning visar — (HEM-15)`() {
        show { HealthTodayCard(HealthSamples.todayCard.copy(day = DailyHealth(HealthSamples.today)), {}) }
        rule.onAllNodesWithText("—").fetchSemanticsNodes().let { assertEquals(2, it.size) }
        rule.onNodeWithText("Steg").assertIsDisplayed()
        rule.onNodeWithText("Vilopuls").assertIsDisplayed()
    }

    @Test
    fun `utan behörighet en koppla-rad med Ge åtkomst i stället för värdena (HEM-15, HLS-3)`() {
        val events = mutableListOf<HealthEvent>()
        show { HealthTodayCard(HealthTodayUiState(HealthStatus.PERMISSIONS_MISSING, HealthSamples.today), { events += it }) }
        rule.onNodeWithText("Koppla klockan via Health Connect för att se steg och vilopuls här.").assertIsDisplayed()
        rule.onNodeWithText("Steg").assertDoesNotExist()
        rule.onNodeWithText("Ge åtkomst").performClick()
        assertEquals(listOf<HealthEvent>(HealthEvent.GrantAccess), events)
    }

    @Test
    fun `utan Health Connect eller med uppdatering krävs visas inget hälsokort (HLS-4)`() {
        show {
            HealthTodayCard(HealthTodayUiState(HealthStatus.UNAVAILABLE, HealthSamples.today), {})
            HealthTodayCard(HealthTodayUiState(HealthStatus.UPDATE_REQUIRED, HealthSamples.today), {})
        }
        rule.onNodeWithText("Hälsa").assertDoesNotExist()
    }

    @Test
    fun `Senaste veckan har raderna steg, vilopuls och energi i den ordningen med min och max (HEM-17, TRD-9)`() {
        var trends = 0
        show { WeekTrendsCard(week, energy, HealthSamples.todayCard, { trends++ }) }
        val titles = listOf("Steg", "Vilopuls", "Energi").map { title -> rule.onNodeWithText(title).fetchSemanticsNode().boundsInRoot.top }
        assertEquals(titles.sorted(), titles)
        rule.onNodeWithText("Lägst 4\u00A0020 · Högst 9\u00A0870 · Idag 7\u00A0842").assertIsDisplayed()
        rule.onNodeWithText("Lägst 57 · Högst 61 · Idag 58").assertIsDisplayed()
        rule.onNodeWithText("Lägst 5 · Högst 7 · Idag 7").assertIsDisplayed()
        rule.onNodeWithText("Visa i Trender").performClick()
        assertEquals(1, trends)
    }

    @Test
    fun `utan klocka bara energin, och för lite mående ber om fler loggade dagar (HEM-7, HEM-17)`() {
        show { WeekTrendsCard(week, listOf(null, null, null, null, null, null, 7f), null, {}) }
        rule.onNodeWithText("Steg").assertDoesNotExist()
        rule.onNodeWithText("Vilopuls").assertDoesNotExist()
        rule.onNodeWithText("Energi").assertIsDisplayed()
        rule.onNodeWithText("Logga mående två dagar för att se energitrenden.").assertIsDisplayed()
        rule.onNodeWithText("Visa i Trender").assertIsDisplayed()
    }

    // ---- Skärmdumpar bredvid mockupen (canvas avsnitt 14: Idag-Halsa, Idag-Halsa-Morkt, Idag-Halsa-Koppla, Idag-Halsa-Saknas) ----

    @Composable
    private fun Sheet(content: @Composable ColumnScope.() -> Unit) {
        Column(
            Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.background).padding(Spacing.l),
            verticalArrangement = Arrangement.spacedBy(Spacing.m),
            content = content,
        )
    }

    @Test
    fun `skärmdump - Idag hälsa kopplad`() = rule.captureLightAndDark("Health_idag_kopplad") {
        Sheet {
            HealthTodayCard(HealthSamples.todayCard, {})
            WeekTrendsCard(week, energy, HealthSamples.todayCard, {})
        }
    }

    @Test
    fun `skärmdump - Idag ej kopplad`() = rule.captureLightAndDark("Health_idag_koppla") {
        val notConnected = HealthTodayUiState(HealthStatus.PERMISSIONS_MISSING, HealthSamples.today)
        Sheet {
            HealthTodayCard(notConnected, {})
            WeekTrendsCard(week, energy, notConnected, {})
        }
    }

    @Test
    fun `skärmdump - Idag dag utan mätning och för lite mående`() = rule.captureLightAndDark("Health_idag_saknas") {
        val empty = HealthSamples.todayCard.copy(day = DailyHealth(HealthSamples.today))
        Sheet {
            HealthTodayCard(empty, {})
            WeekTrendsCard(week, listOf(null, null, null, null, null, null, 7f), empty, {})
        }
    }

    @Test
    fun `skärmdump - Hälsa idag med nattens varningsrader`() = rule.captureLightAndDark("Health_halsa_idag_varningar") {
        Sheet { HealthTodaySection(HealthSamples.clockWithFlags) }
    }

    // ---- Klocka ----

    @Test
    fun `statusen per läge med rätt åtgärd (TRD-20, HLS-3, HLS-4)`() {
        val cases = listOf(
            Triple(HealthStatus.PERMISSIONS_MISSING, "Health Connect är inte kopplat", "Ge åtkomst" to HealthEvent.GrantAccess),
            Triple(HealthStatus.UNAVAILABLE, "Health Connect saknas", "Installera" to HealthEvent.OpenHealthConnect),
            Triple(HealthStatus.UPDATE_REQUIRED, "Health Connect behöver uppdateras", "Uppdatera" to HealthEvent.OpenHealthConnect),
        )
        var current by mutableStateOf(cases.first().first)
        val events = mutableListOf<HealthEvent>()
        show { ClockStatus(current, null, { events += it }) }
        cases.forEach { (status, title, action) ->
            current = status
            rule.onNodeWithText(title).assertIsDisplayed()
            rule.onNodeWithText(action.first).assertHeightIsAtLeast(48.dp).performClick()
            assertEquals(action.second, events.last())
        }
        current = HealthStatus.AVAILABLE
        rule.onNodeWithText("Health Connect kopplad").assertIsDisplayed()
        rule.onNodeWithText("Steg, puls, sömn och sömnkvalitet visas här.").assertIsDisplayed()
        rule.onNode(hasClickAction()).assertDoesNotExist()
        assertEquals(3, events.size)
    }

    @Test
    fun `saknade valfria behörigheter är en egen knapp med måtten – bara när något saknas (HLS-14, NFR-14)`() {
        var missing by mutableStateOf<Set<OptionalHealthMetric>?>(setOf(OptionalHealthMetric.OXYGEN_SATURATION, OptionalHealthMetric.EXERCISE))
        val events = mutableListOf<HealthEvent>()
        show { ClockStatus(HealthStatus.AVAILABLE, missing, { events += it }) }
        rule.onNodeWithText("Träning · Syremättnad").assertIsDisplayed()
        rule.onNodeWithText("2 saknas")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
            .assertHeightIsAtLeast(48.dp)
            .performClick()
        assertEquals(listOf<HealthEvent>(HealthEvent.GrantAccess), events)
        missing = emptySet()
        rule.onNodeWithText("2 saknas").assertDoesNotExist()
        missing = null
        rule.onNodeWithText("2 saknas").assertDoesNotExist()
        rule.onNodeWithText("Ge åtkomst").assertDoesNotExist()
    }

    @Test
    fun `Hälsa idag visar klockans alla mått och stadierna (HLS-6, HLS-8, HLS-10)`() {
        show { HealthTodaySection(HealthSamples.clock) }
        rule.onNodeWithText("Hälsa idag").assertIsDisplayed()
        listOf(
            "7 842" to "Steg", "64" to "Snittpuls idag", "58" to "Vilopuls", "7 tim 12 min" to "Sömn i natt", "78" to "Sömnkvalitet",
            "32 min" to "Träning idag", "412 kcal" to "Aktiva kalorier", "5,8 km" to "Sträcka", "97 %" to "Syremättnad",
        ).forEach { (value, label) ->
            rule.onNodeWithText(value).assertIsDisplayed()
            rule.onNodeWithText(label).assertIsDisplayed()
        }
        rule.onNodeWithText("Djup 1 tim 12 min · REM 1 tim 35 min · Lätt 3 tim 48 min · Vaken 37 min").assertIsDisplayed()
    }

    @Test
    fun `nattens varningsrader står under sömnkvaliteten, bara när natten har dem (HLS-10)`() {
        var flags by mutableStateOf(SleepFlag.entries.toList())
        show { HealthTodaySection(HealthSamples.clock.copy(sleepFlags = flags)) }
        rule.onNodeWithText("Låg syremättnad under natten – ta upp det med vården").assertIsDisplayed()
        rule.onNodeWithText("Sovpulsen ligger över din vanliga nivå").assertIsDisplayed()
        rule.onNode(hasClickAction()).assertDoesNotExist()
        flags = emptyList()
        rule.onNodeWithText("Sovpulsen ligger över din vanliga nivå").assertDoesNotExist()
        rule.onNodeWithText("Låg syremättnad under natten – ta upp det med vården").assertDoesNotExist()
    }

    @Test
    fun `en natt utan stadier visar bara längden, och utan födelseår ingen poäng utan Profil (HLS-8, HLS-11)`() {
        show { HealthTodaySection(ClockUiState(HealthStatus.AVAILABLE, day = HealthSamples.night.copy(sleepStages = SleepStages()), needsBirthYear = true)) }
        rule.onNodeWithText("7 tim 12 min").assertIsDisplayed()
        rule.onNodeWithText("Djup", substring = true).assertDoesNotExist()
        rule.onNodeWithText("Sömnkvalitet · fyll i födelseår i Profil").assertIsDisplayed()
        rule.onNodeWithText("78").assertDoesNotExist()
    }

    @Test
    fun `mått utan värde är —, också sömnpoängen utan profil (HLS-11)`() {
        show { HealthTodaySection(ClockUiState(HealthStatus.AVAILABLE, day = DailyHealth(HealthSamples.today))) }
        assertEquals(9, rule.onAllNodesWithText("—").fetchSemanticsNodes().size)
    }
}

/** Påhittad klockdata som i mockupen (canvas avsnitt 14) – delad av skärmtesterna och skärmdumparna. */
object HealthSamples {
    val today = LocalDate(2026, 10, 6)

    val night = DailyHealth(
        today,
        steps = 7_842,
        restingHeartRate = 58,
        heartRateAvg = 64,
        sleepDuration = 7.hours + 12.minutes,
        sleepStages = SleepStages(deep = 1.hours + 12.minutes, rem = 1.hours + 35.minutes, light = 3.hours + 48.minutes, awake = 37.minutes),
        exerciseSessions = 1,
        exerciseDuration = 32.minutes,
        activeEnergyKcal = 412.0,
        distanceMeters = 5_800.0,
        oxygenSaturationAvg = 97.0,
    )

    val todayCard = HealthTodayUiState(
        HealthStatus.AVAILABLE,
        today,
        night,
        daysEnding(today),
        steps = listOf(6_100f, 9_870f, 4_020f, 8_400f, 6_900f, 5_200f, 7_842f),
        restingHeartRate = listOf(59f, 60f, 61f, 57f, 57f, 59f, 58f),
    )

    val clock = ClockUiState(HealthStatus.AVAILABLE, missing = emptySet(), day = night, sleepScore = 78)

    /** En natt med båda varningsraderna (HLS-10): sovpuls över baslinjen och låg syremättnad. */
    val clockWithFlags = clock.copy(sleepScore = 71, sleepFlags = SleepFlag.entries.toList())
}
