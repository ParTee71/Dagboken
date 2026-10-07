package se.partee71.dagboken.di

import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
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
        fun clock(): Clock = Clock.System

        /** Enhetens tidszon – vad "i dag" är (HEM-14). */
        @Provides
        fun timeZone(): TimeZone = TimeZone.currentSystemDefault()
    }
}
