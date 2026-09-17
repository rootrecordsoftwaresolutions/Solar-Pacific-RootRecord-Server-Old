package com.rootrecord.rootmc.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.rootrecord.rootmc.data.local.entity.AreaEntity
import com.rootrecord.rootmc.data.local.entity.BuildPlanEntity
import com.rootrecord.rootmc.data.local.entity.BuildPlanItemEntity
import com.rootrecord.rootmc.data.local.entity.CoordinateEntity
import com.rootrecord.rootmc.data.local.entity.IngameImportEntity
import com.rootrecord.rootmc.data.local.entity.MediaAttachmentEntity
import com.rootrecord.rootmc.data.local.entity.NoteEntity
import com.rootrecord.rootmc.data.local.entity.NoteLinkEntity
import com.rootrecord.rootmc.data.local.entity.NoteTagCrossRef
import com.rootrecord.rootmc.data.local.entity.NotebookEntity
import com.rootrecord.rootmc.data.local.entity.ProjectTimelineEventEntity
import com.rootrecord.rootmc.data.local.entity.ReferenceCacheEntity
import com.rootrecord.rootmc.data.local.entity.TagEntity
import com.rootrecord.rootmc.data.local.entity.WorldEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface WorldDao {
    @Query("SELECT * FROM worlds ORDER BY sortOrder ASC, name ASC")
    fun observeAll(): Flow<List<WorldEntity>>

    @Query("SELECT * FROM worlds WHERE id = :id")
    suspend fun getById(id: Long): WorldEntity?

    @Query("SELECT * FROM worlds WHERE isActive = 1 LIMIT 1")
    fun observeActive(): Flow<WorldEntity?>

    @Query("SELECT * FROM worlds WHERE isActive = 1 LIMIT 1")
    suspend fun getActive(): WorldEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(world: WorldEntity): Long

    @Update
    suspend fun update(world: WorldEntity)

    @Delete
    suspend fun delete(world: WorldEntity)

    @Query("UPDATE worlds SET isActive = 0")
    suspend fun clearActiveFlags()

    @Query("UPDATE worlds SET isActive = 1 WHERE id = :worldId")
    suspend fun setActive(worldId: Long)

    @Query("SELECT COUNT(*) FROM worlds")
    suspend fun count(): Int

    @Query("SELECT * FROM worlds WHERE serverAddress = :address LIMIT 1")
    suspend fun findByServerAddress(address: String): WorldEntity?

    @Query("SELECT * FROM worlds WHERE serverAddress = :address ORDER BY sortOrder ASC, name ASC")
    suspend fun listByServerAddress(address: String): List<WorldEntity>
}

@Dao
interface NotebookDao {
    @Query("SELECT * FROM notebooks WHERE worldId = :worldId ORDER BY sortOrder ASC, name ASC")
    fun observeByWorld(worldId: Long): Flow<List<NotebookEntity>>

    @Query("SELECT * FROM notebooks WHERE id = :id")
    suspend fun getById(id: Long): NotebookEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(notebook: NotebookEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(notebooks: List<NotebookEntity>): List<Long>

    @Update
    suspend fun update(notebook: NotebookEntity)

    @Delete
    suspend fun delete(notebook: NotebookEntity)

    @Query("SELECT COUNT(*) FROM notebooks WHERE worldId = :worldId")
    suspend fun countByWorld(worldId: Long): Int

    @Query("SELECT * FROM notebooks WHERE worldId = :worldId AND name = :name LIMIT 1")
    suspend fun findByName(worldId: Long, name: String): NotebookEntity?
}

@Dao
interface NoteDao {
    @Query(
        """
        SELECT * FROM notes
        WHERE notebookId = :notebookId AND deleted = 0
        ORDER BY pinned DESC, updatedAt DESC
        """,
    )
    fun observeByNotebook(notebookId: Long): Flow<List<NoteEntity>>

    @Query("SELECT * FROM notes WHERE id = :id")
    suspend fun getById(id: Long): NoteEntity?

    @Query("SELECT * FROM notes WHERE id = :id")
    fun observeById(id: Long): Flow<NoteEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(note: NoteEntity): Long

    @Update
    suspend fun update(note: NoteEntity)

    @Query("UPDATE notes SET deleted = 1, updatedAt = :updatedAt WHERE id = :id")
    suspend fun softDelete(id: Long, updatedAt: Long = System.currentTimeMillis())

    @Query("UPDATE notes SET deleted = 0, updatedAt = :updatedAt WHERE id = :id")
    suspend fun restore(id: Long, updatedAt: Long = System.currentTimeMillis())

    @Query("DELETE FROM notes WHERE id = :id")
    suspend fun hardDelete(id: Long)

    @Query("SELECT id FROM notes WHERE deleted = 1")
    suspend fun trashIds(): List<Long>

    @Query("DELETE FROM notes WHERE deleted = 1")
    suspend fun deleteAllTrash()

    @Query(
        """
        SELECT notes.* FROM notes
        INNER JOIN notes_fts ON notes.rowid = notes_fts.rowid
        WHERE notes_fts MATCH :query AND notes.deleted = 0
        ORDER BY notes.updatedAt DESC
        """,
    )
    suspend fun searchFts(query: String): List<NoteEntity>

    @Query(
        """
        SELECT notes.* FROM notes
        INNER JOIN notes_fts ON notes.rowid = notes_fts.rowid
        WHERE notes_fts MATCH :query AND notes.deleted = 0
        ORDER BY notes.updatedAt DESC
        """,
    )
    fun observeSearchFts(query: String): Flow<List<NoteEntity>>

    @Query(
        """
        SELECT DISTINCT notes.* FROM notes
        INNER JOIN note_tag_cross_ref ON notes.id = note_tag_cross_ref.noteId
        WHERE note_tag_cross_ref.tagId IN (:tagIds) AND notes.deleted = 0
        ORDER BY notes.pinned DESC, notes.updatedAt DESC
        """,
    )
    fun observeByTags(tagIds: List<Long>): Flow<List<NoteEntity>>

    @Query(
        """
        SELECT notes.* FROM notes
        WHERE deleted = 1
        ORDER BY updatedAt DESC
        """,
    )
    fun observeTrash(): Flow<List<NoteEntity>>

    @Query(
        """
        SELECT notes.* FROM notes
        INNER JOIN notebooks ON notes.notebookId = notebooks.id
        WHERE notebooks.worldId = :worldId AND notes.deleted = 0
        ORDER BY notes.updatedAt DESC
        """,
    )
    fun observeByWorld(worldId: Long): Flow<List<NoteEntity>>

    @Query(
        """
        SELECT * FROM notes
        WHERE waypointId = :waypointId AND deleted = 0
        ORDER BY pinned DESC, updatedAt DESC
        """,
    )
    fun observeByWaypoint(waypointId: Long): Flow<List<NoteEntity>>

    @Query(
        """
        SELECT * FROM notes
        WHERE areaId = :areaId AND deleted = 0
        ORDER BY pinned DESC, updatedAt DESC
        """,
    )
    fun observeByArea(areaId: Long): Flow<List<NoteEntity>>
}

@Dao
interface TagDao {
    @Query("SELECT * FROM tags ORDER BY name ASC")
    fun observeAll(): Flow<List<TagEntity>>

    @Query("SELECT * FROM tags WHERE id = :id")
    suspend fun getById(id: Long): TagEntity?

    @Query("SELECT tags.* FROM tags INNER JOIN note_tag_cross_ref ON tags.id = note_tag_cross_ref.tagId WHERE note_tag_cross_ref.noteId = :noteId")
    suspend fun getTagsForNote(noteId: Long): List<TagEntity>

    @Query("SELECT tags.* FROM tags INNER JOIN note_tag_cross_ref ON tags.id = note_tag_cross_ref.tagId WHERE note_tag_cross_ref.noteId = :noteId")
    fun observeTagsForNote(noteId: Long): Flow<List<TagEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(tag: TagEntity): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertCrossRef(crossRef: NoteTagCrossRef)

    @Query("DELETE FROM note_tag_cross_ref WHERE noteId = :noteId AND tagId = :tagId")
    suspend fun deleteCrossRef(noteId: Long, tagId: Long)

    @Query("DELETE FROM note_tag_cross_ref WHERE noteId = :noteId")
    suspend fun clearTagsForNote(noteId: Long)

    @Delete
    suspend fun delete(tag: TagEntity)

    @Query("SELECT * FROM tags WHERE name = :name LIMIT 1")
    suspend fun findByName(name: String): TagEntity?
}

@Dao
interface CoordinateDao {
    @Query("SELECT * FROM coordinates WHERE worldId = :worldId ORDER BY sortOrder ASC, id ASC")
    fun observeByWorld(worldId: Long): Flow<List<CoordinateEntity>>

    @Query("SELECT * FROM coordinates WHERE noteId = :noteId ORDER BY sortOrder ASC, id ASC")
    fun observeByNote(noteId: Long): Flow<List<CoordinateEntity>>

    @Query("SELECT COALESCE(MAX(sortOrder), -1) FROM coordinates WHERE worldId = :worldId")
    suspend fun maxSortOrderForWorld(worldId: Long): Int

    @Query("SELECT * FROM coordinates WHERE id = :id")
    suspend fun getById(id: Long): CoordinateEntity?

    @Query("SELECT * FROM coordinates WHERE worldId = :worldId AND areaId IS NULL")
    suspend fun listWithoutArea(worldId: Long): List<CoordinateEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(coordinate: CoordinateEntity): Long

    @Update
    suspend fun update(coordinate: CoordinateEntity)

    @Delete
    suspend fun delete(coordinate: CoordinateEntity)

    @Query("DELETE FROM coordinates WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("SELECT * FROM coordinates WHERE areaId = :areaId ORDER BY sortOrder ASC, id ASC")
    fun observeByArea(areaId: Long): Flow<List<CoordinateEntity>>
}

@Dao
interface IngameImportDao {
    @Query("SELECT EXISTS(SELECT 1 FROM ingame_imports WHERE ingameEventId = :eventId)")
    suspend fun exists(eventId: Long): Boolean

    @Query("SELECT localId FROM ingame_imports WHERE localType = :localType AND localId = :localId LIMIT 1")
    suspend fun findLocal(localType: String, localId: Long): Long?

    @Query("SELECT localId FROM ingame_imports WHERE localType = 'waypoint'")
    suspend fun allWaypointIds(): List<Long>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(row: IngameImportEntity)
}

@Dao
interface AreaDao {
    @Query("SELECT * FROM areas WHERE worldId = :worldId ORDER BY sortOrder ASC, id ASC")
    fun observeByWorld(worldId: Long): Flow<List<AreaEntity>>

    @Query("SELECT * FROM areas WHERE id = :id")
    suspend fun getById(id: Long): AreaEntity?

    @Query("SELECT * FROM areas WHERE id = :id")
    fun observeById(id: Long): Flow<AreaEntity?>

    @Query(
        """
        SELECT * FROM areas
        WHERE worldId = :worldId AND dimension = :dimension
          AND chunkArea = 1 AND chunkX = :chunkX AND chunkZ = :chunkZ
        LIMIT 1
        """,
    )
    suspend fun findChunkArea(
        worldId: Long,
        dimension: String,
        chunkX: Int,
        chunkZ: Int,
    ): AreaEntity?

    @Query("SELECT COALESCE(MAX(sortOrder), -1) FROM areas WHERE worldId = :worldId")
    suspend fun maxSortOrderForWorld(worldId: Long): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(area: AreaEntity): Long

    @Update
    suspend fun update(area: AreaEntity)

    @Delete
    suspend fun delete(area: AreaEntity)

    @Query("DELETE FROM areas WHERE id = :id")
    suspend fun deleteById(id: Long)
}

@Dao
interface MediaDao {
    @Query("SELECT * FROM media_attachments WHERE noteId = :noteId ORDER BY createdAt ASC")
    fun observeByNote(noteId: Long): Flow<List<MediaAttachmentEntity>>

    @Query(
        """
        SELECT media_attachments.* FROM media_attachments
        INNER JOIN notes ON media_attachments.noteId = notes.id
        INNER JOIN notebooks ON notes.notebookId = notebooks.id
        WHERE notebooks.worldId = :worldId
        ORDER BY media_attachments.createdAt DESC
        """,
    )
    fun observeByWorld(worldId: Long): Flow<List<MediaAttachmentEntity>>

    @Query("SELECT * FROM media_attachments WHERE id = :id")
    suspend fun getById(id: Long): MediaAttachmentEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(attachment: MediaAttachmentEntity): Long

    @Delete
    suspend fun delete(attachment: MediaAttachmentEntity)

    @Query("DELETE FROM media_attachments WHERE noteId = :noteId")
    suspend fun deleteByNote(noteId: Long)
}

@Dao
interface NoteLinkDao {
    @Query(
        """
        SELECT notes.* FROM notes
        INNER JOIN note_links ON notes.id = note_links.toNoteId
        WHERE note_links.fromNoteId = :fromNoteId AND notes.deleted = 0
        """,
    )
    fun observeLinkedNotes(fromNoteId: Long): Flow<List<NoteEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(link: NoteLinkEntity)

    @Query("DELETE FROM note_links WHERE fromNoteId = :fromNoteId AND toNoteId = :toNoteId")
    suspend fun delete(fromNoteId: Long, toNoteId: Long)

    @Query("DELETE FROM note_links WHERE fromNoteId = :noteId OR toNoteId = :noteId")
    suspend fun deleteAllForNote(noteId: Long)
}

@Dao
interface BuildPlanDao {
    @Query("SELECT * FROM build_plans WHERE worldId = :worldId ORDER BY createdAt DESC")
    fun observeByWorld(worldId: Long): Flow<List<BuildPlanEntity>>

    @Query("SELECT * FROM build_plans WHERE noteId = :noteId LIMIT 1")
    suspend fun getByNoteId(noteId: Long): BuildPlanEntity?

    @Query("SELECT * FROM build_plans WHERE id = :id")
    suspend fun getById(id: Long): BuildPlanEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(plan: BuildPlanEntity): Long

    @Update
    suspend fun update(plan: BuildPlanEntity)

    @Delete
    suspend fun delete(plan: BuildPlanEntity)

    @Query("SELECT * FROM build_plan_items WHERE buildPlanId = :buildPlanId ORDER BY id ASC")
    fun observeItems(buildPlanId: Long): Flow<List<BuildPlanItemEntity>>

    @Query("SELECT * FROM build_plan_items WHERE buildPlanId = :buildPlanId ORDER BY id ASC")
    suspend fun getItems(buildPlanId: Long): List<BuildPlanItemEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertItem(item: BuildPlanItemEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertItems(items: List<BuildPlanItemEntity>)

    @Update
    suspend fun updateItem(item: BuildPlanItemEntity)

    @Delete
    suspend fun deleteItem(item: BuildPlanItemEntity)

    @Query("DELETE FROM build_plan_items WHERE buildPlanId = :buildPlanId")
    suspend fun deleteItemsForPlan(buildPlanId: Long)

    @Transaction
    suspend fun replaceItems(buildPlanId: Long, items: List<BuildPlanItemEntity>) {
        deleteItemsForPlan(buildPlanId)
        if (items.isNotEmpty()) insertItems(items)
    }
}

@Dao
interface ReferenceDao {
    @Query("SELECT * FROM reference_cache WHERE category = :category ORDER BY id ASC")
    fun observeByCategory(category: String): Flow<List<ReferenceCacheEntity>>

    @Query("SELECT * FROM reference_cache WHERE id = :id")
    suspend fun getById(id: String): ReferenceCacheEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entry: ReferenceCacheEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(entries: List<ReferenceCacheEntity>)

    @Query("SELECT DISTINCT category FROM reference_cache ORDER BY category ASC")
    fun observeCategories(): Flow<List<String>>

    @Query("DELETE FROM reference_cache WHERE category = :category")
    suspend fun deleteCategory(category: String)
}

@Dao
interface TimelineDao {
    @Query("SELECT * FROM timeline_events WHERE worldId = :worldId ORDER BY timestamp DESC")
    fun observeByWorld(worldId: Long): Flow<List<ProjectTimelineEventEntity>>

    @Query("SELECT * FROM timeline_events WHERE noteId = :noteId ORDER BY timestamp DESC")
    fun observeByNote(noteId: Long): Flow<List<ProjectTimelineEventEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(event: ProjectTimelineEventEntity): Long

    @Delete
    suspend fun delete(event: ProjectTimelineEventEntity)

    @Query("DELETE FROM timeline_events WHERE id = :id")
    suspend fun deleteById(id: Long)
}
