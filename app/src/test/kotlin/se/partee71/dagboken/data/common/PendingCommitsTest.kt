package se.partee71.dagboken.data.common

import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration
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

        var unfinished = 0
        assertNull(commits.await(commit, 15.seconds, onUnfinished = { unfinished++ }), "inte klar i tid")
        assertEquals(1, unfinished)
        assertFalse(commits.awaitAll(1.seconds), "fortfarande spårad")

        commit.complete(Unit)
        assertTrue(commits.awaitAll(1.seconds), "klar – inget kvar att vänta på")
        assertEquals(Unit, commits.await(CompletableDeferred(Unit), 1.seconds, onUnfinished = { unfinished++ }), "en klar commit ger sitt värde")
        assertEquals(1, unfinished, "en klar commit spåras inte")
    }

    @Test
    fun `en commit vars väntan avbryts före tidsgränsen spåras ändå`() = runTest {
        val commits = PendingCommits()
        val commit = CompletableDeferred<Unit>()
        var unfinished = 0
        val waiter = launch { commits.await(commit, 15.seconds, onUnfinished = { unfinished++ }) }
        runCurrent()
        waiter.cancel()
        runCurrent()

        assertFalse(commits.awaitAll(1.seconds), "avbruten väntan – commiten kan ändå landa och spåras")
        assertEquals(1, unfinished, "också vid avbrott – så att ett sent fel syns i SyncStatus")
        commit.complete(Unit)
        assertTrue(commits.awaitAll(1.seconds))
    }

    @Test
    fun `en commit som blir klar precis vid tidsgränsen ger sitt värde och spåras inte`() = runTest {
        val commits = PendingCommits()
        var unfinished = 0
        // Tidsgränsen noll löper ut innan väntan börjar – commiten är ändå klar.
        assertEquals("ok", commits.await(CompletableDeferred("ok"), Duration.ZERO, onUnfinished = { unfinished++ }))
        assertEquals(0, unfinished)
    }
}
