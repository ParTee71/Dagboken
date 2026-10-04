package se.partee71.dagboken.data.firestore

import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Clock
import se.partee71.dagboken.core.model.Identified
import se.partee71.dagboken.core.schema.DocCodec
import se.partee71.dagboken.data.common.EntityCollection
import se.partee71.dagboken.data.common.UserScope

/** Samlingarna i [CollectionTable] som riktiga [FirestoreCollection]. */
@Singleton
class FirestoreCollectionFactory @Inject constructor(
    private val firestore: FirestoreInstance,
    private val scope: UserScope,
    private val sync: FirestoreSyncStatus,
    private val clock: Clock,
) : CollectionTable() {
    override fun <T : Identified> create(
        codec: DocCodec<T>,
        name: String,
        path: (uid: String?) -> String,
    ): EntityCollection<T> = FirestoreCollection(firestore, scope, sync, clock, codec, name, path)
}
