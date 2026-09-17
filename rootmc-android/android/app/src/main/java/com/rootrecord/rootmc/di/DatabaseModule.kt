package com.rootrecord.rootmc.di

import android.content.Context
import androidx.room.Room
import com.rootrecord.rootmc.data.local.RootMcDatabase
import com.rootrecord.rootmc.data.local.MIGRATION_1_2
import com.rootrecord.rootmc.data.local.MIGRATION_2_3
import com.rootrecord.rootmc.data.local.MIGRATION_3_4
import com.rootrecord.rootmc.data.local.MIGRATION_4_5
import com.rootrecord.rootmc.data.local.MIGRATION_5_6
import com.rootrecord.rootmc.data.local.MIGRATION_6_7
import com.rootrecord.rootmc.data.local.MIGRATION_7_8
import com.rootrecord.rootmc.data.local.dao.AreaDao
import com.rootrecord.rootmc.data.local.dao.ChamberDao
import com.rootrecord.rootmc.data.local.dao.IngameImportDao
import com.rootrecord.rootmc.data.local.dao.BuildPlanDao
import com.rootrecord.rootmc.data.local.dao.CoordinateDao
import com.rootrecord.rootmc.data.local.dao.MediaDao
import com.rootrecord.rootmc.data.local.dao.NoteDao
import com.rootrecord.rootmc.data.local.dao.NoteLinkDao
import com.rootrecord.rootmc.data.local.dao.NotebookDao
import com.rootrecord.rootmc.data.local.dao.ReferenceDao
import com.rootrecord.rootmc.data.local.dao.TagDao
import com.rootrecord.rootmc.data.local.dao.SyncSnapshotDao
import com.rootrecord.rootmc.data.local.dao.TimelineDao
import com.rootrecord.rootmc.data.local.dao.WorldDao
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
    fun provideDatabase(@ApplicationContext ctx: Context): RootMcDatabase =
        Room.databaseBuilder(ctx, RootMcDatabase::class.java, "rootmc.db")
            .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8)
            .fallbackToDestructiveMigration(dropAllTables = true)
            .build()

    @Provides
    fun provideWorldDao(db: RootMcDatabase): WorldDao = db.worldDao()

    @Provides
    fun provideNotebookDao(db: RootMcDatabase): NotebookDao = db.notebookDao()

    @Provides
    fun provideNoteDao(db: RootMcDatabase): NoteDao = db.noteDao()

    @Provides
    fun provideTagDao(db: RootMcDatabase): TagDao = db.tagDao()

    @Provides
    fun provideCoordinateDao(db: RootMcDatabase): CoordinateDao = db.coordinateDao()

    @Provides
    fun provideAreaDao(db: RootMcDatabase): AreaDao = db.areaDao()

    @Provides
    fun provideMediaDao(db: RootMcDatabase): MediaDao = db.mediaDao()

    @Provides
    fun provideNoteLinkDao(db: RootMcDatabase): NoteLinkDao = db.noteLinkDao()

    @Provides
    fun provideBuildPlanDao(db: RootMcDatabase): BuildPlanDao = db.buildPlanDao()

    @Provides
    fun provideReferenceDao(db: RootMcDatabase): ReferenceDao = db.referenceDao()

    @Provides
    fun provideTimelineDao(db: RootMcDatabase): TimelineDao = db.timelineDao()

    @Provides
    fun provideSyncSnapshotDao(db: RootMcDatabase): SyncSnapshotDao = db.syncSnapshotDao()

    @Provides
    fun provideChamberDao(db: RootMcDatabase): ChamberDao = db.chamberDao()

    @Provides
    fun provideIngameImportDao(db: RootMcDatabase): IngameImportDao = db.ingameImportDao()
}
