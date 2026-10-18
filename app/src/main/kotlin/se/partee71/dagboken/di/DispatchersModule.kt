package se.partee71.dagboken.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Qualifier
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

/** Dispatchern för blockerande läsningar utanför Firestore, t.ex. Health Connect, filer och assets (NFR-8) – utbytbar i test. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class IoDispatcher

/** Dispatchern för tung beräkning utanför huvudtråden, t.ex. migreringens jämförelser (NFR-8) – utbytbar i test. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class DefaultDispatcher

/**
 * Det enda stället som nämner `Dispatchers` (scopet `[dispatchers]` i `ui-forbidden.txt`). Övrig
 * kod tar en [CoroutineDispatcher] i konstruktorn, så att testerna kan ge en testdispatcher.
 */
@Module
@InstallIn(SingletonComponent::class)
object DispatchersModule {
    @Provides
    @IoDispatcher
    fun ioDispatcher(): CoroutineDispatcher = Dispatchers.IO

    @Provides
    @DefaultDispatcher
    fun defaultDispatcher(): CoroutineDispatcher = Dispatchers.Default
}
