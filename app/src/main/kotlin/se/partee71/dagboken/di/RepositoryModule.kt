package se.partee71.dagboken.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import se.partee71.dagboken.data.repository.DefaultOptionsRepository
import se.partee71.dagboken.data.repository.OptionsRepository
import se.partee71.dagboken.data.repository.DefaultSettingsRepository
import se.partee71.dagboken.data.repository.SettingsRepository

/** Repositories – tunna fasader över samlingarna (skill firestore-data-layer). */
@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {
    @Binds abstract fun settings(repository: DefaultSettingsRepository): SettingsRepository

    @Binds abstract fun options(repository: DefaultOptionsRepository): OptionsRepository
}
