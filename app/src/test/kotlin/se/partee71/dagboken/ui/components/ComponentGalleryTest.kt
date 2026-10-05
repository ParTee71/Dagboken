package se.partee71.dagboken.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.Density
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import se.partee71.dagboken.testing.captureLightAndDarkPaused

/**
 * Galleriet med stor text (fontScale 1.3) – visar att komponenterna tål dynamisk text. Galleriet har
 * laddningsindikatorer som aldrig slutar animera, därför med pausad klocka (`captureLightAndDarkPaused`).
 */
@RunWith(RobolectricTestRunner::class)
class ComponentGalleryTest {

    @get:Rule
    val rule = createComposeRule()

    @Test
    fun `ComponentGallery - stor text`() {
        rule.captureLightAndDarkPaused("ComponentGallery_fontScale13") {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale = FONT_SCALE)) {
                Box(Modifier.background(MaterialTheme.colorScheme.background)) { ComponentGallery(onBack = {}) }
            }
        }
    }

    private companion object {
        const val FONT_SCALE = 1.3f
    }
}
