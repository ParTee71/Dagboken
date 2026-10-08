package se.partee71.dagboken.data.legacy

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import se.partee71.dagboken.data.common.ContentResolverUserFile
import se.partee71.dagboken.data.common.UserFile

/** Migreringen och importen från 3.x (OMB-2, OMB-5, BCK-14): bara läsning av 3.x-filerna och Drive, enhetslokalt läge, kopian och filerna från dokumentväljaren. */
@Module
@InstallIn(SingletonComponent::class)
abstract class LegacyModule {
    @Binds abstract fun room(reader: LegacyRoomReader): LegacyRoomSource

    @Binds abstract fun preferences(reader: LegacyPreferencesReader): LegacyPreferencesSource

    @Binds abstract fun deviceState(state: DataStoreMigrationState): MigrationDeviceState

    @Binds abstract fun ledger(ledger: FileMigrationLedger): MigrationLedger

    @Binds abstract fun work(work: WorkManagerLegacyWork): LegacyWork

    /** Filen från dokumentväljaren – kopian (OMB-8), exporten (BCK-13) och importen (BCK-6). */
    @Binds abstract fun userFile(file: ContentResolverUserFile): UserFile

    @Binds abstract fun drive(backups: DriveRestBackups): DriveBackups
}
