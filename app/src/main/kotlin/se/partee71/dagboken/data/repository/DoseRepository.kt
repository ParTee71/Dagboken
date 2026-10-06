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
     * Id:n bland de inaktiva [prescriptions] som har planerade doser från och med [today] att städa
     * (REC-5) – bara ett urval av kandidater: beslutet fattas sedan per recept av
     * `PrescriptionRepository.syncFromServer` på färska data. Läser doserna från servern.
     */
    suspend fun inactiveWithPlanned(prescriptions: List<Prescription>, today: LocalDate): Result<List<String>>

    /**
     * MED-4: skapar de doser i [doses] som bevisligen inte finns (`EntityCollection.createIfAbsent`):
     * servern prövar varje id i en transaktion, så ett dokument som finns – vilken status det än har,
     * också om cachen här inte känner till det – skrivs aldrig över. Offline skrivs ingenting, utan fel;
     * doserna skapas nästa gång dagen visas med nät.
     */
    suspend fun createIfAbsent(doses: List<Dose>): Result<Unit>
}

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

    override suspend fun inactiveWithPlanned(prescriptions: List<Prescription>, today: LocalDate): Result<List<String>> {
        val inactive = prescriptions.filter { !it.active }
        if (inactive.isEmpty()) return Result.success(emptyList())
        val existing = fromToday(today).getOrElse { return Result.failure(it) }
        val zone = zone.get()
        return Result.success(inactive.filter { it.syncDoses(existing, today, zone).delete.isNotEmpty() }.map { it.id })
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
