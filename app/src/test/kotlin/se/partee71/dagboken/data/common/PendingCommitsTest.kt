package se.partee71.dagboken.data.common

import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test

/** En commit som inte hann bekräftas spåras tills den är klar – också när väntan avbryts. */
@OptIn(ExperimentalCoroutinesApi::class)
class PendingCommitsTest {

    @Test
    fun `en hängande commit spåras efter tidsgränsen och frigörs när den blir klar`() = runTest {
        val commits = PendingCommits()
        val commit = CompletableDeferred<Unit>()

        assertNull(commits.await(commit, 15.seconds), "inte klar i tid")
        assertFalse(commits.awaitAll(1.seconds), "fortfarande spårad")

        commit.complete(Unit)
        assertTrue(commits.awaitAll(1.seconds), "klar – inget kvar att vänta på")
        assertEquals(Unit, commits.await(CompletableDeferred(Unit), 1.seconds), "en klar commit ger sitt värde")
    }

    @Test
    fun `en commit vars väntan avbryts före tidsgränsen spåras ändå`() = runTest {
        val commits = PendingCommits()
        val commit = CompletableDeferred<Unit>()
        val waiter = launch { commits.await(commit, 15.seconds) }
        runCurrent()
        waiter.cancel()
        runCurrent()

        assertFalse(commits.awaitAll(1.seconds), "avbruten väntan – commiten kan ändå landa och spåras")
        commit.complete(Unit)
        assertTrue(commits.awaitAll(1.seconds))
    }
}
