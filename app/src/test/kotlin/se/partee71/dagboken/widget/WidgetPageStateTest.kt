package se.partee71.dagboken.widget

import androidx.datastore.preferences.core.emptyPreferences
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WidgetPageStateTest {

    @Test fun `showMedsPage defaults to false`() {
        assertFalse(emptyPreferences().showMedsPage())
    }

    @Test fun `showMedsPage reflects stored value`() {
        val prefs = emptyPreferences().toMutablePreferences().apply {
            this[WidgetPageKeys.SHOW_MEDS] = true
        }
        assertTrue(prefs.showMedsPage())
    }
}
