package se.partee71.dagboken.data.firestore

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.firestoreSettings
import com.google.firebase.firestore.persistentCacheSettings
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import se.partee71.dagboken.data.common.CollectionFactory
import se.partee71.dagboken.data.common.LocalCacheCleaner
import se.partee71.dagboken.data.common.SyncStatus
import se.partee71.dagboken.data.common.UserVersionSource
import se.partee71.dagboken.data.user.UserDirectory

/** Firestore-instansen och det som bygger på den. Ligger här – `FirebaseFirestore` får inte finnas utanför `data/firestore`. */
@Module
@InstallIn(SingletonComponent::class)
abstract class FirestoreModule {

    @Binds
    abstract fun collections(factory: FirestoreCollectionFactory): CollectionFactory

    @Binds
    abstract fun syncStatus(status: FirestoreSyncStatus): SyncStatus

    @Binds
    abstract fun userVersions(source: FirestoreUserVersions): UserVersionSource

    @Binds
    abstract fun userDirectory(directory: FirestoreUserDirectory): UserDirectory

    @Binds
    abstract fun rawDocuments(documents: FirestoreRawDocuments): RawDocuments

    /** Utloggningen tömmer cachen (AUTH-6). */
    @Binds
    abstract fun localCache(instance: FirestoreInstance): LocalCacheCleaner

    companion object {
        /** Offline först: persistent lokal cache på 100 MB (TP-4, skill firestore-data-layer). */
        private const val CACHE_BYTES = 100L * 1024 * 1024

        /** Samma inställningar även för instansen som skapas efter att cachen tömts. */
        @Provides
        @Singleton
        fun firestore(): FirestoreInstance = FirestoreInstance {
            FirebaseFirestore.getInstance().apply {
                firestoreSettings = firestoreSettings {
                    setLocalCacheSettings(persistentCacheSettings { setSizeBytes(CACHE_BYTES) })
                }
            }
        }
    }
}
