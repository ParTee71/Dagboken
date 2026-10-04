package se.partee71.dagboken.data.common

import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.withTimeoutOrNull
import se.partee71.dagboken.core.model.Identified
import se.partee71.dagboken.core.model.Sortable

/**
 * En samling dokument av en modell – samma kontrakt för riktig Firestore
 * (`FirestoreCollection`) och för tester (`FakeCollection`), bevisat av `CollectionContract`.
 *
 * - Läsning är flöden från den lokala cachen och följer den inloggade användaren; utloggad
 *   emitteras inget. Listor sorteras på `sortOrder` när modellen är `Sortable`.
 * - Skrivning läggs i cachen direkt och synkas när nätet finns (offline först); `Result`
 *   rapporterar bara fel som syns direkt. Fel från synken kommer via [SyncStatus.lastWriteError].
 * - `upsert` slår ihop med det lagrade dokumentet: okända fält bevaras, `null` tömmer (BCK-3).
 */
interface EntityCollection<T : Identified> {
    fun observe(): Flow<List<T>>

    fun observe(id: String): Flow<T?>

    suspend fun get(id: String): Result<T?>

    suspend fun getAll(): Result<List<T>>

    suspend fun upsert(item: T): Result<Unit>

    suspend fun delete(id: String): Result<Unit>

    /** Ändrar bara `archived`; ett dokument som inte finns skapas inte. */
    suspend fun setArchived(id: String, archived: Boolean): Result<Unit>

    /**
     * Skriver bara [fields] ur [item], kodade som vid [upsert] – t.ex. en dos status – och
     * `updatedAt`. Varje fält ersätts helt (en map slås inte ihop). Andra fält som ändrats under
     * tiden (på en annan enhet, i ett annat formulär) skrivs inte över, och ett dokument som inte
     * finns skapas inte. Ett fältnamn som codecen inte känner till är ett fel.
     */
    suspend fun update(item: T, fields: Set<String>): Result<Unit> = updateAll(listOf(item), fields)

    /** [update] för flera på en gång – atomärt inom varje bit om 500 (Firestores gräns). */
    suspend fun updateAll(items: List<T>, fields: Set<String>): Result<Unit>

    /** Flera skrivningar i ett svep – atomärt inom varje bit om 500 (Firestores gräns). */
    suspend fun batch(upserts: List<T>, deletes: List<String> = emptyList()): Result<Unit>

    /** Klientgenererat, slumpat ID – fungerar offline. */
    fun newId(): String
}

/**
 * `sortOrder` för något nytt, så att det hamnar sist i listan – en gång för alla formulär.
 * Läses ur listan som den redan finns i cachen ([EntityCollection.observe]), så att sparandet
 * inte väntar på nätet (offline först). Går listan inte att läsa i tid hamnar det först (0) i
 * stället för att sparandet stoppas.
 */
suspend fun <T> EntityCollection<T>.nextSortOrder(): Int where T : Identified, T : Sortable =
    withTimeoutOrNull(SORT_ORDER_WAIT) { observe().catch { }.firstOrNull() }?.maxOfOrNull { it.sortOrder }?.plus(1) ?: 0

/** Hur länge [nextSortOrder] väntar på listan (ur cachen tar det millisekunder; utloggad kommer den aldrig). */
private val SORT_ORDER_WAIT = 2.seconds

/** Sparar [item]; något nytt ([isNew]) hamnar sist i listan via [placeLast] – samma i alla formulär. */
suspend fun <T> EntityCollection<T>.upsertPlaced(item: T, isNew: Boolean, placeLast: T.(Int) -> T): Result<Unit> where T : Identified, T : Sortable =
    upsert(if (isNew) item.placeLast(nextSortOrder()) else item)
