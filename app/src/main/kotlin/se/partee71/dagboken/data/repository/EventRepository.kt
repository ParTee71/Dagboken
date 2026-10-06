package se.partee71.dagboken.data.repository

import javax.inject.Inject
import se.partee71.dagboken.core.model.Event
import se.partee71.dagboken.core.schema.EventCodec
import se.partee71.dagboken.data.common.CollectionFactory

/** Hälsohändelserna i samlingen `events`. Än så länge läsning och radering – formuläret kommer i #239. */
interface EventRepository : DatedRepository<Event>

/** Tunn fasad över samlingen `events` (skill firestore-data-layer). */
class DefaultEventRepository @Inject constructor(collections: CollectionFactory) :
    EventRepository, DatedRepository<Event> by DatedEntries(collections.events(), EventCodec.DATE)
