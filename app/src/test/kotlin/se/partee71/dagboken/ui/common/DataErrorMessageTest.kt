package se.partee71.dagboken.ui.common

import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import se.partee71.dagboken.data.common.DataError

@RunWith(RobolectricTestRunner::class)
class DataErrorMessageTest {

    private val all = listOf(
        DataError.Offline, DataError.PermissionDenied, DataError.Cancelled,
        DataError.UpdateRequired, DataError.SignInRejected, DataError.NotSignedIn, DataError.NotFound, DataError.QuotaExceeded, DataError.Unknown,
    )

    @Test
    fun `varje fel har en egen svensk text`() {
        val context = RuntimeEnvironment.getApplication()
        val texts = all.map { context.getString(it.toMessage()) }
        assertEquals(all.size, texts.toSet().size, "två fel delar text: $texts")
        assertTrue(texts.all { it.isNotBlank() })
        assertEquals("Något gick fel. Försök igen.", context.getString(DataError.Unknown.toMessage()))
    }
}
