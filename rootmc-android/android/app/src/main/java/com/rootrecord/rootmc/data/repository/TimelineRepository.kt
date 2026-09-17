package com.rootrecord.rootmc.data.repository

import com.rootrecord.rootmc.data.local.dao.TimelineDao
import com.rootrecord.rootmc.data.local.entity.ProjectTimelineEventEntity
import com.rootrecord.rootmc.di.IoDispatcher
import com.rootrecord.rootmc.sync.CloudBackupCoordinator
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TimelineRepository @Inject constructor(
    private val timelineDao: TimelineDao,
    private val cloudBackup: CloudBackupCoordinator,
    @param:IoDispatcher private val io: CoroutineDispatcher,
) {
    suspend fun log(
        worldId: Long,
        eventType: String,
        description: String,
        noteId: Long? = null,
    ) = withContext(io) {
        timelineDao.insert(
            ProjectTimelineEventEntity(
                worldId = worldId,
                noteId = noteId,
                eventType = eventType,
                description = description,
            ),
        )
        cloudBackup.scheduleBackupAfterLocalChange()
    }
}
