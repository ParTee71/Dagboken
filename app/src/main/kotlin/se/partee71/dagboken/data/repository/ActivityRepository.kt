package se.partee71.dagboken.data.repository

import javax.inject.Inject
import kotlin.time.Clock
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import se.partee71.dagboken.core.model.Activity
import se.partee71.dagboken.core.schema.ActivityCodec
import se.partee71.dagboken.data.common.CollectionFactory
import se.partee71.dagboken.data.common.EntityCollection

/** Aktiviteterna i samlingen `activities` (AKT-serien): läsning, formuläret och radering. */
interface ActivityRepository : DatedRepository<Activity> {
    /**
     * En ny, osparad aktivitet på [date] kl. [time] (AKT-12): nytt slumpat id och `createdAt` = nu, satta **en gång**
     * här, och inga förval från tidigare aktiviteter. Fungerar offline.
     */
    fun new(date: LocalDate, time: LocalTime): Activity
}

/** Tunn fasad över samlingen `activities` (skill firestore-data-layer). */
class DefaultActivityRepository private constructor(
    private val collection: EntityCollection<Activity>,
    private val clock: Clock
) : ActivityRepository,
    DatedRepository<Activity> by DatedEntries(collection, ActivityCodec) {
    @Inject constructor(collections: CollectionFactory, clock: Clock) : this(collections.activities(), clock)

    override fun new(date: LocalDate, time: LocalTime): Activity = Activity(collection.newId(), date = date, time = time, createdAt = clock.now())
}
