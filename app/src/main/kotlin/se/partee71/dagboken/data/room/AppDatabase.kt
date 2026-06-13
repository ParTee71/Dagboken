package se.partee71.dagboken.data.room

import androidx.room.Database
import androidx.room.RoomDatabase
import se.partee71.dagboken.data.room.daos.AktivitetDao
import se.partee71.dagboken.data.room.daos.FavoritDao
import se.partee71.dagboken.data.room.daos.MedicinDao
import se.partee71.dagboken.data.room.daos.ReceptDao
import se.partee71.dagboken.data.room.entities.AktivitetEntity
import se.partee71.dagboken.data.room.entities.FavoritEntity
import se.partee71.dagboken.data.room.entities.MedicinEntity
import se.partee71.dagboken.data.room.entities.ReceptEntity

@Database(
    entities = [
        AktivitetEntity::class,
        MedicinEntity::class,
        ReceptEntity::class,
        FavoritEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun aktivitetDao(): AktivitetDao
    abstract fun medicinDao(): MedicinDao
    abstract fun receptDao(): ReceptDao
    abstract fun favoritDao(): FavoritDao
}
