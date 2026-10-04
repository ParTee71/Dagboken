package se.partee71.dagboken.data.firestore

import android.os.Looper
import com.google.android.gms.tasks.TaskCompletionSource
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import se.partee71.dagboken.data.common.DataError

/** Robolectric: Task-lyssnarna körs på huvudtråden. */
@RunWith(RobolectricTestRunner::class)
class FirestoreSyncStatusTest {

    private val status = FirestoreSyncStatus(TestScope(UnconfinedTestDispatcher()))

    @Test
    fun `synkar tills alla skrivningar fått svar från servern`() {
        val first = TaskCompletionSource<Void>()
        val second = TaskCompletionSource<Void>()
        status.track(first.task)
        status.track(second.task)
        assertTrue(status.syncing.value)
        first.setResult(null)
        shadowOf(Looper.getMainLooper()).idle()
        assertTrue(status.syncing.value)
        second.setResult(null)
        shadowOf(Looper.getMainLooper()).idle()
        assertFalse(status.syncing.value)
        assertNull(status.lastWriteError.value)
    }

    @Test
    fun `ett sent serverfel ligger kvar tills det visats`() {
        val task = TaskCompletionSource<Void>()
        status.track(task.task)
        task.setException(FirebaseFirestoreException("nekad", FirebaseFirestoreException.Code.PERMISSION_DENIED))
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(DataError.PermissionDenied, status.lastWriteError.value)
        assertEquals(DataError.PermissionDenied, status.lastWriteError.value, "läses igen av en ny prenumerant")
        status.clearWriteError()
        assertNull(status.lastWriteError.value)
        assertFalse(status.syncing.value)
    }
}
