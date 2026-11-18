package se.kodhast.dagboken.di

import android.content.Context
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineDispatcher
import se.kodhast.dagboken.data.auth.AuthRepository
import se.kodhast.dagboken.data.auth.GoogleAuthRepository
import se.kodhast.ui.account.CoilProfilePhotoCache
import se.kodhast.ui.account.ProfilePhotoCache

@Module
@InstallIn(SingletonComponent::class)
abstract class AuthModule {
    @Binds abstract fun auth(repository: GoogleAuthRepository): AuthRepository

    companion object {
        /** Profilfotots cache på enheten (AUTH-3), töms vid utloggning – android-ui:s `CoilProfilePhotoCache`. */
        @Provides
        fun profilePhotos(@ApplicationContext context: Context, @IoDispatcher io: CoroutineDispatcher): ProfilePhotoCache = CoilProfilePhotoCache(context, io)
    }
}
