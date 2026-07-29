package se.partee71.dagboken.widget

import androidx.datastore.preferences.core.emptyPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import se.partee71.dagboken.domain.model.Favorit

class VidBehovWidgetStateTest {

    private fun favorit(namn: String, isFavorite: Boolean) = Favorit(
        id = namn, namn = namn, dos = "500", enhet = "mg", tidpunkt = "Vid behov",
        minTidMellan = 0, maxDoserPerDag = 0, isFavorite = isFavorite,
    )

    @Test fun `favoriteVidBehov keeps only favorites sorted by name`() {
        val all = listOf(
            favorit("Zink", isFavorite = true),
            favorit("Alvedon", isFavorite = false),
            favorit("Bamyl", isFavorite = true),
        )
        assertEquals(listOf("Bamyl", "Zink"), favoriteVidBehov(all).map { it.namn })
    }

    @Test fun `allVidBehovSorted lists favorites before non-favorites, alphabetical within each group`() {
        val all = listOf(
            favorit("Zink", isFavorite = true),
            favorit("Alvedon", isFavorite = false),
            favorit("Bamyl", isFavorite = true),
            favorit("Ipren", isFavorite = false),
        )
        assertEquals(listOf("Bamyl", "Zink", "Alvedon", "Ipren"), allVidBehovSorted(all).map { it.namn })
    }

    @Test fun `toVidBehovDraft defaults showAll to false`() {
        assertFalse(emptyPreferences().toVidBehovDraft().showAll)
    }

    @Test fun `toVidBehovDraft reflects stored showAll value`() {
        val prefs = emptyPreferences().toMutablePreferences().apply {
            this[VidBehovWidgetKeys.SHOW_ALL] = true
        }
        assertEquals(true, prefs.toVidBehovDraft().showAll)
    }
}
