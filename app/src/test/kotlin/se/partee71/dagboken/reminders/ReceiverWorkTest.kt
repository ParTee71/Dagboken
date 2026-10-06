package se.partee71.dagboken.reminders

import kotlin.test.assertEquals
import kotlin.coroutines.cancellation.CancellationException
import kotlin.test.assertTrue
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Mottagarnas steg: ett fel eller en timeout i det första hindrar aldrig notissteget och fäller inte appen. */
@RunWith(RobolectricTestRunner::class)
class ReceiverWorkTest {

    @Test
    fun `ett undantag i första steget - andra steget körs ändå`() = runTest {
        val ran = mutableListOf<String>()
        ReceiverWork.steps(first = { throw SecurityException("exakt larm") }, firstBudget = ReceiverWork.FIRST_BUDGET) { ran += "then" }
        assertEquals(listOf("then"), ran)
    }

    @Test
    fun `första steget som hänger avbryts i tid - andra steget körs ändå`() = runTest {
        val ran = mutableListOf<String>()
        ReceiverWork.steps(first = { awaitCancellation() }, firstBudget = ReceiverWork.FIRST_BUDGET) { ran += "then" }
        assertEquals(listOf("then"), ran)
    }

    @Test
    fun `ett undantag i andra steget fångas`() = runTest {
        ReceiverWork.steps(first = {}, firstBudget = ReceiverWork.FIRST_BUDGET) { error("notisen") }
    }

    @Test
    fun `ett CancellationException inifrån steget (avbruten Task) - andra steget körs ändå`() = runTest {
        val ran = mutableListOf<String>()
        ReceiverWork.steps(first = { throw CancellationException("Task avbruten") }, firstBudget = ReceiverWork.FIRST_BUDGET) { ran += "then" }
        assertEquals(listOf("then"), ran)
    }

    @Test
    fun `avbryts mottagarens coroutine själv körs inget mer`() = runTest {
        val ran = mutableListOf<String>()
        val job = launch {
            ReceiverWork.steps(first = { coroutineContext.job.cancel(); awaitCancellation() }, firstBudget = ReceiverWork.FIRST_BUDGET) { ran += "then" }
        }
        job.join()
        assertTrue(job.isCancelled)
        assertEquals(emptyList(), ran)
    }
}
