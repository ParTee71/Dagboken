package se.partee71.dagboken.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import kotlin.test.assertEquals
import kotlinx.datetime.LocalDate
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import se.partee71.dagboken.R
import se.partee71.dagboken.testing.captureLightAndDark
import se.partee71.dagboken.testing.captureScreenLightAndDark
import se.partee71.dagboken.testing.clickWithoutRipple
import se.partee71.dagboken.ui.theme.DagbokenTheme
import se.partee71.dagboken.ui.theme.Spacing

/** Komponenter som ritas ovanpå innehållet, i rörelse eller över tid – en regel per test. */
@RunWith(RobolectricTestRunner::class)
class OverlayScreenshotTest {

    @get:Rule
    val rule = createComposeRule()

    private val menu = listOf(
        AppMenuItem("Arkivera", {}, R.drawable.ic_archive),
        AppMenuItem("Radera", {}, R.drawable.ic_delete, destructive = true),
    )

    @Test
    fun `ConfirmDialog - radera`() = rule.captureScreenLightAndDark("ConfirmDialog_radera") {
        ConfirmDialog("Radera Alvedon?", "Receptet tas bort för gott. Det går inte att ångra.", "Radera", {}, {}, destructive = true)
    }

    @Test
    fun `ConfirmDialog - med fält`() = rule.captureScreenLightAndDark("ConfirmDialog_med_falt") {
        ConfirmDialog("Avsluta episoden?", "Förkylning markeras som avslutad. Incheckningarna står kvar.", "Avsluta", {}, {}) {
            DateField("Slutdatum", LocalDate(2026, 10, 6), {})
        }
    }

    @Test
    fun `AppBottomSheet - ändra mängd`() = rule.captureScreenLightAndDark("AppBottomSheet_mangd") {
        AppBottomSheet("Promenad", onDismiss = {}) {
            QuantityStepper(45, {}, "minuter")
            AppButton("Spara", {}, variant = ButtonVariant.Text)
        }
    }

    @Test
    fun `AppMenu - öppen`() = rule.captureScreenLightAndDark("AppMenu_oppen", open = { onNodeWithContentDescription("Fler val").clickWithoutRipple() }) {
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background), contentAlignment = Alignment.TopEnd) { AppMenu(menu) }
    }

    @Test
    fun `UndoSnackbar - arkiverad`() {
        var undone = 0
        rule.mainClock.autoAdvance = false
        rule.captureLightAndDark("UndoSnackbar_arkiverad", settle = { mainClock.advanceTimeBy(FRAME); waitForIdle() }) {
            val host = remember { SnackbarHostState() }
            UndoSnackbar(UndoRequest("yoga", "Yoga"), host, onUndo = { undone++ }, onDismissed = {})
            Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background), contentAlignment = Alignment.BottomCenter) {
                AppSnackbarHost(host)
            }
        }
        rule.onNodeWithText("Yoga arkiverad").assertExists()
        rule.onNodeWithText("Ångra").performClick()
        rule.mainClock.advanceTimeBy(FRAME)
        assertEquals(1, undone)
    }

    @Test
    fun `UndoSnackbar försvinner efter fem sekunder`() {
        var dismissed = 0
        rule.mainClock.autoAdvance = false
        rule.setContent {
            DagbokenTheme {
                val host = remember { SnackbarHostState() }
                UndoSnackbar(UndoRequest("yoga", "Yoga"), host, onUndo = {}, onDismissed = { dismissed++ })
                AppSnackbarHost(host)
            }
        }
        rule.mainClock.advanceTimeBy(UNDO_MILLIS - FRAME)
        rule.onNodeWithText("Yoga arkiverad").assertExists()
        assertEquals(0, dismissed)
        rule.mainClock.advanceTimeBy(2 * FRAME)
        assertEquals(1, dismissed)
    }

    @Test
    fun `UndoSnackbar - att lämna skärmen avslutar ångra, så att den inte visas igen`() {
        var dismissed = 0
        var visible by mutableStateOf(true)
        rule.setContent {
            DagbokenTheme {
                val host = remember { SnackbarHostState() }
                if (visible) UndoSnackbar(UndoRequest("yoga", "Yoga"), host, onUndo = {}, onDismissed = { dismissed++ })
                AppSnackbarHost(host)
            }
        }
        rule.onNodeWithText("Yoga arkiverad").assertExists()
        visible = false
        rule.waitForIdle()
        assertEquals(1, dismissed)
    }

    @Test
    fun `UndoSnackbar - samma sak arkiverad igen efter Ångra avslutas också när skärmen lämnas`() {
        var dismissed = 0
        var request by mutableStateOf<UndoRequest?>(UndoRequest("yoga", "Yoga"))
        var visible by mutableStateOf(true)
        rule.setContent {
            DagbokenTheme {
                val host = remember { SnackbarHostState() }
                if (visible) UndoSnackbar(request, host, onUndo = { request = null }, onDismissed = { dismissed++; request = null })
                AppSnackbarHost(host)
            }
        }
        rule.onNodeWithText("Ångra").performClick()
        rule.waitForIdle()
        request = UndoRequest("yoga", "Yoga")
        rule.onNodeWithText("Yoga arkiverad").assertExists()
        visible = false
        rule.waitForIdle()
        assertEquals(1, dismissed)
    }

    @Test
    fun `SwipeToHide - halvvägs svept`() {
        var swiped = false
        rule.captureLightAndDark("SwipeToHide_svept", settle = {
            // Fingret hålls kvar mitt i svepet, så att bakgrunden syns i båda bilderna.
            if (!swiped) onRoot().performTouchInput { down(Offset(width * 0.8f, height / 2f)); repeat(STEPS) { moveBy(Offset(-width * 0.3f / STEPS, 0f)) } }
            swiped = true
            waitForIdle()
        }) { SwipeSample() }
    }

    @Composable
    private fun SwipeSample() {
        Box(Modifier.background(MaterialTheme.colorScheme.background).padding(Spacing.l)) {
            AppCard { SwipeToHide(onHide = {}) { ItemRow("Yoga", it, subtitle = "Favorit") } }
        }
    }

    @Test
    fun `Confetti - mitt i regnet`() {
        rule.mainClock.autoAdvance = false
        rule.captureLightAndDark("Confetti_mitt", settle = { mainClock.advanceTimeBy(CONFETTI_MIDDLE); waitForIdle() }) {
            Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) { Confetti(Modifier.fillMaxSize()) }
        }
    }

    private companion object {
        const val FRAME = 16L
        const val CONFETTI_MIDDLE = 500L
        const val STEPS = 10
    }
}
