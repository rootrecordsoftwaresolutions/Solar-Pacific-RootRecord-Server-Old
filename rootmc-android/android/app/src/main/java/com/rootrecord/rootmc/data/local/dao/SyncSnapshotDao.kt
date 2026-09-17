package com.rootrecord.rootmc.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.rootrecord.rootmc.data.local.entity.AreaEntity
import com.rootrecord.rootmc.data.local.entity.BuildPlanEntity
import com.rootrecord.rootmc.data.local.entity.BuildPlanItemEntity
import com.rootrecord.rootmc.data.local.entity.CoordinateEntity
import com.rootrecord.rootmc.data.local.entity.MediaAttachmentEntity
import com.rootrecord.rootmc.data.local.entity.NoteEntity
import com.rootrecord.rootmc.data.local.entity.NoteLinkEntity
import com.rootrecord.rootmc.data.local.entity.NoteTagCrossRef
import com.rootrecord.rootmc.data.local.entity.NotebookEntity
import com.rootrecord.rootmc.data.local.entity.ProjectTimelineEventEntity
import com.rootrecord.rootmc.data.local.entity.SpawnerTrackEntity
import com.rootrecord.rootmc.data.local.entity.TrialChamberEntity
import com.rootrecord.rootmc.data.local.entity.TagEntity
import com.rootrecord.rootmc.data.local.entity.WorldEntity
import com.rootrecord.rootmc.data.sync.RootMcCloudSnapshot

@Dao
interface SyncSnapshotDao {

    @Query("SELECT * FROM worlds ORDER BY sortOrder ASC, id ASC")
    suspend fun allWorlds(): List<WorldEntity>

    @Query("SELECT * FROM notebooks ORDER BY worldId ASC, sortOrder ASC, id ASC")
    suspend fun allNotebooks(): List<NotebookEntity>

    @Query("SELECT * FROM notes ORDER BY id ASC")
    suspend fun allNotes(): List<NoteEntity>

    @Query("SELECT * FROM tags ORDER BY name ASC")
    suspend fun allTags(): List<TagEntity>

    @Query("SELECT * FROM note_tag_cross_ref")
    suspend fun allNoteTagCrossRefs(): List<NoteTagCrossRef>

    @Query("SELECT * FROM coordinates ORDER BY worldId ASC, sortOrder ASC, id ASC")
    suspend fun allCoordinates(): List<CoordinateEntity>

    @Query("SELECT * FROM areas ORDER BY worldId ASC, sortOrder ASC, id ASC")
    suspend fun allAreas(): List<AreaEntity>

    @Query("SELECT * FROM note_links")
    suspend fun allNoteLinks(): List<NoteLinkEntity>

    @Query("SELECT * FROM build_plans ORDER BY id ASC")
    suspend fun allBuildPlans(): List<BuildPlanEntity>

    @Query("SELECT * FROM build_plan_items ORDER BY buildPlanId ASC, id ASC")
    suspend fun allBuildPlanItems(): List<BuildPlanItemEntity>

    @Query("SELECT * FROM timeline_events ORDER BY id ASC")
    suspend fun allTimelineEvents(): List<ProjectTimelineEventEntity>

    @Query("SELECT * FROM trial_chambers ORDER BY worldId ASC, sortOrder ASC, id ASC")
    suspend fun allTrialChambers(): List<TrialChamberEntity>

    @Query("SELECT * FROM spawner_tracks ORDER BY chamberId ASC, sortOrder ASC, id ASC")
    suspend fun allSpawnerTracks(): List<SpawnerTrackEntity>

    @Query("SELECT * FROM media_attachments ORDER BY id ASC")
    suspend fun allMediaAttachments(): List<MediaAttachmentEntity>

    @Query("DELETE FROM build_plan_items")
    suspend fun clearBuildPlanItems()

    @Query("DELETE FROM build_plans")
    suspend fun clearBuildPlans()

    @Query("DELETE FROM timeline_events")
    suspend fun clearTimelineEvents()

    @Query("DELETE FROM spawner_tracks")
    suspend fun clearSpawnerTracks()

    @Query("DELETE FROM trial_chambers")
    suspend fun clearTrialChambers()

    @Query("DELETE FROM media_attachments")
    suspend fun clearMediaAttachments()

    @Query("DELETE FROM note_links")
    suspend fun clearNoteLinks()

    @Query("DELETE FROM note_tag_cross_ref")
    suspend fun clearNoteTagCrossRefs()

    @Query("DELETE FROM notes")
    suspend fun clearNotes()

    @Query("DELETE FROM coordinates")
    suspend fun clearCoordinates()

    @Query("DELETE FROM areas")
    suspend fun clearAreas()

    @Query("DELETE FROM notebooks")
    suspend fun clearNotebooks()

    @Query("DELETE FROM tags")
    suspend fun clearTags()

    @Query("DELETE FROM worlds")
    suspend fun clearWorlds()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWorlds(items: List<WorldEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNotebooks(items: List<NotebookEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNotes(items: List<NoteEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTags(items: List<TagEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNoteTagCrossRefs(items: List<NoteTagCrossRef>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCoordinates(items: List<CoordinateEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAreas(items: List<AreaEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNoteLinks(items: List<NoteLinkEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBuildPlans(items: List<BuildPlanEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBuildPlanItems(items: List<BuildPlanItemEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTimelineEvents(items: List<ProjectTimelineEventEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTrialChambers(items: List<TrialChamberEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSpawnerTracks(items: List<SpawnerTrackEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMediaAttachments(items: List<MediaAttachmentEntity>)

    @Transaction
    suspend fun replaceAllFromSnapshot(snapshot: RootMcCloudSnapshot) {
        clearBuildPlanItems()
        clearBuildPlans()
        clearSpawnerTracks()
        clearTrialChambers()
        clearTimelineEvents()
        clearMediaAttachments()
        clearNoteLinks()
        clearNoteTagCrossRefs()
        clearNotes()
        clearCoordinates()
        clearAreas()
        clearNotebooks()
        clearTags()
        clearWorlds()

        if (snapshot.worlds.isNotEmpty()) insertWorlds(snapshot.worlds)
        if (snapshot.notebooks.isNotEmpty()) insertNotebooks(snapshot.notebooks)
        if (snapshot.notes.isNotEmpty()) insertNotes(snapshot.notes)
        if (snapshot.tags.isNotEmpty()) insertTags(snapshot.tags)
        if (snapshot.noteTagCrossRefs.isNotEmpty()) insertNoteTagCrossRefs(snapshot.noteTagCrossRefs)
        if (snapshot.coordinates.isNotEmpty()) insertCoordinates(snapshot.coordinates)
        if (snapshot.areas.isNotEmpty()) insertAreas(snapshot.areas)
        if (snapshot.noteLinks.isNotEmpty()) insertNoteLinks(snapshot.noteLinks)
        if (snapshot.buildPlans.isNotEmpty()) insertBuildPlans(snapshot.buildPlans)
        if (snapshot.buildPlanItems.isNotEmpty()) insertBuildPlanItems(snapshot.buildPlanItems)
        if (snapshot.timelineEvents.isNotEmpty()) insertTimelineEvents(snapshot.timelineEvents)
        if (snapshot.trialChambers.isNotEmpty()) insertTrialChambers(snapshot.trialChambers)
        if (snapshot.spawnerTracks.isNotEmpty()) insertSpawnerTracks(snapshot.spawnerTracks)
        if (snapshot.mediaAttachments.isNotEmpty()) insertMediaAttachments(snapshot.mediaAttachments)
    }
}
