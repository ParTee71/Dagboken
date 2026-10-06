package se.partee71.dagboken.data.repository

import javax.inject.Inject
import se.partee71.dagboken.core.model.Activity
import se.partee71.dagboken.core.schema.ActivityCodec
import se.partee71.dagboken.data.common.CollectionFactory

/** Aktiviteterna i samlingen `activities` (AKT-serien). Än så länge läsning och radering – formuläret kommer i #239. */
interface ActivityRepository : DatedRepository<Activity>

/** Tunn fasad över samlingen `activities` (skill firestore-data-layer). */
class DefaultActivityRepository @Inject constructor(collections: CollectionFactory) :
    ActivityRepository, DatedRepository<Activity> by DatedEntries(collections.activities(), ActivityCodec.DATE)
