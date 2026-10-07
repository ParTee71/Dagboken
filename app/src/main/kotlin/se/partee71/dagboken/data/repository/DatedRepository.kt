package se.partee71.dagboken.data.repository

import kotlin.time.Instant
import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.LocalDate
import se.partee71.dagboken.core.model.Identified
import se.partee71.dagboken.core.model.Post
import se.partee71.dagboken.core.schema.DocCodec
import se.partee71.dagboken.core.schema.DatedCodec
import se.partee71.dagboken.data.common.EntityCollection
import se.partee71.dagboken.data.common.observeDates
import se.partee71.dagboken.data.common.updateChanged

/**
 * Läsning, sparning och radering av en post från dess formulär (`EntryEditor`) – aktivitet, händelse, dos,
 * incheckning. [DatedRepository] lägger till läsningen per period.
 */
interface EntryStore<T> {
    /** Posten [id] för formuläret (`null` = finns inte), offline först. */
    suspend fun get(id: String): Result<T?>

    /**
     * Sparar formuläret (SCR-1, AKT-9, HAN-1). [loaded] `null` = ny: [edited] (från repositoryts `new`) skrivs som
     * den är, med sitt id och sin `createdAt` – satta **en gång** när formuläret öppnades, så att varje nytt försök
     * skriver samma dokument med samma skapandetid; utan `createdAt` är det ett fel ([IllegalArgumentException]) och
     * ingenting skrivs. En befintlig skriver bara de fält som skiljer [edited] från [loaded] (`updateChanged`): id:t,
     * skapandetiden och fält som en annan enhet eller en nyare app skrivit står kvar, och en post som raderats under
     * tiden återuppstår inte. En ändring som byter skapandetiden är ett fel ([IllegalArgumentException]) och ingenting
     * skrivs (SJ-11). Offline först.
     */
    suspend fun save(loaded: T?, edited: T): Result<Unit>

    /** Tar bort posten permanent med sin anteckning (efter `ConfirmDialog` i UI:t, HIST-5, DAT-7). */
    suspend fun delete(id: String): Result<Unit>
}

/** En samling poster med ett datumfält: läsning per period, formulärets läsning och sparning, och radering – mående, aktiviteter och händelser. */
interface DatedRepository<T> : EntryStore<T> {
    /** Posterna för [from]…[to], båda inräknade, offline först (Idag per dag, Dagboken ett år i taget, HIST-8). */
    fun observeDays(from: LocalDate, to: LocalDate): Flow<List<T>>
}

/**
 * Den enda implementationen av [EntryStore] över en samling – [collection] och dess [codec] (fälten som jämförs
 * vid en ändring). [createdAt] är postens skapandetid och [withId] samma post med ett annat id (det lästa
 * dokumentets, så att en ändring skrivs där den lästes).
 */
class StoredEntries<T : Identified>(
    private val collection: EntityCollection<T>,
    private val codec: DocCodec<T>,
    private val createdAt: (T) -> Instant?,
    private val withId: (T, String) -> T,
) : EntryStore<T> {
    override suspend fun get(id: String): Result<T?> = collection.get(id)

    override suspend fun save(loaded: T?, edited: T): Result<Unit> = when {
        loaded != null -> keepsCreatedAt(loaded, edited).fold({ collection.updateChanged(codec, loaded, withId(edited, loaded.id)) }, { Result.failure(it) })
        createdAt(edited) == null -> Result.failure(IllegalArgumentException("En ny post skapas med new()"))
        else -> collection.upsert(edited)
    }

    override suspend fun delete(id: String): Result<Unit> = collection.delete(id)

    /**
     * SJ-11: en ändring av [loaded] behåller skapandetiden – [edited] har samma `createdAt` (också `null` för en äldre
     * post utan), annars [IllegalArgumentException]. Också för en ändring som inte går genom [save] (dosens flytt).
     */
    fun keepsCreatedAt(loaded: T, edited: T): Result<Unit> =
        if (createdAt(edited) == createdAt(loaded)) Result.success(Unit) else Result.failure(IllegalArgumentException("En ändring behåller skapandetiden"))
}

/** [StoredEntries] för en post ([Post]): skapandetiden och id:t ur modellen. */
fun <T : Post<T>> StoredEntries(collection: EntityCollection<T>, codec: DocCodec<T>): StoredEntries<T> =
    StoredEntries(collection, codec, { it.createdAt }, { post, id -> post.withId(id) })

/**
 * Ett formulär som bara skapar något nytt ([create]) – vid behov i efterhand, en ny sjukdomsepisod: inget lagrat
 * att läsa eller radera.
 */
fun <T> creatingStore(create: suspend (T) -> Result<Unit>): EntryStore<T> = object : EntryStore<T> {
    override suspend fun get(id: String): Result<T?> = Result.success(null)

    override suspend fun save(loaded: T?, edited: T): Result<Unit> = create(edited)

    override suspend fun delete(id: String): Result<Unit> = Result.success(Unit)
}

/**
 * Den enda implementationen av [DatedRepository] – över [collection] och dess [codec] (datumfältet, och fälten som
 * jämförs vid en ändring). Postens skapandetid och id kommer ur modellen ([Post]).
 */
class DatedEntries<T : Post<T>>(private val collection: EntityCollection<T>, private val codec: DatedCodec<T>) :
    DatedRepository<T>, EntryStore<T> by StoredEntries(collection, codec) {
    override fun observeDays(from: LocalDate, to: LocalDate): Flow<List<T>> = collection.observeDates(codec.dateField, from, to)
}
