package se.partee71.dagboken.data.repository

import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import se.partee71.dagboken.core.engine.PrnCheck
import se.partee71.dagboken.core.engine.checkDays
import se.partee71.dagboken.core.engine.checkDose
import se.partee71.dagboken.core.engine.ensureDoses
import se.partee71.dagboken.core.engine.extraDose
import se.partee71.dagboken.core.engine.isPrescribed
import se.partee71.dagboken.core.engine.moveTarget
import se.partee71.dagboken.core.engine.movesPrescribedDose
import se.partee71.dagboken.core.engine.oneOffDose
import se.partee71.dagboken.core.engine.prescriptionRef
import se.partee71.dagboken.core.engine.syncDoses
import se.partee71.dagboken.core.engine.takenDose
import se.partee71.dagboken.core.model.Dose
import se.partee71.dagboken.core.model.DoseStatus
import se.partee71.dagboken.core.model.Prescription
import se.partee71.dagboken.core.model.PrnMedicine
import se.partee71.dagboken.core.schema.DoseCodec
import se.partee71.dagboken.core.schema.encodeDate
import se.partee71.dagboken.data.common.CollectionFactory
import se.partee71.dagboken.data.common.DataError
import se.partee71.dagboken.data.common.offlineIsNoOp
import se.partee71.dagboken.data.common.LatestWins
import se.partee71.dagboken.data.common.cachedDates
import se.partee71.dagboken.data.common.moveChanged
import se.partee71.dagboken.data.common.TargetExists
import se.partee71.dagboken.data.common.observeDates

/**
 * Doserna i samlingen `doses` (DAT-8) – **alla** dosskrivningar i appen går härigenom. Beräkningen –
 * vilka doser ett recept ska ha, kylperiod och dagsgräns – ligger i `:core/engine` (`syncDoses`,
 * `ensureDoses`, `checkDose`); här läses underlaget och skrivs resultatet.
 *
 * Det som skapar eller ändrar **planerade** doser är villkorat på servern (`createIfAbsent`, `updateIf`,
 * `deleteIf`) och beslutas från en serverbekräftad läsning – aldrig från en cachekopia, som kan sakna en
 * avbockning från en annan enhet. Det användaren själv gör med en dos – bocka av ([setStatus]), logga
 * vid behov ([logAsNeeded], [logExtraDose]) – är offline först: det läggs i cachen direkt och synkas sedan.
 */
interface DoseRepository : EntryStore<Dose> {
    /** HEM-4: doserna för [date] ur cachen och sedan servern (offline först), sorterade på id. */
    fun observeDay(date: LocalDate): Flow<List<Dose>>

    /** Doserna för [from]…[to], båda inräknade (veckosammanfattningen HEM-13, dagar i rad HEM-20). */
    fun observeDays(from: LocalDate, to: LocalDate): Flow<List<Dose>>

    /**
     * HEM-10, MED-4: id:n för de av [prescriptions] (lästa från servern) som enligt cachen saknar doser på
     * [date] (`ensureDoses`; recept avslutade sett från [today] ger inga). Bara dem behöver
     * `PrescriptionRepository` skapa doser för, under receptets lås – finns allt i cachen körs ingen
     * transaktion. Ett fel i läsningen (t.ex. `NotSignedIn`) ges tillbaka.
     */
    suspend fun missingOn(prescriptions: List<Prescription>, date: LocalDate, today: LocalDate): Result<List<String>>

    /**
     * HEM-10, MED-4: skapar [prescription]s saknade doser på [date] (`createIfAbsent` – en befintlig dos,
     * vilken status den än har, skrivs aldrig över). [prescription] ska vara läst från servern, och anropet
     * görs **bara** av `PrescriptionRepository` (dagens doser och städningen, en väg) under receptets lås, så att en samtidig dossynk
     * (t.ex. en avaktivering som tar bort dagens planerade doser) aldrig följs av att de skapas igen.
     * Recept vars period passerats avslutas inte här – bara av `PrescriptionRepository.tidyUp` (REC-8).
     * Offline görs ingenting, utan fel.
     */
    suspend fun createDay(prescription: Prescription, date: LocalDate, today: LocalDate): Result<Unit>

    /**
     * MED-2, MED-3, MED-14: tagen, överhoppad eller planerad (ångrad). Skriver **bara** `status` och
     * `takenAt` (fältvis `update`): ett namn eller en anteckning som ändrats på en annan enhet skrivs
     * inte tillbaka, och en dos som raderats under tiden återuppstår inte. [takenAt] i framtiden är ett fel
     * ([IllegalArgumentException], MED-16) och skriver inget. [takenAt] sparas bara för en
     * tagen dos – annars nollställs tagningstiden; en tagen dos har alltid en tagningstid (utan [takenAt]:
     * nu). Offline först: fungerar utan nät.
     */
    suspend fun setStatus(dose: Dose, status: DoseStatus, takenAt: Instant? = null): Result<Unit>

    /**
     * FAV-4, FAV-5, MED-16: loggar en dos av vid behov-medicinen vid [at] (nu, eller i efterhand). Läser
     * doserna för `checkDays` (max([PrnMedicine.minHoursBetween], 24) timmar före [at] plus en vecka) ur
     * cachen (offline först; med en ofullständig cache utan nät kan kontrollen inte se allt, FAV-5), prövar dagsgränsen och kylperioden (`checkDose`; [force] = kylperioden
     * bekräftad bort, dagsgränsen gäller alltid) och skriver vid [PrnCheck.Allowed] en ny tagen dos
     * (`takenDose`, nytt id). Annars skrivs ingenting och utfallet ges tillbaka för snackbaren (FAV-6).
     * Loggningar av samma medicin går en i taget, så att ett dubbeltryck aldrig passerar dagsgränsen eller
     * kylperioden. [at] i framtiden är ett fel ([IllegalArgumentException], MED-16) och skriver inget.
     * [note] är dosens anteckning – som förval medicinens (MED-11), i efterhand det formuläret visar (MED-16).
     * Den skrivna dosen står i [PrnLog.dose], så att den kan ångras ([remove], FAV-2).
     */
    suspend fun logAsNeeded(medicine: PrnMedicine, at: Instant, force: Boolean = false, note: String? = medicine.note): Result<PrnLog>

    /**
     * FAV-11: en extrados av receptets medicin vid [at] (`extraDose`, nytt id) – utan kylperiod och
     * dagsgräns. [at] i framtiden är ett fel ([IllegalArgumentException]) och skriver inget. Ger den
     * skrivna dosen, så att den kan ångras ([remove]).
     */
    suspend fun logExtraDose(prescription: Prescription, at: Instant): Result<Dose>

    /**
     * MED-11, NAV-10: en ny, osparad engångsdos – tagen på [date] kl. [time] med "Vid behov" och enheten mg som
     * förval (`oneOffDose`); nytt slumpat id och `createdAt` = nu, satta **en gång** här. Fungerar offline.
     */
    fun newOneOff(date: LocalDate, time: LocalTime): Dose

    /**
     * Dosformuläret (MED-11, MED-15, MED-16): en ny engångsdos skrivs som den är; en ändrad skriver bara de ändrade
     * fälten ([EntryStore.save]). En **receptdos med datumbundet id som flyttas till en annan dag**
     * (`movesPrescribedDose`) får måldagens id `recept_{id}_{datum}_{tidpunkt}` (`moveTarget`) med receptkopplingen
     * kvar, och den gamla tas bort – i samma transaktion (`moveChanged`), så att den varken dubbleras eller
     * försvinner: det lagrade dokumentet följer med (också fält från en nyare app), med de ändrade fälten ovanpå; en
     * dos som raderats under tiden återuppstår inte ([DataError.NotFound]). Dosgenereringen ser den på måldagen och
     * skapar ingen till; har måldagen redan dosen för tidpunkten skrivs ingenting och det blir [DoseSlotTaken].
     * Ursprungsdagens dos skapas sedan på nytt som planerad (MED-4). En dos med annat id ändras fältvis som andra. En tagningstid i framtiden är ett fel
     * ([IllegalArgumentException], MED-16) och skriver inget. Offline först – utom flytten, som kräver nät
     * ([DataError.Offline], inget skrivs).
     */
    override suspend fun save(loaded: Dose?, edited: Dose): Result<Unit>

    /** Dosformulärets Radera: läser dosen och tar bort den som [remove] (receptdos → överhoppad). */
    override suspend fun delete(id: String): Result<Unit>

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
     * doserna för alla: recept som bara saknar doser står i [DosePlan.missing] (de skapas per recept under
     * receptets lås, samma väg som `PrescriptionRepository.ensureDay`, [createDay]); recept vars doser ska ändras eller
     * tas bort – inaktiva med planerade kvar (REC-5), aktiva med inaktuella (REC-10) – står i
     * [DosePlan.locked] och avgörs per recept av `PrescriptionRepository.syncFromServer` på färska data.
     */
    suspend fun plan(prescriptions: List<Prescription>, today: LocalDate): Result<DosePlan>

    /**
     * HIST-5, MED-15 (som 3.x `deleteMedicin`): tar bort [dose] efter `ConfirmDialog` i UI:t – samma väg som
     * dosformuläret. En **receptdos** raderas inte utan markeras som överhoppad ([setStatus], samma som
     * "Hoppa över"), så att dosgenereringen (MED-4) inte skapar den igen som planerad; en dos utan recept (vid
     * behov, extrados, engångsdos) raderas permanent med sin anteckning (DAT-7). Receptdos = `isPrescribed`, också
     * en migrerad dos som bara har receptet i sitt id. Offline först.
     */
    suspend fun remove(dose: Dose): Result<Unit>
}

/** MED-15: måldagen har redan receptets dos för tidpunkten – den flyttade dosen skrevs inte ([DoseRepository.save]). */
class DoseSlotTaken : Exception()

/** [DoseRepository.logAsNeeded]: utfallet av kontrollen och – när den släppte igenom – den skrivna dosen. */
data class PrnLog(val check: PrnCheck, val dose: Dose? = null)

/** [DoseRepository.plan]: id:n som kräver en synk under receptets lås, och id:n som bara saknar doser ([DoseRepository.createDay]). */
data class DosePlan(val locked: List<String> = emptyList(), val missing: List<String> = emptyList())

/** Tunn fasad över samlingen `doses` (skill firestore-data-layer); en instans, så att väntande commits delas. */
@Singleton
class DefaultDoseRepository @Inject constructor(
    collections: CollectionFactory,
    private val clock: Clock,
    /** Enhetens tidszon, läst vid varje användning – ett byte slår igenom direkt. */
    private val zone: Provider<TimeZone>,
) : DoseRepository {
    private val collection = collections.doses()

    /** Formulärets läsning och sparning – samma som för dagbokens andra poster. */
    private val entries = StoredEntries(collection, DoseCodec, { it.createdAt }, { dose, id -> dose.copy(id = id) })

    /** Vid behov-loggningarna per medicin-id, en i taget ([logAsNeeded]). */
    private val prnLogs = LatestWins<String>()

    override fun observeDay(date: LocalDate): Flow<List<Dose>> = observeDays(date, date)

    override fun observeDays(from: LocalDate, to: LocalDate): Flow<List<Dose>> = collection.observeDates(DoseCodec.DATE, from, to)

    override suspend fun missingOn(prescriptions: List<Prescription>, date: LocalDate, today: LocalDate): Result<List<String>> =
        collection.cachedDates(DoseCodec.DATE, date, date).map { cached ->
            ensureDoses(prescriptions, cached, date, today, zone.get()).create.mapNotNull { it.prescriptionId }.distinct()
        }

    override suspend fun createDay(prescription: Prescription, date: LocalDate, today: LocalDate): Result<Unit> =
        createIfAbsent(ensureDoses(listOf(prescription), existing = emptyList(), date = date, today = today, zone = zone.get()).create)

    override suspend fun setStatus(dose: Dose, status: DoseStatus, takenAt: Instant?): Result<Unit> {
        takenAt?.let { notInFuture(it) }?.onFailure { return Result.failure(it) }
        val taken = if (status == DoseStatus.TAKEN) takenAt ?: clock.now() else null
        return collection.update(dose.copy(status = status, takenAt = taken), STATUS_FIELDS)
    }

    override suspend fun logAsNeeded(medicine: PrnMedicine, at: Instant, force: Boolean, note: String?): Result<PrnLog> {
        notInFuture(at).onFailure { return Result.failure(it) }
        return prnLogs.runExclusive(medicine.id) {
            val zone = zone.get()
            val days = medicine.checkDays(at, zone)
            val recent = collection.cachedDates(DoseCodec.DATE, from = days.start, to = days.endInclusive)
                .getOrElse { return@runExclusive Result.failure(it) }
            val check = medicine.checkDose(recent, at, zone, force)
            if (check != PrnCheck.Allowed) return@runExclusive Result.success(PrnLog(check))
            val dose = medicine.takenDose(collection.newId(), at, zone).copy(note = note?.takeIf { it.isNotBlank() })
            collection.upsert(dose).map { PrnLog(check, dose) }
        }
    }

    override suspend fun logExtraDose(prescription: Prescription, at: Instant): Result<Dose> {
        notInFuture(at).onFailure { return Result.failure(it) }
        val dose = prescription.extraDose(collection.newId(), at, zone.get())
        return collection.upsert(dose).map { dose }
    }

    override fun newOneOff(date: LocalDate, time: LocalTime): Dose =
        oneOffDose(collection.newId(), date, time, zone.get(), createdAt = clock.now(), unit = DEFAULT_UNIT)

    override suspend fun get(id: String): Result<Dose?> = collection.get(id)

    override suspend fun save(loaded: Dose?, edited: Dose): Result<Unit> {
        edited.takenAt?.takeIf { edited.status == DoseStatus.TAKEN }?.let { notInFuture(it) }?.onFailure { return Result.failure(it) }
        return when {
            loaded == null -> entries.save(null, edited)
            loaded.movesPrescribedDose(edited) -> collection.moveChanged(DoseCodec, loaded, loaded.moveTarget(edited, collection::newId))
                .recoverCatching { throw if (it is TargetExists) DoseSlotTaken() else it }
            else -> entries.save(loaded, edited)
        }
    }

    override suspend fun delete(id: String): Result<Unit> {
        val dose = collection.get(id).getOrElse { return Result.failure(it) } ?: return Result.success(Unit)
        return remove(dose)
    }

    /** MED-16: en dos loggas aldrig i framtiden. */
    private fun notInFuture(at: Instant): Result<Unit> =
        if (at > clock.now()) Result.failure(IllegalArgumentException("En dos kan inte loggas i framtiden")) else Result.success(Unit)

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
        return Result.success(DosePlan(locked = locked.map { it.key }, missing = createOnly.map { it.key }))
    }

    override suspend fun remove(dose: Dose): Result<Unit> =
        if (dose.isPrescribed) setStatus(dose, DoseStatus.SKIPPED) else collection.delete(dose.id)

    /**
     * MED-4: skapar de doser i [doses] som bevisligen inte finns (`EntityCollection.createIfAbsent`): ett
     * dokument som finns – vilken status det än har, också om cachen inte känner till det – skrivs aldrig
     * över. Offline skrivs ingenting, utan fel.
     */
    private suspend fun createIfAbsent(doses: List<Dose>): Result<Unit> = collection.createIfAbsent(doses).offlineIsNoOp()

    /** Dagens och senare doser, från servern – aldrig hela historiken (som syncDoses ändå inte rör). */
    private suspend fun fromToday(today: LocalDate) = collection.confirmedFrom(DoseCodec.DATE, checkNotNull(today.encodeDate()))

    /** REC-10: bara en dos som fortfarande är planerad på servern följer receptet eller tas bort. */
    private fun isPlanned(stored: Dose): Boolean = stored.status == DoseStatus.PLANNED

    private companion object {
        /** Fälten som följer receptet (REC-10, REC-12). */
        val FOLLOWS_PRESCRIPTION = setOf(DoseCodec.NAME, DoseCodec.DOSE, DoseCodec.UNIT)

        /** Avbockningens fält (MED-2, MED-14). */
        val STATUS_FIELDS = setOf(DoseCodec.STATUS, DoseCodec.TAKEN_AT)

        /** En ny engångsdos enhet – som en ny vid behov-medicin (FAV-1). */
        const val DEFAULT_UNIT = "mg"
    }
}
