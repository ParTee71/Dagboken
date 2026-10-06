package se.partee71.dagboken.data.repository

import javax.inject.Inject
import kotlin.time.Clock
import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.LocalDate
import se.partee71.dagboken.core.model.Occasion
import se.partee71.dagboken.core.model.Screening
import se.partee71.dagboken.core.schema.ScreeningCodec
import se.partee71.dagboken.data.common.CollectionFactory
import se.partee71.dagboken.data.common.EntityCollection

/** Måendeloggarna i samlingen `screenings` (SCR-1…SCR-6, HEM-5). */
interface ScreeningRepository : DatedRepository<Screening> {
    /** HEM-4, HEM-5: [date]s loggar ur cachen och sedan servern (offline först). */
    fun observeDay(date: LocalDate): Flow<List<Screening>>

    // observeDays: loggarna för en period (energin HEM-7, HEM-13, HEM-19, dagar i rad HEM-20 och Dagboken, HIST-8).

    /**
     * En ny, osparad logg för [date] och [occasion] (SCR-6, HEM-8b): nytt slumpat id och `createdAt` = nu,
     * satta **en gång** här – formuläret behåller dem, så att varje nytt försök att spara skriver samma
     * dokument med samma skapandetid. Fungerar offline.
     */
    fun new(date: LocalDate, occasion: Occasion?): Screening

    // save: måendeformuläret (SCR-1, SCR-2, SCR-6) – ny som den är (från [new]), ändrad fältvis (`DatedRepository.save`).
}

/** Tunn fasad över samlingen `screenings` (skill firestore-data-layer). */
class DefaultScreeningRepository private constructor(
    private val collection: EntityCollection<Screening>,
    private val clock: Clock,
) : ScreeningRepository, DatedRepository<Screening> by DatedEntries(collection, ScreeningCodec) {
    @Inject constructor(collections: CollectionFactory, clock: Clock) : this(collections.screenings(), clock)

    override fun observeDay(date: LocalDate): Flow<List<Screening>> = observeDays(date, date)

    override fun new(date: LocalDate, occasion: Occasion?): Screening =
        Screening(collection.newId(), date = date, occasion = occasion, createdAt = clock.now())
}
