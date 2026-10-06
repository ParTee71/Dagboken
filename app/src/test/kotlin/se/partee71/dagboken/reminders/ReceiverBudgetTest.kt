package se.partee71.dagboken.reminders

import kotlin.test.assertTrue
import org.junit.Test

/**
 * Mottagarnas tidsbudget (NOT-10, NOT-14): nästa larm ryms i första steget, notisens läsningar i den tid som alltid
 * finns kvar, och "Markera tagen" hinner läsa, skriva och visa utfallet – allt under systemets gräns.
 */
class ReceiverBudgetTest {

    @Test
    fun `nästa larm ryms i första steget`() {
        val worst = AlarmScheduler.AUTH_WAIT + AlarmScheduler.AUTH_CHECK + AlarmScheduler.READ_WAIT
        assertTrue(worst <= ReceiverWork.FIRST_BUDGET, "$worst > ${ReceiverWork.FIRST_BUDGET}")
    }

    @Test
    fun `notisens läsningar ryms alltid efter nästa larm`() {
        val reads = ReminderContent.SETTINGS_WAIT + ReminderContent.READ_WAIT
        assertTrue(reads <= ReceiverWork.thenBudget(ReceiverWork.FIRST_BUDGET), "$reads")
    }

    @Test
    fun `markera tagen - läsningarna ryms (skrivningen är i cachen direkt), och tid för att visa utfallet finns kvar`() {
        assertTrue(ReminderContent.READ_WAIT < ReminderActions.MARK_BUDGET)
        assertTrue(ReminderActions.MARK_BUDGET < ReminderActions.MARK_RECEIVER_BUDGET)
        assertTrue(ReceiverWork.thenBudget(ReminderActions.MARK_RECEIVER_BUDGET).isPositive())
        assertTrue(ReminderActions.MARK_RECEIVER_BUDGET < ReceiverWork.TOTAL_BUDGET)
    }
}
