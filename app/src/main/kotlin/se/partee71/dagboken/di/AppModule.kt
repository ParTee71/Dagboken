package se.partee71.dagboken.di

import android.content.Context
import androidx.room.Room
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import se.partee71.dagboken.data.room.AppDatabase
import se.partee71.dagboken.data.room.daos.AktivitetDao
import se.partee71.dagboken.data.room.daos.FavoritDao
import se.partee71.dagboken.data.room.daos.MedicinDao
import se.partee71.dagboken.data.room.daos.ReceptDao
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, "dagboken.db")
            .addMigrations(*AppDatabase.MIGRATIONS)
            .build()

    @Provides fun provideAktivitetDao(db: AppDatabase): AktivitetDao = db.aktivitetDao()
    @Provides fun provideMedicinDao(db: AppDatabase): MedicinDao     = db.medicinDao()
    @Provides fun provideReceptDao(db: AppDatabase): ReceptDao       = db.receptDao()
    @Provides fun provideFavoritDao(db: AppDatabase): FavoritDao     = db.favoritDao()
}
