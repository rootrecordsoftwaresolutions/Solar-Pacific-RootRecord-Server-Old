package com.rootrecord.rootmc.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.rootrecord.rootmc.data.local.dao.ChamberDao
import com.rootrecord.rootmc.data.local.dao.AreaDao
import com.rootrecord.rootmc.data.local.dao.BuildPlanDao
import com.rootrecord.rootmc.data.local.dao.CoordinateDao
import com.rootrecord.rootmc.data.local.dao.IngameImportDao
import com.rootrecord.rootmc.data.local.dao.MediaDao
import com.rootrecord.rootmc.data.local.dao.NoteDao
import com.rootrecord.rootmc.data.local.dao.NoteLinkDao
import com.rootrecord.rootmc.data.local.dao.NotebookDao
import com.rootrecord.rootmc.data.local.dao.ReferenceDao
import com.rootrecord.rootmc.data.local.dao.TagDao
import com.rootrecord.rootmc.data.local.dao.SyncSnapshotDao
import com.rootrecord.rootmc.data.local.dao.TimelineDao
import com.rootrecord.rootmc.data.local.dao.WorldDao
import com.rootrecord.rootmc.data.local.entity.AreaEntity
import com.rootrecord.rootmc.data.local.entity.BuildPlanEntity
import com.rootrecord.rootmc.data.local.entity.BuildPlanItemEntity
import com.rootrecord.rootmc.data.local.entity.CoordinateEntity
import com.rootrecord.rootmc.data.local.entity.IngameImportEntity
import com.rootrecord.rootmc.data.local.entity.MediaAttachmentEntity
import com.rootrecord.rootmc.data.local.entity.NoteEntity
import com.rootrecord.rootmc.data.local.entity.NoteFtsEntity
import com.rootrecord.rootmc.data.local.entity.NoteLinkEntity
import com.rootrecord.rootmc.data.local.entity.NoteTagCrossRef
import com.rootrecord.rootmc.data.local.entity.NotebookEntity
import com.rootrecord.rootmc.data.local.entity.ProjectTimelineEventEntity
import com.rootrecord.rootmc.data.local.entity.SpawnerTrackEntity
import com.rootrecord.rootmc.data.local.entity.TrialChamberEntity
import com.rootrecord.rootmc.data.local.entity.ReferenceCacheEntity
import com.rootrecord.rootmc.data.local.entity.TagEntity
import com.rootrecord.rootmc.data.local.entity.WorldEntity

@Database(
    entities = [
        WorldEntity::class,
        NotebookEntity::class,
        NoteEntity::class,
        NoteFtsEntity::class,
        TagEntity::class,
        NoteTagCrossRef::class,
        CoordinateEntity::class,
        AreaEntity::class,
        MediaAttachmentEntity::class,
        NoteLinkEntity::class,
        BuildPlanEntity::class,
        BuildPlanItemEntity::class,
        ReferenceCacheEntity::class,
        ProjectTimelineEventEntity::class,
        TrialChamberEntity::class,
        SpawnerTrackEntity::class,
        IngameImportEntity::class,
    ],
    version = 8,
    exportSchema = false,
)
abstract class RootMcDatabase : RoomDatabase() {
    abstract fun worldDao(): WorldDao
    abstract fun notebookDao(): NotebookDao
    abstract fun noteDao(): NoteDao
    abstract fun tagDao(): TagDao
    abstract fun coordinateDao(): CoordinateDao
    abstract fun areaDao(): AreaDao
    abstract fun mediaDao(): MediaDao
    abstract fun noteLinkDao(): NoteLinkDao
    abstract fun buildPlanDao(): BuildPlanDao
    abstract fun referenceDao(): ReferenceDao
    abstract fun timelineDao(): TimelineDao
    abstract fun syncSnapshotDao(): SyncSnapshotDao
    abstract fun chamberDao(): ChamberDao
    abstract fun ingameImportDao(): IngameImportDao
}
