package se.partee71.dagboken

import android.content.Context
import android.graphics.drawable.AdaptiveIconDrawable
import android.widget.ImageView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.test.core.app.ApplicationProvider
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import se.partee71.dagboken.testing.captureLightAndDark
import se.partee71.dagboken.ui.theme.AppColors
import se.partee71.dagboken.ui.theme.Spacing

/**
 * Appikonen: adaptiv och satt i manifestet. Ikonen är 3.x-ikonen (ur `assets/icon.png`) tills en
 * ikon i Papper och teal har mockats – den har därför ingen enfärgad temaikon än.
 */
@RunWith(RobolectricTestRunner::class)
class AppIconTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test
    fun `appikonen är adaptiv och används av appen`() {
        for (icon in listOf(R.mipmap.ic_launcher, R.mipmap.ic_launcher_round)) {
            val drawable = context.getDrawable(icon) as AdaptiveIconDrawable
            assertNotNull(drawable.foreground)
            assertNotNull(drawable.background)
        }
        assertEquals(R.mipmap.ic_launcher, context.applicationInfo.icon)
    }

    @Test
    fun `fönstrets och startskärmens bakgrund är temats – en källa (NFR-5)`() {
        assertEquals(AppColors.light.background.toArgb(), context.getColor(R.color.window_background))
    }

    @Test
    @Config(qualifiers = "night")
    fun `fönstrets bakgrund i mörkt läge är det mörka temats`() {
        assertEquals(AppColors.dark.background.toArgb(), context.getColor(R.color.window_background))
    }

    @Test
    fun `Appikon`() = captureLightAndDark("AppIcon_launcher") {
        Row(
            Modifier.background(MaterialTheme.colorScheme.background).padding(Spacing.l),
            horizontalArrangement = Arrangement.spacedBy(Spacing.l),
        ) {
            for (size in listOf(108.dp, 48.dp)) {
                AndroidView({ ImageView(it).apply { setImageResource(R.mipmap.ic_launcher) } }, Modifier.size(size))
            }
        }
    }
}
