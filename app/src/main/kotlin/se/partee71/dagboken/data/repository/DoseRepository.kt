package se.partee71.dagboken.data.repository

import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import se.partee71.dagboken.core.engine.syncDoses
import se.partee71.dagboken.core.model.Dose
import se.partee71.dagboken.core.model.DoseStatus
import se.partee71.dagboken.core.model.Prescription
import se.partee71.dagboken.core.schema.DoseCodec
import se.partee71.dagboken.core.schema.encodeDate
import se.partee71.dagboken.data.common.CollectionFactory
import se.partee71.dagboken.data.common.DataError

/**
 * Doserna i samlingen `doses` (DAT-8). Beräkningen – vilka doser ett recept ska ha – ligger i
 * `:core/engine` (`syncDoses`, `ensureDoses`); här skrivs bara resultatet, utan att något som redan
 * finns skrivs över.
 *
 * Alla skrivningar här är villkorade på servern (`createIfAbsent`, `updateIf`, `deleteIf`) och
 * beslutas från en serverbekräftad läsning av dagens och senare doser (`confirmedFrom`) – aldrig från
 * en cachekopia, som kan sakna en avbockning från en annan enhet.
 */
interface DoseRepository {
    /**
     * REC-5, REC-10: receptets planerade doser från och med [today] följer [prescription], som det
     * lästs från servern. Anropas **bara** via `PrescriptionRepository.syncFromServer` – den enda vägen,
     * med receptets lås. Väntar först in appens egna dosskrivningar (`awaitWrites`), läser sedan dagens
     * och senare doser från servern och skriver diffen: namn/dos/enhet på planerade (`updateIf`),
     * planerade som inte längre gäller tas bort (`deleteIf`), saknade skapas (`createIfAbsent`). Tagna
     * och överhoppade rörs aldrig – villkoret prövas mot serverns version. Första `Offline` avbryter
     * resten och ges tillbaka som [DataError.Offline]. [isCurrent] blir `false` när synken blivit
     * inaktuell (en senare växling): då avslutas den före nästa skrivning, aldrig mitt i en.
     */
    suspend fun syncPrescription(prescription: Prescription, today: LocalDate, isCurrent: () -> Boolean = { true }): Result<Unit>

    /**
     * Vad städningen behöver göra för [prescriptions] från och med [today], med **en** serverläsning av
     * doserna för alla: recept som bara saknar doser ger dem i [DosePlan.create] (en `createIfAbsent`-
     * batch, säker utan lås eftersom den bara skapar det som saknas); recept vars doser ska ändras eller
     * tas bort – inaktiva med planerade kvar (REC-5), aktiva med inaktuella (REC-10) – står i
     * [DosePlan.locked] och avgörs per recept av `PrescriptionRepository.syncFromServer` på färska data.
     */
    suspend fun plan(prescriptions: List<Prescription>, today: LocalDate): Result<DosePlan>

    /**
     * MED-4: skapar de doser i [doses] som bevisligen inte finns (`EntityCollection.createIfAbsent`):
     * servern prövar varje id i en transaktion, så ett dokument som finns – vilken status det än har,
     * också om cachen här inte känner till det – skrivs aldrig över. Offline skrivs ingenting, utan fel;
     * doserna skapas nästa gång dagen visas med nät.
     */
    suspend fun createIfAbsent(doses: List<Dose>): Result<Unit>
}

/** [DoseRepository.plan]: id:n som kräver en synk under receptets lås, och saknade doser att skapa direkt. */
data class DosePlan(val locked: List<String> = emptyList(), val create: List<Dose> = emptyList())

/** Tunn fasad över samlingen `doses` (skill firestore-data-layer); en instans, så att väntande commits delas. */
@Singleton
class DefaultDoseRepository @Inject constructor(
    collections: CollectionFactory,
    /** Enhetens tidszon, läst vid varje användning – ett byte slår igenom direkt. */
    private val zone: Provider<TimeZone>,
) : DoseRepository {
    private val collection = collections.doses()

    override suspend fun syncPrescription(prescription: Prescription, today: LocalDate, isCurrent: () -> Boolean): Result<Unit> {
        collection.awaitWrites().onFailure { return Result.failure(it) }
        val existing = fromToday(today).getOrElse { return Result.failure(it) }
        val zone = zone.get()
        val sync = prescription.syncDoses(existing, today, zone)
        val steps: List<suspend () -> Result<Unit>> = listOf(
            { collection.updateIf(sync.update, FOLLOWS_PRESCRIPTION, ::isPlanned) },
            { collection.deleteIf(sync.delete.map { it.id }, ::isPlanned) },
            { collection.createIfAbsent(sync.create) },
        )
        for (step in steps) {
            if (!isCurrent()) break
            step().onFailure { return Result.failure(it) }
        }
        return Result.success(Unit)
    }

    override suspend fun plan(prescriptions: List<Prescription>, today: LocalDate): Result<DosePlan> {
        if (prescriptions.isEmpty()) return Result.success(DosePlan())
        val existing = fromToday(today).getOrElse { return Result.failure(it) }
        val zone = zone.get()
        val syncs = prescriptions.associate { it.id to it.syncDoses(existing, today, zone) }
        val (locked, createOnly) = syncs.filterValues { !it.isEmpty }.entries.partition { (_, sync) -> sync.update.isNotEmpty() || sync.delete.isNotEmpty() }
        return Result.success(DosePlan(locked = locked.map { it.key }, create = createOnly.flatMap { it.value.create }))
    }

    override suspend fun createIfAbsent(doses: List<Dose>): Result<Unit> =
        collection.createIfAbsent(doses).let { if (it.exceptionOrNull() == DataError.Offline) Result.success(Unit) else it }

    /** Dagens och senare doser, från servern – aldrig hela historiken (som syncDoses ändå inte rör). */
    private suspend fun fromToday(today: LocalDate) = collection.confirmedFrom(DoseCodec.DATE, checkNotNull(today.encodeDate()))

    /** REC-10: bara en dos som fortfarande är planerad på servern följer receptet eller tas bort. */
    private fun isPlanned(stored: Dose): Boolean = stored.status == DoseStatus.PLANNED

    private companion object {
        /** Fälten som följer receptet (REC-10, REC-12). */
        val FOLLOWS_PRESCRIPTION = setOf(DoseCodec.NAME, DoseCodec.DOSE, DoseCodec.UNIT)
    }
}
