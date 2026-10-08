package se.partee71.dagboken.data.legacy

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/** Legacy-läsaren för migreringen på enheten (OMB-2): bara läsning av 3.x-filerna, enhetslokalt läge och kopian. */
@Module
@InstallIn(SingletonComponent::class)
abstract class LegacyModule {
    @Binds abstract fun room(reader: LegacyRoomReader): LegacyRoomSource

    @Binds abstract fun preferences(reader: LegacyPreferencesReader): LegacyPreferencesSource

    @Binds abstract fun deviceState(state: DataStoreMigrationState): MigrationDeviceState

    @Binds abstract fun ledger(ledger: FileMigrationLedger): MigrationLedger

    @Binds abstract fun work(work: WorkManagerLegacyWork): LegacyWork

    @Binds abstract fun copy(file: ContentResolverCopyFile): LegacyCopyFile

}
