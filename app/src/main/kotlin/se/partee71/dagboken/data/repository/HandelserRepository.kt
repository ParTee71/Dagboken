package se.partee71.dagboken.data.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import se.partee71.dagboken.data.room.daos.HandelseDao
import se.partee71.dagboken.data.room.entities.toDomain
import se.partee71.dagboken.data.room.entities.toEntity
import se.partee71.dagboken.domain.model.Handelse
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class HandelserRepository @Inject constructor(
    private val dao: HandelseDao,
) {
    val all: Flow<List<Handelse>> = dao.getAllFlow().map { it.map { e -> e.toDomain() } }

    fun fromDate(days: Int): Flow<List<Handelse>> {
        val from = LocalDate.now().minusDays(days.toLong())
            .format(DateTimeFormatter.ISO_LOCAL_DATE)
        return dao.getFromDateFlow(from).map { it.map { e -> e.toDomain() } }
    }

    suspend fun getById(id: String): Handelse? = dao.getById(id)?.toDomain()

    suspend fun save(handelse: Handelse) = dao.upsert(handelse.toEntity())

    suspend fun delete(handelse: Handelse) = dao.delete(handelse.toEntity())

    suspend fun importAll(entries: List<Handelse>) =
        dao.upsertAll(entries.map { it.toEntity() })

    suspend fun isEmpty(): Boolean = dao.count() == 0
}
