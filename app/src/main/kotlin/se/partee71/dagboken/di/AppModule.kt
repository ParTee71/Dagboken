package se.partee71.dagboken.di

import android.content.Context
import androidx.room.Room
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.serialization.json.Json
import se.partee71.dagboken.data.room.AppDatabase
import se.partee71.dagboken.data.room.daos.AktivitetDao
import se.partee71.dagboken.data.room.daos.FavoritDao
import se.partee71.dagboken.data.room.daos.HandelseDao
import se.partee71.dagboken.data.room.daos.MedicinDao
import se.partee71.dagboken.data.room.daos.NoteDao
import se.partee71.dagboken.data.room.daos.ReceptDao
import se.partee71.dagboken.data.room.daos.SjukdomsEpisodDao
import se.partee71.dagboken.data.room.daos.SjukdomsIncheckningDao
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideJson(): Json = Json { ignoreUnknownKeys = true }

    @Provides
    @IoDispatcher
    fun provideIoDispatcher(): CoroutineDispatcher = Dispatchers.IO
}

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, "dagboken.db")
            .addMigrations(*AppDatabase.MIGRATIONS)
            .build()

    @Provides fun provideAktivitetDao(db: AppDatabase): AktivitetDao                   = db.aktivitetDao()
    @Provides fun provideMedicinDao(db: AppDatabase): MedicinDao                       = db.medicinDao()
    @Provides fun provideReceptDao(db: AppDatabase): ReceptDao                         = db.receptDao()
    @Provides fun provideFavoritDao(db: AppDatabase): FavoritDao                       = db.favoritDao()
    @Provides fun provideHandelseDao(db: AppDatabase): HandelseDao                     = db.handelseDao()
    @Provides fun provideNoteDao(db: AppDatabase): NoteDao                             = db.noteDao()
    @Provides fun provideSjukdomsEpisodDao(db: AppDatabase): SjukdomsEpisodDao         = db.sjukdomsEpisodDao()
    @Provides fun provideSjukdomsIncheckningDao(db: AppDatabase): SjukdomsIncheckningDao = db.sjukdomsIncheckningDao()
}
