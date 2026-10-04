package se.partee71.dagboken.data.firestore

import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.FirebaseFirestoreException.Code
import kotlin.test.assertEquals
import kotlin.time.Instant
import kotlinx.coroutines.CancellationException
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import se.partee71.dagboken.data.common.DataError

/** Robolectric: Firestores undantag använder Android-klasser. */
@RunWith(RobolectricTestRunner::class)
class FirestoreValuesTest {

    private val instant = Instant.fromEpochSeconds(1_790_000_000, 123_000_000)
    private val timestamp = Timestamp(1_790_000_000, 123_000_000)

    @Test
    fun `Instant blir Timestamp före skrivning, även nästlat, och tillbaka efter läsning`() {
        val app = mapOf("createdAt" to instant, "nested" to mapOf("at" to instant), "list" to listOf(instant), "name" to "Anna")
        val stored = mapOf("createdAt" to timestamp, "nested" to mapOf("at" to timestamp), "list" to listOf(timestamp), "name" to "Anna")
        assertEquals(stored, toFirestore(app))
        assertEquals(app, fromFirestore(stored))
    }

    @Test
    fun `Firestores felkoder mappas till DataError en gång`() {
        fun error(code: Code) = firestoreError(FirebaseFirestoreException("x", code))
        assertEquals(DataError.Offline, error(Code.UNAVAILABLE))
        assertEquals(DataError.Offline, error(Code.DEADLINE_EXCEEDED))
        assertEquals(DataError.PermissionDenied, error(Code.PERMISSION_DENIED))
        assertEquals(DataError.PermissionDenied, error(Code.UNAUTHENTICATED))
        assertEquals(DataError.Cancelled, error(Code.CANCELLED))
        assertEquals(DataError.NotFound, error(Code.NOT_FOUND))
        assertEquals(DataError.Unknown, error(Code.INTERNAL))
        assertEquals(DataError.Unknown, firestoreError(IllegalStateException()))
        assertEquals(DataError.UpdateRequired, firestoreError(DataError.UpdateRequired))
    }

    @Test
    fun `ett fel som snapshots() lindat in i ett avbrott mappas efter sin orsak`() {
        val denied = FirebaseFirestoreException("nekad", Code.PERMISSION_DENIED)
        val wrapped = CancellationException("Error getting DocumentReference snapshot", denied)
        assertEquals(Code.PERMISSION_DENIED, wrapped.firestoreCode())
        assertEquals(DataError.PermissionDenied, firestoreError(wrapped))
        assertEquals(null, CancellationException("avbrutet").firestoreCode())
        assertEquals(DataError.Unknown, firestoreError(CancellationException("avbrutet")))
    }

    @Test
    fun `sökvägarna ligger under användaren`() {
        assertEquals("users/u", Paths.user("u"))
        assertEquals("users/u/settings", Paths.settings("u"))
        assertEquals("users/u/doses", Paths.doses("u"))
        assertEquals("users/u/illnessEpisodes/e/checkins", Paths.checkins("u", "e"))
    }

    @Test
    fun `alla samlingar i ARKITEKTUR-md, Datamodell, finns – i tabellens ordning`() {
        assertEquals(
            listOf("settings", "options", "prescriptions", "prnMedicines", "doses", "screenings", "activities", "events", "illnessEpisodes"),
            Paths.USER_COLLECTIONS,
        )
        assertEquals(mapOf("illnessEpisodes" to listOf("checkins")), Paths.SUBCOLLECTIONS)
        Paths.USER_COLLECTIONS.forEach { assertEquals("users/u/$it", Paths.collection("u", it)) }
        assertEquals(
            listOf(Paths.options("u"), Paths.prescriptions("u"), Paths.prnMedicines("u"), Paths.screenings("u"), Paths.activities("u"), Paths.events("u"), Paths.illnessEpisodes("u")),
            listOf("options", "prescriptions", "prnMedicines", "screenings", "activities", "events", "illnessEpisodes").map { "users/u/$it" },
        )
    }
}
