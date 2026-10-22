package se.partee71.dagboken.data.legacy

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import se.partee71.dagboken.data.common.ContentResolverUserFile
import se.partee71.dagboken.data.common.UserFile

/** Importen från 3.x och 4.0 (BCK-6, BCK-14): filerna från dokumentväljaren och Drive. */
@Module
@InstallIn(SingletonComponent::class)
abstract class LegacyModule {
    /** Filen från dokumentväljaren – exporten (BCK-13) och importen (BCK-6). */
    @Binds abstract fun userFile(file: ContentResolverUserFile): UserFile

    @Binds abstract fun drive(backups: DriveRestBackups): DriveBackups
}
