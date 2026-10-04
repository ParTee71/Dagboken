package se.partee71.dagboken

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Appen startar på en riktig enhet med Hilt, startskärmen och Expressive-temat (NFR-5). */
@RunWith(AndroidJUnit4::class)
class AppLaunchTest {

    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    @Test
    fun startskarmen_visas() {
        val appName = InstrumentationRegistry.getInstrumentation().targetContext.getString(R.string.app_name)
        // Inloggningen läses asynkront – det väntar inte Compose in av sig självt.
        compose.waitUntil(START_TIMEOUT_MS) { compose.onAllNodes(hasText(appName)).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText(appName).assertIsDisplayed()
    }

    private companion object {
        const val START_TIMEOUT_MS = 10_000L
    }
}
