package se.kodhast.dagboken.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import se.kodhast.dagboken.data.auth.AuthRepository
import se.kodhast.dagboken.data.auth.GoogleAuthRepository

@Module
@InstallIn(SingletonComponent::class)
abstract class AuthModule {
    @Binds abstract fun auth(repository: GoogleAuthRepository): AuthRepository
}
