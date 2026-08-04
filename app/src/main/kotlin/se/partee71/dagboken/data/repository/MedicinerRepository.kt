package se.partee71.dagboken.data.repository

import android.content.Context
import android.util.Log
import androidx.room.withTransaction
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import se.partee71.dagboken.data.room.AppDatabase
import se.partee71.dagboken.data.room.daos.FavoritDao
import se.partee71.dagboken.data.room.daos.MedicinDao
import se.partee71.dagboken.data.room.daos.ReceptDao
import se.partee71.dagboken.data.room.entities.toDomain
import se.partee71.dagboken.data.room.entities.toEntity
import se.partee71.dagboken.domain.model.Dosperiod
import se.partee71.dagboken.domain.model.Favorit
import se.partee71.dagboken.domain.model.Medicin
import se.partee71.dagboken.domain.model.NoteTarget
import se.partee71.dagboken.domain.model.Recept
import se.partee71.dagboken.domain.model.dosFor
import se.partee71.dagboken.domain.model.hasExpiredOn
import se.partee71.dagboken.domain.model.tidpunktToHour
import se.partee71.dagboken.domain.usecase.EnsureTodayEntriesUseCase
import se.partee71.dagboken.widget.WidgetUpdater
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MedicinerRepository @Inject constructor(
    private val db: AppDatabase,
    private val medicinDao: MedicinDao,
    private val receptDao: ReceptDao,
    private val favoritDao: FavoritDao,
    private val noteRepo: NoteRepository,
    private val ensureTodayEntries: EnsureTodayEntriesUseCase,
    private val json: Json,
    @ApplicationContext private val appContext: Context,
) {

    /**
     * WID-4: hemskärmswidgetarna ska aldrig visa inaktuellt läge. Uppdateringen låg
     * tidigare bara på två anropsplatser i UI-lagret, så de flesta skrivvägar (spara,
     * hoppa över, radera, nya schemalagda doser) lämnade widgetarna med gammal data.
     * Här ligger den i stället intill själva skrivningen, så alla vägar täcks.
     */
    private fun refreshWidgets() = WidgetUpdater.requestUpdate(appContext)

    // ─── Medicin ──────────────────────────────────────────────────────────────
    fun todayFlow(): Flow<List<Medicin>> = entriesForDate(LocalDate.now())

    /** Same as [todayFlow] but for any date — backs Idag's datumnavigering (#114). */
    fun entriesForDate(date: LocalDate): Flow<List<Medicin>> {
        val datum = date.format(DateTimeFormatter.ISO_LOCAL_DATE)
        return medicinDao.getTodayFlow(datum).map { list -> list.map { it.toDomain() } }
    }

    val allMediciner: Flow<List<Medicin>> =
        medicinDao.getAllFlow().map { list -> list.map { it.toDomain() } }

    /** Historik (HIST-7) — endast faktiskt tagna, ej överhoppade doser. */
    val takenMediciner: Flow<List<Medicin>> =
        medicinDao.getTakenFlow().map { list -> list.map { it.toDomain() } }

    suspend fun getMedicinById(id: String): Medicin? = medicinDao.getById(id)?.toDomain()

    suspend fun saveMedicin(medicin: Medicin) {
        medicinDao.upsert(medicin.toEntity())
        refreshWidgets()
    }

    /** Raderar dosen och dess anteckning tillsammans (DAT-4). */
    suspend fun deleteMedicin(medicin: Medicin) {
        medicinDao.delete(medicin.toEntity())
        noteRepo.delete(NoteTarget.MEDICATION, medicin.id)
        refreshWidgets()
    }

    /** Sätter/nollställer tagningstidpunkten (MED-14) tillsammans med tagen-flaggan. */
    suspend fun toggleTagen(id: String, tagen: Boolean) {
        medicinDao.updateTagen(id, tagen, if (tagen) nowTid() else null)
        refreshWidgets()
    }

    /**
     * Marks every scheduled, still-pending dose for today as taken and returns the
     * count. Backs the "Markera tagen"-notification action so a reminder can be
     * cleared without opening the app. Vid behov-doser (no scheduled hour) are
     * left untouched — those are logged explicitly from the Idag-checklist.
     */
    suspend fun markTodayDosesTaken(): Int {
        val due = todayFlow().first().filter {
            !it.tagen && !it.skipped && tidpunktToHour(it.tidpunkt) != null
        }
        val tagenTid = nowTid()
        due.forEach { medicinDao.updateTagen(it.id, true, tagenTid) }
        refreshWidgets()
        return due.size
    }

    suspend fun skipMedicin(id: String) {
        medicinDao.markSkipped(id)
        refreshWidgets()
    }

    suspend fun getLastTaken(namn: String): Medicin? = medicinDao.getLastTaken(namn)?.toDomain()

    /** Senaste tagna dosen vid eller före [beforeTimestamp] — cooldown för efterhandsloggning (MED-16). */
    suspend fun getLastTakenBefore(namn: String, beforeTimestamp: String): Medicin? =
        medicinDao.getLastTakenBefore(namn, beforeTimestamp)?.toDomain()

    private fun nowTid(): String =
        LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm"))

    suspend fun countDailyDoses(datum: String, namn: String): Int =
        medicinDao.countDailyDoses(datum, namn)

    // ─── Recept ───────────────────────────────────────────────────────────────
    val allRecept: Flow<List<Recept>> = receptDao.getAllFlow().map { list ->
        list.map { it.toDomain(::decodeStringList, ::decodeIntList, ::decodeDosperioder) }
    }

    suspend fun getReceptById(id: String): Recept? =
        receptDao.getById(id)?.toDomain(::decodeStringList, ::decodeIntList, ::decodeDosperioder)

    suspend fun saveRecept(recept: Recept) {
        receptDao.upsert(recept.toEntity(::encodeStringList, ::encodeIntList, ::encodeDosperioder))
        syncPendingDoses(recept)
        refreshWidgets()
    }

    /**
     * REC-10 — otagna, ej överhoppade doser från och med idag följer receptets nya period
     * och dosperioder: doser utanför perioden (eller utanför upprepningsmönstret) tas bort,
     * övriga får rätt dos/enhet. Tagna och överhoppade doser rörs aldrig.
     */
    private suspend fun syncPendingDoses(recept: Recept, today: LocalDate = LocalDate.now()) {
        val fromDatum = today.format(DateTimeFormatter.ISO_LOCAL_DATE)
        val pending = medicinDao.getPendingByReceptFrom(recept.id, fromDatum)
        if (pending.isEmpty()) return

        // Doser blir obsoleta både när dagen faller utanför receptets period/upprepning
        // och när själva tidpunkten tagits bort ur receptet — det senare missades förut,
        // så en borttagen tidpunkt lämnade sin otagna dos kvar i checklistan för alltid.
        val aktivaTidpunkter = recept.tidpunkter.ifEmpty { listOf("Morgon") }.toSet()
        val (obsolete, kept) = pending.partition { entry ->
            val date = runCatching {
                LocalDate.parse(entry.datum, DateTimeFormatter.ISO_LOCAL_DATE)
            }.getOrNull() ?: return@partition false
            !ensureTodayEntries.shouldTakeToday(recept, date) || entry.tidpunkt !in aktivaTidpunkter
        }

        obsolete.forEach {
            medicinDao.delete(it)
            noteRepo.delete(NoteTarget.MEDICATION, it.id)
        }

        val updated = kept.mapNotNull { entry ->
            val date = runCatching {
                LocalDate.parse(entry.datum, DateTimeFormatter.ISO_LOCAL_DATE)
            }.getOrNull() ?: return@mapNotNull null
            val (dos, enhet) = recept.dosFor(date)
            entry.takeIf { it.dos != dos || it.enhet != enhet || it.namn != recept.namn }
                ?.copy(dos = dos, enhet = enhet, namn = recept.namn)
        }
        if (updated.isNotEmpty()) medicinDao.upsertAll(updated)
    }

    suspend fun deleteRecept(recept: Recept) {
        receptDao.delete(recept.toEntity(::encodeStringList, ::encodeIntList, ::encodeDosperioder))
        noteRepo.delete(NoteTarget.RECEPT, recept.id)
    }

    suspend fun toggleReceptAktiv(id: String, aktiv: Boolean) =
        receptDao.updateAktiv(id, aktiv)

    // ─── Favorit ──────────────────────────────────────────────────────────────
    val allFavoriter: Flow<List<Favorit>> =
        favoritDao.getAllFlow().map { list -> list.map { it.toDomain() } }

    suspend fun getFavoritById(id: String): Favorit? = favoritDao.getById(id)?.toDomain()

    suspend fun saveFavorit(favorit: Favorit) {
        favoritDao.upsert(favorit.toEntity())
        refreshWidgets()
    }

    suspend fun deleteFavorit(favorit: Favorit) {
        favoritDao.delete(favorit.toEntity())
        noteRepo.delete(NoteTarget.FAVORIT, favorit.id)
        refreshWidgets()
    }

    suspend fun setFavoritFavorite(id: String, isFavorite: Boolean) =
        favoritDao.updateFavorite(id, isFavorite)

    // ─── ensureTodayEntries ───────────────────────────────────────────────────
    /**
     * Creates synthetic Medicin entries for all active Recept that should fire today.
     * Idempotent — stable IDs prevent duplicates.
     */
    suspend fun ensureTodayEntries() = ensureEntriesForDate(LocalDate.now())

    /**
     * Same as [ensureTodayEntries] but for any date — lets Idag's datumnavigering (#114)
     * seed a past date's scheduled doses on demand (e.g. a date never opened while it was
     * "today"). Idempotent for the same reason: stable, date-scoped IDs.
     */
    suspend fun ensureEntriesForDate(date: LocalDate) {
        val datum = date.format(DateTimeFormatter.ISO_LOCAL_DATE)
        val newEntries = db.withTransaction {
            val active = receptDao.getActive()
                .map { it.toDomain(::decodeStringList, ::decodeIntList, ::decodeDosperioder) }
            // REC-8 — recept vars period passerats markeras som avslutade (aldrig raderade).
            // Utvärderas mot dagens datum, inte mot [date], så att bläddring bakåt i
            // Idag-vyn (HEM-14) inte återuppväcker eller avslutar något felaktigt.
            val today = LocalDate.now()
            val (expired, recept) = active.partition { it.hasExpiredOn(today) }
            expired.forEach { receptDao.updateAktiv(it.id, false) }
            val existing = medicinDao.getByDate(datum).map { it.toDomain() }
            val newEntries = ensureTodayEntries.compute(recept, existing, date)
            if (newEntries.isNotEmpty()) {
                medicinDao.upsertAll(newEntries.map { it.toEntity() })
            }
            newEntries
        }
        // A recept's note is a default carried forward onto each dose it generates for the day.
        newEntries.forEach { entry ->
            val receptId = entry.receptId ?: return@forEach
            val receptNote = noteRepo.observe(NoteTarget.RECEPT, receptId).first()
            if (receptNote.isNotBlank()) noteRepo.save(NoteTarget.MEDICATION, entry.id, receptNote)
        }
        if (newEntries.isNotEmpty()) refreshWidgets()
    }

    // ─── Import (migration) ───────────────────────────────────────────────────
    suspend fun importMediciner(entries: List<Medicin>) =
        medicinDao.upsertAll(entries.map { it.toEntity() })

    suspend fun importRecept(entries: List<Recept>) =
        receptDao.upsertAll(entries.map {
            it.toEntity(::encodeStringList, ::encodeIntList, ::encodeDosperioder)
        })

    suspend fun importFavoriter(entries: List<Favorit>) =
        favoritDao.upsertAll(entries.map { it.toEntity() })

    suspend fun isMedicinerEmpty(): Boolean = medicinDao.count() == 0

    // ─── JSON type converters for ReceptEntity ────────────────────────────────
    private fun encodeStringList(list: List<String>): String = json.encodeToString(list)
    private fun encodeIntList(list: List<Int>): String = json.encodeToString(list)
    // Loggar aldrig `raw` — det är persisterad användardata och loggning sker även i
    // releasebygget. Fältnamnet räcker för att felsöka (NFR-13).
    private fun decodeStringList(raw: String): List<String> =
        runCatching { json.decodeFromString<List<String>>(raw) }
            .onFailure { Log.w("MedicinerRepo", "decodeStringList failed") }
            .getOrDefault(emptyList())

    private fun decodeIntList(raw: String): List<Int> =
        runCatching { json.decodeFromString<List<Int>>(raw) }
            .onFailure { Log.w("MedicinerRepo", "decodeIntList failed") }
            .getOrDefault(emptyList())

    private fun encodeDosperioder(list: List<Dosperiod>): String = json.encodeToString(list)

    private fun decodeDosperioder(raw: String): List<Dosperiod> =
        runCatching { json.decodeFromString<List<Dosperiod>>(raw) }
            .onFailure { Log.w("MedicinerRepo", "decodeDosperioder failed") }
            .getOrDefault(emptyList())
}
