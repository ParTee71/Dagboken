package se.partee71.dagboken.data.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import se.partee71.dagboken.data.room.daos.AktivitetDao
import se.partee71.dagboken.data.room.entities.toDomain
import se.partee71.dagboken.data.room.entities.toEntity
import se.partee71.dagboken.domain.model.Aktivitet
import se.partee71.dagboken.domain.model.NoteTarget
import se.partee71.dagboken.domain.usecase.SymptomUtils
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AktiviteterRepository @Inject constructor(
    private val dao: AktivitetDao,
    private val noteRepo: NoteRepository,
) {
    val all: Flow<List<Aktivitet>> = dao.getAllFlow().map { list -> list.map { it.toDomain() } }

    // "Idag" beräknas när flödet börjar samlas in, inte när det skapas — annars
    // fastnar ett långlivat flöde (t.ex. i en ViewModel) på det datum appen startade
    // och rullar aldrig över vid midnatt.
    fun fromDate(days: Int): Flow<List<Aktivitet>> = flow {
        emitAll(dao.getFromDateFlow(fromDatum(days)).map { list -> list.map { it.toDomain() } })
    }

    fun screeningFromDate(days: Int): Flow<List<Aktivitet>> = flow {
        emitAll(dao.getScreeningFromDate(fromDatum(days)).map { list -> list.map { it.toDomain() } })
    }

    private fun fromDatum(days: Int): String =
        LocalDate.now().minusDays(days.toLong()).format(DateTimeFormatter.ISO_LOCAL_DATE)

    suspend fun getById(id: String): Aktivitet? = dao.getById(id)?.toDomain()

    suspend fun getRecent(type: String, limit: Int = 3): List<Aktivitet> =
        dao.getRecent(type, limit).map { it.toDomain() }

    suspend fun save(aktivitet: Aktivitet) = dao.upsert(aktivitet.toEntity())

    /**
     * Raderar posten och dess anteckning i samma anrop. Anteckningsstädningen låg
     * tidigare i varje ViewModel, vilket gjorde att raderingsvägar utan ViewModel
     * (Historik, framtida use case) lämnade föräldralösa `notes`-rader kvar
     * som följde med i varje backup (DAT-4).
     */
    suspend fun delete(aktivitet: Aktivitet) {
        dao.delete(aktivitet.toEntity())
        noteRepo.delete(NoteTarget.forAktivitet(aktivitet.type), aktivitet.id)
    }

    /**
     * Byter namn på ett aktivitetsalternativ i redan loggade poster (HAN-9). Utan detta
     * blev historiken kvar på det gamla namnet medan listan visade det nya.
     */
    suspend fun renameAktivitet(old: String, new: String) = dao.renameAktivitet(old, new)

    /**
     * Byter namn på ett symptom inuti den kodade `symptom`-strängen. Strängen är ett
     * "Namn:Värde,Namn:Värde"-format, så raderna måste avkodas och kodas om — en ren
     * SQL-ersättning skulle träffa delsträngar i andra symptomnamn.
     */
    suspend fun renameSymptom(old: String, new: String) {
        val updated = dao.withSymptomContaining(old).mapNotNull { entity ->
            val scores = SymptomUtils.decode(entity.symptom)
            if (old !in scores) return@mapNotNull null
            val renamed = scores.mapKeys { (name, _) -> if (name == old) new else name }
            entity.copy(symptom = SymptomUtils.encode(renamed))
        }
        if (updated.isNotEmpty()) dao.upsertAll(updated)
    }

    suspend fun importAll(entries: List<Aktivitet>) =
        dao.upsertAll(entries.map { it.toEntity() })

    suspend fun isEmpty(): Boolean = dao.count() == 0

    /**
     * Dagens screeningposter. Screeningpåminnelsen frågar efter dem för att se om just
     * dess måltidshändelse redan är loggad (NOT-19); tidigare fanns en `hasScreeningToday`
     * som bara räknade dagens screeningar, och den tystade alla påminnelser efter den
     * första.
     */
    suspend fun getScreeningToday(): List<Aktivitet> {
        val today = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)
        return dao.getScreeningToday(today).map { it.toDomain() }
    }
}
