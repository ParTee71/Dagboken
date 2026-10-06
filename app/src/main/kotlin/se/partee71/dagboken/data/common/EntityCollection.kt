package se.partee71.dagboken.data.common

import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import se.partee71.dagboken.core.model.Identified
import se.partee71.dagboken.core.model.Sortable
import se.partee71.dagboken.core.schema.DocCodec

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
     * finns skapas inte. Ett fältnamn som codecen inte känner till är ett fel. Toppfälten i [remove]
     * tas bort ur dokumentet (Firestores `FieldValue.delete()`) – för ett fält som codecen inte längre
     * skriver ([updateChanged]).
     */
    suspend fun update(item: T, fields: Set<String>, remove: Set<String> = emptySet()): Result<Unit> = updateAll(listOf(item), fields, remove)

    /**
     * Skriver bara [fields] ur [item], kodade som vid [upsert], med djup merge – och `updatedAt` när
     * modellen har det. Ett fält är en [FieldPath]: ett toppfält (`listOf("theme")`) eller en väg in
     * i en nästlad map (`listOf("theme", "mode")`), segment för segment så att en punkt i en nyckel
     * aldrig tolkas som en väg. Allt som inte står i [fields] – andra fält, andra nycklar i samma map,
     * okända fält från en nyare app – står kvar som det är lagrat. Saknas dokumentet skapas det med
     * bara [fields]. Ingen läsning, så det fungerar offline och skriver aldrig tillbaka en gammal
     * kopia av något annat. Ett fält som codecen inte skriver är ett fel. Se [changedFields].
     */
    suspend fun merge(item: T, fields: Set<FieldPath>): Result<Unit>

    /**
     * Listan inför en skrivning, offline först ([firstFromCache]): direkt ur cachen när den kan
     * svara (Firestore delar lyssnaren med skärmar som redan följer listan), annars från servern
     * när nätet finns. Utloggad: [DataError.NotSignedIn] direkt. En tom lista ur cachen offline är
     * ett svar – kontrollen är aldrig starkare än cachen.
     */
    suspend fun cached(): Result<List<T>>

    /**
     * Dokumentet [id] som i [cached]. `null` = finns inte – bara när cachen eller servern vet det;
     * saknas det i cachen och servern inte kan nås blir det [DataError.Offline] direkt.
     */
    suspend fun cached(id: String): Result<T?>

    /**
     * Dokumenten vars toppfält [field] är minst [from] – värdet som codecen skriver det (text jämförs
     * som text, tal som tal; dokument där fältet saknas eller har en annan typ kommer inte med) –
     * **bekräftade av servern**: en avgränsad läsning inför en skrivning, t.ex. dagens och senare
     * doser i stället för hela samlingen. Till skillnad från [cached] godtas aldrig en ofullständig
     * lista ur cachen: utan svar från servern blir det [DataError.Offline] (direkt när Firestore
     * vet att den är offline, annars efter skyddsnätet). Utloggad: [DataError.NotSignedIn].
     */
    suspend fun confirmedFrom(field: String, from: Any): Result<List<T>>

    /** Hela samlingen **bekräftad av servern**, som [confirmedFrom] – för beslut som inte får fattas på cachen. */
    suspend fun confirmed(): Result<List<T>>

    /** Dokumentet [id] **bekräftat av servern** (`null` = finns inte), som [confirmedFrom]. */
    suspend fun confirmed(id: String): Result<T?>

    /**
     * Väntar tills appens egna skrivningar nått servern – också villkorade skrivningar som inte hann
     * bekräftas i tid men fortfarande kan committa – så att en serverbekräftad läsning efteråt ser
     * dem. Offline (eller längre än skyddsnätet): [DataError.Offline].
     */
    suspend fun awaitWrites(): Result<Unit>

    /**
     * Skapar de av [items] vars dokument **bevisligen inte finns**: servern läser varje id i en
     * transaktion och skriver bara de som saknas, så ett dokument som finns – också ett som en
     * annan enhet just skapat och som cachen här inte känner till, eller ett som inte går att avkoda –
     * skrivs aldrig över ([Stored]). Kräver nät: offline blir det [DataError.Offline] och ingenting
     * skrivs (läggs inte i kö). Hinner servern inte svara inom skyddsnätet blir det också `Offline`,
     * men transaktionen spåras tills den är klar ([awaitWrites] väntar in den, sena fel syns i `SyncStatus`).
     */
    suspend fun createIfAbsent(items: List<T>): Result<Unit>

    /**
     * Tar bort de av [ids] vars **lagrade** dokument uppfyller [condition], prövat mot serverns
     * version i en transaktion – inte mot anroparens kopia, som kan vara inaktuell (t.ex. en dos som
     * tagits på en annan enhet). Ett id som inte finns eller inte går att avkoda hoppas över. Kräver nät
     * som [createIfAbsent].
     */
    suspend fun deleteIf(ids: List<String>, condition: (T) -> Boolean): Result<Unit>

    /**
     * [update] av [fields] ur [items], men bara för de dokument vars **lagrade** version uppfyller
     * [condition] – prövat mot serverns version i en transaktion, som [deleteIf]. Ett dokument som
     * inte finns skapas inte; ett okänt fält är ett fel innan något läses. Kräver nät som
     * [createIfAbsent].
     */
    suspend fun updateIf(items: List<T>, fields: Set<String>, condition: (T) -> Boolean): Result<Unit>

    /** [update] för flera på en gång – atomärt inom varje bit om 500 (Firestores gräns). */
    suspend fun updateAll(items: List<T>, fields: Set<String>, remove: Set<String> = emptySet()): Result<Unit>

    /** Flera skrivningar i ett svep – atomärt inom varje bit om 500 (Firestores gräns). */
    suspend fun batch(upserts: List<T>, deletes: List<String> = emptyList()): Result<Unit>

    /** Klientgenererat, slumpat ID – fungerar offline. */
    fun newId(): String
}

/** Ett värde ur en lyssnare, och om det kom ur den lokala cachen utan svar från servern. */
data class Snapshot<R>(val value: R, val fromCache: Boolean)

/**
 * [EntityCollection.cached] – en gång för båda implementationerna. [listen] öppnar lyssnaren (och
 * kastar `NotSignedIn` direkt när ingen är inloggad). Den första ögonblicksbilden avgör:
 * Firestore ger den direkt ur cachen när cachen kan svara, och väntar annars på servern – eller
 * ger den ur cachen så fort den vet att den är offline. Är den ett svar ([isAnswer], t.ex. "finns"
 * eller "bekräftat saknas") blir det värdet; annars – det som efterfrågas finns inte i cachen och
 * servern kan inte nås – [DataError.Offline] direkt. [SERVER_WAIT] är bara ett skyddsnät om
 * ingen ögonblicksbild alls kommer; Firestore själv slutar vänta på servern efter högst ~10 s.
 */
suspend fun <R> firstFromCache(
    mapError: (Throwable) -> DataError,
    isAnswer: (Snapshot<R>) -> Boolean = { true },
    listen: () -> Flow<Snapshot<R>>,
): Result<R> = suspendRunCatching(mapError) {
    // Som `observe()`: ett fel i lyssnaren (från Firestore ofta inlindat i ett CancellationException)
    // mappas till DataError, så att det blir ett misslyckande och inte avbryter anroparen.
    val source = listen().catch { throw mapError(it) }
    val first = withTimeoutOrNull(SERVER_WAIT) { source.first() } ?: throw DataError.Offline
    if (isAnswer(first)) first.value else throw DataError.Offline
}

/** Ett dokument är ett svar när det finns, eller när servern bekräftat att det saknas. */
fun <T> Snapshot<T?>.isDocumentAnswer(): Boolean = value != null || !fromCache

/**
 * Skyddsnät för [firstFromCache], [EntityCollection.confirmedFrom] och de villkorade skrivningarna
 * ([EntityCollection.createIfAbsent], [EntityCollection.deleteIf], [EntityCollection.updateIf]) –
 * längre än Firestores egen väntan på servern. `FirestoreCollection` tar väntan som parameter
 * (kortare i offlinetestet).
 */
internal val SERVER_WAIT = 15.seconds

/** `sortOrder` för något nytt i en lista som redan lästs, så att det hamnar sist; 0 i en tom lista. */
fun <T : Sortable> List<T>.sortOrderAfterLast(): Int = maxOfOrNull { it.sortOrder }?.plus(1) ?: 0

/**
 * `sortOrder` för något nytt, så att det hamnar sist i listan – en gång för alla formulär.
 * Läses ur cachen ([EntityCollection.cached]), men väntar högst [SORT_ORDER_WAIT] på servern: går
 * listan inte att läsa i tid hamnar det först (0) i stället för att sparandet hänger – platsen är
 * ingen data som kan tappas.
 */
suspend fun <T> EntityCollection<T>.nextSortOrder(): Int where T : Identified, T : Sortable {
    val list = withTimeoutOrNull(SORT_ORDER_WAIT) { cached().getOrNull() } ?: return 0
    return list.sortOrderAfterLast()
}

/** Hur länge [nextSortOrder] väntar (ur cachen tar det millisekunder). */
private val SORT_ORDER_WAIT = 2.seconds

/** Sparar [item]; något nytt ([isNew]) hamnar sist i listan via [placeLast] – samma i alla formulär. */
suspend fun <T> EntityCollection<T>.upsertPlaced(item: T, isNew: Boolean, placeLast: T.(Int) -> T): Result<Unit> where T : Identified, T : Sortable =
    upsert(if (isNew) item.placeLast(nextSortOrder()) else item)

/**
 * Skriver bara de toppfält som skiljer [after] från [before] – kodade med [codec] – via
 * [EntityCollection.update]: fält som en annan enhet eller skärm ändrat under tiden (en stjärna, ett
 * fält från en nyare app) skrivs inte tillbaka, och ett dokument som raderats under tiden återuppstår
 * inte. Båda sidornas nycklar jämförs: en ändrad map (t.ex. ett schema, också med en borttagen nyckel)
 * skrivs i sin helhet, och ett toppfält som codecen inte längre skriver tas bort. Ett värde som aldrig
 * skrivs ([isWrittenValue], t.ex. `createdAt` utan värde) räknas inte. Fälten i [always] skrivs även
 * när de inte ändrats – för en ändring som måste gälla också om den lästa kopian var inaktuell. Inget
 * ändrat → ingen skrivning.
 */
suspend fun <T : Identified> EntityCollection<T>.updateChanged(codec: DocCodec<T>, before: T, after: T, always: Set<String> = emptySet()): Result<Unit> {
    val old = codec.encode(before)
    val new = codec.encode(after)
    val changed = new.filter { (key, value) -> (key !in old || old[key] != value || key in always) && isWrittenValue(key, value) }.keys
    val removed = old.keys - new.keys
    return if (changed.isEmpty() && removed.isEmpty()) Result.success(Unit) else update(after, changed, removed)
}
