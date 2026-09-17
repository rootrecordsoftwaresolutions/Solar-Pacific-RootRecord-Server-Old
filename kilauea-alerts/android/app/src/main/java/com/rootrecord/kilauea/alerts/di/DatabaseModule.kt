package com.rootrecord.kilauea.alerts.di

import android.content.Context
import androidx.room.Room
import com.rootrecord.kilauea.alerts.data.local.KilaueaDatabase
import com.rootrecord.kilauea.alerts.data.local.KilaueaDataDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDb(@ApplicationContext ctx: Context): KilaueaDatabase =
        Room.databaseBuilder(ctx, KilaueaDatabase::class.java, "kilauea_alerts.db")
            .fallbackToDestructiveMigration(dropAllTables = true)
            .build()

    @Provides
    fun provideDao(db: KilaueaDatabase): KilaueaDataDao = db.kilaueaDataDao()
}
