package se.partee71.dagboken.testing

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.captureRoboImage
import com.github.takahirom.roborazzi.captureScreenRoboImage
import se.partee71.dagboken.ui.theme.DagbokenTheme

private val themes = listOf("light" to false, "dark" to true)

/**
 * Tar skärmdumpar i ljust och mörkt tema:
 * `<name>_light.png` och `<name>_dark.png` i Roborazzis outputDir (skill testing-strategy).
 * [name] följer `<Komponent>_<variant>`.
 */
fun captureLightAndDark(name: String, content: @Composable () -> Unit) {
    for ((suffix, dark) in themes) {
        captureRoboImage("${name}_$suffix.png") {
            DagbokenTheme(darkTheme = dark, content = content)
        }
    }
}

/**
 * Som [captureLightAndDark], men i en compose-regel: innehållet sätts en gång och byter bara
 * tema, så att effekter som redan körts (t.ex. en visad snackbar) finns med i båda bilderna.
 * [settle] väntar in varje bild; en tidsbegränsad snackbar kräver en fast tid i stället för
 * `waitForIdle`, som annars spolar fram tills den försvunnit.
 */
fun ComposeContentTestRule.captureLightAndDark(
    name: String,
    settle: ComposeContentTestRule.() -> Unit = { waitForIdle() },
    content: @Composable () -> Unit,
) = captureThemes(name, settle, content) { onRoot().captureRoboImage(it) }

/**
 * Som [captureLightAndDark], men fotograferar hela skärmen – för dialoger, menyer och sheets,
 * som ritas i egna fönster ovanpå innehållet. [open] körs före bilderna (t.ex. [clickWithoutRipple]
 * på knappen som öppnar menyn).
 */
@OptIn(ExperimentalRoborazziApi::class)
fun ComposeContentTestRule.captureScreenLightAndDark(
    name: String,
    open: ComposeContentTestRule.() -> Unit = {},
    content: @Composable () -> Unit,
) {
    var opened = false
    captureThemes(name, settle = { waitForIdle(); if (!opened) { open(); opened = true; waitForIdle() } }, content) { captureScreenRoboImage(it) }
}

private fun ComposeContentTestRule.captureThemes(
    name: String,
    settle: ComposeContentTestRule.() -> Unit,
    content: @Composable () -> Unit,
    capture: (String) -> Unit,
) {
    var dark by mutableStateOf(false)
    setContent { DagbokenTheme(darkTheme = dark, content = content) }
    for ((suffix, isDark) in themes) {
        dark = isDark
        settle()
        capture("${name}_$suffix.png")
    }
}

/**
 * Klick via tillgänglighetsåtgärden: ingen tryckeffekt (ripple) som hinner tona olika långt
 * före bilden – annars blir skärmdumpen instabil.
 */
fun SemanticsNodeInteraction.clickWithoutRipple() = performSemanticsAction(SemanticsActions.OnClick)
