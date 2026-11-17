package se.kodhast.dagboken.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import se.kodhast.dagboken.data.auth.AuthRepository
import se.kodhast.dagboken.data.auth.CoilProfilePhotoCache
import se.kodhast.dagboken.data.auth.GoogleAuthRepository
import se.kodhast.dagboken.data.auth.ProfilePhotoCache

@Module
@InstallIn(SingletonComponent::class)
abstract class AuthModule {
    @Binds abstract fun auth(repository: GoogleAuthRepository): AuthRepository

    /** Profilfotots cache på enheten (AUTH-3), töms vid utloggning. */
    @Binds abstract fun profilePhotos(cache: CoilProfilePhotoCache): ProfilePhotoCache
}
