package se.partee71.dagboken.data.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.LocalDate
import se.partee71.dagboken.core.model.Post
import se.partee71.dagboken.core.schema.DatedCodec
import se.partee71.dagboken.data.common.EntityCollection
import se.partee71.dagboken.data.common.observeDates
import se.partee71.dagboken.data.common.updateChanged

/** En samling poster med ett datumfält: läsning per period, formulärets läsning och sparning, och radering – mående, aktiviteter och händelser. */
interface DatedRepository<T> {
    /** Posterna för [from]…[to], båda inräknade, offline först (Idag per dag, Dagboken ett år i taget, HIST-8). */
    fun observeDays(from: LocalDate, to: LocalDate): Flow<List<T>>

    /** Posten [id] för formuläret (`null` = finns inte), offline först. */
    suspend fun get(id: String): Result<T?>

    /**
     * Sparar formuläret (SCR-1, AKT-9, HAN-1). [loaded] `null` = ny: [edited] (från repositoryts `new`) skrivs som
     * den är, med sitt id och sin `createdAt` – satta **en gång** när formuläret öppnades, så att varje nytt försök
     * skriver samma dokument med samma skapandetid; utan `createdAt` är det ett fel ([IllegalArgumentException]) och
     * ingenting skrivs. En befintlig skriver bara de fält som skiljer [edited] från [loaded] (`updateChanged`): id:t,
     * skapandetiden och fält som en annan enhet eller en nyare app skrivit står kvar, och en post som raderats under
     * tiden återuppstår inte. Offline först.
     */
    suspend fun save(loaded: T?, edited: T): Result<Unit>

    /** Tar bort posten permanent med sin anteckning (efter `ConfirmDialog` i UI:t, HIST-5, DAT-7). */
    suspend fun delete(id: String): Result<Unit>
}

/**
 * Den enda implementationen av [DatedRepository] – över [collection] och dess [codec] (datumfältet, och fälten som
 * jämförs vid en ändring). Postens skapandetid och id kommer ur modellen ([Post]).
 */
class DatedEntries<T : Post<T>>(private val collection: EntityCollection<T>, private val codec: DatedCodec<T>) : DatedRepository<T> {
    override fun observeDays(from: LocalDate, to: LocalDate): Flow<List<T>> = collection.observeDates(codec.dateField, from, to)

    override suspend fun get(id: String): Result<T?> = collection.get(id)

    override suspend fun save(loaded: T?, edited: T): Result<Unit> = when {
        loaded != null -> collection.updateChanged(codec, loaded, edited.withId(loaded.id))
        edited.createdAt == null -> Result.failure(IllegalArgumentException("En ny post skapas med new()"))
        else -> collection.upsert(edited)
    }

    override suspend fun delete(id: String): Result<Unit> = collection.delete(id)
}
