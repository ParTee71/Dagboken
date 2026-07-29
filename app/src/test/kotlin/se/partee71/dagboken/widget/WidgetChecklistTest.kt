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

    // ─── widgetMedsSummary (#159) ─────────────────────────────────────────────

    @Test fun `widgetMedsSummary counts taken doses out of total`() {
        val items = listOf(
            medicin(id = "m1", tidpunkt = "Morgon", tagen = true),
            medicin(id = "m2", tidpunkt = "Lunch", tagen = false),
            medicin(id = "m3", tidpunkt = "Kväll", tagen = false),
        )
        val summary = widgetMedsSummary(items, nowHour = 8)
        assertEquals(1, summary.taken)
        assertEquals(3, summary.total)
    }

    @Test fun `widgetMedsSummary counts untaken doses whose scheduled hour has passed as overdue`() {
        val items = listOf(
            medicin(id = "m1", tidpunkt = "Morgon", tagen = false),  // 07:00, passed
            medicin(id = "m2", tidpunkt = "Kväll", tagen = false),   // 19:00, not yet
        )
        val summary = widgetMedsSummary(items, nowHour = 12)
        assertEquals(1, summary.overdue)
    }

    @Test fun `widgetMedsSummary does not count taken doses as overdue`() {
        val items = listOf(medicin(id = "m1", tidpunkt = "Morgon", tagen = true))
        val summary = widgetMedsSummary(items, nowHour = 12)
        assertEquals(0, summary.overdue)
    }

    @Test fun `widgetMedsSummary is zero for an empty list`() {
        val summary = widgetMedsSummary(emptyList(), nowHour = 12)
        assertEquals(0, summary.taken)
        assertEquals(0, summary.total)
        assertEquals(0, summary.overdue)
    }

    // ─── widgetActionableItems (#164) ──────────────────────────────────────────

    @Test fun `widgetActionableItems excludes taken doses`() {
        val items = widgetActionableItems(
            listOf(medicin(id = "m1", tagen = true), medicin(id = "m2", tagen = false)),
        )
        assertEquals(listOf("m2"), items.map { it.id })
    }

    @Test fun `widgetActionableItems is empty when all doses are taken`() {
        val items = widgetActionableItems(listOf(medicin(id = "m1", tagen = true), medicin(id = "m2", tagen = true)))
        assertEquals(0, items.size)
    }
}
