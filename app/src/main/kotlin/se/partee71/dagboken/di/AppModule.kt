package se.partee71.dagboken.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Qualifier
import javax.inject.Singleton
import kotlin.time.Clock
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.datetime.TimeZone
import se.partee71.dagboken.data.common.UserScope
import se.partee71.dagboken.data.user.UserSession

/** Scope som lever lika länge som appen – för delade StateFlows. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ApplicationScope

/** Dispatchern för blockerande läsningar utanför Firestore, t.ex. Health Connect (NFR-8) – utbytbar i test. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class IoDispatcher

/** Dispatchern för tung beräkning utanför huvudtråden, t.ex. migreringens jämförelser (NFR-8) – utbytbar i test. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class DefaultDispatcher

@Module
@InstallIn(SingletonComponent::class)
abstract class AppModule {

    @Binds
    abstract fun userScope(session: UserSession): UserScope

    companion object {
        @Provides
        @Singleton
        @ApplicationScope
        fun applicationScope(): CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

        @Provides
        @IoDispatcher
        fun ioDispatcher(): CoroutineDispatcher = Dispatchers.IO

        @Provides
        @DefaultDispatcher
        fun defaultDispatcher(): CoroutineDispatcher = Dispatchers.Default

        @Provides
        fun clock(): Clock = Clock.System

        /** Enhetens tidszon – vad "i dag" är (HEM-14). */
        @Provides
        fun timeZone(): TimeZone = TimeZone.currentSystemDefault()

        /**
         * Enhetslokalt tillstånd (TP-4): migreringsflaggan (OMB-2) och senare senast valda flik. 4.0:s egen
         * fil – aldrig 3.x-filen `dagboken_prefs`, som migreringen bara läser. Ett DataStore per fil och process.
         */
        @Provides
        @Singleton
        fun deviceState(@ApplicationContext context: Context): DataStore<Preferences> =
            PreferenceDataStoreFactory.create { context.preferencesDataStoreFile(DEVICE_STATE_NAME) }

        const val DEVICE_STATE_NAME = "dagboken_device"
    }
}
