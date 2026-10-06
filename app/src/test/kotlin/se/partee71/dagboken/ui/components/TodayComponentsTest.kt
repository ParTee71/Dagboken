@file:OptIn(coil3.annotation.DelicateCoilApi::class)

package se.partee71.dagboken.ui.components

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.intercept.Interceptor
import coil3.request.CachePolicy
import coil3.request.ErrorResult
import coil3.request.ImageRequest
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTouchHeightIsEqualTo
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import androidx.compose.ui.unit.dp
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.datetime.LocalDate
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import se.partee71.dagboken.core.engine.OccasionStatus
import se.partee71.dagboken.testing.pixels
import se.partee71.dagboken.ui.theme.AppColors
import se.partee71.dagboken.ui.theme.DagbokenTheme

/**
 * Beteende och tillgänglighet för Idags nya komponenter (etapp 4.2): datumremsan (HEM-14), framstegsraden
 * (HEM-18), tillfällesraden (HEM-4, HEM-5), dagen klar (HEM-19) och avataren (NAV-9).
 */
@RunWith(RobolectricTestRunner::class)
class TodayComponentsTest {

    @get:Rule
    val rule = createComposeRule()

    private fun show(content: @Composable () -> Unit) = rule.setContent { DagbokenTheme(content = content) }

    private fun day(text: String) = rule.onNodeWithContentDescription(text, substring = true)
    private fun state(text: String) = SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, text)
    private fun role(role: Role) = SemanticsMatcher.expectValue(SemanticsProperties.Role, role)
    private fun progress(fraction: Float) = SemanticsMatcher.expectValue(SemanticsProperties.ProgressBarRangeInfo, ProgressBarRangeInfo(fraction, 0f..1f))

    /** Onsdag – veckan har tre framtida dagar. */
    private val today = LocalDate(2026, 10, 7)

    // ---- DateStrip ----

    @Test
    fun `datumremsan - en tidigare dag går att välja, framtida dagar inte`() {
        var selected by mutableStateOf(today)
        show { DateStrip(today, selected, { selected = it }, {}, today = today) }
        day("ons 7 okt 2026").assertIsSelected().assert(role(Role.Tab))
        day("mån 5 okt 2026").assertIsEnabled().assertIsNotSelected().performClick()
        assertEquals(LocalDate(2026, 10, 5), selected)
        day("mån 5 okt 2026").assertIsSelected()
        day("tors 8 okt 2026").assertIsNotEnabled().performClick()
        day("sön 11 okt 2026").assertIsNotEnabled()
        assertEquals(LocalDate(2026, 10, 5), selected)
    }

    @Test
    fun `datumremsan - idag, poster och klar dag läses upp`() {
        show { DateStrip(today, today, {}, {}, today = today, datesWithEntries = setOf(LocalDate(2026, 10, 6), today), todayDone = true) }
        day("ons 7 okt 2026, idag, har poster").assert(state("Klar"))
        day("tis 6 okt 2026, har poster").assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.StateDescription))
        day("mån 5 okt 2026").assertHeightIsAtLeast(48.dp)
    }

    @Test
    fun `datumremsan - svep åt höger ger föregående vecka, åt vänster aldrig en framtida vecka`() {
        var week by mutableStateOf(today)
        val changes = mutableListOf<LocalDate>()
        show { DateStrip(week, today, {}, { changes += it; week = it }, today = today) }
        day("ons 7 okt 2026").performTouchInput { swipeLeft(startX = right, endX = right - 4 * width) }
        assertEquals(emptyList(), changes)
        day("ons 7 okt 2026").performTouchInput { swipeRight(startX = left, endX = left + 4 * width) }
        assertEquals(listOf(LocalDate(2026, 9, 28)), changes)
        day("ons 30 sep 2026").assertIsDisplayed().assertIsEnabled()
        day("ons 30 sep 2026").performTouchInput { swipeLeft(startX = right, endX = right - 4 * width) }
        assertEquals(listOf(LocalDate(2026, 9, 28), LocalDate(2026, 10, 5)), changes)
    }

    @Test
    fun `datumremsan - veckobytet finns som TalkBack-åtgärd`() {
        val changes = mutableListOf<LocalDate>()
        show { DateStrip(LocalDate(2026, 9, 30), today, {}, { changes += it }, today = today) }
        val strip = rule.onNode(SemanticsMatcher.keyIsDefined(SemanticsActions.CustomActions))
        val actions = strip.fetchSemanticsNode().config[SemanticsActions.CustomActions]
        assertEquals(listOf("Föregående vecka", "Nästa vecka"), actions.map { it.label })
        actions.forEach { it.action() }
        assertEquals(listOf(LocalDate(2026, 9, 21), LocalDate(2026, 10, 5)), changes)
    }

    // ---- ProgressBar ----

    @Test
    fun `framstegsraden fylls animerat från 0 och läser upp x av y klara`() {
        var done by mutableIntStateOf(0)
        show { ProgressBar(done, 9) }
        val bar = rule.onNodeWithContentDescription("0 av 9 klara").assert(progress(0f))
        val teal = AppColors.light.primary
        assertEquals(0, bar.pixels(teal))
        rule.mainClock.autoAdvance = false
        done = 4
        Snapshot.sendApplyNotifications()
        rule.mainClock.advanceTimeBy(FRAMES)
        val halfway = rule.onNodeWithContentDescription("4 av 9 klara").assert(progress(4f / 9)).pixels(teal)
        rule.mainClock.advanceTimeBy(SETTLE)
        val filled = rule.onNodeWithContentDescription("4 av 9 klara").pixels(teal)
        assertTrue(halfway in 1 until filled, "fyllnaden växer: $halfway → $filled")
    }

    @Test
    fun `framstegsraden blir solgul och säger allt klart när allt är klart`() {
        var done by mutableIntStateOf(8)
        show { ProgressBar(done, 9) }
        assertEquals(0, rule.onNodeWithContentDescription("8 av 9 klara").pixels(AppColors.light.secondary))
        done = 9
        rule.waitForIdle()
        val bar = rule.onNodeWithContentDescription("9 av 9 klara, allt klart").assert(progress(1f))
        assertTrue(bar.pixels(AppColors.light.secondary) > 0, "solgul fyllnad")
        assertEquals(0, bar.pixels(AppColors.light.primary), "ingen teal kvar")
    }

    // ---- OccasionRow ----

    @Test
    fun `tillfällesraden visar alla fyra lägen som text och Logga nu bara när det ska loggas`() {
        val logged = mutableListOf<String>()
        show {
            Column {
                OccasionRow(
                    "Efter frukost",
                    OccasionStatus.LOGGED,
                    { logged += "frukost" },
                    time = "08:12",
                    values = listOf(OccasionValue("Energi", 7), OccasionValue("Stress", 4, higherIsBetter = false)),
                )
                OccasionRow("Lunch", OccasionStatus.LATE, { logged += "lunch" }, time = "12:00")
                OccasionRow("Kvällsmat", OccasionStatus.SOON, { logged += "kvällsmat" }, time = "18:00")
                OccasionRow("Läggdags", OccasionStatus.UPCOMING, { logged += "läggdags" }, time = "22:00")
            }
        }
        rule.onNodeWithText("Loggad 08:12").assertIsDisplayed()
        rule.onNodeWithText("Energi 7").assertIsDisplayed()
        rule.onNodeWithText("Stress 4").assertIsDisplayed()
        rule.onNodeWithText("Försenat").assertIsDisplayed()
        rule.onNodeWithText("Snart").assertIsDisplayed()
        rule.onNodeWithText("Kommande · 22:00").assertIsDisplayed()
        val buttons = rule.onAllNodesWithText("Logga nu")
        assertEquals(2, buttons.fetchSemanticsNodes().size, "Logga nu bara för försenat och snart")
        buttons[0].assertTouchHeightIsEqualTo(48.dp).assertHasClickAction().performClick()
        buttons[1].performClick()
        assertEquals(listOf("lunch", "kvällsmat"), logged)
    }

    @Test
    fun `tillfällesraden - en tidigare dag är ej loggad, inte försenad (HEM-4)`() {
        var logged = 0
        show { OccasionRow("Lunch", OccasionStatus.NOT_LOGGED, { logged++ }, time = "12:00") }
        rule.onNodeWithText("Försenat").assertDoesNotExist()
        rule.onNodeWithText("Ej loggad").assertIsDisplayed()
        rule.onNodeWithText("Logga nu").performClick()
        assertEquals(1, logged)
    }

    // ---- DayDoneCard ----

    @Test
    fun `dagen klar visar nyckeltalen och streck utan underlag`() {
        var values by mutableStateOf(Triple<Double?, Double?, Int?>(6.84, -0.3, 12))
        show { DayDoneCard(values.first, values.second, values.third) }
        rule.onNodeWithText("Allt klart för idag").assertIsDisplayed()
        listOf("6,8", "−0,3", "12", "Snittenergi", "Mot igår", "Dagar i rad").forEach { rule.onNodeWithText(it, useUnmergedTree = true).assertIsDisplayed() }
        values = Triple(6.0, 0.5, 1)
        rule.onNodeWithText("+0,5", useUnmergedTree = true).assertIsDisplayed()
        values = Triple(null, null, null)
        assertEquals(3, rule.onAllNodes(hasText("—"), useUnmergedTree = true).fetchSemanticsNodes().size)
    }

    @Test
    fun `dagen klar - konfettin faller en gång per play`() {
        var play by mutableStateOf(false)
        var finished = 0
        show { DayDoneCard(6.8, 0.5, 12, play = play, onConfettiFinished = { finished++ }) }
        rule.waitForIdle()
        assertEquals(0, finished, "ingen konfetti utan play")
        play = true
        rule.waitForIdle()
        assertEquals(1, finished)
        // Samma play igen (t.ex. en omkomposition) startar inget nytt regn.
        rule.mainClock.advanceTimeBy(SETTLE)
        assertEquals(1, finished)
        play = false
        rule.waitForIdle()
        play = true
        rule.waitForIdle()
        assertEquals(2, finished)
    }

    // ---- AccountAvatar ----

    @Test
    fun `avataren är en 48 dp knapp som läses Konto och inställningar`() {
        var opened = 0
        show { AccountAvatar("Anna Berg", { opened++ }) }
        rule.onNodeWithContentDescription("Konto och inställningar")
            .assert(role(Role.Button))
            .assertHasClickAction()
            .assertHeightIsAtLeast(48.dp)
            .assertWidthIsAtLeast(48.dp)
            .performClick()
        assertEquals(1, opened)
    }

    @Test
    fun `avataren visar fotot i stället för initialerna`() {
        var photoShown = false
        show {
            AccountAvatar("Anna Berg", {}) {
                photoShown = true
                Box(Modifier.fillMaxSize().background(AppColors.light.secondary))
            }
        }
        rule.waitForIdle()
        assertTrue(photoShown)
        assertTrue(rule.onNodeWithContentDescription("Konto och inställningar").pixels(AppColors.light.secondary) > 0)
    }

    @Test
    fun `fotot hämtas bara till minnet och utan foto syns initialerna (AUTH-3)`() {
        // Ingen riktig nätåtkomst i JVM-testet: en egen ImageLoader fångar requesten och svarar med fel.
        val requests = mutableListOf<ImageRequest>()
        val context = ApplicationProvider.getApplicationContext<Context>()
        SingletonImageLoader.setUnsafe(
            ImageLoader.Builder(context).components {
                add(Interceptor { chain -> synchronized(requests) { requests += chain.request }; ErrorResult(null, chain.request, IllegalStateException("inget nät i test")) })
            }.build(),
        )
        try {
            show { AccountAvatar("Anna Berg", {}, photoUrl = "https://exempel.se/anna.jpg") }
            rule.waitUntil(WAIT_MILLIS) { synchronized(requests) { requests.isNotEmpty() } }
            rule.waitForIdle()
            val request = synchronized(requests) { requests.single() }
            assertEquals("https://exempel.se/anna.jpg", request.data)
            assertEquals(CachePolicy.DISABLED, request.diskCachePolicy, "fotot får aldrig sparas på enheten")
            assertTrue(rule.onNodeWithContentDescription("Konto och inställningar").pixels(AppColors.light.onPrimaryContainer) > 0, "initialerna ritas")
        } finally {
            SingletonImageLoader.reset()
        }
    }

    @Test
    fun `utan onClick är avataren bara en bild som inte läses upp`() {
        show { AccountAvatar("Anna Berg", onClick = null) }
        rule.onNodeWithContentDescription("Konto och inställningar").assertDoesNotExist()
        rule.onAllNodes(hasClickAction()).assertCountEquals(0)
    }

    @Test
    fun `initialerna tas ur första och sista ordet, utan namn blir det person-ikonen`() {
        assertEquals("AB", initials("Anna Berg"))
        assertEquals("AB", initials("  anna maria   berg "))
        assertEquals("Å", initials("Åsa"))
        assertEquals("EÖ", initials("Eva (privat) Östlund"))
        assertNull(initials(""))
        assertNull(initials("   "))
        assertNull(initials("123"))
    }

    private companion object {
        const val SETTLE = 3_000L

        /** Några bildrutor in i fyllnadens fjäder – långt före slutet. */
        const val FRAMES = 64L
    }
}

/** Hur länge testet väntar på bildladdningens request. */
private const val WAIT_MILLIS = 5_000L
