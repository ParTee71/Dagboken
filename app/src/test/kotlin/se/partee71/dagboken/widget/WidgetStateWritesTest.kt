package se.partee71.dagboken.widget

import androidx.datastore.preferences.core.mutablePreferencesOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Regressionstester för widgetarnas skrivningar till Glance-state.
 *
 * Alla skrivningar sker inuti `updateAppWidgetState(context, glanceId) { prefs -> ... }`, där
 * `prefs` *är* den [androidx.datastore.preferences.core.MutablePreferences] som persisteras.
 * Tidigare gjorde varje action `prefs.toMutablePreferences().apply { ... }` — muterade en
 * kopia som slängdes, eftersom lambdans returvärde coercas till `Unit`. Det kompilerade rent,
 * men ingen screening- eller vid behov-skrivning nådde någonsin disk (#164).
 *
 * Testerna nedan verifierar därför inte bara *vad* som skrivs, utan att skrivningen sker på
 * **mottagaren själv** — precis det misstaget som slank igenom hela 3.15.0–3.15.3.
 */
class WidgetStateWritesTest {

    @Test fun `startScreeningDraft mutates the receiver onto the energy step`() {
        val prefs = mutablePreferencesOf()

        prefs.startScreeningDraft("Lunch")

        assertEquals(SCREENING_STEP_ENERGY, prefs[ScreeningWidgetKeys.STEP])
        assertEquals(5, prefs[ScreeningWidgetKeys.ENERGY])
        assertEquals(5, prefs[ScreeningWidgetKeys.STRESS])
        assertEquals("", prefs[ScreeningWidgetKeys.SYMPTOM_SCORES])
        assertEquals("Lunch", prefs[ScreeningWidgetKeys.LABEL])
    }

    @Test fun `startScreeningDraft resets a stale draft from a previous run`() {
        val prefs = mutablePreferencesOf().apply {
            this[ScreeningWidgetKeys.ENERGY] = 9
            this[ScreeningWidgetKeys.SYMPTOM_SCORES] = "Huvudvärk:7"
            this[ScreeningWidgetKeys.LABEL] = "Morgon"
        }

        prefs.startScreeningDraft("Kväll")

        assertEquals(5, prefs[ScreeningWidgetKeys.ENERGY])
        assertEquals("", prefs[ScreeningWidgetKeys.SYMPTOM_SCORES])
        assertEquals("Kväll", prefs[ScreeningWidgetKeys.LABEL])
    }

    @Test fun `stepScreeningDraft advances energy to stress`() {
        val prefs = mutablePreferencesOf().apply { this[ScreeningWidgetKeys.STEP] = SCREENING_STEP_ENERGY }

        prefs.stepScreeningDraft("next", hasFavoriteSymptoms = true)

        assertEquals(SCREENING_STEP_STRESS, prefs[ScreeningWidgetKeys.STEP])
    }

    @Test fun `stepScreeningDraft cancels back to the inactive step`() {
        val prefs = mutablePreferencesOf().apply { this[ScreeningWidgetKeys.STEP] = SCREENING_STEP_STRESS }

        prefs.stepScreeningDraft("cancel", hasFavoriteSymptoms = true)

        assertEquals(SCREENING_STEP_INACTIVE, prefs[ScreeningWidgetKeys.STEP])
    }

    @Test fun `adjustScreeningValue steps from the default when nothing is stored`() {
        val prefs = mutablePreferencesOf()

        prefs.adjustScreeningValue(ScreeningWidgetKeys.ENERGY, delta = 1)

        assertEquals(6, prefs[ScreeningWidgetKeys.ENERGY])
    }

    @Test fun `adjustScreeningValue clamps at the bounds`() {
        val prefs = mutablePreferencesOf().apply { this[ScreeningWidgetKeys.STRESS] = 10 }

        prefs.adjustScreeningValue(ScreeningWidgetKeys.STRESS, delta = 1)
        assertEquals(10, prefs[ScreeningWidgetKeys.STRESS])

        prefs[ScreeningWidgetKeys.STRESS] = 0
        prefs.adjustScreeningValue(ScreeningWidgetKeys.STRESS, delta = -1)
        assertEquals(0, prefs[ScreeningWidgetKeys.STRESS])
    }

    @Test fun `adjustScreeningSymptomScore accumulates across repeated taps`() {
        val prefs = mutablePreferencesOf()

        prefs.adjustScreeningSymptomScore("Huvudvärk", delta = 1)
        prefs.adjustScreeningSymptomScore("Huvudvärk", delta = 1)

        assertEquals(2, prefs.toScreeningDraft().symptomScores["Huvudvärk"])
    }

    @Test fun `adjustScreeningSymptomScore leaves other symptoms untouched`() {
        val prefs = mutablePreferencesOf()

        prefs.adjustScreeningSymptomScore("Huvudvärk", delta = 3)
        prefs.adjustScreeningSymptomScore("Illamående", delta = 1)

        val scores = prefs.toScreeningDraft().symptomScores
        assertEquals(3, scores["Huvudvärk"])
        assertEquals(1, scores["Illamående"])
    }

    @Test fun `clearScreeningDraft wipes the draft and returns to the inactive step`() {
        val prefs = mutablePreferencesOf().apply { startScreeningDraft("Lunch") }

        prefs.clearScreeningDraft()

        assertEquals(SCREENING_STEP_INACTIVE, prefs[ScreeningWidgetKeys.STEP])
        assertNull(prefs[ScreeningWidgetKeys.ENERGY])
        assertNull(prefs[ScreeningWidgetKeys.STRESS])
        assertNull(prefs[ScreeningWidgetKeys.SYMPTOM_SCORES])
        assertNull(prefs[ScreeningWidgetKeys.LABEL])
    }

    @Test fun `setVidBehovMessage stores the message and ends any pending confirmation`() {
        val prefs = mutablePreferencesOf().apply { setVidBehovPendingConfirm("alvedon", 2.5) }

        prefs.setVidBehovMessage("Alvedon loggad")

        val draft = prefs.toVidBehovDraft()
        assertEquals("Alvedon loggad", draft.message)
        assertNull(draft.pending)
    }

    @Test fun `clearVidBehovMessage makes the message show only once`() {
        val prefs = mutablePreferencesOf().apply { setVidBehovMessage("Alvedon loggad") }

        prefs.clearVidBehovMessage()

        assertNull(prefs.toVidBehovDraft().message)
    }

    @Test fun `setVidBehovPendingConfirm stores the favourite and its remaining cooldown`() {
        val prefs = mutablePreferencesOf()

        prefs.setVidBehovPendingConfirm("alvedon", 1.5)

        assertEquals(VidBehovPendingConfirm("alvedon", 1.5), prefs.toVidBehovDraft().pending)
    }

    @Test fun `clearVidBehovPendingConfirm cancels the confirmation step`() {
        val prefs = mutablePreferencesOf().apply { setVidBehovPendingConfirm("alvedon", 1.5) }

        prefs.clearVidBehovPendingConfirm()

        assertNull(prefs.toVidBehovDraft().pending)
    }

    @Test fun `setVidBehovShowAll toggles the expanded list`() {
        val prefs = mutablePreferencesOf()

        prefs.setVidBehovShowAll(true)
        assertTrue(prefs.toVidBehovDraft().showAll)

        prefs.setVidBehovShowAll(false)
        assertEquals(false, prefs.toVidBehovDraft().showAll)
    }

    @Test fun `incrementWidgetTapCount counts up from an empty state`() {
        val prefs = mutablePreferencesOf()

        prefs.incrementWidgetTapCount()
        prefs.incrementWidgetTapCount()

        assertEquals(2, prefs[WIDGET_TAP_COUNT])
    }
}
