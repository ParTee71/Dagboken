package se.partee71.dagboken.data.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.LocalDate
import se.partee71.dagboken.core.model.Identified
import se.partee71.dagboken.data.common.EntityCollection
import se.partee71.dagboken.data.common.observeDates

/** En samling poster med ett datumfält: läsning per period och radering – mående, aktiviteter och händelser. */
interface DatedRepository<T> {
    /** Posterna för [from]…[to], båda inräknade, offline först (Idag per dag, Dagboken ett år i taget, HIST-8). */
    fun observeDays(from: LocalDate, to: LocalDate): Flow<List<T>>

    /** Tar bort posten permanent med sin anteckning (efter `ConfirmDialog` i UI:t, HIST-5, DAT-7). */
    suspend fun delete(id: String): Result<Unit>
}

/** Den enda implementationen av [DatedRepository] – över [collection] och codecens datumfält [dateField]. */
class DatedEntries<T : Identified>(private val collection: EntityCollection<T>, private val dateField: String) : DatedRepository<T> {
    override fun observeDays(from: LocalDate, to: LocalDate): Flow<List<T>> = collection.observeDates(dateField, from, to)

    override suspend fun delete(id: String): Result<Unit> = collection.delete(id)
}
