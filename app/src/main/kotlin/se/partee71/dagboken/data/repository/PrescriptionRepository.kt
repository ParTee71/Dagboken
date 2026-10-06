package se.partee71.dagboken.data.repository

import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton
import kotlin.time.Clock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn
import se.partee71.dagboken.core.engine.toDeactivateOn
import se.partee71.dagboken.core.model.Prescription
import se.partee71.dagboken.core.schema.PrescriptionCodec
import se.partee71.dagboken.data.common.CollectionFactory
import se.partee71.dagboken.data.common.DataError
import se.partee71.dagboken.data.common.LatestWins
import se.partee71.dagboken.data.common.SyncStatus
import se.partee71.dagboken.di.ApplicationScope
import se.partee71.dagboken.data.common.updateChanged

/**
 * Recepten i samlingen `prescriptions` (REC-1…REC-13). Ett recept skrivs aldrig om i sin helhet härifrån:
 * reglaget skriver bara `active`, formuläret bara de fält som ändrats (DAT-10).
 */
interface PrescriptionRepository {
    fun observe(): Flow<List<Prescription>>

    suspend fun get(id: String): Result<Prescription?>

    /** Ett nytt, slumpat id för ett nytt recept ur samlingen – fungerar offline. */
    fun newId(): String

    /**
     * Sparar receptformuläret (REC-1…REC-10). Lyckas när receptet skrivits (offline först); doserna följer
     * sedan via [syncFromServer] (REC-10, den enda dossynkvägen) i appens livslånga scope – så att en
     * stängd skärm inte avbryter den halvvägs – och ett fel där syns som skrivfel i `SyncStatus`, inte i
     * formuläret. Offline, eller om synken avbryts, läker [tidyUp] doserna nästa gång.
     * [loaded] `null` = nytt: [edited] skrivs med sitt id (från [newId]) och sin `createdAt` (sätts en gång
     * av formuläret; saknas den, nu), så att ett nytt försök efter ett fel skriver samma dokument. Ett
     * befintligt skriver bara de toppfält som skiljer [edited] från [loaded] (`updateChanged`) – [loaded]
     * ska vara receptet **som formuläret visade det** (med formulärets förval), så att förvalen inte
     * skrivs som ändringar. Okända fält och fält som en annan enhet ändrat och formuläret inte rört står
     * kvar, en okänd upprepning (`Schedule.Unknown`) skrivs inte om den inte ändrats, och ett recept som
     * raderats under tiden återuppstår inte. Listfälten (`slots`, `boosts`) skrivs hela när de ändrats;
     * `slots` aldrig när tidpunkterna inte kan visas ([Prescription.hasUnknownSlots]). Med [extended]
     * ("Förläng och aktivera", MEDF-5) skrivs `active`, `period` och `boosts` alltid – också när den
     * lästa kopian redan hade samma värde men servern hunnit avsluta receptet (REC-8).
     */
    suspend fun save(loaded: Prescription?, edited: Prescription, extended: Boolean = false): Result<Unit>

    /**
     * REC-5: aktiverar eller avaktiverar. Bara fältet `active` skrivs, direkt (`update` – ett recept
     * som raderats under tiden återuppstår inte), sedan följer doserna via [syncFromServer]. En ny
     * växling väntar aldrig på den förra synken för att skrivas.
     */
    suspend fun setActive(prescription: Prescription, active: Boolean): Result<Unit>

    /**
     * REC-5, REC-10: **den enda vägen** för receptets dossynk – från reglaget, städningen ([tidyUp]) och
     * (#236) Idag. Under receptets lås ("senaste vinner", [LatestWins]: en inaktuell synk hoppas över
     * eller avslutas vid nästa steg): väntar in appens egna skrivningar, läser receptet **från servern**
     * (aldrig cachen), läser dagens och senare doser från servern, räknar diffen i `:core` och skriver
     * den villkorat (`DoseRepository.syncPrescription`). Ett recept som inte finns ger inga doser.
     * Offline: ingenting görs, utan fel – nästa synk med nät gör det.
     */
    suspend fun syncFromServer(prescriptionId: String, today: LocalDate): Result<Unit>

    /**
     * Städningen när en vy med recepten laddas (fliken Mediciner; Idag, #236, anropar samma), allt på
     * recept som servern bekräftat – aldrig på cachen: aktiva recept vars period passerats sett från
     * [today] avslutas (REC-8 – bara om de under receptets lås fortfarande är aktiva och utgångna,
     * så att en samtidig återaktivering inte skrivs över), och de inaktivas planerade doser från och med
     * [today] städas (REC-5). Även aktiva recept vars doser inte stämmer synkas – en sparning eller
     * växling vars synk avbröts eller var offline läks här (REC-10). En gemensam serverläsning av dagens
     * och senare doser avgör: saknade doser skapas i en batch, och bara recept vars doser ska ändras
     * eller tas bort går via samma synk som [syncFromServer] (idempotent: stämmer allt skrivs ingenting).
     * Offline görs ingenting, utan fel.
     */
    suspend fun tidyUp(today: LocalDate): Result<Unit>

    /** Tar bort receptet. Doser som redan finns lämnas orörda, som i 3.x – historiken tappas aldrig. */
    suspend fun delete(id: String): Result<Unit>
}

/**
 * Tunn fasad över samlingen `prescriptions` (skill firestore-data-layer). En instans i appen
 * ([Singleton]), så att växlingarna från alla skärmar delar [syncs].
 */
@Singleton
class DefaultPrescriptionRepository @Inject constructor(
    collections: CollectionFactory,
    private val doses: DoseRepository,
    private val clock: Clock,
    /** Appens livslånga scope – dossynken efter en sparning ska inte avbrytas när skärmen stängs. */
    @ApplicationScope private val background: CoroutineScope,
    private val sync: SyncStatus,
    /** Enhetens tidszon, läst vid varje växling. */
    private val zone: Provider<TimeZone>,
) : PrescriptionRepository {
    private val collection = collections.prescriptions()

    /** Dossynken per recept-id. */
    private val syncs = LatestWins<String>()

    override fun observe(): Flow<List<Prescription>> = collection.observe()

    override suspend fun get(id: String): Result<Prescription?> = collection.get(id)

    override fun newId(): String = collection.newId()

    override suspend fun save(loaded: Prescription?, edited: Prescription, extended: Boolean): Result<Unit> {
        val written = if (loaded == null) {
            collection.upsert(edited.copy(createdAt = edited.createdAt ?: clock.now()))
        } else {
            // Okända tidpunkter skrivs aldrig om: formuläret jämförs mot det lästa i det fältet.
            val after = edited.copy(id = loaded.id).let { if (loaded.hasUnknownSlots) it.copy(slots = loaded.slots, unknownSlots = loaded.unknownSlots) else it }
            collection.updateChanged(PrescriptionCodec, loaded, after, always = if (extended) EXTEND_FIELDS else emptySet())
        }
        written.onFailure { return Result.failure(it) }
        val id = loaded?.id ?: edited.id
        val today = clock.todayIn(zone.get())
        background.launch { sync.trackWork { syncFromServer(id, today) } }
        return Result.success(Unit)
    }

    override suspend fun setActive(prescription: Prescription, active: Boolean): Result<Unit> {
        collection.update(prescription.copy(active = active), setOf(PrescriptionCodec.ACTIVE)).onFailure { return Result.failure(it) }
        return syncFromServer(prescription.id, clock.todayIn(zone.get()))
    }

    override suspend fun syncFromServer(prescriptionId: String, today: LocalDate): Result<Unit> = sync(prescriptionId, today).offlineIsNoOp()

    /**
     * [syncFromServer] med `Offline` kvar som fel, så att [tidyUp] kan sluta vid första. Med
     * [endIfExpired] avslutas receptet först (REC-8) – bara om det enligt servern, under låset, fortfarande
     * är aktivt och utgånget, så att en samtidig återaktivering aldrig skrivs över.
     */
    private suspend fun sync(prescriptionId: String, today: LocalDate, endIfExpired: Boolean = false): Result<Unit> =
        syncs.run(prescriptionId, superseded = Result.success(Unit)) { isCurrent ->
            // Egna skrivningar (t.ex. reglagets `active`) först till servern, så att läsningen ser dem.
            collection.awaitWrites().onFailure { return@run Result.failure(it) }
            var stored = collection.confirmed(prescriptionId).getOrElse { return@run Result.failure(it) }
                ?: return@run Result.success(Unit)
            if (endIfExpired && listOf(stored).toDeactivateOn(today).isNotEmpty()) {
                stored = stored.copy(active = false)
                collection.update(stored, setOf(PrescriptionCodec.ACTIVE)).onFailure { return@run Result.failure(it) }
            }
            doses.syncPrescription(stored, today, isCurrent)
        }

    override suspend fun tidyUp(today: LocalDate): Result<Unit> {
        val stored = collection.confirmed().getOrElse { return Result.failure<Unit>(it).offlineIsNoOp() }
        // Bara ett urval: beslutet – avsluta, städa – fattas per recept i synken, på färska data under
        // receptets lås.
        val ended = stored.toDeactivateOn(today).mapTo(LinkedHashSet()) { it.id }
        val plan = doses.plan(stored.filter { it.id !in ended }, today).getOrElse { return Result.failure<Unit>(it).offlineIsNoOp() }
        // Saknade doser i en batch – utan lås och utan en serverläsning per recept.
        doses.createIfAbsent(plan.create).onFailure { return Result.failure(it) }
        for (id in ended) sync(id, today, endIfExpired = true).onFailure { return Result.failure<Unit>(it).offlineIsNoOp() }
        for (id in plan.locked) sync(id, today).onFailure { return Result.failure<Unit>(it).offlineIsNoOp() }
        return Result.success(Unit)
    }

    private companion object {
        /** "Förläng och aktivera" skriver alltid dessa (MEDF-5). */
        val EXTEND_FIELDS = setOf(PrescriptionCodec.ACTIVE, PrescriptionCodec.PERIOD, PrescriptionCodec.BOOSTS)
    }

    /** Offline görs ingenting, och det är inget fel – nästa gång med nät. */
    private fun Result<Unit>.offlineIsNoOp(): Result<Unit> = if (exceptionOrNull() == DataError.Offline) Result.success(Unit) else this

    override suspend fun delete(id: String): Result<Unit> = collection.delete(id)
}
