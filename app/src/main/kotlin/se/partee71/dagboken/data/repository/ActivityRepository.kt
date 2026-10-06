package se.partee71.dagboken.data.repository

import javax.inject.Inject
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.minus
import se.partee71.dagboken.core.engine.prefilledFrom
import se.partee71.dagboken.core.model.Activity
import se.partee71.dagboken.core.model.Option
import se.partee71.dagboken.data.repository.ActivityRepository.Companion.PREFILL_WAIT
import se.partee71.dagboken.core.schema.ActivityCodec
import se.partee71.dagboken.data.common.CollectionFactory
import se.partee71.dagboken.data.common.EntityCollection
import se.partee71.dagboken.data.common.cachedDates

/** Aktiviteterna i samlingen `activities` (AKT-serien): läsning, formuläret och radering. */
interface ActivityRepository : DatedRepository<Activity> {
    companion object {
        /** Hur länge en ny aktivitet väntar på förvalen (ur cachen tar det millisekunder) – som `nextSortOrder`. */
        val PREFILL_WAIT = 2.seconds
    }

    /**
     * En ny, osparad aktivitet på [date] kl. [time] (AKT-12): nytt slumpat id och `createdAt` = nu, satta **en gång**
     * här, förifylld med typen och tidsåtgången från den senast loggade aktiviteten det senaste året fram till
     * [today] (`prefilledFrom`; en arkiverad typ förifylls inte) – ur cachen, offline först. Går de inte att läsa
     * inom [PREFILL_WAIT] blir den utan förval, hellre än att formuläret väntar. Inget sparas.
     */
    suspend fun new(date: LocalDate, time: LocalTime, today: LocalDate): Activity
}

/** Tunn fasad över samlingen `activities` (skill firestore-data-layer). */
class DefaultActivityRepository private constructor(
    private val collection: EntityCollection<Activity>,
    private val options: EntityCollection<Option>,
    private val clock: Clock,
) : ActivityRepository, DatedRepository<Activity> by DatedEntries(collection, ActivityCodec) {
    @Inject constructor(collections: CollectionFactory, clock: Clock) : this(collections.activities(), collections.options(), clock)

    override suspend fun new(date: LocalDate, time: LocalTime, today: LocalDate): Activity {
        val recent = withTimeoutOrNull(PREFILL_WAIT) { collection.cachedDates(ActivityCodec.DATE, today.minus(PREFILL_DAYS, DateTimeUnit.DAY), today).getOrNull() }.orEmpty()
        val types = if (recent.isEmpty()) emptyList() else withTimeoutOrNull(PREFILL_WAIT) { options.cached().getOrNull() }.orEmpty()
        return Activity(collection.newId(), date = date, time = time, createdAt = clock.now()).prefilledFrom(recent, types)
    }

    private companion object {
        /** Hur långt bakåt den senaste aktiviteten söks – ett år, som Dagbokens fönster (HIST-8). */
        const val PREFILL_DAYS = 365
    }
}
