package se.partee71.dagboken.data.repository

import javax.inject.Inject
import kotlin.time.Clock
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import se.partee71.dagboken.core.model.Event
import se.partee71.dagboken.core.model.Severity
import se.partee71.dagboken.core.schema.EventCodec
import se.partee71.dagboken.data.common.CollectionFactory
import se.partee71.dagboken.data.common.EntityCollection

/** Hälsohändelserna i samlingen `events` (HAN-serien): läsning, formuläret och radering. */
interface EventRepository : DatedRepository<Event> {
    /**
     * En ny, osparad händelse på [date] kl. [time] (HAN-1): nytt slumpat id och `createdAt` = nu, satta **en gång**
     * här, och svårighetsgraden [Severity.DEFAULT] (mitt på skalan, som 3.x). Fungerar offline.
     */
    fun new(date: LocalDate, time: LocalTime): Event
}

/** Tunn fasad över samlingen `events` (skill firestore-data-layer). */
class DefaultEventRepository private constructor(
    private val collection: EntityCollection<Event>,
    private val clock: Clock
) : EventRepository,
    DatedRepository<Event> by DatedEntries(collection, EventCodec) {
    @Inject constructor(collections: CollectionFactory, clock: Clock) : this(collections.events(), clock)

    override fun new(date: LocalDate, time: LocalTime): Event = Event(collection.newId(), date = date, time = time, severity = Severity.DEFAULT, createdAt = clock.now())
}
