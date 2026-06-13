package se.partee71.dagboken.data.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import se.partee71.dagboken.data.room.daos.AktivitetDao
import se.partee71.dagboken.data.room.entities.toDomain
import se.partee71.dagboken.data.room.entities.toEntity
import se.partee71.dagboken.domain.model.Aktivitet
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AktiviteterRepository @Inject constructor(
    private val dao: AktivitetDao,
) {
    val all: Flow<List<Aktivitet>> = dao.getAllFlow().map { list -> list.map { it.toDomain() } }

    fun fromDate(days: Int): Flow<List<Aktivitet>> {
        val from = LocalDate.now().minusDays(days.toLong())
            .format(DateTimeFormatter.ISO_LOCAL_DATE)
        return dao.getFromDateFlow(from).map { list -> list.map { it.toDomain() } }
    }

    fun screeningFromDate(days: Int): Flow<List<Aktivitet>> {
        val from = LocalDate.now().minusDays(days.toLong())
            .format(DateTimeFormatter.ISO_LOCAL_DATE)
        return dao.getScreeningFromDate(from).map { list -> list.map { it.toDomain() } }
    }

    suspend fun getById(id: String): Aktivitet? = dao.getById(id)?.toDomain()

    suspend fun getRecent(type: String, limit: Int = 3): List<Aktivitet> =
        dao.getRecent(type, limit).map { it.toDomain() }

    suspend fun save(aktivitet: Aktivitet) = dao.upsert(aktivitet.toEntity())

    suspend fun delete(aktivitet: Aktivitet) = dao.delete(aktivitet.toEntity())

    suspend fun importAll(entries: List<Aktivitet>) =
        dao.upsertAll(entries.map { it.toEntity() })

    suspend fun isEmpty(): Boolean = dao.count() == 0
}
