package se.partee71.dagboken.widget

import org.junit.Assert.assertEquals
import org.junit.Test
import se.partee71.dagboken.domain.model.Medicin

class WidgetChecklistTest {

    private fun medicin(
        id: String,
        tidpunkt: String = "Morgon",
        tagen: Boolean = false,
        skipped: Boolean = false,
    ) = Medicin(
        id = id, timestamp = "2026-07-28T07:00:00.000Z", datum = "2026-07-28", tid = "07:00",
        namn = "Ibuprofen", dos = "400", enhet = "mg", tidpunkt = tidpunkt, tagen = tagen, skipped = skipped,
    )

    @Test fun `excludes skipped doses`() {
        val items = widgetChecklistItems(listOf(medicin(id = "m1", skipped = true), medicin(id = "m2")))
        assertEquals(listOf("m2"), items.map { it.id })
    }

    @Test fun `excludes vid behov doses`() {
        val items = widgetChecklistItems(
            listOf(medicin(id = "m1", tidpunkt = "Vid behov"), medicin(id = "m2", tidpunkt = "Morgon")),
        )
        assertEquals(listOf("m2"), items.map { it.id })
    }

    @Test fun `sorts by tidpunkt order regardless of input order`() {
        val items = widgetChecklistItems(
            listOf(
                medicin(id = "kväll", tidpunkt = "Kväll"),
                medicin(id = "morgon", tidpunkt = "Morgon"),
                medicin(id = "lunch", tidpunkt = "Lunch"),
            ),
        )
        assertEquals(listOf("morgon", "lunch", "kväll"), items.map { it.id })
    }

    @Test fun `keeps both taken and untaken doses so widget can show all-done state`() {
        val items = widgetChecklistItems(listOf(medicin(id = "m1", tagen = true), medicin(id = "m2", tagen = false)))
        assertEquals(2, items.size)
    }
}
